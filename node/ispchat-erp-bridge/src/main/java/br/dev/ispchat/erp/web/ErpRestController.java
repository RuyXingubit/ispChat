package br.dev.ispchat.erp.web;

import br.dev.ispchat.erp.service.ErpGatewayService;
import br.dev.ispchat.erp.service.ErpGatewayService.ErpDeliveryException;
import br.dev.ispchat.erp.service.ErpGatewayService.SendMessageResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoints REST canonicos na LAN do ISP para integracao de ERPs (IXC, SGP, MK-Auth),
 * protegidos por autenticacao Authorization: Bearer verificada em tempo constante.
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnBean(ErpGatewayService.class)
public class ErpRestController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MIN_SECRET_LENGTH = 16;

    public record SendErpMessageRequest(
            String recipientUuid,
            String text,
            String idempotencyKey,
            String erpAdapter) {
    }

    private final ErpGatewayService gatewayService;
    private final byte[] expectedBearerSecretUtf8;

    public ErpRestController(
            ErpGatewayService gatewayService,
            @Value("${ispchat.erp.api-bearer-secret}") String apiBearerSecret) {
        if (apiBearerSecret == null || apiBearerSecret.trim().length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException(
                    "ispchat.erp.api-bearer-secret deve possuir pelo menos " + MIN_SECRET_LENGTH + " caracteres");
        }
        this.gatewayService = gatewayService;
        this.expectedBearerSecretUtf8 = apiBearerSecret.trim().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody(required = false) SendErpMessageRequest body) {
        if (!isAuthorizedBearerConstantTime(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHORIZED", "message", "Token Bearer invalido ou ausente"));
        }
        if (body == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_BODY", "message", "Payload JSON obrigatorio"));
        }

        try {
            SendMessageResult result = gatewayService.sendCorporateMessage(
                    body.recipientUuid(),
                    body.text(),
                    body.idempotencyKey(),
                    body.erpAdapter());

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "messageId", result.messageId(),
                            "status", result.status()));
        } catch (ErpDeliveryException e) {
            Map<String, Object> errBody = new LinkedHashMap<>();
            errBody.put("error", e.errorCode());
            errBody.put("message", e.getMessage());
            if (e.replacedByUuid() != null && !e.replacedByUuid().isBlank()) {
                errBody.put("replacedByUuid", e.replacedByUuid());
            }
            return ResponseEntity.status(e.httpStatus()).body(errBody);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (!isAuthorizedBearerConstantTime(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHORIZED", "message", "Token Bearer invalido ou ausente"));
        }
        boolean connected = gatewayService.isStreamConnected();
        HttpStatus status = connected ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(Map.of(
                "status", connected ? "UP" : "DEGRADED",
                "streamConnected", connected,
                "corporateUuid", gatewayService.getCorporateUuid(),
                "deviceId", gatewayService.getDeviceId()));
    }

    private boolean isAuthorizedBearerConstantTime(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] provided = authHeader.substring(BEARER_PREFIX.length()).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBearerSecretUtf8, provided);
    }
}
