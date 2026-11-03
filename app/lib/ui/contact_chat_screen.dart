import 'package:flutter/material.dart';

import '../core/crypto/identity_crypto_service.dart';
import '../core/grpc/ispchat_pb.dart';
import '../core/messaging/e2e_session_manager.dart';
import '../core/storage/secure_local_vault.dart';
import '../core/webrtc/webrtc_call_coordinator.dart';

/// Tela de Conversa E2E (X25519 + ChaCha20-Poly1305) e Chamada de Voz/Video WebRTC
/// com um contato verificado.
class ContactChatScreen extends StatefulWidget {
  final UserIdentity localIdentity;
  final ContactBundle contact;
  final E2eSessionManager sessionManager;
  final SecureLocalVault vault;

  const ContactChatScreen({
    super.key,
    required this.localIdentity,
    required this.contact,
    required this.sessionManager,
    required this.vault,
  });

  @override
  State<ContactChatScreen> createState() => _ContactChatScreenState();
}

class _ContactChatScreenState extends State<ContactChatScreen> {
  final TextEditingController _messageController = TextEditingController();
  late final WebRtcCallCoordinator _callCoordinator;

  List<StoredChatMessage> _messages = <StoredChatMessage>[];
  String? _cryptoStatusBanner;
  bool _isSending = false;

  @override
  void initState() {
    super.initState();
    _callCoordinator = WebRtcCallCoordinator(sessionManager: widget.sessionManager);
    _loadSavedMessages();
  }

  @override
  void dispose() {
    _messageController.dispose();
    super.dispose();
  }

  Future<void> _loadSavedMessages() async {
    final List<StoredChatMessage> loaded =
        await widget.vault.loadChatMessages(widget.contact.userUuid);
    if (!mounted) return;
    setState(() {
      _messages = loaded;
    });
  }

  Future<void> _handleSendMessage() async {
    final String text = _messageController.text.trim();
    if (text.isEmpty || _isSending) return;

    setState(() {
      _isSending = true;
    });

    try {
      // Criptografa E2E (X25519 + ChaCha20-Poly1305) e assina (Ed25519)
      final ClientEnvelopeProto envelope =
          await widget.sessionManager.buildOutgoingEncryptedEnvelope(
        senderIdentity: widget.localIdentity,
        recipientContact: widget.contact,
        type: ProtoMessageType.text,
        cleartextOrJsonSignal: text,
      );

      final StoredChatMessage stored = StoredChatMessage(
        messageId: envelope.messageId,
        senderUuid: widget.localIdentity.userUuid,
        receiverUuid: widget.contact.userUuid,
        text: text,
        timestampMillis: envelope.timestamp,
        isOutgoing: true,
      );

      final List<StoredChatMessage> updated =
          await widget.vault.appendChatMessage(widget.contact.userUuid, stored);

      if (!mounted) return;
      _messageController.clear();
      setState(() {
        _messages = updated;
        _cryptoStatusBanner =
            'Pacote E2E assinado (${envelope.encryptedPayload.length} bytes cifrados, ID: ${envelope.messageId.substring(0, 8)}...)';
      });
    } finally {
      if (mounted) {
        setState(() {
          _isSending = false;
        });
      }
    }
  }

  Future<void> _handleStartCall({required bool video}) async {
    final String callId = widget.sessionManager.cryptoService.generateTemporalUuidV7();
    await _callCoordinator.initiateCallRing(
      callerIdentity: widget.localIdentity,
      calleeContact: widget.contact,
      callId: callId,
      videoEnabled: video,
    );
    if (!mounted) return;
    setState(() {
      _cryptoStatusBanner = video
          ? 'Sinalizacao RING de Video E2E disparada (Call ID: ${callId.substring(0, 8)}...)'
          : 'Sinalizacao RING de Audio E2E disparada (Call ID: ${callId.substring(0, 8)}...)';
    });
  }

  Future<void> _handleEndCall() async {
    final String callId = _callCoordinator.currentCall.callId ??
        widget.sessionManager.cryptoService.generateTemporalUuidV7();
    await _callCoordinator.declineOrHangupCall(
      localIdentity: widget.localIdentity,
      peerContact: widget.contact,
      callId: callId,
    );
    if (!mounted) return;
    setState(() {
      _cryptoStatusBanner = 'Chamada encerrada (CALL_DECLINE E2E assinado).';
    });
  }

  @override
  Widget build(BuildContext context) {
    final WebRtcCallSnapshot callSnapshot = _callCoordinator.currentCall;
    final bool hasActiveCall = callSnapshot.state == CallLifecycleState.ringingOutgoing ||
        callSnapshot.state == CallLifecycleState.ringingIncoming ||
        callSnapshot.state == CallLifecycleState.connecting ||
        callSnapshot.state == CallLifecycleState.connected;

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            const Text(
              'Chat E2E Verificado',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
              overflow: TextOverflow.ellipsis,
            ),
            Text(
              widget.contact.userUuid,
              style: const TextStyle(fontSize: 11),
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
        actions: <Widget>[
          IconButton(
            key: const Key('btn_start_audio_call'),
            tooltip: 'Chamada de Voz WebRTC E2E',
            icon: const Icon(Icons.call),
            onPressed: () => _handleStartCall(video: false),
          ),
          IconButton(
            key: const Key('btn_start_video_call'),
            tooltip: 'Chamada de Video WebRTC E2E',
            icon: const Icon(Icons.videocam),
            onPressed: () => _handleStartCall(video: true),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: <Widget>[
            if (widget.contact.directIpv6Address != null)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                color: Colors.teal.withValues(alpha: 0.2),
                child: Text(
                  'Rota Doomsday IPv6 Direta disponivel: ${widget.contact.directIpv6Address}',
                  style: const TextStyle(fontSize: 12),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            if (hasActiveCall)
              Card(
                margin: const EdgeInsets.all(8),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  child: Row(
                    children: <Widget>[
                      const Icon(Icons.ring_volume, size: 20),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Chamada WebRTC E2E (${callSnapshot.state.name})',
                          key: const Key('txt_active_call_status'),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      TextButton.icon(
                        key: const Key('btn_hangup_call'),
                        onPressed: _handleEndCall,
                        icon: const Icon(Icons.call_end, size: 18),
                        label: const Text('Encerrar'),
                      ),
                    ],
                  ),
                ),
              ),
            if (_cryptoStatusBanner != null)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                child: Row(
                  children: <Widget>[
                    const Icon(Icons.lock_outline, size: 14),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        _cryptoStatusBanner!,
                        style: const TextStyle(fontSize: 12),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
            Expanded(
              child: _messages.isEmpty
                  ? const Center(
                      child: Padding(
                        padding: EdgeInsets.all(24),
                        child: Text(
                          'Nenhuma mensagem trocada ainda.\nTodas as mensagens sao criptografadas no seu aparelho com X25519 + ChaCha20-Poly1305 e assinadas com Ed25519.',
                          textAlign: TextAlign.center,
                        ),
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.all(12),
                      itemCount: _messages.length,
                      itemBuilder: (BuildContext context, int index) {
                        final StoredChatMessage msg = _messages[index];
                        return Align(
                          alignment:
                              msg.isOutgoing ? Alignment.centerRight : Alignment.centerLeft,
                          child: Container(
                            margin: const EdgeInsets.symmetric(vertical: 4),
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                            constraints: const BoxConstraints(maxWidth: 280),
                            decoration: BoxDecoration(
                              color: msg.isOutgoing
                                  ? Theme.of(context).colorScheme.primaryContainer
                                  : Theme.of(context).colorScheme.surfaceContainerHighest,
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: <Widget>[
                                Text(msg.text),
                                const SizedBox(height: 2),
                                Text(
                                  'UUIDv7: ${msg.messageId.substring(0, 8)} • E2E',
                                  style: const TextStyle(fontSize: 10),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
            ),
            Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: <Widget>[
                  Expanded(
                    child: TextField(
                      key: const Key('input_chat_message'),
                      controller: _messageController,
                      decoration: const InputDecoration(
                        hintText: 'Mensagem criptografada E2E...',
                        border: OutlineInputBorder(),
                      ),
                      onSubmitted: (_) => _handleSendMessage(),
                    ),
                  ),
                  const SizedBox(width: 8),
                  FilledButton.icon(
                    key: const Key('btn_send_chat_message'),
                    onPressed: _isSending ? null : _handleSendMessage,
                    icon: const Icon(Icons.send, size: 18),
                    label: const Text('Enviar'),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
