package br.dev.ispchat.node.grpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Pool direto de canais gRPC mTLS para comunicacao No-a-No (substituindo io.grpc.xds).
 * Inclui protecao Anti-SSRF para impedir que registros maliciosos na DHT forcem
 * o no a abrir conexoes contra IPs internos/loopback em ambiente de producao.
 */
@Component
public class GrpcChannelPool {

    private static final Logger log = LoggerFactory.getLogger(GrpcChannelPool.class);
    public static final int DEFAULT_FEDERATION_PORT = 443;

    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final boolean devMode;
    private final String caCertPath;
    private final String nodeCertPath;
    private final String nodeKeyPath;

    private volatile SslContext clientSslContext;

    public GrpcChannelPool(
            @Value("${ispchat.security.dev-mode:false}") boolean devMode,
            @Value("${ispchat.security.ca-cert-path:../ca/ca.crt}") String caCertPath,
            @Value("${ispchat.security.node-cert-path:../ca/dev-node.crt}") String nodeCertPath,
            @Value("${ispchat.security.node-key-path:../ca/dev-node.key}") String nodeKeyPath) {
        this.devMode = devMode;
        this.caCertPath = caCertPath;
        this.nodeCertPath = nodeCertPath;
        this.nodeKeyPath = nodeKeyPath;
    }

    /**
     * Retorna ou cria um ManagedChannel mTLS seguro para o endereco do no informado.
     * Em producao (devMode=false), exige IP publico roteavel e porta 443.
     */
    public ManagedChannel getChannel(String nodeAddress) {
        HostAndPort target = validateAndParseTarget(nodeAddress);
        String normalizedKey = target.host() + ":" + target.port();

        return channels.compute(normalizedKey, (key, existing) -> {
            if (existing != null && !existing.isShutdown() && !existing.isTerminated()) {
                return existing;
            }
            return createMtlsChannel(target.host(), target.port());
        });
    }

    /**
     * Valida o endereco contra SSRF (Server-Side Request Forgery).
     */
    public HostAndPort validateAndParseTarget(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            throw new IllegalArgumentException("Endereco do no destino nao pode ser vazio");
        }

        String trimmed = rawAddress.trim();
        String host = trimmed;
        int port = DEFAULT_FEDERATION_PORT;

        // Suporte a IPv6 entre colchetes [::1]:443 ou host:port IPv4/DNS
        if (trimmed.startsWith("[")) {
            int closeBracket = trimmed.indexOf(']');
            if (closeBracket < 0) {
                throw new IllegalArgumentException("Endereco IPv6 mal formatado: " + rawAddress);
            }
            host = trimmed.substring(1, closeBracket);
            if (closeBracket + 1 < trimmed.length()) {
                if (trimmed.charAt(closeBracket + 1) != ':') {
                    throw new IllegalArgumentException("Porta invalida no endereco: " + rawAddress);
                }
                port = parsePort(trimmed.substring(closeBracket + 2), rawAddress);
            }
        } else {
            int firstColon = trimmed.indexOf(':');
            int lastColon = trimmed.lastIndexOf(':');
            if (firstColon > 0 && firstColon == lastColon) {
                host = trimmed.substring(0, firstColon);
                port = parsePort(trimmed.substring(firstColon + 1), rawAddress);
            }
        }

        if (!devMode && port != DEFAULT_FEDERATION_PORT) {
            throw new SecurityException("Em producao, conexoes entre nos exigem estritamente a porta 443: " + rawAddress);
        }

        validateHostAgainstSsrf(host);
        return new HostAndPort(host, port);
    }

    private void validateHostAgainstSsrf(String host) {
        if (devMode) {
            return;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress()
                        || addr.isAnyLocalAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress()
                        || addr.isMulticastAddress()) {
                    throw new SecurityException("Bloqueio Anti-SSRF: endereco privado/interno nao permitido na federacao: " + host);
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Nao foi possivel resolver o host do no destino: " + host, e);
        }
    }

    private ManagedChannel createMtlsChannel(String host, int port) {
        try {
            SslContext sslContext = getOrCreateClientSslContext();
            NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port)
                    .sslContext(sslContext)
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .idleTimeout(5, TimeUnit.MINUTES);

            if (devMode) {
                // Em modo de desenvolvimento local, faz override do authority para bater com o SAN do dev-node.crt
                builder.overrideAuthority("127.0.0.1");
            }
            return builder.build();
        } catch (SSLException e) {
            throw new IllegalStateException("Falha ao inicializar contexto mTLS para o no " + host + ":" + port, e);
        }
    }

    private SslContext getOrCreateClientSslContext() throws SSLException {
        if (clientSslContext == null) {
            synchronized (this) {
                if (clientSslContext == null) {
                    File caCert = resolveCertFile(caCertPath);
                    File nodeCert = resolveCertFile(nodeCertPath);
                    File nodeKey = resolveCertFile(nodeKeyPath);
                    clientSslContext = GrpcSslContexts.forClient()
                            .trustManager(caCert)
                            .keyManager(nodeCert, nodeKey)
                            .protocols("TLSv1.3")
                            .build();
                }
            }
        }
        return clientSslContext;
    }

    private File resolveCertFile(String configuredPath) {
        File file = new File(configuredPath);
        if (file.exists()) {
            return file;
        }
        String normalized = configuredPath.startsWith("../")
                ? configuredPath.substring(3)
                : configuredPath;
        File fallback = new File(normalized);
        if (fallback.exists()) {
            return fallback;
        }
        throw new IllegalStateException("Arquivo de certificado/chave nao encontrado: " + configuredPath);
    }

    private static int parsePort(String portStr, String rawAddress) {
        try {
            int port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Porta fora do intervalo valido (1-65535): " + rawAddress);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Porta numerica invalida no endereco: " + rawAddress, e);
        }
    }

    @PreDestroy
    public void shutdownAll() {
        for (Map.Entry<String, ManagedChannel> entry : channels.entrySet()) {
            ManagedChannel channel = entry.getValue();
            channel.shutdown();
            try {
                if (!channel.awaitTermination(3, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        channels.clear();
        log.info("Todos os canais do GrpcChannelPool foram encerrados.");
    }

    public record HostAndPort(String host, int port) {}
}
