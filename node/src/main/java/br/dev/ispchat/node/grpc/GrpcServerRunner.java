package br.dev.ispchat.node.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GrpcServerRunner {

    private Server server;

    @Value("${grpc.server.port:443}")
    private int port;

    @PostConstruct
    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                // TODO: Adicionar os servicos gerados do Protobuf (DhtService, NodeRoutingService, etc.)
                // TODO: Adicionar configuracao de mTLS com chaves do diretorio ca/ 
                .build()
                .start();

        System.out.println("Servidor gRPC iniciado na porta " + port);
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            System.out.println("Desligando servidor gRPC...");
            server.shutdown();
        }
    }
}
