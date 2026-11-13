package br.dev.ispchat.erp.webhook;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Despachante assincrono de webhooks HTTP para ERPs (IXC, SGP, MK-Auth) com assinatura
 * HMAC-SHA256 (X-IspChat-Signature: sha256=<hex>) e garantia estrita At-Least-Once:
 * retem o envio de DELIVERY_ACK ao No ISP ate o ERP responder HTTP 2xx, retentando
 * falhas 5xx/429/timeout com backoff exponencial (1s..60s).
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    public record InboundWebhookPayload(
            String messageId,
            String senderUuid,
            boolean senderVerified,
            String corporateUuid,
            String text,
            long timestampMs) {
    }

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final String webhookUrl;
    private final byte[] webhookHmacSecret;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final int maxAttempts;
    private final Counter webhookRetriesCounter;

    private final Set<String> inFlightOrAckedMessages = ConcurrentHashMap.newKeySet();

    @Autowired
    public WebhookDispatcher(
            ObjectMapper objectMapper,
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Value("${ispchat.erp.webhook-url:}") String webhookUrl,
            @Value("${ispchat.erp.webhook-hmac-secret:}") String webhookHmacSecret,
            @Value("${ispchat.erp.webhook-initial-backoff-ms:1000}") long initialBackoffMs,
            @Value("${ispchat.erp.webhook-max-backoff-ms:60000}") long maxBackoffMs,
            @Value("${ispchat.erp.webhook-max-attempts:20}") int maxAttempts) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());
        this.webhookUrl = webhookUrl != null ? webhookUrl.trim() : "";
        this.webhookHmacSecret = webhookHmacSecret != null
                ? webhookHmacSecret.trim().getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        this.initialBackoffMs = Math.max(10L, initialBackoffMs);
        this.maxBackoffMs = Math.max(this.initialBackoffMs, maxBackoffMs);
        this.maxAttempts = Math.max(1, maxAttempts);

        MeterRegistry registry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.webhookRetriesCounter = Counter.builder("ispchat_erp_webhook_retries_total")
                .description("Total de retentativas de entrega de webhook ao ERP")
                .register(registry);
    }

    /**
     * Agenda a entrega do payload ao ERP; o callback onDelivered2xx(senderUuid, messageId)
     * e invocado SOMENTE apos confirmacao HTTP 2xx do ERP.
     */
    public void dispatchWithRetry(
            InboundWebhookPayload payload,
            BiConsumer<String, String> onDelivered2xx) {
        if (webhookUrl.isBlank()) {
            log.warn("Mensagem {} recebida, mas ispchat.erp.webhook-url nao esta configurado (DELIVERY_ACK retido)",
                    payload.messageId());
            return;
        }
        if (webhookHmacSecret.length == 0) {
            log.error("ispchat.erp.webhook-hmac-secret vazio: recusando despacho inseguro de webhook");
            return;
        }
        if (!inFlightOrAckedMessages.add(payload.messageId())) {
            return; // Ja em processamento ou ja confirmado com 2xx nesta sessao
        }

        final byte[] bodyUtf8;
        try {
            bodyUtf8 = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            inFlightOrAckedMessages.remove(payload.messageId());
            log.error("Falha ao serializar JSON do webhook para messageId={}", payload.messageId(), e);
            return;
        }

        String signatureHeader = computeSignatureHeader(webhookHmacSecret, bodyUtf8);
        scheduler.execute(() -> attemptPost(payload, bodyUtf8, signatureHeader, 1, initialBackoffMs, onDelivered2xx));
    }

    public static String computeSignatureHeader(byte[] secretBytes, byte[] rawBodyUtf8) {
        byte[] mac = CanonicalTranscripts.hmacSha256(secretBytes, rawBodyUtf8);
        return "sha256=" + HexFormat.of().formatHex(mac);
    }

    private void attemptPost(
            InboundWebhookPayload payload,
            byte[] bodyUtf8,
            String signatureHeader,
            int attempt,
            long currentBackoffMs,
            BiConsumer<String, String> onDelivered2xx) {
        if (scheduler.isShutdown()) {
            inFlightOrAckedMessages.remove(payload.messageId());
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-IspChat-Message-Id", payload.messageId())
                    .header("X-IspChat-Signature", signatureHeader)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyUtf8))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status <= 299) {
                onDelivered2xx.accept(payload.senderUuid(), payload.messageId());
                return;
            }

            if (status >= 400 && status < 500 && status != 429) {
                log.error("Webhook ERP rejeitou permanentemente messageId={} com HTTP {} (DELIVERY_ACK retido)",
                        payload.messageId(), status);
                inFlightOrAckedMessages.remove(payload.messageId());
                return;
            }

            scheduleNextRetry(
                    payload, bodyUtf8, signatureHeader, attempt, currentBackoffMs, onDelivered2xx, "HTTP " + status);
        } catch (Exception e) {
            scheduleNextRetry(
                    payload, bodyUtf8, signatureHeader, attempt, currentBackoffMs, onDelivered2xx, e.getMessage());
        }
    }

    private void scheduleNextRetry(
            InboundWebhookPayload payload,
            byte[] bodyUtf8,
            String signatureHeader,
            int attempt,
            long currentBackoffMs,
            BiConsumer<String, String> onDelivered2xx,
            String failureReason) {
        if (attempt >= maxAttempts || scheduler.isShutdown()) {
            log.error("Esgotadas {} tentativas de entrega de webhook para messageId={} (ultimo erro: {})",
                    attempt, payload.messageId(), failureReason);
            inFlightOrAckedMessages.remove(payload.messageId());
            return;
        }

        webhookRetriesCounter.increment();
        long nextBackoff = Math.min(maxBackoffMs, currentBackoffMs * 2L);
        log.warn("Falha na tentativa {} de webhook para messageId={} ({}). Retentando em {} ms (DELIVERY_ACK retido)",
                attempt, payload.messageId(), failureReason, currentBackoffMs);

        scheduler.schedule(
                () -> attemptPost(payload, bodyUtf8, signatureHeader, attempt + 1, nextBackoff, onDelivered2xx),
                currentBackoffMs,
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
