# v8 — Proposta de Remediação (`/egm-resolve`) para `docs/designs/isp_governance_and_erp_gateway.md`

* **Data:** 2026-11-12
* **Etapa EGM:** `/egm-resolve` — Steps 1–4 (`Intake`, `Evaluate & Categorize`, `Report Fixes`, `Interactive Resolution Gate`)
* **Artefato Gerado:** [goldfish_review_report.md](file:///Volumes/240/Code/ispChat/goldfish_review_report.md)
* **Documento Alvo:** [isp_governance_and_erp_gateway.md](file:///Volumes/240/Code/ispChat/docs/designs/isp_governance_and_erp_gateway.md) (`1..463`)

## 1. Resumo da Avaliação do Agente Primário (Elephant)

Todos os **22 itens consolidados** do ciclo `/egm-review` (`9 High / Blockers`, `8 Medium / Majors`, `5 Low / Nits`) foram avaliados individualmente contra o código real do repositório (`node/`, `app/`, `ca/`, `.github/workflows/ci.yml`) e contra as diretrizes globais do projeto:

* **`PROPOSE FIX`:** `22` itens (`B-01`..`B-09`, `M-01`..`M-08`, `N-01`..`N-05`)
* **`CONTEST`:** `0` itens
* **`ESCALATE`:** `0` itens
* **`DECLINE`:** `0` itens

## 2. Verificação de Segurança (`Segurança em Primeiro Lugar`)

Nenhuma das 22 correções reduz segurança; todas **aumentam** ou preservam estritamente as garantias criptográficas e operacionais:
1. **`B-04` (`Argon2id` `64 MiB` + `HMAC-SHA256` em `BlindIndexService`):** Eleva o custo de força bruta offline sobre o universo finito de $10^9$ CPFs válidos (em caso de invasão da VPS Principal) de `< 0,1s` em GPU para `~3,2 CPU-years` / `64 PB-rounds`, sem dependência de Cloud KMS externo.
2. **`B-02` (Zero chave privada `Ed25519` em servidores ISP + vínculo SAN mTLS + `RevokeDevice`/`RevokeCollaborator` + encerramento imediato de streams revogados):** Elimina `isp_ed25519` online dos nós de provedores, amarra o SAN do certificado X.509 mTLS ao `verified_isps.node_address`, fixa a chave pública Root Nível 0 e encerra sessões ativas imediatamente ao receber revogação.
3. **`B-06` (`ISPCHAT-DHT-V2` assinando `device_x25519_pub` + framing binário AEAD `[nonce(12) || ciphertext || tag(16)]`):** Fecha brecha de substituição MitM de chave `X25519` no DHT para contas `MOBILE_ROOT` e padroniza criptografia E2E entre Java 25 e Dart.
4. **`B-03` (Raiz BIP39 corporativa unificada para `CNPJ` + retenção de `DELIVERY_ACK` até HTTP `2xx` no webhook do ERP):** Mantém a chave raiz corporativa sob posse exclusiva do celular Admin do ISP e impede perda silenciosa de mensagens de clientes quando o ERP estiver indisponível.
5. **`B-07` (`turnserver.conf` com `max-bps=64000`, `user-quota=2`, `total-quota=200` + TTL `600s` para não-verificados):** Aplica cotas reais de banda e portas UDP diretamente no Coturn stateless.
6. **`M-01` & `M-02` (`HomeNodeClaim` de ISP assinado pelo `Principal`, rate-limit por IP/`/64`, `hop_count <= 2`, `pg_advisory_xact_lock` + `UNIQUE(prev_entry_hash)` + `UUIDv7` PK em todas as tabelas):** Bloqueia exaustão Sybil de Redis em nós parceiros, evita bifurcação concorrente na cadeia SHA-256 de auditoria e garante `UUIDv7` universal.

## 3. Próximo Passo

Aguardando decisão do usuário no *Interactive Resolution Gate* do `/egm-resolve` (ex.: `"auto accept"` para aplicar em lote todas as 22 correções em `docs/designs/isp_governance_and_erp_gateway.md`).
