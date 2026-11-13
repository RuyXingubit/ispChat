# Tech Spec — ispChat Architecture & Protocol Contract

## 1. Módulos e Contratos gRPC (`ispchat.proto`)

O contrato canônico reside em [`node/ispchat-common/src/main/proto/ispchat.proto`](../node/ispchat-common/src/main/proto/ispchat.proto) e gera código Java (`protobuf-java` 4.30.2 / `grpc-java`) e código Dart (`protoc-gen-dart` 25.0.0 via [`app/tool/generate_proto.sh`](../app/tool/generate_proto.sh)):

- **Edge / Home Node (`ispchat-node`)**:
  - `DhtClientService`: `RegisterSession` (V2 com `DeviceDelegationCert`, `HomeNodeClaim`, `VerificationCredential`) e `FindUserSessions`.
  - `ClientStreamService`: stream bidirecional `Connect(stream ClientEnvelope) returns (stream ServerEnvelope)` com frames de controle (`AUTH_HANDSHAKE`, `SERVER_QUEUED_ACK`, `DELIVERY_ACK`, `DELIVERY_ERROR`, `REVOCATION_NOTICE`, `IDENTITY_REPLACED`) e carga E2EE (`TEXT`, `RING`, `SDP_OFFER`, `SDP_ANSWER`, `ICE_CANDIDATE`, `CALL_DECLINE`).
  - `DhtPeerService` & `NodeRoutingService`: federação Kademlia e roteamento entre nós protegido por interceptor mTLS.
  - `TurnCredentialService`: emissão efêmera de credenciais TURN HMAC-SHA256 mediante assinatura `ISPCHAT-TURN-REQ-V1`.
  - `DirectoryService`: busca corporativa por CNPJ ou razão social verificada (`SearchVerifiedCnpj`).
- **Principal Governance (`ispchat-principal`)**:
  - Persistência PostgreSQL 17 com migrações Flyway, ledger encadeado por hash (`SHA-256`) e escrow cifrado (X25519 + HKDF-SHA256 + ChaCha20/AES-GCM).
  - `PrincipalGovernanceService`: `RegisterPartnerIsp`, `AuthorizeCollaborator`, `RevokeCollaborator`, `SubmitVerification`, `SubmitCpfRebind`, `GetOrRenewCredential`, `RevokeDevice`, `PortAccount` e `StreamRevocations`.
  - CLI offline em `OfflineGovernanceCli` para assinatura Root fora da rede e abertura controlada de custódia em auditoria judicial.
- **ERP Bridge (`ispchat-erp-bridge`)**:
  - API REST Spring Boot isolada por chave de API/mTLS permitindo aos sistemas internos dos ISPs submeter verificações e rebinds autenticados ao Principal.

## 2. Transcrições Binárias & Criptografia E2EE Multi-Dispositivo

Todas as assinaturas V2 utilizam `TranscriptWriter` length-prefixed (`UTF8(TAG) || [uint32_be(len) || bytes]...`) eliminando ambiguidades de delimitador:

- Tags implementadas com paridade exata Java <-> Dart (`CanonicalTranscripts`):
  - `ISPCHAT-ROOT-PRIN-V1`, `ISPCHAT-CHAIN-ISP-V1`, `ISPCHAT-CHAIN-COLLAB-V1`, `ISPCHAT-COLLAB-REVOKE-V1`, `ISPCHAT-VERIFY-REQ-V1`, `ISPCHAT-CHAIN-USER-V1`, `ISPCHAT-REBIND-V1`, `ISPCHAT-CRED-V1`, `ISPCHAT-CRED-FETCH-V1`, `ISPCHAT-DELEGATE-V1`, `ISPCHAT-DEVICE-REVOKE-V1`, `ISPCHAT-PORT-V1`, `ISPCHAT-TURN-REQ-V1`, `ISPCHAT-DHT-V2`, `ISPCHAT-HOME-V1`, `ISPCHAT-REPLACE-V1`, `ISPCHAT-REVOKE-V1`, `ISPCHAT-MSG-V2`.
- **Encapsulamento Multi-Dispositivo (`MultiDeviceEncryptedPayload`)**:
  - Chave de conteúdo efêmera `contentKey` (32 bytes) cifrando o payload uma vez com `ChaCha20-Poly1305` (`aad = "$msgId|$senderUuid|$receiverUuid"` -> `ciphertext || tag(16)`).
  - Para cada dispositivo alvo válido (`deviceId`, `targetX25519Pub`): segredo `X25519` com chave efêmera da mensagem, derivação `HKDF-SHA256` (`salt = ephPub || targetX25519Pub`, `info = "ISPCHAT-WRAP-V2:$deviceId"`) e embrulho da `contentKey` com `ChaCha20-Poly1305` (`aad = "$msgId|$deviceId"` -> `wrappedKeyBlob` de 60 bytes `[nonce(12) | ct(32) | tag(16)]`).

## 3. Cliente Flutter (`app/`)

- **Bootstrap & Rede (`DnsSrvResolver` + `NodeDiscoveryService` + `IspChatNodeClient`)**:
  - Prioridade: `HomeNodeClaim` -> Sementes locais -> DoH JSON (`application/dns-json`) -> fallback DoH RFC 8484 binário (`application/dns-message`) para `_ispchat._tcp.ispchat.dev.br` -> fallback `principal.ispchat.dev.br:443`.
  - Reconexão com backoff exponencial `1s -> 2s -> 4s -> 8s -> 16s -> 30s` e renovação automática de `VerificationCredential` após 60 dias.
- **Processamento Seguro de Envelopes (`E2eSessionManager`)**:
  - Descarta remetentes bloqueados localmente (`BlocklistService`).
  - Trata frames de controle (`SERVER_QUEUED_ACK`, `DELIVERY_ACK`, `DELIVERY_ERROR`, `REVOCATION_NOTICE`, `IDENTITY_REPLACED`) antes de descriptografar payloads.
  - Verifica assinatura `ISPCHAT-MSG-V2` e cadeia `DeviceDelegationCert` antes da descriptografia AEAD e antes de persistir novos contatos autenticados.
  - Emite `DELIVERY_ACK` sem certificados redundantes após persistir mensagens `TEXT`.
- **Plataforma Desktop vs Mobile**:
  - Leitura de QR via câmera (`mobile_scanner`) estritamente restrita a `Platform.isAndroid || Platform.isIOS`; Desktop opera via exibição QR (`qr_flutter`) e troca de links `ispchat://`.
