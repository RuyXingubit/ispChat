import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:app/core/config/network_bootstrap_config.dart';
import 'package:app/core/crypto/identity_crypto_service.dart';
import 'package:app/core/grpc/generated/ispchat.pb.dart';
import 'package:app/core/grpc/ispchat_node_client.dart';
import 'package:app/core/storage/secure_local_vault.dart';
import 'package:app/ui/shared_widgets.dart';
import 'package:qr_flutter/qr_flutter.dart';

/// Corporate CNPJ directory, subscriber verification/portability (`ISPCHAT-VERIFY-REQ-V1`
/// & `PortAccount`), and ISP Collaborator Portal (`SubmitVerification` / `SubmitCpfRebind`).
class VerificationAndDirectoryScreen extends StatefulWidget {
  final IspChatNodeClient nodeClient;
  final SecureLocalVault vault;
  final IdentityCryptoService crypto;
  final IdentityBundle? rootIdentity;
  final LocalDeviceIdentity? deviceIdentity;
  final VerificationCredential? currentCredential;
  final NetworkBootstrapConfig bootstrapConfig;
  final Uint8List? pinnedRootPubOverride;
  final Future<void> Function(ContactBundle added)? onContactAdded;
  final Future<void> Function(VerificationBundle updated)? onCredentialUpdated;

  const VerificationAndDirectoryScreen({
    super.key,
    required this.nodeClient,
    required this.vault,
    required this.crypto,
    this.rootIdentity,
    this.deviceIdentity,
    this.currentCredential,
    this.bootstrapConfig = const NetworkBootstrapConfig(),
    this.pinnedRootPubOverride,
    this.onContactAdded,
    this.onCredentialUpdated,
  });

  @override
  State<VerificationAndDirectoryScreen> createState() =>
      _VerificationAndDirectoryScreenState();
}

class _VerificationAndDirectoryScreenState
    extends State<VerificationAndDirectoryScreen> {
  final TextEditingController _searchController = TextEditingController();
  final TextEditingController _verifyDocController = TextEditingController();
  final TextEditingController _verifyIspCnpjController =
      TextEditingController();
  final TextEditingController _portHomeNodeController = TextEditingController();
  final TextEditingController _portIspCnpjController = TextEditingController();

  final TextEditingController _collabCertB64Controller =
      TextEditingController();
  final TextEditingController _consentUriController = TextEditingController();
  final TextEditingController _collabDocController = TextEditingController();
  final TextEditingController _collabDisplayNameController =
      TextEditingController();
  final TextEditingController _collabHomeNodeController =
      TextEditingController();
  final TextEditingController _rebindOldUuidController =
      TextEditingController();

  DocumentType _selectedDocType = DocumentType.CPF;
  bool _rebindMode = false;
  bool _busy = false;
  bool _hasSearched = false;
  String? _statusBanner;
  String? _generatedConsentUri;
  VerificationCredential? _credential;
  List<VerifiedCorporateEntry> _directoryResults =
      const <VerifiedCorporateEntry>[];

  @override
  void initState() {
    super.initState();
    _credential = widget.currentCredential;
  }

  @override
  void dispose() {
    _searchController.dispose();
    _verifyDocController.dispose();
    _verifyIspCnpjController.dispose();
    _portHomeNodeController.dispose();
    _portIspCnpjController.dispose();
    _collabCertB64Controller.dispose();
    _consentUriController.dispose();
    _collabDocController.dispose();
    _collabDisplayNameController.dispose();
    _collabHomeNodeController.dispose();
    _rebindOldUuidController.dispose();
    super.dispose();
  }

  Uint8List? get _effectiveRootPub =>
      widget.pinnedRootPubOverride ??
      widget.bootstrapConfig.decodePinnedRootPublicKeyBytes();

  Future<void> _searchDirectory() async {
    final query = _searchController.text.trim();
    if (query.isEmpty) {
      setState(() {
        _hasSearched = false;
        _directoryResults = const <VerifiedCorporateEntry>[];
      });
      return;
    }
    if (!widget.nodeClient.isConnected) {
      setState(() {
        _statusBanner = 'Conecte-se a um Home/Edge Node antes de buscar.';
      });
      return;
    }
    setState(() {
      _busy = true;
      _statusBanner = null;
    });
    try {
      final resp = await widget.nodeClient.searchVerifiedCnpj(query);
      if (!mounted) return;
      setState(() {
        _hasSearched = true;
        _directoryResults = resp.entries;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _statusBanner = 'Erro ao consultar diretório CNPJ: $e';
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _addDirectoryEntry(VerifiedCorporateEntry entry) async {
    final rootPub = _effectiveRootPub;
    if (entry.hasVerificationCredential() && rootPub != null) {
      final valid = await widget.crypto.verifyVerificationCredential(
        entry.verificationCredential,
        pinnedRootEd25519Pub: rootPub,
      );
      if (!valid) {
        if (!mounted) return;
        setState(() {
          _statusBanner =
              'Entrada corporativa rejeitada: credencial falhou na validação Root.';
        });
        return;
      }
    }

    final bundle = ContactBundle(
      uuid: CanonicalTranscripts.canonicalUuid(entry.corporateUuid),
      ed25519PublicKey: Uint8List.fromList(entry.rootEd25519Pub),
      x25519PublicKey: Uint8List.fromList(entry.rootX25519Pub),
      displayName: entry.displayName,
      isVerified: true,
      docType: DocumentType.CNPJ,
      publicCnpj: entry.cnpj,
      publicDisplayName: entry.displayName,
      verifyingIspCnpj: entry.hasVerificationCredential()
          ? entry.verificationCredential.verifyingIspCnpj
          : null,
      verifyingIspName: entry.hasVerificationCredential()
          ? entry.verificationCredential.verifyingIspName
          : null,
      verificationCredential:
          entry.hasVerificationCredential() ? entry.verificationCredential : null,
    );
    await widget.vault.saveContact(bundle);
    if (widget.onContactAdded != null) {
      await widget.onContactAdded!(bundle);
    }
    if (!mounted) return;
    setState(() {
      _statusBanner = 'Contato corporativo ${entry.displayName} salvo.';
    });
  }

  Future<void> _generateConsentUri() async {
    final root = widget.rootIdentity;
    if (root == null) {
      setState(() {
        _statusBanner =
            'Desbloqueie sua identidade Root (BIP39) para assinar o pedido de verificação.';
      });
      return;
    }
    setState(() {
      _busy = true;
      _statusBanner = null;
    });
    try {
      final consent = await widget.crypto.buildSignedUserVerificationConsent(
        userIdentity: root,
        docType: _selectedDocType,
        rawDocument: _verifyDocController.text,
        ispCnpj: _verifyIspCnpjController.text,
      );
      final uri = widget.crypto.buildVerificationConsentUri(consent: consent);
      if (!mounted) return;
      setState(() {
        _generatedConsentUri = uri;
        _statusBanner = 'Pedido de verificação assinado com sucesso.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _statusBanner = 'Dados inválidos para consentimento: $e';
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _renewCredential() async {
    final deviceId = widget.deviceIdentity;
    final root = widget.rootIdentity;
    if (!widget.nodeClient.isConnected) {
      setState(() => _statusBanner = 'Conecte-se ao nó para renovar.');
      return;
    }
    setState(() {
      _busy = true;
      _statusBanner = null;
    });
    try {
      final effectiveDevice = deviceId ??
          (root != null
              ? await widget.crypto.deriveMobileRootDeviceIdentity(root)
              : null);
      if (effectiveDevice == null) {
        throw StateError('Nenhuma identidade local ativa.');
      }
      final userUuid = root?.userUuid ??
          _credential?.userUuid ??
          effectiveDevice.delegationCert?.userUuid ??
          '';
      final req = await widget.crypto.buildSignedGetCredentialRequest(
        userUuid: userUuid,
        deviceIdentity: effectiveDevice,
      );
      final bundle = await widget.nodeClient.getOrRenewCredential(req);
      await _persistBundleIfValid(bundle);
      if (!mounted) return;
      setState(() {
        _statusBanner = 'Credencial renovada com o Principal.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _statusBanner = 'Falha na renovação: $e';
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _submitPortAccount() async {
    final root = widget.rootIdentity;
    if (root == null || !widget.nodeClient.isConnected) {
      setState(() {
        _statusBanner =
            'Conecte-se e desbloqueie sua chave Root para solicitar portabilidade.';
      });
      return;
    }
    setState(() {
      _busy = true;
      _statusBanner = null;
    });
    try {
      final nextEpoch = (_credential?.routingEpoch.toInt() ?? 0) + 1;
      final req = await widget.crypto.buildSignedPortAccountRequest(
        rootIdentity: root,
        targetHomeNodeAddress: _portHomeNodeController.text.trim(),
        targetIspCnpj: _portIspCnpjController.text.trim(),
        expectedRoutingEpoch: nextEpoch,
      );
      final bundle = await widget.nodeClient.portAccount(req);
      await _persistBundleIfValid(bundle);
      if (!mounted) return;
      setState(() {
        _statusBanner =
            'Conta portada com sucesso para ${bundle.credential.homeNodeAddress}.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _statusBanner = 'Erro na portabilidade: $e';
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _submitCollaboratorAttestation() async {
    final collabIdentity = widget.rootIdentity;
    if (collabIdentity == null || !widget.nodeClient.isConnected) {
      setState(() {
        _statusBanner =
            'Conecte-se e desbloqueie a identidade do Colaborador ISP.';
      });
      return;
    }
    setState(() {
      _busy = true;
      _statusBanner = null;
    });
    try {
      final rawCert = _collabCertB64Controller.text.trim();
      final cert = CollaboratorAuthCert.fromBuffer(
        base64Decode(base64.normalize(rawCert)),
      );
      final consentBundle = await widget.crypto
          .parseAndVerifyVerificationConsentUri(_consentUriController.text);

      VerificationBundle resultBundle;
      if (_rebindMode) {
        final req = await widget.crypto.buildSignedSubmitCpfRebindRequest(
          collaboratorIdentity: collabIdentity,
          collaboratorCert: cert,
          oldUserUuid: _rebindOldUuidController.text.trim(),
          newUserConsent: consentBundle.consent,
          rawCpf: _collabDocController.text,
          homeNodeAddress: _collabHomeNodeController.text.trim(),
        );
        resultBundle = await widget.nodeClient.submitCpfRebind(req);
      } else {
        final req = await widget.crypto.buildSignedSubmitVerificationRequest(
          collaboratorIdentity: collabIdentity,
          collaboratorCert: cert,
          userConsent: consentBundle.consent,
          rawDocument: _collabDocController.text,
          publicDisplayName: _collabDisplayNameController.text.trim(),
          homeNodeAddress: _collabHomeNodeController.text.trim(),
        );
        resultBundle = await widget.nodeClient.submitVerification(req);
      }
      if (!mounted) return;
      setState(() {
        _statusBanner =
            'Atestação aprovada pelo Principal para ${resultBundle.credential.userUuid}.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _statusBanner = 'Falha na atestação: $e';
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _persistBundleIfValid(VerificationBundle bundle) async {
    final rootPub = _effectiveRootPub;
    if (bundle.hasCredential()) {
      if (rootPub != null &&
          !await widget.crypto.verifyVerificationCredential(
            bundle.credential,
            pinnedRootEd25519Pub: rootPub,
          )) {
        throw const FormatException(
          'Credencial retornada pelo Principal falhou na verificação Root.',
        );
      }
      await widget.vault.saveVerificationCredential(bundle.credential);
      _credential = bundle.credential;
    }
    if (bundle.hasHomeNodeClaim()) {
      await widget.vault.saveHomeNodeClaim(bundle.homeNodeClaim);
    }
    if (widget.onCredentialUpdated != null) {
      await widget.onCredentialUpdated!(bundle);
    }
  }

  Future<void> _scanConsentUri() async {
    final scanned = await openMobileQrScannerDialog(
      context,
      title: 'Escanear Consentimento do Assinante',
    );
    if (scanned != null && mounted) {
      setState(() => _consentUriController.text = scanned);
    }
  }

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: const Text(
            'Verificação & Diretório ISP',
            overflow: TextOverflow.ellipsis,
          ),
          bottom: const TabBar(
            isScrollable: true,
            tabs: [
              Tab(text: 'Diretório CNPJ'),
              Tab(text: 'Minha Verificação'),
              Tab(text: 'Portal Colaborador'),
            ],
          ),
        ),
        body: Column(
          children: [
            if (_statusBanner != null)
              MaterialBanner(
                content: Text(_statusBanner!),
                actions: [
                  TextButton(
                    onPressed: () => setState(() => _statusBanner = null),
                    child: const Text('OK'),
                  ),
                ],
              ),
            Expanded(
              child: TabBarView(
                children: [
                  _buildDirectoryTab(context),
                  _buildSubscriberTab(context),
                  _buildCollaboratorTab(context),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDirectoryTab(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 760),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      key: const Key('input_cnpj_search'),
                      controller: _searchController,
                      decoration: const InputDecoration(
                        labelText: 'CNPJ ou Razão Social verificada',
                        border: OutlineInputBorder(),
                      ),
                      onSubmitted: (_) => _searchDirectory(),
                    ),
                  ),
                  const SizedBox(width: 8),
                  FilledButton.icon(
                    key: const Key('btn_search_cnpj'),
                    onPressed: _busy ? null : _searchDirectory,
                    icon: const Icon(Icons.search),
                    label: const Text('Buscar'),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              if (!_hasSearched)
                Text(
                  'Digite um CNPJ ou razão social verificada para consultar empresas oficiais no diretório.',
                  key: const Key('txt_directory_empty'),
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                )
              else if (_directoryResults.isEmpty)
                const Text(
                  'Nenhuma empresa verificada encontrada para a pesquisa informada.',
                  key: Key('txt_directory_no_matches'),
                )
              else
                ..._directoryResults.map(
                  (entry) => Card(
                    margin: const EdgeInsets.only(bottom: 10),
                    child: ListTile(
                      title: Text(
                        entry.displayName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const SizedBox(height: 4),
                          VerifiedIdentityBadge(
                            isVerified: true,
                            docType: DocumentType.CNPJ,
                            publicCnpj: entry.cnpj,
                            verifyingIspName: entry.hasVerificationCredential()
                                ? entry.verificationCredential.verifyingIspName
                                : null,
                            compact: true,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Home Node: ${entry.homeNodeAddress}',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                      trailing: TextButton.icon(
                        key: Key(
                          'btn_add_directory_contact_${entry.corporateUuid}',
                        ),
                        onPressed:
                            _busy ? null : () => _addDirectoryEntry(entry),
                        icon: const Icon(Icons.person_add_alt_1),
                        label: const Text('Adicionar'),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSubscriberTab(BuildContext context) {
    final cred = _credential;
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 760),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          Text(
                            'Status da Credencial:',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          VerifiedIdentityBadge(
                            isVerified: cred != null,
                            docType: cred?.docType,
                            publicCnpj: cred?.publicCnpj,
                            verifyingIspName: cred?.verifyingIspName,
                          ),
                        ],
                      ),
                      if (cred != null) ...[
                        const SizedBox(height: 8),
                        Text('ISP Verificador: ${cred.verifyingIspName} (${cred.verifyingIspCnpj})'),
                        Text('Home Node: ${cred.homeNodeAddress} (Época ${cred.routingEpoch})'),
                        Text(
                          'Expira em: ${DateTime.fromMillisecondsSinceEpoch(cred.expiresAtMs.toInt()).toIso8601String()}',
                        ),
                        const SizedBox(height: 8),
                        OutlinedButton.icon(
                          key: const Key('btn_renew_credential'),
                          onPressed: _busy ? null : _renewCredential,
                          icon: const Icon(Icons.refresh),
                          label: const Text('Renovar Credencial (Principal)'),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Solicitar Verificação em Loja / ISP (ISPCHAT-VERIFY-REQ-V1)',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 12),
                      DropdownButtonFormField<DocumentType>(
                        initialValue: _selectedDocType,
                        decoration: const InputDecoration(
                          labelText: 'Tipo de Documento',
                          border: OutlineInputBorder(),
                        ),
                        items: const [
                          DropdownMenuItem(
                            value: DocumentType.CPF,
                            child: Text('CPF (Pessoa Física — Privado)'),
                          ),
                          DropdownMenuItem(
                            value: DocumentType.CNPJ,
                            child: Text('CNPJ (Empresa — Diretório Público)'),
                          ),
                        ],
                        onChanged: (value) {
                          if (value != null) {
                            setState(() => _selectedDocType = value);
                          }
                        },
                      ),
                      const SizedBox(height: 8),
                      TextField(
                        key: const Key('input_verify_doc'),
                        controller: _verifyDocController,
                        decoration: InputDecoration(
                          labelText: _selectedDocType == DocumentType.CPF
                              ? 'Seu CPF'
                              : 'Seu CNPJ (numérico ou alfanumérico)',
                          border: const OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 8),
                      TextField(
                        key: const Key('input_verify_isp_cnpj'),
                        controller: _verifyIspCnpjController,
                        decoration: const InputDecoration(
                          labelText: 'CNPJ do ISP Verificador',
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 12),
                      FilledButton.icon(
                        key: const Key('btn_generate_verify_consent'),
                        onPressed: _busy ? null : _generateConsentUri,
                        icon: const Icon(Icons.verified_user_outlined),
                        label: const Text('Assinar Pedido de Verificação'),
                      ),
                      if (_generatedConsentUri != null) ...[
                        const SizedBox(height: 12),
                        Center(
                          child: Container(
                            color: Colors.white,
                            padding: const EdgeInsets.all(8),
                            child: QrImageView(
                              data: _generatedConsentUri!,
                              size: 168,
                            ),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            Expanded(
                              child: SelectableText(
                                _generatedConsentUri!,
                                key: const Key('txt_verify_consent_uri'),
                                maxLines: 2,
                                style: Theme.of(context)
                                    .textTheme
                                    .bodySmall
                                    ?.copyWith(fontFamily: 'monospace'),
                              ),
                            ),
                            IconButton(
                              tooltip: 'Copiar consent URI',
                              onPressed: () {
                                Clipboard.setData(
                                  ClipboardData(text: _generatedConsentUri!),
                                );
                              },
                              icon: const Icon(Icons.copy),
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Portabilidade de Home Node (PortAccount)',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 8),
                      TextField(
                        key: const Key('input_port_home_node'),
                        controller: _portHomeNodeController,
                        decoration: const InputDecoration(
                          labelText: 'Novo Home Node (host:port)',
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 8),
                      TextField(
                        key: const Key('input_port_isp_cnpj'),
                        controller: _portIspCnpjController,
                        decoration: const InputDecoration(
                          labelText: 'CNPJ do ISP de Destino',
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 12),
                      OutlinedButton.icon(
                        key: const Key('btn_port_account'),
                        onPressed: _busy ? null : _submitPortAccount,
                        icon: const Icon(Icons.swap_horiz),
                        label: const Text('Solicitar Portabilidade'),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCollaboratorTab(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 760),
          child: Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Atestação Presencial por Colaborador Autorizado',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 8),
                  SwitchListTile(
                    key: const Key('switch_cpf_rebind'),
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Modo Rebind de CPF (Substituir Conta Perdida)'),
                    value: _rebindMode,
                    onChanged: (v) => setState(() => _rebindMode = v),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    key: const Key('input_collab_cert_b64'),
                    controller: _collabCertB64Controller,
                    decoration: const InputDecoration(
                      labelText: 'CollaboratorAuthCert (Base64 Protobuf)',
                      border: OutlineInputBorder(),
                    ),
                    maxLines: 2,
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    key: const Key('input_subscriber_consent_uri'),
                    controller: _consentUriController,
                    decoration: InputDecoration(
                      labelText: 'URI ispchat://verify-req?... do Assinante',
                      border: const OutlineInputBorder(),
                      suffixIcon: isMobileCameraSupported()
                          ? IconButton(
                              tooltip: 'Escanear QR de consentimento',
                              icon: const Icon(Icons.qr_code_scanner),
                              onPressed: _scanConsentUri,
                            )
                          : null,
                    ),
                  ),
                  const SizedBox(height: 8),
                  if (_rebindMode) ...[
                    TextField(
                      key: const Key('input_rebind_old_uuid'),
                      controller: _rebindOldUuidController,
                      decoration: const InputDecoration(
                        labelText: 'UUIDv7 Antigo a Revogar',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 8),
                  ],
                  TextField(
                    key: const Key('input_collab_doc'),
                    controller: _collabDocController,
                    decoration: const InputDecoration(
                      labelText: 'CPF / CNPJ conferido no documento oficial',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 8),
                  if (!_rebindMode) ...[
                    TextField(
                      key: const Key('input_collab_display_name'),
                      controller: _collabDisplayNameController,
                      decoration: const InputDecoration(
                        labelText: 'Nome de Exibição Público (para CNPJ)',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 8),
                  ],
                  TextField(
                    key: const Key('input_collab_home_node'),
                    controller: _collabHomeNodeController,
                    decoration: const InputDecoration(
                      labelText: 'Home Node Designado (host:port)',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    key: const Key('btn_submit_attestation'),
                    onPressed: _busy ? null : _submitCollaboratorAttestation,
                    icon: const Icon(Icons.fact_check),
                    label: Text(
                      _rebindMode
                          ? 'Submeter Rebind de CPF'
                          : 'Aprovar & Emitir Credencial',
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
