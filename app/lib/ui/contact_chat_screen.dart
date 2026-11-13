import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/messaging/e2e_session_manager.dart';
import 'package:app/core/storage/secure_local_vault.dart';
import 'package:app/core/webrtc/webrtc_call_coordinator.dart';
import 'package:app/ui/shared_widgets.dart';

/// Peer chat thread with verified badge header, live delivery states, and
/// embedded `flutter_webrtc` audio/video call controls & renderers.
class ContactChatScreen extends StatefulWidget {
  final ContactBundle contact;
  final SecureLocalVault vault;
  final E2eSessionManager? sessionManager;
  final WebRtcCallCoordinator? callCoordinator;

  const ContactChatScreen({
    super.key,
    required this.contact,
    required this.vault,
    this.sessionManager,
    this.callCoordinator,
  });

  @override
  State<ContactChatScreen> createState() => _ContactChatScreenState();
}

class _ContactChatScreenState extends State<ContactChatScreen> {
  final TextEditingController _messageController = TextEditingController();
  List<StoredChatMessage> _messages = const <StoredChatMessage>[];
  WebRtcCallSnapshot _callSnapshot = const WebRtcCallSnapshot();
  bool _sending = false;
  String? _errorBanner;

  StreamSubscription<StoredChatMessage>? _incomingSub;
  StreamSubscription<String>? _statusSub;
  StreamSubscription<WebRtcCallSnapshot>? _callSub;

  @override
  void initState() {
    super.initState();
    _loadMessages();
    _incomingSub = widget.sessionManager?.incomingMessages.listen((msg) {
      if (CanonicalTranscripts.canonicalUuid(msg.peerUuid) ==
          CanonicalTranscripts.canonicalUuid(widget.contact.uuid)) {
        _loadMessages();
      }
    });
    _statusSub = widget.sessionManager?.messageStatusUpdates.listen((_) {
      _loadMessages();
    });
    final coordinator = widget.callCoordinator;
    if (coordinator != null) {
      _callSnapshot = coordinator.currentSnapshot;
      _callSub = coordinator.callStateStream.listen((snap) {
        if (!mounted) return;
        setState(() => _callSnapshot = snap);
      });
    }
  }

  @override
  void dispose() {
    _incomingSub?.cancel();
    _statusSub?.cancel();
    _callSub?.cancel();
    _messageController.dispose();
    super.dispose();
  }

  Future<void> _loadMessages() async {
    final loaded = await widget.vault.loadChatMessages(widget.contact.uuid);
    if (!mounted) return;
    setState(() => _messages = loaded);
  }

  Future<void> _sendMessage() async {
    final text = _messageController.text.trim();
    if (text.isEmpty || _sending) return;
    setState(() {
      _sending = true;
      _errorBanner = null;
    });

    try {
      final session = widget.sessionManager;
      if (session != null && session.nodeClient.isConnected) {
        await session.sendTextMessage(
          recipient: widget.contact,
          text: text,
        );
      } else {
        final msg = StoredChatMessage(
          messageId: IdentityCryptoService.generateTemporalUuidV7(),
          peerUuid: widget.contact.uuid,
          senderUuid: session?.activeUserUuid ?? 'local',
          text: text,
          isOutgoing: true,
          timestampMs: DateTime.now().millisecondsSinceEpoch,
          deliveryState: 'offline_saved',
        );
        await widget.vault.appendChatMessage(msg);
      }
      _messageController.clear();
      await _loadMessages();
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorBanner = 'Falha ao enviar: $e');
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _startCall({required bool video}) async {
    final coordinator = widget.callCoordinator;
    if (coordinator == null) {
      setState(() => _errorBanner = 'Conecte-se ao nó para iniciar chamadas.');
      return;
    }
    try {
      await coordinator.startCall(peer: widget.contact, videoEnabled: video);
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorBanner = 'Erro ao iniciar chamada: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final peerName = widget.contact.publicDisplayName ??
        widget.contact.displayName ??
        widget.contact.uuid;

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              peerName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 2),
            VerifiedIdentityBadge(
              isVerified: widget.contact.isVerified,
              docType: widget.contact.docType,
              publicCnpj: widget.contact.publicCnpj,
              verifyingIspName: widget.contact.verifyingIspName,
              compact: true,
            ),
          ],
        ),
        actions: [
          IconButton(
            key: const Key('btn_start_audio_call'),
            tooltip: 'Chamada de áudio WebRTC',
            onPressed: () => _startCall(video: false),
            icon: const Icon(Icons.call),
          ),
          IconButton(
            key: const Key('btn_start_video_call'),
            tooltip: 'Chamada de vídeo WebRTC',
            onPressed: () => _startCall(video: true),
            icon: const Icon(Icons.videocam),
          ),
        ],
      ),
      body: Column(
        children: [
          if (_errorBanner != null)
            MaterialBanner(
              content: Text(_errorBanner!),
              actions: [
                TextButton(
                  onPressed: () => setState(() => _errorBanner = null),
                  child: const Text('OK'),
                ),
              ],
            ),
          if (_callSnapshot.status != CallStatus.idle &&
              _callSnapshot.status != CallStatus.ended)
            _buildActiveCallPanel(context),
          Expanded(
            child: _messages.isEmpty
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Text(
                        'Nenhuma mensagem trocada ainda. Todas as mensagens usam E2EE Multi-Dispositivo.',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSurfaceVariant,
                            ),
                      ),
                    ),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.all(12),
                    itemCount: _messages.length,
                    itemBuilder: (context, index) {
                      final m = _messages[index];
                      return _MessageBubble(message: m);
                    },
                  ),
          ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 6, 12, 10),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      key: const Key('input_chat_message'),
                      controller: _messageController,
                      minLines: 1,
                      maxLines: 3,
                      decoration: const InputDecoration(
                        hintText: 'Mensagem E2EE...',
                        border: OutlineInputBorder(),
                        isDense: true,
                      ),
                      onSubmitted: (_) => _sendMessage(),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    key: const Key('btn_send_message'),
                    onPressed: _sending ? null : _sendMessage,
                    icon: const Icon(Icons.send),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActiveCallPanel(BuildContext context) {
    final coordinator = widget.callCoordinator;
    final remoteRenderer = coordinator?.mediaEngine.remoteVideoRenderer;
    final localRenderer = coordinator?.mediaEngine.localVideoRenderer;

    return Card(
      margin: const EdgeInsets.all(10),
      color: Theme.of(context).colorScheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Icon(Icons.phone_in_talk),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Chamada WebRTC: ${_callSnapshot.status.name}'
                    '${_callSnapshot.turnAuthenticated ? ' (TURN)' : ''}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                ),
              ],
            ),
            if (_callSnapshot.videoEnabled &&
                remoteRenderer != null &&
                localRenderer != null) ...[
              const SizedBox(height: 8),
              SizedBox(
                height: 180,
                child: Row(
                  children: [
                    Expanded(
                      flex: 2,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: RTCVideoView(
                          remoteRenderer,
                          objectFit:
                              RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: RTCVideoView(
                          localRenderer,
                          mirror: true,
                          objectFit:
                              RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                if (_callSnapshot.status == CallStatus.ringingIncoming)
                  FilledButton.icon(
                    key: const Key('btn_accept_call'),
                    onPressed: () => coordinator?.acceptIncomingCall(),
                    icon: const Icon(Icons.call),
                    label: const Text('Atender'),
                  ),
                if (_callSnapshot.status == CallStatus.ringingIncoming)
                  OutlinedButton.icon(
                    key: const Key('btn_decline_call'),
                    onPressed: () => coordinator?.declineCall(),
                    icon: const Icon(Icons.call_end),
                    label: const Text('Recusar'),
                  ),
                if (_callSnapshot.status != CallStatus.ringingIncoming) ...[
                  OutlinedButton.icon(
                    key: const Key('btn_toggle_mute'),
                    onPressed: coordinator?.toggleMute,
                    icon: Icon(
                      _callSnapshot.audioMuted ? Icons.mic_off : Icons.mic,
                    ),
                    label: Text(_callSnapshot.audioMuted ? 'Ativar' : 'Mudo'),
                  ),
                  if (_callSnapshot.videoEnabled)
                    OutlinedButton.icon(
                      key: const Key('btn_toggle_camera'),
                      onPressed: coordinator?.toggleCamera,
                      icon: Icon(
                        _callSnapshot.cameraDisabled
                            ? Icons.videocam_off
                            : Icons.videocam,
                      ),
                      label: const Text('Câmera'),
                    ),
                  FilledButton.icon(
                    key: const Key('btn_hangup_call'),
                    style: FilledButton.styleFrom(backgroundColor: Colors.red),
                    onPressed: () => coordinator?.hangup(),
                    icon: const Icon(Icons.call_end),
                    label: const Text('Encerrar'),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  final StoredChatMessage message;

  const _MessageBubble({required this.message});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final align =
        message.isOutgoing ? Alignment.centerRight : Alignment.centerLeft;
    final bg = message.isOutgoing
        ? scheme.primaryContainer
        : scheme.surfaceContainerHighest;

    return Align(
      alignment: align,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Card(
          color: bg,
          margin: const EdgeInsets.symmetric(vertical: 4),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(message.text),
                const SizedBox(height: 4),
                Text(
                  message.deliveryState,
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
