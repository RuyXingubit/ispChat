# v5 — Consolidação Final Aprovada do Brainstorming & Handoff para `/egm-draft`

**Data:** 11/11/2026
**Status:** Brainstorming Aprovado pelo Usuário (`positivo` -> `Prosseguir para /egm-draft`)

---

## 1. Resumo das Decisões Finais Aprovadas (`v1` a `v5`)

1. **Propósito de Negócio:** Alternativa de mensageria e voz/vídeo E2E sem cobrança por conversa para libertar os Provedores de Internet (ISPs) da dependência financeira do WhatsApp Business no atendimento, faturamento e relacionamento com assinantes.
2. **Dois Níveis de Identidade:**
   - **Não-Verificados (Anônimos):** Abertos para preservar o efeito de rede familiar/social, porém restritos na fronteira criptográfica do protocolo (`ClientStreamGrpcService`) a capacidades Mobile individuais (máximo de 1 stream ativa, zero sub-chaves delegadas, rate-limit rígido e fora do diretório público).
   - **Verificados (CPF ou CNPJ):** Recebem **Selo Exclusivo de Verificado**, permissão de pareamento **Desktop** (via QR Code assinado pelo celular verificado, gerando sub-chave delegada `DESKTOP`), permissão de **API** (`ERP_API` para integração com IXC/SGP/MK-Auth via `ispchat-erp-bridge` local) e elegibilidade para **Portabilidade**.
3. **Cadeia Criptográfica de Fé e Blindagem Anti-Espionagem no Servidor Principal:**
   - Assinaturas encadeadas `Ed25519` (`Governança Raiz -> CNPJ e Sócios do Provedor -> Colaborador CPF -> Usuário CPF/CNPJ + UUIDv7`) gravadas em log imutável encadeado por hash (`SHA-256`) no `ispchat-principal` (PostgreSQL 17).
   - CPFs e dados de auditoria lacrados com criptografia assimétrica `X25519` cuja chave privada fica **offline fora da VPS Principal** (acionada apenas em auditoria jurídica de fraude), mantendo online apenas índice cego `HMAC-SHA256` (`1 CPF = 1 conta verificada`) e exigindo assinatura de consentimento do celular do titular antes da submissão.
   - CPFs jamais são buscáveis publicamente (impedindo espionagem de carteira de assinantes entre ISPs concorrentes); CNPJs verificados permanecem abertos no diretório público.
4. **Roteamento por Nó Casa, Fila Multi-Dispositivo com ACK e Prontidão para VPS:**
   - Correção do roteamento offline inter-provedores via `HomeNodeClaim` assinado (encaminhando mensagens offline ao Nó Casa do destinatário ou ao Nó Principal de fallback).
   - Substituição do `LPOP` destrutivo em `OfflineMessageQueueService` por cursores por dispositivo com `DELIVERY_ACK` (até 5 dispositivos por conta verificada).
   - Correção dos bloqueios de VPS (`ISPCHAT_DHT_NODE_ID`, mapeamento `443:8443` sem root, `protoc` Dart codegen, DNS SRV real e `flutter_webrtc` `RTCPeerConnection` + credenciais HMAC `Coturn`).

---

## 2. Artefatos Consolidados Gerados
- Brief oficial EGM para ingestão automática do `/egm-draft`: `concepts_brief.md`
- Documento oficial consolidado de produto/governança em `docs/`: `docs/PRD_Governanca_ISP_e_Atendimento.md`
