# PRD — Governança de Identidade Verificada por ISPs, Atendimento Desktop/API e Portabilidade

* **Autor:** Ruy / Antigravity
* **Data:** 12/11/2026
* **Status:** Consolidado e Sincronizado pós-`/egm-review` & `/egm-resolve` (`v1`–`v9`)
* **Especificação Técnica de Referência:** [isp_governance_and_erp_gateway.md](file:///Volumes/240/Code/ispChat/docs/designs/isp_governance_and_erp_gateway.md)

---

## 1. Motivação e Problema de Negócio

Provedores de Internet (ISPs) concentram hoje o atendimento técnico, envio de faturas, avisos de manutenção e relacionamento comercial em uma plataforma centralizada (WhatsApp Business) que cobra por conversa iniciada pelas empresas enquanto permanece gratuita para o usuário final.

O **ISP Chat** opera como uma rede federada de comunicação E2E (texto, áudio e vídeo) mantida pelos próprios provedores participantes junto ao Servidor Principal de Governança (`ispchat-principal`), sem custo por mensagem, atendendo simultaneamente **Mobile (Android/iOS)**, **Desktop** e **API de integração com ERPs (IXC, SGP, MK-Auth)**.

---

## 2. Modelo de Identidade em Dois Níveis

| Característica | Nível 1: Usuário Não-Verificado | Nível 2: Conta Verificada (CPF ou CNPJ) |
| :--- | :--- | :--- |
| **Vínculo Documental** | Sem CPF/CNPJ validado (auto-gerado no aparelho). | Vinculado obrigatoriamente a um **CPF** (11 dígitos) ou **CNPJ** (14 caracteres alfanuméricos IN RFB 2229/2024) validado por um ISP participante. |
| **Selo Visual** | Identificado explicitamente na interface como não-verificado. | **Selo Exclusivo de Verificado** (`VerificationCredential` de `90 dias`, renovada aos `60 dias`), validável criptograficamente offline. |
| **Plataformas & Sub-Chaves** | Exclusivamente **Mobile** (`1` stream simultâneo ativo, `0` sub-chaves delegadas). | **Mobile** + até **`5` dispositivos ativos** para **CPF** (`DESKTOP`) e até **`10` dispositivos ativos** para **CNPJ** (`DESKTOP` e `ERP_API`). |
| **Limites Anti-Spam & TURN** | Limite por IP/`/64`, `10` msg/min, `20` novos contatos/24h e credencial TURN de `600s` sob teto stateless de `512 kbps` (`max-bps=64000`). | Cotas ampliadas (`600` msg/min padrão para `CNPJ` corporativo) e credencial TURN de `3600s`. |
| **Diretório Público** | Não listado publicamente. | **CPF:** Nunca buscável publicamente (proteção comercial e LGPD). **CNPJ:** Listado e buscável em `DirectoryService.SearchVerifiedCnpj`. |
| **Roteamento & Portabilidade** | Sessão ao vivo no nó SRV de menor latência; fila offline no Nó Principal de Fallback. | Hospedado no Nó do seu Provedor (`HomeNodeClaim` assinado pelo `Principal`), portável entre provedores participantes ou para o Nó Principal de Fallback (`max_hops = 2`). |

---

## 3. Cadeia Criptográfica de Confiança (`Chain of Trust`) e Responsabilização Jurídica

Toda verificação de conta carrega fé atestada por uma cadeia hierárquica de assinaturas `Ed25519` registrada em livro imutável serializado (`pg_advisory_xact_lock` + hash-chain `SHA-256`) no **`ispchat-principal`**:

1. **Governança Raiz ispChat (Offline Air-Gapped)** assina o `PrincipalSigningCert` (`1 ano`) e o cadastro inicial do **CNPJ do Provedor Participante** e dos **CPFs de seus Sócios Administradores** (`RegisterPartnerIsp`).
2. **Sócios Verificados (CPFs)** autorizam e assinam (ou revogam instantaneamente) as credenciais `CollaboratorAuthCert` dos **Colaboradores Verificados (CPFs)** vinculados àquele CNPJ (validadas na origem via X.509 SAN do mTLS do nó do provedor, sem custódia de chaves `Ed25519` em servidores ISP).
3. **Colaborador Verificado (CPF + CNPJ)** confere a documentação oficial do **Usuário Final (CPF ou CNPJ)** mediante URI/QR de consentimento assinado pelo celular do titular (`ISPCHAT-VERIFY-REQ-V1`, janela de `300s`) e submete a atestação via proxy mTLS do nó ISP ao `ispchat-principal`.

### Blindagem de Segurança e Anti-Espionagem entre Concorrentes
* **Índice Cego Memory-Hard (`BlindIndexService`):** Nenhum endpoint permite consultar se um CPF de terceiro existe ou a qual provedor pertence. A unicidade (`1 CPF = 1 Conta Verificada ATIVA`) utiliza `HMAC-SHA256(pepper, Argon2id(64 MiB, t=3, p=1))`, tornando inviável enumerar o universo de $10^9$ CPFs válidos mesmo em cenário extremo de vazamento offline da VPS (`~3,2 CPU-years` / `64 PB-rounds`).
* **Cofre Assimétrico Offline (`X25519` Escrow):** O dossiê completo de auditoria (`CPF do usuário`, `CPF do colaborador`, `CNPJ/CPFs dos sócios` e assinaturas) é selado com `X25519 + HKDF-SHA256 + ChaCha20-Poly1305` para uma chave privada de custódia offline (fora da VPS), decifrável apenas via `OfflineGovernanceCli` sob ordem judicial/auditoria formal.
* **Operação Fora do Caminho Crítico:** Qualquer nó ISP valida `VerificationCredential` localmente em memória (`< 0,5 ms`) e recebe revogações assíncronas via `StreamRevocations`, mantendo mensagens e chamadas operacionais mesmo se o `ispchat-principal` estiver indisponível.

---

## 4. Atendimento Corporativo (CNPJ), Pareamento Desktop e Sidecar `ispchat-erp-bridge`

1. **Raiz BIP39 Universal (CPF e CNPJ) e Pareamento QR:**
   * A semente BIP39 raiz permanece exclusivamente no celular verificado (celular do titular para `CPF`; celular Admin do ISP para a conta corporativa `CNPJ`). Workstations Desktop exibem QR Code (`qr_flutter`) e o `ispchat-erp-bridge` emite `--print-pairing-uri`; o celular escaneia e assina um `DeviceDelegationCert` (`DESKTOP` ou `ERP_API`), com revogação individual imediata via `RevokeDevice`.
2. **Integração Local com ERPs (`ispchat-erp-bridge`):**
   * Roda na LAN privada do provedor (`127.0.0.1:8090` padrão), autenticando IXC/SGP/MK-Auth via Bearer token em tempo constante, cifrando mensagens E2E multi-dispositivo sobre TLS 1.3 (`:443`) e retendo o `DELIVERY_ACK` de respostas de clientes até que o webhook do ERP retorne HTTP `2xx` (`X-IspChat-Message-Id` + `X-IspChat-Signature`).
3. **Fila Offline Ring-Buffer por Dispositivo e Sessões DHT de `30 Dias`:**
   * Sessões de dispositivos no DHT (`ispchat:dht:sessions:<user_uuid>`) expiram em `30 dias` com chaves `X25519` assinadas (`ISPCHAT-DHT-V2`). A fila Redis (`1.000` mensagens, TTL `7 dias`) rastreia `DELIVERY_ACK` por `device_id` com reciclagem ring-buffer para que um computador desligado nunca bloqueie dispositivos ativos.
4. **Portabilidade Assinada e Re-Vínculo Imediato (`0h`) de CPF em Perda de Aparelho:**
   * Em caso de perda do celular sem backup das 12 palavras, o colaborador verificado executa `SubmitCpfRebind` com efeito imediato (`0h` de espera) mediante assinatura de consentimento do novo celular, revogando instantaneamente o `UUIDv7_old` (`REVOCATION_NOTICE` + desconexão imediata do stream ativo), emitindo `IdentityReplacementNotice` aos contatos, notificando os Sócios Administradores e gravando a cadeia completa de responsabilização no ledger.

---

## 5. Fases Compiláveis de Implementação (`Phase 0` a `Phase 4`)

1. **`Phase 0`:** Workspace Gradle com 4 subprojetos irmãos (`:ispchat-common`, `:ispchat-node`, `:ispchat-principal`, `:ispchat-erp-bridge`), contrato `ispchat.proto` V2 completo, `CanonicalTranscripts` e `BlindIndexService` (`Argon2id` + `HMAC-SHA256`).
2. **`Phase 1`:** `:ispchat-node` com enforcement de 2 níveis, amarração mTLS SAN, sessões DHT `ISPCHAT-DHT-V2` (`30d`), roteamento `HomeNodeClaim`, fila Lua ring-buffer `DELIVERY_ACK`, proxies `@ConditionalOnMissingBean`, `TurnCredentialGrpcService` e cotas stateless no `turnserver.conf`.
3. **`Phase 2`:** `:ispchat-principal` compondo `:ispchat-node` (Fallback Home Node 100% reutilizado), Flyway PostgreSQL 17 (`UUIDv7` em todas as tabelas, índice único parcial `WHERE status = 'ACTIVE'`), `AuditLedgerService` serializado, `OfflineGovernanceCli`, `PrincipalGovernanceGrpcService` e `DirectoryGrpcService`.
4. **`Phase 3`:** Sidecar `:ispchat-erp-bridge` (REST JSON + Webhook pós-`2xx` HMAC + testes integrados `CrossModuleCujEndToEndTest` cobrindo `CUJ 1`–`CUJ 6`).
5. **`Phase 4`:** Cliente Flutter Mobile/Desktop com stubs gerados por `protoc_plugin 25.0.0`, permissões/entitlements nativos Android/iOS/macOS, descoberta DNS-over-HTTPS SRV, mídia real `flutter_webrtc`, telas zero-overflow/zero-mock e pipeline GitHub Actions com containers `postgres:17-alpine` e `redis:7-alpine` (`timeout-minutes: 10`).
