package br.dev.ispchat.node.grpc;

import br.dev.ispchat.node.security.MtlsFederationInterceptor;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Inicializador do servidor gRPC unificado na porta 443 com TLS 1.3 (ClientAuth.OPTIONAL)
 * e enforcement de mTLS por servico via MtlsFederationInterceptor.
 */
@Component
@ConditionalOnProperty(name = "grpc.server.enabled", havingValue = "true", matchIfMissing = true)
public class GrpcServerRunner {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerRunner.class);

    private Server server;

    private final List<BindableService> grpcServices;
    private final MtlsFederationInterceptor mtlsFederationInterceptor;

    @Value("${grpc.server.port:443}")
    private int port;

    @Value("${ispchat.security.ca-cert-path:../ca/ca.crt}")
    private String caCertPath;

    @Value("${ispchat.security.node-cert-path:../ca/dev-node.crt}")
    private String nodeCertPath;

    @Value("${ispchat.security.node-key-path:../ca/dev-node.key}")
    private String nodeKeyPath;

    public GrpcServerRunner(
            List<BindableService> grpcServices,
            MtlsFederationInterceptor mtlsFederationInterceptor) {
        this.grpcServices = grpcServices;
        this.mtlsFederationInterceptor = mtlsFederationInterceptor;
    }

    @PostConstruct
    public void start() throws IOException {
        File caCert = resolveCertFile(caCertPath);
        File nodeCert = resolveCertFile(nodeCertPath);
        File nodeKey = resolveCertFile(nodeKeyPath);

        SslContext sslContext = buildServerSslContext(caCert, nodeCert, nodeKey);

        NettyServerBuilder builder = NettyServerBuilder.forPort(port)
                .sslContext(sslContext);

        for (BindableService service : grpcServices) {
            builder.addService(ServerInterceptors.intercept(service, mtlsFederationInterceptor));
        }

        server = builder.build().start();
        log.info("Servidor gRPC TLS 1.3 (mTLS Federation + Ed25519 Edge) iniciado na porta {} com {} servico(s)",
                server.getPort(), grpcServices.size());
    }

    /**
     * Constroi o contexto SSL/TLS 1.3 do servidor com ClientAuth.OPTIONAL.
     * Qualquer cliente que apresentar um certificado nao assinado pela CA raiz falha no handshake;
     * clientes sem certificado (Apps Flutter) completam o TLS 1.3 mas sao barrados pelo
     * MtlsFederationInterceptor caso tentem invocar servicos de federacao.
     */
    public static SslContext buildServerSslContext(File caCert, File nodeCert, File nodeKey) throws SSLException {
        return GrpcSslContexts.forServer(nodeCert, nodeKey)
                .trustManager(caCert)
                .clientAuth(ClientAuth.OPTIONAL)
                .protocols("TLSv1.3")
                .build();
    }

    private File resolveCertFile(String configuredPath) {
        File file = new File(configuredPath);
        if (file.exists()) {
            return file;
        }
        // Fallback caso o working directory seja a raiz do repositorio em vez de node/
        String normalized = configuredPath.startsWith("../")
                ? configuredPath.substring(3)
                : configuredPath;
        File fallback = new File(normalized);
        if (fallback.exists()) {
            return fallback;
        }
        throw new IllegalStateException("Arquivo de certificado/chave nao encontrado: " + configuredPath);
    }

    public int getPort() {
        return server != null ? server.getPort() : port;
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            log.info("Desligando servidor gRPC...");
            server.shutdown();
        }
    }
}
