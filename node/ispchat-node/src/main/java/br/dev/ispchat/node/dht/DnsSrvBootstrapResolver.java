package br.dev.ispchat.node.dht;

import br.dev.ispchat.grpc.DhtPeerServiceGrpc;
import br.dev.ispchat.grpc.PingRequest;
import br.dev.ispchat.grpc.PingResponse;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import io.grpc.ManagedChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Resolvedor periodico de registros DNS SRV (_ispchat._tcp.ispchat.dev.br) via JNDI
 * para descoberta automatica e populamento da KademliaRoutingTable via DhtPeerService.Ping.
 */
@Component
@ConditionalOnProperty(name = "ispchat.dht.srv-bootstrap-enabled", havingValue = "true", matchIfMissing = true)
public class DnsSrvBootstrapResolver {

    private static final Logger log = LoggerFactory.getLogger(DnsSrvBootstrapResolver.class);

    @FunctionalInterface
    public interface SrvLookupStrategy {
        List<String> lookupRawSrvRecords(String srvDomain) throws Exception;
    }

    public record SrvEndpoint(int priority, int weight, int port, String host) {
        public String hostAndPort() {
            return host + ":" + port;
        }
    }

    private final KademliaRoutingTable routingTable;
    private final GrpcChannelPool channelPool;
    private final String srvDomain;
    private final String localPublicAddress;
    private final long refreshIntervalMs;
    private final SrvLookupStrategy lookupStrategy;
    private ScheduledExecutorService scheduler;

    @Autowired
    public DnsSrvBootstrapResolver(
            KademliaRoutingTable routingTable,
            GrpcChannelPool channelPool,
            @Value("${ispchat.dht.srv-domain:_ispchat._tcp.ispchat.dev.br}") String srvDomain,
            @Value("${ispchat.node.public-address:}") String localPublicAddress,
            @Value("${ispchat.dht.srv-refresh-interval-ms:900000}") long refreshIntervalMs) {
        this(routingTable, channelPool, srvDomain, localPublicAddress, refreshIntervalMs, DnsSrvBootstrapResolver::jndiSrvLookup);
    }

    public DnsSrvBootstrapResolver(
            KademliaRoutingTable routingTable,
            GrpcChannelPool channelPool,
            String srvDomain,
            String localPublicAddress,
            long refreshIntervalMs,
            SrvLookupStrategy lookupStrategy) {
        this.routingTable = Objects.requireNonNull(routingTable, "routingTable nao pode ser nulo");
        this.channelPool = Objects.requireNonNull(channelPool, "channelPool nao pode ser nulo");
        this.srvDomain = srvDomain != null ? srvDomain.trim() : "";
        this.localPublicAddress = localPublicAddress != null ? localPublicAddress.trim() : "";
        this.refreshIntervalMs = Math.max(10_000L, refreshIntervalMs);
        this.lookupStrategy = Objects.requireNonNull(lookupStrategy, "lookupStrategy nao pode ser nulo");
    }

    @PostConstruct
    public void start() {
        if (srvDomain.isBlank()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ispchat-dns-srv-bootstrap");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::refreshPeersOnce, 0L, refreshIntervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Executa uma rodada de consulta DNS SRV seguida de DhtPeerService.Ping mTLS.
     *
     * @return quantidade de nos pares respondentes registrados/atualizados.
     */
    public int refreshPeersOnce() {
        if (srvDomain.isBlank()) {
            return 0;
        }
        int discovered = 0;
        try {
            List<String> rawRecords = lookupStrategy.lookupRawSrvRecords(srvDomain);
            List<SrvEndpoint> endpoints = parseAndSortSrvRecords(rawRecords);
            for (SrvEndpoint endpoint : endpoints) {
                String targetAddress = endpoint.hostAndPort();
                if (isSelfAddress(targetAddress, endpoint.host())) {
                    continue;
                }
                try {
                    ManagedChannel channel = channelPool.getChannel(targetAddress);
                    DhtPeerServiceGrpc.DhtPeerServiceBlockingStub stub = DhtPeerServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(3, TimeUnit.SECONDS);
                    PingResponse pong = stub.ping(PingRequest.newBuilder()
                            .setSenderNodeId(routingTable.getLocalNodeId())
                            .setSenderAddress(localPublicAddress)
                            .build());
                    if (!pong.getResponderNodeId().isBlank()) {
                        routingTable.registerOrUpdateNode(pong.getResponderNodeId(), targetAddress);
                        discovered++;
                    }
                } catch (Exception e) {
                    log.debug("Falha no Ping SRV para {}: {}", targetAddress, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Nenhum registro SRV resolvido para {}: {}", srvDomain, e.getMessage());
        }
        return discovered;
    }

    public static List<SrvEndpoint> parseAndSortSrvRecords(List<String> rawRecords) {
        if (rawRecords == null || rawRecords.isEmpty()) {
            return List.of();
        }
        List<SrvEndpoint> parsed = new ArrayList<>();
        for (String raw : rawRecords) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.trim().split("\\s+");
            if (parts.length < 4) {
                continue;
            }
            try {
                int priority = Integer.parseInt(parts[0]);
                int weight = Integer.parseInt(parts[1]);
                int port = Integer.parseInt(parts[2]);
                String host = parts[3].endsWith(".")
                        ? parts[3].substring(0, parts[3].length() - 1)
                        : parts[3];
                if (!host.isBlank() && port > 0 && port <= 65535) {
                    parsed.add(new SrvEndpoint(priority, weight, port, host.toLowerCase(Locale.ROOT)));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        parsed.sort(Comparator.comparingInt(SrvEndpoint::priority)
                .thenComparing(Comparator.comparingInt(SrvEndpoint::weight).reversed())
                .thenComparing(SrvEndpoint::host));
        return parsed;
    }

    private boolean isSelfAddress(String candidateHostPort, String candidateHost) {
        if (localPublicAddress.isBlank()) {
            return false;
        }
        if (localPublicAddress.equalsIgnoreCase(candidateHostPort)
                || localPublicAddress.equalsIgnoreCase(candidateHost)) {
            return true;
        }
        if (!localPublicAddress.contains(":") && (localPublicAddress + ":443").equalsIgnoreCase(candidateHostPort)) {
            return true;
        }
        return false;
    }

    private static List<String> jndiSrvLookup(String domain) throws Exception {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "3000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext ctx = new InitialDirContext(env);
        try {
            Attributes attrs = ctx.getAttributes(domain, new String[]{"SRV"});
            Attribute srvAttr = attrs.get("SRV");
            if (srvAttr == null) {
                return List.of();
            }
            List<String> results = new ArrayList<>();
            NamingEnumeration<?> all = srvAttr.getAll();
            while (all.hasMore()) {
                Object val = all.next();
                if (val != null) {
                    results.add(val.toString());
                }
            }
            return results;
        } finally {
            ctx.close();
        }
    }
}
