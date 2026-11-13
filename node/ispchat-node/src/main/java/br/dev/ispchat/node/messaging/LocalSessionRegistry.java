package br.dev.ispchat.node.messaging;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.grpc.DeviceRole;
import br.dev.ispchat.grpc.MessageType;
import br.dev.ispchat.grpc.RevocationEvent;
import br.dev.ispchat.grpc.ServerEnvelope;
import br.dev.ispchat.node.security.TierRateLimiterService.AccountTier;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registro de streams gRPC ativas conectadas localmente neste No, indexadas por (userUuid, deviceId).
 *
 * Regras Dual-Tier:
 * - Contas nao verificadas (UNVERIFIED): no maximo 1 stream concorrente (deviceId == userUuid).
 * - Contas CPF verificadas (VERIFIED_CPF): ate 5 dispositivos ativos distintos.
 * - Contas CNPJ verificadas (VERIFIED_CNPJ): ate 10 dispositivos ativos distintos.
 * - Qualquer tentativa de abrir stream duplicada concorrente para o mesmo (userUuid, deviceId)
 *   ou acima do teto do tier e rejeitada deterministicamente (RESOURCE_EXHAUSTED).
 */
@Component
public class LocalSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(LocalSessionRegistry.class);

    public record ActiveStreamEntry(
            String userUuid,
            String deviceId,
            DeviceRole role,
            AccountTier tier,
            StreamObserver<ServerEnvelope> observer,
            long connectedAtMs) {
    }

    private final Map<String, Map<String, ActiveStreamEntry>> activeStreams = new ConcurrentHashMap<>();

    /**
     * Tenta registrar uma stream autenticada respeitando o limite Dual-Tier e unicidade por deviceId.
     *
     * @return true se registrada com sucesso; false se ja houver stream ativa para o deviceId ou teto excedido.
     */
    public boolean tryRegisterDeviceStream(
            String userUuid,
            String deviceId,
            DeviceRole role,
            AccountTier tier,
            StreamObserver<ServerEnvelope> streamObserver) {
        if (userUuid == null || userUuid.isBlank() || streamObserver == null || tier == null) {
            return false;
        }
        String normalizedUuid = userUuid.trim().toLowerCase(Locale.ROOT);
        String normalizedDeviceId = (deviceId == null || deviceId.isBlank())
                ? normalizedUuid
                : deviceId.trim().toLowerCase(Locale.ROOT);
        DeviceRole safeRole = role != null ? role : DeviceRole.MOBILE_ROOT;

        Map<String, ActiveStreamEntry> deviceMap = activeStreams.computeIfAbsent(
                normalizedUuid, k -> new ConcurrentHashMap<>());

        synchronized (deviceMap) {
            pruneCancelledStreamsLocked(deviceMap);

            ActiveStreamEntry existingForDevice = deviceMap.get(normalizedDeviceId);
            if (existingForDevice != null) {
                return false; // Rejeita segunda stream concorrente para o mesmo deviceId / conta Unverified
            }

            if (deviceMap.size() >= tier.maxActiveDevices()) {
                return false; // Limite maximo de dispositivos ativos do tier atingido
            }

            deviceMap.put(normalizedDeviceId, new ActiveStreamEntry(
                    normalizedUuid,
                    normalizedDeviceId,
                    safeRole,
                    tier,
                    streamObserver,
                    System.currentTimeMillis()));
            return true;
        }
    }

    /**
     * Metodo legado mantido para compatibilidade direta em testes isolados.
     */
    public void registerStream(String userUuid, StreamObserver<ServerEnvelope> streamObserver) {
        tryRegisterDeviceStream(
                userUuid,
                userUuid,
                DeviceRole.MOBILE_ROOT,
                AccountTier.UNVERIFIED,
                streamObserver);
    }

    public void unregisterDeviceStream(
            String userUuid,
            String deviceId,
            StreamObserver<ServerEnvelope> streamObserver) {
        if (userUuid == null || userUuid.isBlank() || streamObserver == null) {
            return;
        }
        String normalizedUuid = userUuid.trim().toLowerCase(Locale.ROOT);
        String normalizedDeviceId = (deviceId == null || deviceId.isBlank())
                ? normalizedUuid
                : deviceId.trim().toLowerCase(Locale.ROOT);

        Map<String, ActiveStreamEntry> deviceMap = activeStreams.get(normalizedUuid);
        if (deviceMap == null) {
            return;
        }
        synchronized (deviceMap) {
            ActiveStreamEntry current = deviceMap.get(normalizedDeviceId);
            if (current != null && current.observer() == streamObserver) {
                deviceMap.remove(normalizedDeviceId);
            }
            if (deviceMap.isEmpty()) {
                activeStreams.remove(normalizedUuid, deviceMap);
            }
        }
    }

    public void unregisterStream(String userUuid, StreamObserver<ServerEnvelope> streamObserver) {
        if (userUuid == null || userUuid.isBlank() || streamObserver == null) {
            return;
        }
        String normalizedUuid = userUuid.trim().toLowerCase(Locale.ROOT);
        Map<String, ActiveStreamEntry> deviceMap = activeStreams.get(normalizedUuid);
        if (deviceMap == null) {
            return;
        }
        synchronized (deviceMap) {
            deviceMap.entrySet().removeIf(entry -> entry.getValue().observer() == streamObserver);
            if (deviceMap.isEmpty()) {
                activeStreams.remove(normalizedUuid, deviceMap);
            }
        }
    }

    /**
     * Envia um ServerEnvelope simultaneamente para todos os dispositivos conectados localmente do destinatario.
     *
     * @return lista dos device_id locais que receberam o pacote com sucesso.
     */
    public List<String> deliverToLocalUserDevices(String receiverUuid, ServerEnvelope envelope) {
        if (receiverUuid == null || receiverUuid.isBlank() || envelope == null) {
            return List.of();
        }
        String normalizedUuid = receiverUuid.trim().toLowerCase(Locale.ROOT);
        Map<String, ActiveStreamEntry> deviceMap = activeStreams.get(normalizedUuid);
        if (deviceMap == null || deviceMap.isEmpty()) {
            return List.of();
        }

        List<ActiveStreamEntry> snapshot;
        synchronized (deviceMap) {
            pruneCancelledStreamsLocked(deviceMap);
            snapshot = new ArrayList<>(deviceMap.values());
        }

        List<String> deliveredDeviceIds = new ArrayList<>(snapshot.size());
        for (ActiveStreamEntry entry : snapshot) {
            StreamObserver<ServerEnvelope> observer = entry.observer();
            try {
                synchronized (observer) {
                    observer.onNext(envelope);
                }
                deliveredDeviceIds.add(entry.deviceId());
            } catch (Exception e) {
                log.debug("Removendo stream inativa para UUID {} device {}: {}",
                        normalizedUuid, entry.deviceId(), e.getMessage());
                unregisterDeviceStream(normalizedUuid, entry.deviceId(), observer);
            }
        }
        return deliveredDeviceIds;
    }

    public int deliverToLocalStreams(String receiverUuid, ServerEnvelope envelope) {
        return deliverToLocalUserDevices(receiverUuid, envelope).size();
    }

    /**
     * Sincroniza uma mensagem enviada com os dispositivos irmaos (sibling sync) da mesma conta conectados localmente.
     */
    public List<String> deliverToSiblingDevices(String senderUuid, String senderDeviceId, ServerEnvelope envelope) {
        if (senderUuid == null || senderUuid.isBlank() || envelope == null) {
            return List.of();
        }
        String normalizedUuid = senderUuid.trim().toLowerCase(Locale.ROOT);
        String normalizedSenderDev = (senderDeviceId == null || senderDeviceId.isBlank())
                ? normalizedUuid
                : senderDeviceId.trim().toLowerCase(Locale.ROOT);

        Map<String, ActiveStreamEntry> deviceMap = activeStreams.get(normalizedUuid);
        if (deviceMap == null || deviceMap.isEmpty()) {
            return List.of();
        }

        List<ActiveStreamEntry> snapshot;
        synchronized (deviceMap) {
            pruneCancelledStreamsLocked(deviceMap);
            snapshot = new ArrayList<>(deviceMap.values());
        }

        List<String> deliveredSiblings = new ArrayList<>();
        for (ActiveStreamEntry entry : snapshot) {
            if (entry.deviceId().equals(normalizedSenderDev)) {
                continue;
            }
            StreamObserver<ServerEnvelope> observer = entry.observer();
            try {
                synchronized (observer) {
                    observer.onNext(envelope);
                }
                deliveredSiblings.add(entry.deviceId());
            } catch (Exception e) {
                unregisterDeviceStream(normalizedUuid, entry.deviceId(), observer);
            }
        }
        return deliveredSiblings;
    }

    /**
     * Emite REVOCATION_NOTICE e encerra imediatamente a(s) stream(s) revogada(s).
     */
    public int revokeAndDisconnect(String targetUuid, String revokedDeviceId, RevocationEvent revocationEvent) {
        if (targetUuid == null || targetUuid.isBlank()) {
            return 0;
        }
        String normalizedUuid = targetUuid.trim().toLowerCase(Locale.ROOT);
        Map<String, ActiveStreamEntry> deviceMap = activeStreams.get(normalizedUuid);
        if (deviceMap == null || deviceMap.isEmpty()) {
            return 0;
        }

        List<ActiveStreamEntry> toClose = new ArrayList<>();
        synchronized (deviceMap) {
            if (revokedDeviceId != null && !revokedDeviceId.isBlank()) {
                ActiveStreamEntry removed = deviceMap.remove(revokedDeviceId.trim().toLowerCase(Locale.ROOT));
                if (removed != null) {
                    toClose.add(removed);
                }
            } else {
                toClose.addAll(deviceMap.values());
                deviceMap.clear();
            }
            if (deviceMap.isEmpty()) {
                activeStreams.remove(normalizedUuid, deviceMap);
            }
        }

        if (toClose.isEmpty()) {
            return 0;
        }

        ServerEnvelope.Builder noticeBuilder = ServerEnvelope.newBuilder()
                .setMessageId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setSenderUuid(normalizedUuid)
                .setReceiverUuid(normalizedUuid)
                .setType(MessageType.REVOCATION_NOTICE)
                .setTimestamp(System.currentTimeMillis());
        if (revocationEvent != null) {
            noticeBuilder.setRevocationEvent(revocationEvent);
        }
        ServerEnvelope notice = noticeBuilder.build();

        AtomicInteger closedCount = new AtomicInteger(0);
        for (ActiveStreamEntry entry : toClose) {
            StreamObserver<ServerEnvelope> observer = entry.observer();
            try {
                synchronized (observer) {
                    observer.onNext(notice);
                    observer.onCompleted();
                }
                closedCount.incrementAndGet();
            } catch (Exception e) {
                log.debug("Stream ja encerrada durante revogacao de {} ({}): {}",
                        normalizedUuid, entry.deviceId(), e.getMessage());
            }
        }
        return closedCount.get();
    }

    public int getActiveDeviceCount(String userUuid) {
        if (userUuid == null || userUuid.isBlank()) {
            return 0;
        }
        Map<String, ActiveStreamEntry> deviceMap = activeStreams.get(userUuid.trim().toLowerCase(Locale.ROOT));
        if (deviceMap == null) {
            return 0;
        }
        synchronized (deviceMap) {
            pruneCancelledStreamsLocked(deviceMap);
            return deviceMap.size();
        }
    }

    private static void pruneCancelledStreamsLocked(Map<String, ActiveStreamEntry> deviceMap) {
        Iterator<Map.Entry<String, ActiveStreamEntry>> it = deviceMap.entrySet().iterator();
        while (it.hasNext()) {
            StreamObserver<ServerEnvelope> obs = it.next().getValue().observer();
            if (obs instanceof ServerCallStreamObserver<?> scso && scso.isCancelled()) {
                it.remove();
            }
        }
    }
}
