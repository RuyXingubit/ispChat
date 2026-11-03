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
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Set;

/**
 * Interceptor de seguranca da fronteira na porta 443.
 *
 * Como o servidor gRPC opera na porta 443 com ClientAuth.OPTIONAL para permitir tanto
 * conexoes de aplicativos Flutter (Fronteira de Borda) quanto conexoes de outros Nos ISP
 * (Fronteira de Federacao), este Interceptor exige obrigatoriamente um certificado X.509
 * de cliente valido (assinado pela CA do ispChat) para qualquer chamada direcionada aos
 * servicos de federacao (DhtPeerService e NodeRoutingService).
 */
@Component
public class MtlsFederationInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MtlsFederationInterceptor.class);

    public static final Context.Key<X509Certificate> PEER_CERT_CONTEXT_KEY =
            Context.key("ispchat-peer-x509-cert");

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
        if (!FEDERATION_SERVICES.contains(serviceName)) {
            // Servicos de borda (ClientStreamService, DhtClientService) sao autenticados via assinatura Ed25519
            return next.startCall(call, headers);
        }

        SSLSession sslSession = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
        if (sslSession == null) {
            log.warn("Bloqueada tentativa de acesso sem TLS ao servico de federacao: {}", serviceName);
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Conexao TLS/mTLS obrigatoria para servicos de federacao"),
                    new Metadata()
            );
            return (ServerCall.Listener<ReqT>) NOOP_LISTENER;
        }

        try {
            Certificate[] peerCertificates = sslSession.getPeerCertificates();
            if (peerCertificates == null || peerCertificates.length == 0
                    || !(peerCertificates[0] instanceof X509Certificate peerCert)) {
                log.warn("Bloqueado acesso sem certificado X.509 de cliente ao servico: {}", serviceName);
                call.close(
                        Status.UNAUTHENTICATED.withDescription("Certificado mTLS da CA ispChat obrigatorio"),
                        new Metadata()
                );
                return (ServerCall.Listener<ReqT>) NOOP_LISTENER;
            }

            peerCert.checkValidity();
            Context contextWithCert = Context.current().withValue(PEER_CERT_CONTEXT_KEY, peerCert);
            return Contexts.interceptCall(contextWithCert, call, headers, next);
        } catch (SSLPeerUnverifiedException e) {
            log.warn("Rejeitada chamada ao servico de federacao {} sem certificado mTLS de cliente", serviceName);
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Certificado de cliente mTLS ausente ou nao verificado pela CA"),
                    new Metadata()
            );
            return (ServerCall.Listener<ReqT>) NOOP_LISTENER;
        } catch (Exception e) {
            log.warn("Certificado mTLS invalido/expirado ao acessar {}: {}", serviceName, e.getMessage());
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Certificado mTLS invalido: " + e.getMessage()),
                    new Metadata()
            );
            return (ServerCall.Listener<ReqT>) NOOP_LISTENER;
        }
    }
}
