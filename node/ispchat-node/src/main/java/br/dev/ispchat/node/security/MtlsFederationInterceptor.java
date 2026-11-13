package br.dev.ispchat.node.security;

import br.dev.ispchat.grpc.DhtPeerServiceGrpc;
import br.dev.ispchat.grpc.NodeRoutingServiceGrpc;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.net.SocketAddress;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Interceptor de seguranca da fronteira na porta 443/8443.
 *
 * Como o servidor gRPC opera na porta 443 com ClientAuth.OPTIONAL para permitir tanto
 * conexoes de aplicativos Flutter (Fronteira de Borda) quanto conexoes de outros Nos ISP
 * (Fronteira de Federacao), este Interceptor exige obrigatoriamente um certificado X.509
 * de cliente valido (assinado pela CA do ispChat) para qualquer chamada direcionada aos
 * servicos de federacao (DhtPeerService e NodeRoutingService).
 *
 * Adicionalmente, extrai os Subject Alternative Names (DNS e IP) verificados pela CA
 * para o Contexto gRPC (PEER_SAN_CONTEXT_KEY) e registra o endereco remoto TCP
 * (REMOTE_ADDR_CONTEXT_KEY) para rate limiting por prefixo (/32 IPv4 e /64 IPv6).
 */
@Component
public class MtlsFederationInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MtlsFederationInterceptor.class);

    public static final Context.Key<X509Certificate> PEER_CERT_CONTEXT_KEY =
            Context.key("ispchat-peer-x509-cert");

    public static final Context.Key<Set<String>> PEER_SAN_CONTEXT_KEY =
            Context.keyWithDefault("ispchat-peer-x509-sans", Set.of());

    public static final Context.Key<SocketAddress> REMOTE_ADDR_CONTEXT_KEY =
            Context.key("ispchat-remote-addr");

    private static final int SAN_TYPE_DNS_NAME = 2;
    private static final int SAN_TYPE_IP_ADDRESS = 7;

    private static final Set<String> FEDERATION_SERVICES = Set.of(
            DhtPeerServiceGrpc.SERVICE_NAME,
            NodeRoutingServiceGrpc.SERVICE_NAME
    );

    private static final ServerCall.Listener<?> NOOP_LISTENER = new ServerCall.Listener<>() {};

    @Override
    @SuppressWarnings("unchecked")
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String serviceName = call.getMethodDescriptor().getServiceName();
        SocketAddress remoteAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        Context baseContext = Context.current().withValue(REMOTE_ADDR_CONTEXT_KEY, remoteAddr);

        SSLSession sslSession = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
        boolean requiresFederationMtls = FEDERATION_SERVICES.contains(serviceName);

        if (sslSession == null) {
            if (requiresFederationMtls) {
                log.warn("Bloqueada tentativa de acesso sem TLS ao servico de federacao: {}", serviceName);
                call.close(
                        Status.UNAUTHENTICATED.withDescription("Conexao TLS/mTLS obrigatoria para servicos de federacao"),
                        new Metadata()
                );
                return (ServerCall.Listener<ReqT>) NOOP_LISTENER;
            }
            return Contexts.interceptCall(baseContext, call, headers, next);
        }

        try {
            Certificate[] peerCertificates = sslSession.getPeerCertificates();
            if (peerCertificates == null || peerCertificates.length == 0
                    || !(peerCertificates[0] instanceof X509Certificate peerCert)) {
                if (requiresFederationMtls) {
                    log.warn("Bloqueado acesso sem certificado X.509 de cliente ao servico: {}", serviceName);
                    call.close(
                            Status.UNAUTHENTICATED.withDescription("Certificado mTLS da CA ispChat obrigatorio"),
                            new Metadata()
                    );
                    return (ServerCall.Listener<ReqT>) NOOP_LISTENER;
                }
                return Contexts.interceptCall(baseContext, call, headers, next);
            }

            peerCert.checkValidity();
            Set<String> peerSans = extractPeerSans(peerCert);
            Context contextWithCert = baseContext
                    .withValue(PEER_CERT_CONTEXT_KEY, peerCert)
                    .withValue(PEER_SAN_CONTEXT_KEY, peerSans);
            return Contexts.interceptCall(contextWithCert, call, headers, next);
        } catch (SSLPeerUnverifiedException e) {
            if (requiresFederationMtls) {
                log.warn("Rejeitada chamada ao servico de federacao {} sem certificado mTLS de cliente", serviceName);
                call.close(
                        Status.UNAUTHENTICATED.withDescription("Certificado de cliente mTLS ausente ou nao verificado pela CA"),
                        new Metadata()
                );
                return (ServerCall.Listener<ReqT>) NOOP_LISTENER;
            }
            // Servicos de borda (ClientStreamService, DhtClientService, TurnCredentialService, Proxies) sao autenticados via Ed25519
            return Contexts.interceptCall(baseContext, call, headers, next);
        } catch (Exception e) {
            log.warn("Certificado mTLS invalido/expirado ao acessar {}: {}", serviceName, e.getMessage());
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Certificado mTLS invalido: " + e.getMessage()),
                    new Metadata()
            );
            return (ServerCall.Listener<ReqT>) NOOP_LISTENER;
        }
    }

    /**
     * Extrai todos os Subject Alternative Names (dNSName=2 e iPAddress=7) normalizados em lowercase.
     */
    public static Set<String> extractPeerSans(X509Certificate cert) {
        if (cert == null) {
            return Set.of();
        }
        try {
            Collection<List<?>> altNames = cert.getSubjectAlternativeNames();
            if (altNames == null || altNames.isEmpty()) {
                return Set.of();
            }
            Set<String> extracted = new LinkedHashSet<>();
            for (List<?> entry : altNames) {
                if (entry == null || entry.size() < 2) {
                    continue;
                }
                Object typeObj = entry.get(0);
                Object valueObj = entry.get(1);
                if (typeObj instanceof Integer type
                        && (type == SAN_TYPE_DNS_NAME || type == SAN_TYPE_IP_ADDRESS)
                        && valueObj instanceof String value
                        && !value.isBlank()) {
                    extracted.add(value.trim().toLowerCase(Locale.ROOT));
                }
            }
            return Collections.unmodifiableSet(extracted);
        } catch (CertificateParsingException e) {
            return Set.of();
        }
    }
}
