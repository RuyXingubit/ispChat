# Brainstorming: Ambiente de Testes e Homologação (`v1`)

* **Data:** 2026-11-12
* **Tema:** Infraestrutura, topologia, segredos, builds e roteiro de validação E2E fora do localhost (`ispchat-principal`, `ispchat-node`, `coturn`, `ispchat-erp-bridge` e apps móveis/desktop).
* **Regra de Segurança:** Nenhuma ação automática em VPS ou infraestrutura de produção será executada sem comunicação prévia e autorização explícita.

---

## 1. Objetivo do Ambiente de Testes

Validar em rede real (fora de mocks in-memory e loopback) todos os fluxos críticos implementados nas Fases 0–4 do [isp_governance_and_erp_gateway.md](file:///Volumes/240/Code/ispChat/docs/designs/isp_governance_and_erp_gateway.md):

1. Descoberta DNS-over-HTTPS SRV (`DnsSrvResolver` / `DnsSrvBootstrapResolver`) e eleição por latência TCP.
2. TLS 1.3 + mTLS entre nós federados e falha fechada na Cadeia de Confiança V2 (`Root -> Principal -> ISP -> Colaborador -> Usuário`).
3. Ledger PostgreSQL 17 + Flyway + Redis 7 no Principal Server, feed assíncrono de revogações e pesquisa pública de CNPJs verificados.
4. Mensageria multi-dispositivo (`MOBILE_ROOT` + `DESKTOP` pareado via QR + `ERP_API`), `DELIVERY_ACK`, fila offline cross-ISP e portabilidade (`PortAccount` / `SubmitCpfRebind`).
5. Chamada de áudio/vídeo WebRTC atravessando NAT/CGNAT via `coturn` com credenciais HMAC-SHA1 efêmeras (`TurnCredentialService`).

---

## 2. O Que Precisamos (Checklist de Requisitos Reais)

### 2.1. Topologia de Servidores / Containers

* **Nó Principal + Fallback (`ispchat-principal`)** — conforme [docker-compose.yml](file:///Volumes/240/Code/ispChat/node/ispchat-principal/docker-compose.yml):
  * Serviços: `postgres:17-alpine`, `redis:7-alpine`, `ispchat-principal` (porta `443:8443/tcp`) e `coturn/coturn:latest` fallback.
  * Portas liberadas no firewall: `443/tcp` (gRPC mTLS/TLS) e, se usar Coturn integrado no fallback, `3478/tcp+udp`, `5349/tcp+udp`, `49152-50152/udp`.
* **Pelo menos 1 Partner ISP Edge/Home Node (`ispchat-node`)** — conforme [docker-compose.yml](file:///Volumes/240/Code/ispChat/node/docker-compose.yml):
  * Serviços: `redis:7-alpine`, `ispchat-node` (porta `443:8443/tcp`) e `coturn/coturn:latest`.
  * **Recomendado ter 2 Edge Nodes (`node1` e `node2`)** se quisermos validar roteamento offline cross-ISP (`Node1 -> Node2`) e migração assinada de Home Node (`PortAccount`) entre provedores distintos (caso contrário, testa-se `Node1 <-> Principal Fallback`).
  * Portas no firewall de cada Edge Node: `443/tcp`, `3478/tcp+udp`, `5349/tcp+udp`, `49152-50152/udp` + IP público real em `ISP_EXTERNAL_IP` (para candidatos relay ICE do Coturn).
* **Gateway ERP Corporativo (`ispchat-erp-bridge`) (Opcional na Fase 1 do teste, obrigatório para CUJ 4)** — conforme [docker-compose.erp.yml](file:///Volumes/240/Code/ispChat/node/ispchat-erp-bridge/docker-compose.erp.yml):
  * Roda em rede isolada (`172.28.0.10:8090/tcp` ou bind `127.0.0.1:8090`) conectando em TLS 1.3 ao `ispchat-node` local.
  * Exige credencial delegada `ERP_API` pareada pelo celular Root da conta corporativa CNPJ + URL receptora simples para validar o webhook HMAC-SHA256.

### 2.2. Domínio, DNS Público e CA mTLS de Homologação

* **Subdomínio dedicado de testes/homologação** (ex.: `*.staging.ispchat.dev.br` ou domínio separado de produção):
  * Registros `A`: `principal-test.<dom>`, `node1-test.<dom>` (e `node2-test.<dom>`).
  * Registro `SRV`: `_ispchat._tcp.test.<dom>` apontando prioridade/peso/porta `443` para `node1-test.<dom>` e `node2-test.<dom>` (resolvido via Cloudflare/Google DoH).
* **Certificados X.509 com CA da Federação** ([generate_node_cert.sh](file:///Volumes/240/Code/ispChat/ca/generate_node_cert.sh)):
  * Emitir `principal.crt`/`.key` e `node1.crt`/`.key` (com SAN exato igual ao hostname público de `ISPCHAT_*_PUBLIC_ADDRESS`, `extendedKeyUsage = serverAuth, clientAuth`).
  * Garantir que `app/assets/certs/ca.crt` inclua a CA que assinou os nós de teste (ou usar certificados reconhecidos para TLS do app + CA interna no mTLS entre nós).

### 2.3. Cerimônia Offline de Chaves (`OfflineGovernanceCli`) — Antes do Primeiro Boot

* Usar uma chave Raiz Nível 0 **exclusiva de homologação** (nunca misturar com a chave Raiz de produção!):
  1. `OfflineGovernanceCli --init-root --out-dir <dir>` -> gera `root_ed25519` e `escrow_x25519`.
  2. Gerar par Ed25519 do Principal de testes e assinar `PrincipalSigningCert` via `--sign-principal-cert`.
  3. Gerar pares do ISP parceiro de teste e assinar o bootstrap do ISP via `--sign-isp-bootstrap` para registrar o ISP e um Colaborador de testes no Principal.
  4. Preencher `.env` (não versionados!) com secrets aleatórios (`openssl rand -hex 32`) para `POSTGRES_PASSWORD`, `ISPCHAT_PRINCIPAL_PEPPER`, `TURN_STATIC_AUTH_SECRET`, `ISPCHAT_ERP_API_SECRET` e `ISPCHAT_ERP_WEBHOOK_HMAC_SECRET`.

### 2.4. Builds do App Cliente & Hardware Físico para Teste Manual

* **Compilação do app Flutter com parâmetros do ambiente de testes** (via `--dart-define`):
  * `ISPCHAT_ROOT_PUBKEY_B64=<Root Ed25519 Pub Base64 de testes>`
  * `ISPCHAT_SRV_DOMAIN=_ispchat._tcp.test.<dom>`
  * `ISPCHAT_PRINCIPAL_ADDRESS=principal-test.<dom>:443`
* **Dispositivos necessários**:
  * **2 smartphones reais** (preferencialmente um no Wi-Fi e outro em **4G/5G CGNAT** para forçar mídia pelo Coturn/TURN).
  * **1 desktop (macOS/Linux/Windows)** para testar pareamento QR (`MOBILE_ROOT -> DESKTOP`), sincronia de histórico E2EE nos dois dispositivos e revogação instantânea.

---

## 3. Roteiro de Validação Ponta a Ponta (Smoke & CUJs)

1. **Descoberta & Handshake**: app sem seed manual descobre `node1` via DoH SRV e conecta em gRPC TLS.
2. **Tier Unverified**: conta nova envia mensagens dentro da cota e confirma bloqueio de pareamento Desktop / aparição no Diretório CNPJ.
3. **Cadeia de Confiança (CUJ 2)**: assinante assina consentimento `ispchat://verify-req?...`; colaborador aprova via `SubmitVerification`; selo verificado + busca por CNPJ no Diretório funcionam; dados em PostgreSQL ficam selados ao Escrow X25519 offline (`--verify-ledger-chain` + `--decrypt-audit-entry`).
4. **Multi-Dispositivo & Revogação (CUJ 3)**: Mobile Root autoriza Desktop (`pair-req`/`pair-grant`), troca mensagens sincronizadas com `DELIVERY_ACK` e revoga Desktop verificando `REVOCATION_NOTICE`.
5. **Cross-ISP, Portabilidade & Re-Binding (CUJ 5 e 6)**: entrega offline entre `node1 <-> node2/principal`, migração via `PortAccount` e rotação de CPF perdido via `SubmitCpfRebind` + `IDENTITY_REPLACED`.
6. **WebRTC sobre TURN**: chamada áudio/vídeo entre celular no 4G/CGNAT e celular/desktop em outra rede atravessando UDP relay no Coturn.
7. **ERP Bridge (CUJ 4)**: `POST` autenticado com Bearer token enviando aviso para cliente verificado + recebimento de webhook HMAC no receptor de teste.

---

## 4. Decisões em Aberto para Conversarmos

1. Qual formato você prefere para esse ambiente de testes:
   * **(A)** Staging em nuvem (ex.: 2 ou 3 VPS pequenas — Principal + Node1 + opcional Node2 — com domínio/DNS real e 4G/CGNAT real);
   * **(B)** Homologação local primeiro (Docker Compose local multi-container simulando Principal + Node1 + ERP Bridge) antes de subir VPS;
   * **(C)** Híbrido (Principal + Node1 em VPS e ERP Bridge + apps rodando na sua máquina/celulares).
2. Qual domínio/zona DNS vamos usar para os registros `A` e `_ispchat._tcp` de homologação?
3. Quer testar também `node2` separado para validar roteamento cross-ISP + portabilidade entre ISPs distintos já nesta primeira rodada?
