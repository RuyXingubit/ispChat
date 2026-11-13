# v7 — Relatório Consolidado da Auditoria `/egm-review` (`Stages 5–9`)

* **Data:** 2026-11-11
* **Documento Auditado:** [isp_governance_and_erp_gateway.md](file:///Volumes/240/Code/ispChat/docs/designs/isp_governance_and_erp_gateway.md)
* **Modo Executado:** Preset 1 (`Uninterrupted Full Flow`: Etapas 5, 6, 7, 8 e 9 sem interrupções)
* **Entregável Consolidado:** [consolidated_review_report.md](file:///Volumes/240/Code/ispChat/consolidated_review_report.md)

## Artefatos Gerados por Etapa

1. **Etapa 5 (`Initial Zoom Out`):** [zoom_out_report.md](file:///Volumes/240/Code/ispChat/zoom_out_report.md) — `1 Blocker`, `4 Majors`, `1 Nit`
2. **Etapa 6 (`Comprehension Test`):** [comprehension_report.md](file:///Volumes/240/Code/ispChat/comprehension_report.md) — `2 Blockers`, `6 Majors`, `1 Nit`
3. **Etapa 7 (`Critic Review` — Design, Structural, Style):** [critic_report.md](file:///Volumes/240/Code/ispChat/critic_report.md) — `4 Blockers`, `9 Majors`, `9 Nits`
4. **Etapa 8 (`Intermediate Zoom Out`):** [intermediate_zoom_out_report.md](file:///Volumes/240/Code/ispChat/intermediate_zoom_out_report.md) — `3 Blockers`, `2 Majors`, `1 Nit`
5. **Etapa 9 (`Implementation Readiness` — Readiness, Codebase Details, Style):** [implementation_readiness_report.md](file:///Volumes/240/Code/ispChat/implementation_readiness_report.md) — `7 Blockers`, `5 Majors`, `1 Nit`

## Síntese dos 9 Blockers Consolidados (`B-01` a `B-09`)

* **`B-01` (Topologia Multi-Módulo Gradle & Fallback Home Node):** Reorganizar `node/` em 4 submódulos irmãos (`:ispchat-common`, `:ispchat-node`, `:ispchat-principal`, `:ispchat-erp-bridge`), declarar `api(...)` + `javax.annotation-api` em `:ispchat-common`, `flyway-core` + `grpc-netty-shaded` e fazer `:ispchat-principal` depender de `:ispchat-node` para herdar 100% dos serviços de Fallback Home Node sem duplicação, ajustando `context: ..` nos Dockerfiles.
* **`B-02` (Proxy Edge-to-Principal mTLS, Remoção de `isp_ed25519` no Servidor e Ciclo de Revogação/Renovação):** Eliminar chaves `isp_ed25519` armazenadas no servidor do provedor (autenticando o nó parceiro via certificado mTLS X.509 SAN pareado a `verified_isps.node_address`), adicionar proxies `@ConditionalOnMissingBean` em `:ispchat-node`, ancorar a chave pública offline Nível 0 (`PrincipalSigningCert`), especificar handoff QR/URI (`ispchat://verify-req?...`), adicionar `GetOrRenewCredential` (TTL 90d), `RevokeDevice`, `RevokeCollaborator`, `OfflineGovernanceCli` e desconexão imediata de streams revogadas.
* **`B-03` (Modelo Único de Chave Raiz Corporativa `CNPJ`, Limite `5`/`10` Dispositivos, Fila Ring-Buffer e Webhook Pós-`2xx`):** Padronizar `corporate_uuid` (`CNPJ`) com par de chaves raiz BIP39 em dispositivo administrativo do ISP assinando `DESKTOP` e `ERP_API` (`--print-pairing-uri`), diferenciar teto de dispositivos (`5` CPF / `10` CNPJ), usar evicção ring-buffer na fila offline de `1.000` mensagens e emitir `DELIVERY_ACK` no `ispchat-erp-bridge` somente após HTTP `2xx` do ERP.
* **`B-04` (Blindagem de CPF contra Brute-Force de $10^9$ Candidatos via `Argon2id`):** Endurecer `doc_blind_index` localmente com `Argon2id` (`64 MiB, t=3, p=1`, com separação de domínio `CPF`/`CNPJ` atrás de `BlindIndexService`) sem criar dependência de KMS em nuvem, explicitando a fronteira exata entre o blob de auditoria offline (`X25519`) e o índice cego online.
* **`B-05` (Contrato Protobuf v2 Completo, `ServerEnvelope.receiver_uuid = 8`, Frames de Controle e `NodeRoutingGrpcService`):** Definir todos os campos/tags em `ispchat.proto`, adicionar `receiver_uuid = 8` em `ServerEnvelope` (necessário para sincronia de mensagens enviadas entre dispositivos), separar `SERVER_QUEUED_ACK` de `DELIVERY_ACK`, incluir `REVOCATION_NOTICE`, persistir `replacement_notice_bytes` em `credential_revocations` e incluir `NodeRoutingGrpcService.java` nos arquivos modificados.
* **`B-06` (Transcrições Binárias `ISPCHAT-DHT-V2`/`ISPCHAT-MSG-V2`, Framing AEAD Java/Dart e Sessões DHT de `30 Dias`):** Definir tuplas exatas e ordem de bytes `[nonce(12) || ciphertext || tag(16)]` entre Java 25 JCA e Dart `cryptography`, assinar `x25519_public_key` no DHT (`ISPCHAT-DHT-V2`), indexar sessões DHT por `(user_uuid, device_id)` com TTL de `30 dias` e aceitar envelopes autenticados de primeiro contato em `E2eSessionManager`.
* **`B-07` (Quotas Reais no `turnserver.conf` Sem Acoplamento Coturn-Redis):** Configurar `max-bps=64000`, `user-quota=2`, `total-quota=200` e faixa `49152-50152` no `node/turnserver.conf` mantendo Coturn stateless, diferenciando tiers pelo TTL e rate limit de emissão em `TurnCredentialGrpcService`.
* **`B-08` (Permissões Nativas Mobile/Desktop e Entitlements Sandbox do macOS):** Incluir `AndroidManifest.xml`, `ios/Runner/Info.plist`, `macos/Runner/Info.plist`, `DebugProfile.entitlements` e `Release.entitlements` no plano para liberar rede/câmera/microfone e isolar `mobile_scanner` apenas para Android/iOS.
* **`B-09` (Fases Compiláveis `Phase 0`–`Phase 4` e Containers PostgreSQL 17 + Redis 7 no `ci.yml`):** Estruturar os itens em 5 fases atômicas com checkpoints de compilação/teste e adicionar `services:` (`postgres:17-alpine`, `redis:7-alpine`) no `.github/workflows/ci.yml` respeitando `timeout-minutes: 10`.

## Próximo Passo

Aguardar orientação do usuário sobre aplicar as correções consolidadas em [isp_governance_and_erp_gateway.md](file:///Volumes/240/Code/ispChat/docs/designs/isp_governance_and_erp_gateway.md) (fluxo de remediação `/egm-resolve`) antes de iniciar qualquer implementação em código.
