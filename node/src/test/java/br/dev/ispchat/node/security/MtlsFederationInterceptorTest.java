package br.dev.ispchat.node.security;

import br.dev.ispchat.grpc.DhtClientServiceGrpc;
import br.dev.ispchat.grpc.DhtPeerServiceGrpc;
import br.dev.ispchat.grpc.FindUserSessionsRequest;
import br.dev.ispchat.grpc.FindUserSessionsResponse;
import br.dev.ispchat.grpc.PingRequest;
import br.dev.ispchat.grpc.PingResponse;
import br.dev.ispchat.node.grpc.GrpcServerRunner;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MtlsFederationInterceptorTest {

    private Server server;
    private int serverPort;
    private File caCert;
    private File nodeCert;
    private File nodeKey;

    @BeforeEach
    void setUp() throws Exception {
        caCert = resolveFile("../ca/ca.crt", "ca/ca.crt");
        nodeCert = resolveFile("../ca/dev-node.crt", "ca/dev-node.crt");
        nodeKey = resolveFile("../ca/dev-node.key", "ca/dev-node.key");

        SslContext serverSslContext = GrpcServerRunner.buildServerSslContext(caCert, nodeCert, nodeKey);
        MtlsFederationInterceptor interceptor = new MtlsFederationInterceptor();

        DhtPeerServiceGrpc.DhtPeerServiceImplBase federationService = new DhtPeerServiceGrpc.DhtPeerServiceImplBase() {
            @Override
            public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
                X509Certificate peerCert = MtlsFederationInterceptor.PEER_CERT_CONTEXT_KEY.get();
                assertNotNull(peerCert, "Certificado X.509 do no par deve estar presente no Contexto gRPC");
                responseObserver.onNext(PingResponse.newBuilder()
                        .setResponderNodeId("pong-from-federated-node")
                        .build());
                responseObserver.onCompleted();
            }
        };

        DhtClientServiceGrpc.DhtClientServiceImplBase edgeService = new DhtClientServiceGrpc.DhtClientServiceImplBase() {
            @Override
            public void findUserSessions(FindUserSessionsRequest request, StreamObserver<FindUserSessionsResponse> responseObserver) {
                responseObserver.onNext(FindUserSessionsResponse.newBuilder()
                        .addNodeAddresses("127.0.0.1")
                        .build());
                responseObserver.onCompleted();
            }
        };

        server = NettyServerBuilder.forPort(0)
                .sslContext(serverSslContext)
                .addService(ServerInterceptors.intercept(federationService, interceptor))
                .addService(ServerInterceptors.intercept(edgeService, interceptor))
                .build()
                .start();

        serverPort = server.getPort();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Deve permitir chamada de federacao (DhtPeerService) quando cliente apresenta certificado mTLS assinado pela CA")
    void shouldAllowFederationCallWithValidCaSignedMtlsCert() throws Exception {
        SslContext mtlsClientContext = GrpcSslContexts.forClient()
                .trustManager(caCert)
                .keyManager(nodeCert, nodeKey)
                .protocols("TLSv1.3")
                .build();

        ManagedChannel channel = NettyChannelBuilder.forAddress("127.0.0.1", serverPort)
                .sslContext(mtlsClientContext)
                .build();

        try {
            DhtPeerServiceGrpc.DhtPeerServiceBlockingStub stub = DhtPeerServiceGrpc.newBlockingStub(channel);
            PingResponse response = stub.ping(PingRequest.newBuilder().setSenderNodeId("node-a").build());
            assertEquals("pong-from-federated-node", response.getResponderNodeId());
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Deve bloquear com UNAUTHENTICATED chamada de federacao quando cliente conecta via TLS sem certificado mTLS")
    void shouldRejectFederationCallWithoutClientCertificate() throws Exception {
        // Cliente confiando na CA mas SEM apresentar certificado proprio (ex: App Flutter ou scanner externo)
        SslContext tlsOnlyClientContext = GrpcSslContexts.forClient()
                .trustManager(caCert)
                .protocols("TLSv1.3")
                .build();

        ManagedChannel channel = NettyChannelBuilder.forAddress("127.0.0.1", serverPort)
                .sslContext(tlsOnlyClientContext)
                .build();

        try {
            DhtPeerServiceGrpc.DhtPeerServiceBlockingStub federationStub = DhtPeerServiceGrpc.newBlockingStub(channel);
            StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () ->
                    federationStub.ping(PingRequest.newBuilder().setSenderNodeId("unauthenticated-node").build())
            );
            assertEquals(Status.Code.UNAUTHENTICATED, exception.getStatus().getCode());

            // Porém, na mesma conexao TLS na porta 443, servicos de borda (DhtClientService) devem passar pelo interceptor mTLS
            DhtClientServiceGrpc.DhtClientServiceBlockingStub edgeStub = DhtClientServiceGrpc.newBlockingStub(channel);
            FindUserSessionsResponse edgeResponse = edgeStub.findUserSessions(
                    FindUserSessionsRequest.newBuilder().setTargetUuid("01950000-0000-7000-8000-000000000000").build()
            );
            assertEquals(1, edgeResponse.getNodeAddressesCount());
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static File resolveFile(String primary, String fallback) {
        File f1 = new File(primary);
        if (f1.exists()) {
            return f1;
        }
        File f2 = new File(fallback);
        if (f2.exists()) {
            return f2;
        }
        throw new IllegalStateException("Certificado de teste nao encontrado: " + primary);
    }
}
