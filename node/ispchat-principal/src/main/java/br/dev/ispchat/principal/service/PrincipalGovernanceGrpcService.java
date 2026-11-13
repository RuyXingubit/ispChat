package br.dev.ispchat.principal.service;

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
import br.dev.ispchat.node.security.MtlsFederationInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Implementacao primaria do PrincipalGovernanceService no servidor Principal (ispchat-principal).
 * Substitui automaticamente o EdgeGovernanceProxyGrpcService de :ispchat-node.
 */
@Service
public class PrincipalGovernanceGrpcService
        extends PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(PrincipalGovernanceGrpcService.class);

    private final PrincipalGovernanceCoordinator coordinator;

    public PrincipalGovernanceGrpcService(PrincipalGovernanceCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void registerPartnerIsp(
            RegisterPartnerIspRequest request,
            StreamObserver<GovernanceAck> responseObserver) {
        X509Certificate peerCert = MtlsFederationInterceptor.PEER_CERT_CONTEXT_KEY.get();
        executeUnary(responseObserver, () -> coordinator.registerPartnerIsp(request, peerCert));
    }

    @Override
    public void authorizeCollaborator(
            AuthorizeCollaboratorRequest request,
            StreamObserver<CollaboratorAuthCert> responseObserver) {
        X509Certificate peerCert = MtlsFederationInterceptor.PEER_CERT_CONTEXT_KEY.get();
        Set<String> peerSans = MtlsFederationInterceptor.PEER_SAN_CONTEXT_KEY.get();
        executeUnary(responseObserver, () -> coordinator.authorizeCollaborator(request, peerCert, peerSans));
    }

    @Override
    public void revokeCollaborator(
            RevokeCollaboratorRequest request,
            StreamObserver<RevocationEvent> responseObserver) {
        X509Certificate peerCert = MtlsFederationInterceptor.PEER_CERT_CONTEXT_KEY.get();
        Set<String> peerSans = MtlsFederationInterceptor.PEER_SAN_CONTEXT_KEY.get();
        executeUnary(responseObserver, () -> coordinator.revokeCollaborator(request, peerCert, peerSans));
    }

    @Override
    public void submitVerification(
            SubmitVerificationRequest request,
            StreamObserver<VerificationBundle> responseObserver) {
        X509Certificate peerCert = MtlsFederationInterceptor.PEER_CERT_CONTEXT_KEY.get();
        Set<String> peerSans = MtlsFederationInterceptor.PEER_SAN_CONTEXT_KEY.get();
        executeUnary(responseObserver, () -> coordinator.submitVerification(request, peerCert, peerSans));
    }

    @Override
    public void submitCpfRebind(
            SubmitCpfRebindRequest request,
            StreamObserver<VerificationBundle> responseObserver) {
        X509Certificate peerCert = MtlsFederationInterceptor.PEER_CERT_CONTEXT_KEY.get();
        Set<String> peerSans = MtlsFederationInterceptor.PEER_SAN_CONTEXT_KEY.get();
        executeUnary(responseObserver, () -> coordinator.submitCpfRebind(request, peerCert, peerSans));
    }

    @Override
    public void getOrRenewCredential(
            GetCredentialRequest request,
            StreamObserver<VerificationBundle> responseObserver) {
        executeUnary(responseObserver, () -> coordinator.getOrRenewCredential(request));
    }

    @Override
    public void revokeDevice(
            RevokeDeviceRequest request,
            StreamObserver<RevocationEvent> responseObserver) {
        executeUnary(responseObserver, () -> coordinator.revokeDevice(request));
    }

    @Override
    public void portAccount(
            PortAccountRequest request,
            StreamObserver<VerificationBundle> responseObserver) {
        X509Certificate peerCert = MtlsFederationInterceptor.PEER_CERT_CONTEXT_KEY.get();
        Set<String> peerSans = MtlsFederationInterceptor.PEER_SAN_CONTEXT_KEY.get();
        executeUnary(responseObserver, () -> coordinator.portAccount(request, peerCert, peerSans));
    }

    @Override
    public void streamRevocations(
            StreamRevocationsRequest request,
            StreamObserver<RevocationEvent> responseObserver) {
        X509Certificate peerCert = MtlsFederationInterceptor.PEER_CERT_CONTEXT_KEY.get();
        if (peerCert == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Certificado X.509 mTLS obrigatorio para StreamRevocations")
                    .asRuntimeException());
            return;
        }

        AtomicLong lastSentSeq = new AtomicLong(Math.max(0L, request.getSinceRevocationSeq()));
        Object streamLock = new Object();

        Runnable unsubscribe = coordinator.subscribeRevocations(liveEvent -> {
            synchronized (streamLock) {
                if (responseObserver instanceof ServerCallStreamObserver<?> scso && scso.isCancelled()) {
                    return;
                }
                if (liveEvent.getRevocationSeq() > lastSentSeq.get()) {
                    responseObserver.onNext(liveEvent);
                    lastSentSeq.set(liveEvent.getRevocationSeq());
                }
            }
        });

        if (responseObserver instanceof ServerCallStreamObserver<?> scso) {
            scso.setOnCancelHandler(unsubscribe);
        }

        try {
            synchronized (streamLock) {
                List<RevocationEvent> backlog = coordinator.queryRevocationsSince(lastSentSeq.get());
                for (RevocationEvent event : backlog) {
                    if (event.getRevocationSeq() > lastSentSeq.get()) {
                        responseObserver.onNext(event);
                        lastSentSeq.set(event.getRevocationSeq());
                    }
                }
            }
        } catch (Exception e) {
            unsubscribe.run();
            log.error("Falha ao consultar backlog de revogacoes", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Erro interno ao iniciar StreamRevocations")
                    .asRuntimeException());
        }
    }

    private static <T> void executeUnary(StreamObserver<T> responseObserver, Supplier<T> action) {
        try {
            T result = action.get();
            responseObserver.onNext(result);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Erro inesperado em PrincipalGovernanceGrpcService", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Erro interno ao processar operacao de governanca")
                    .asRuntimeException());
        }
    }
}
