# ispChat — Mensageria E2EE P2P Soberana & Governança Federada de ISPs

Arquitetura multi-módulo composta por infraestrutura Java 25 LTS (Edge Nodes gRPC/mTLS + Principal Governance + Gateway ERP) e cliente multiplataforma Flutter (Android, iOS, macOS, Linux, Windows).

## Visão Geral da Arquitetura

- **Identidade Soberana (BIP39 + Ed25519 + X25519 + UUIDv7 RFC 9562)**:
  - Toda identidade raiz nasce no dispositivo (ou hardware) a partir de frase mnemônica BIP39, derivando chaves Ed25519 (assinatura) e X25519 (troca de chaves) e um `UUIDv7` determinístico vinculado à chave pública (`SHA-256(ed25519_pub)` com versão `7` e variante `2`).
  - Delegação multi-dispositivo sem exportar a frase raiz: o celular Root assina um `DeviceDelegationCert` via QR/URI para Desktops (`DESKTOP`) ou sistemas corporativos (`ERP_API`).
- **Modelo Dual-Tier & Cadeia de Confiança (4 Níveis)**:
  - **Modo Livre (Não Verificado)**: uso P2P completo sem KYC obrigatório, com rate-limit conservador em Edge Nodes e `HomeNodeClaim` auto-assinado (`routingEpoch = 0`).
  - **Modo Verificado (CPF ou CNPJ Alfanumérico IN RFB 2229/2024)**: atestação presencial ou via gateway ERP de ISP parceiro. O CPF permanece estritamente privado (apenas HMAC/sha256 e custódia cifrada sob AES-GCM/X25519 para auditoria legal); o CNPJ corporativo é publicável no diretório (`DirectoryService`).
  - **Hierarquia Criptográfica**: `Root Offline Key` -> `PrincipalSigningCert` & `PartnerISP` -> `CollaboratorAuthCert` -> `VerificationCredential` (validade 90 dias, renovação automática aos 60 dias).
- **E2EE Multi-Dispositivo & WebRTC Federado**:
  - `ChaCha20-Poly1305` com `MultiDeviceEncryptedPayload`: cifra o conteúdo uma única vez com `contentKey` aleatória e encapsula a chave por dispositivo ativo do destinatário e irmãos do remetente (`X25519` + `HKDF-SHA256` `ISPCHAT-WRAP-V2:<deviceId>`).
  - Chamadas WebRTC (`flutter_webrtc`) sinalizadas por envelopes E2EE e credenciais TURN efêmeras HMAC-SHA256 (`TurnCredentialService`).
- **Descoberta de Rede via DoH SRV**:
  - Cliente resolve `_ispchat._tcp.ispchat.dev.br` exclusivamente via DNS-over-HTTPS (DoH JSON e fallback RFC 8484 wireformat), priorizando `HomeNodeClaim` fixado, sementes sociais/manuais e fallback para `principal.ispchat.dev.br:443`.

## Estrutura do Repositório

- [`node/ispchat-common`](node/ispchat-common): contrato Protobuf/gRPC ([`ispchat.proto`](node/ispchat-common/src/main/proto/ispchat.proto)), transcrições canônicas binárias (`CanonicalTranscripts`), validadores CPF/CNPJ alfanumérico e criptografia multi-dispositivo Java.
- [`node/ispchat-node`](node/ispchat-node): nó de borda/home P2P (Kademlia DHT, streaming bidirecional gRPC, roteamento multi-nó, mTLS de federação, rate-limiting por tier, sync de revogações e provedor TURN HMAC).
- [`node/ispchat-principal`](node/ispchat-principal): serviço central de governança (PostgreSQL + Flyway, ledger encadeado por hash com escrow X25519, emissão/renovação de credenciais, portabilidade de Home Node, rebind de CPF, diretório CNPJ e CLI offline de governança Root/Escrow).
- [`node/ispchat-erp-bridge`](node/ispchat-erp-bridge): gateway REST Spring Boot para integração com ERPs de ISPs (emissão de atestações corporativas/assinantes com assinatura Ed25519 configurada pelo parceiro).
- [`app`](app): cliente Flutter com geração oficial `protoc` ([`app/tool/generate_proto.sh`](app/tool/generate_proto.sh)), cofre seguro (`FlutterSecureStorage`), DoH SRV, WebRTC e UI responsiva mobile/desktop.

## Comandos de Desenvolvimento & Testes

```bash
# Backend Java 25 LTS (todos os módulos + cobertura JaCoCo)
cd node && ./gradlew test jacocoTestReport build --no-daemon

# Regenerar stubs Dart a partir de node/ispchat-common/src/main/proto/ispchat.proto
cd app && ./tool/generate_proto.sh

# Análise estática e suíte de testes Flutter com LCOV
cd app && flutter analyze && flutter test --coverage
```

Consulte também:
- [docs/PRD_MVP.md](docs/PRD_MVP.md)
- [docs/Tech_Spec_MVP.md](docs/Tech_Spec_MVP.md)
- [docs/QA_Strategy_MVP.md](docs/QA_Strategy_MVP.md)
- [docs/designs/isp_governance_and_erp_gateway.md](docs/designs/isp_governance_and_erp_gateway.md)
