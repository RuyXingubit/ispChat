package br.dev.ispchat.node.governance;

import br.dev.ispchat.grpc.AuthorizeCollaboratorRequest;
import br.dev.ispchat.grpc.CollaboratorAuthCert;
import br.dev.ispchat.grpc.GetCredentialRequest;
import br.dev.ispchat.grpc.GovernanceAck;
import br.dev.ispchat.grpc.PortAccountRequest;
import br.dev.ispchat.grpc.PrincipalGovernanceServiceGrpc;
import br.dev.ispchat.grpc.RegisterPartnerIspRequest;
import br.dev.ispchat.grpc.RevocationEvent;
import br.dev.ispchat.grpc.RevokeCollaboratorRequest;
import br.dev.ispchat.grpc.RevokeDeviceRequest;
import br.dev.ispchat.grpc.StreamRevocationsRequest;
import br.dev.ispchat.grpc.SubmitCpfRebindRequest;
import br.dev.ispchat.grpc.SubmitVerificationRequest;
import br.dev.ispchat.grpc.VerificationBundle;
import br.dev.ispchat.node.dht.DhtStoreService;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Proxy gRPC de borda do No ISP Parceiro para o PrincipalGovernanceService (ispchat-principal).
 *
 * Quando um Colaborador, Socio Administrador ou Assinante invoca operacoes de governanca no
 * seu No ISP local, este servico repassa a chamada sobre o canal mTLS (GrpcChannelPool) do
 * provedor, autenticando a origem via SAN do certificado X.509 do No ISP perante o Principal.
 *
 * Registrado via EdgeProxyAutoConfiguration com @ConditionalOnMissingBean para que o modulo
 * ispchat-principal o substitua automaticamente pela implementacao primaria com PostgreSQL.
 */
public class EdgeGovernanceProxyGrpcService
        extends PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(EdgeGovernanceProxyGrpcService.class);
    private static final long UPSTREAM_DEADLINE_SECONDS = 10L;

    private final GrpcChannelPool channelPool;
    private final DhtStoreService dhtStoreService;
    private final String principalAddress;
    private final String localPublicAddress;

    public EdgeGovernanceProxyGrpcService(
            GrpcChannelPool channelPool,
            DhtStoreService dhtStoreService,
            String principalAddress,
            String localPublicAddress) {
        this.channelPool = channelPool;
        this.dhtStoreService = dhtStoreService;
        this.principalAddress = principalAddress != null ? principalAddress.trim() : "";
        this.localPublicAddress = localPublicAddress != null ? localPublicAddress.trim() : "";
    }

    @Override
    public void registerPartnerIsp(
            RegisterPartnerIspRequest request,
            StreamObserver<GovernanceAck> responseObserver) {
        responseObserver.onError(Status.PERMISSION_DENIED
                .withDescription("RegisterPartnerIsp deve ser invocado diretamente contra o servidor Principal")
                .asRuntimeException());
    }

    @Override
    public void authorizeCollaborator(
            AuthorizeCollaboratorRequest request,
            StreamObserver<CollaboratorAuthCert> responseObserver) {
        forwardUnary(responseObserver, stub -> stub.authorizeCollaborator(request));
    }

    @Override
    public void revokeCollaborator(
            RevokeCollaboratorRequest request,
            StreamObserver<RevocationEvent> responseObserver) {
        forwardUnary(responseObserver, stub -> stub.revokeCollaborator(request));
    }

    @Override
    public void submitVerification(
            SubmitVerificationRequest request,
            StreamObserver<VerificationBundle> responseObserver) {
        forwardUnary(responseObserver, stub -> {
            VerificationBundle bundle = stub.submitVerification(request);
            cacheIssuedBundleLocally(bundle);
            return bundle;
        });
    }

    @Override
    public void submitCpfRebind(
            SubmitCpfRebindRequest request,
            StreamObserver<VerificationBundle> responseObserver) {
        forwardUnary(responseObserver, stub -> {
            VerificationBundle bundle = stub.submitCpfRebind(request);
            cacheIssuedBundleLocally(bundle);
            return bundle;
        });
    }

    @Override
    public void getOrRenewCredential(
            GetCredentialRequest request,
            StreamObserver<VerificationBundle> responseObserver) {
        forwardUnary(responseObserver, stub -> {
            VerificationBundle bundle = stub.getOrRenewCredential(request);
            cacheIssuedBundleLocally(bundle);
            return bundle;
        });
    }

    @Override
    public void revokeDevice(
            RevokeDeviceRequest request,
            StreamObserver<RevocationEvent> responseObserver) {
        forwardUnary(responseObserver, stub -> stub.revokeDevice(request));
    }

    @Override
    public void portAccount(
            PortAccountRequest request,
            StreamObserver<VerificationBundle> responseObserver) {
        forwardUnary(responseObserver, stub -> {
            VerificationBundle bundle = stub.portAccount(request);
            cacheIssuedBundleLocally(bundle);
            // Se esta conta possuia este no como No Casa anterior, grava redirect tombstone de 30 dias
            if (dhtStoreService != null
                    && bundle.hasHomeNodeClaim()
                    && !bundle.getHomeNodeClaim().getHomeNodeAddress().isBlank()
                    && !bundle.getHomeNodeClaim().getHomeNodeAddress().equalsIgnoreCase(localPublicAddress)) {
                dhtStoreService.storeRedirectTombstone(
                        request.getUserUuid(),
                        bundle.getHomeNodeClaim().getHomeNodeAddress());
            }
            return bundle;
        });
    }

    @Override
    public void streamRevocations(
            StreamRevocationsRequest request,
            StreamObserver<RevocationEvent> responseObserver) {
        responseObserver.onError(Status.PERMISSION_DENIED
                .withDescription("StreamRevocations e restrito a sincronizacao mTLS direta com o servidor Principal")
                .asRuntimeException());
    }

    private void cacheIssuedBundleLocally(VerificationBundle bundle) {
        if (dhtStoreService == null || bundle == null) {
            return;
        }
        if (bundle.hasHomeNodeClaim() && !bundle.getHomeNodeClaim().getUserUuid().isBlank()) {
            dhtStoreService.storeHomeNodeClaim(
                    bundle.getHomeNodeClaim(),
                    bundle.hasCredential() ? bundle.getCredential() : null,
                    bundle.hasCredential() ? bundle.getCredential().getRootEd25519Pub().toByteArray() : null);
        }
        if (bundle.hasReplacementNotice() && !bundle.getReplacementNotice().getRevocationId().isBlank()) {
            dhtStoreService.storeReplacementNotice(bundle.getReplacementNotice());
        }
    }

    private <RespT> void forwardUnary(
            StreamObserver<RespT> responseObserver,
            Function<PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceBlockingStub, RespT> action) {
        if (principalAddress.isBlank()) {
            responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("Servidor Principal nao configurado neste No ISP (ispchat.principal.address)")
                    .asRuntimeException());
            return;
        }
        try {
            ManagedChannel channel = channelPool.getChannel(principalAddress);
            PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceBlockingStub stub =
                    PrincipalGovernanceServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(UPSTREAM_DEADLINE_SECONDS, TimeUnit.SECONDS);
            RespT response = action.apply(stub);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            responseObserver.onError(e.getStatus().asRuntimeException());
        } catch (Exception e) {
            log.warn("Falha ao repassar RPC de governanca ao servidor Principal ({}): {}",
                    principalAddress, e.getMessage());
            responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("Servidor Principal temporariamente indisponivel")
                    .asRuntimeException());
        }
    }
}
