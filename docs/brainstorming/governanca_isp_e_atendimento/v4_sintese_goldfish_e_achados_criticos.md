# v4 — Síntese da Revisão Multi-Lente Goldfish (`concepts_brief.md` Atualizado)

**Data:** 11/11/2026
**Status:** Etapa 3 do `/egm-brainstorm` — Objeções e Ajustes Arquiteturais Levantados pelos 6 Revisores Goldfish

---

## 1. Principais Objeções, Riscos e Bugs Encontrados pelos Goldfishes

### 1.1. Limite Técnico do "Mobile-Only" em Software Livre (`egm-zoom-out`)
- **Objeção:** Como `app/` (GPLv3) e `node/` (AGPLv3) têm código aberto, um script em PC pode fingir ser `platform = MOBILE` no gRPC. Usar Play Integrity / App Attest prenderia o projeto novamente ao Google/Apple.
- **Solução no Brief:** O bloqueio do nível Não-Verificado no protocolo (`ClientStreamGrpcService`) será por **capacidade criptográfica**: no máximo **1 stream simultânea**, **zero sub-chaves delegadas** (impede parear Desktop ou gerar chave de API), rate-limit rígido por minuto/destinatários novos e fora do diretório público. Apenas quem apresenta a `VerificationCredential` assinada destrava múltiplas sessões, pareamento `DESKTOP` e sub-chave `ERP_API`.

### 1.2. Servidor Principal fora do Caminho Crítico + Cofre Assimétrico Offline contra Vazamento LGPD (`egm-design`)
- **Objeção A (Disponibilidade):** Se cada nó ISP consultar o Servidor Principal a cada `Connect` ou mensagem, uma queda na VPS Principal derruba todos os provedores do país.
  - **Solução:** O Servidor Principal emite uma **Credencial de Verificação Assinada (`Ed25519`)** guardada pelo cliente e validada localmente em memória por qualquer nó ISP usando a chave pública raiz. Revogações descem de forma assíncrona para o Redis dos nós.
- **Objeção B (Invasão da VPS Principal):** Se os CPFs ficarem cifrados com chave simétrica salva na própria VPS Principal, um invasor que obtiver root na VPS extrai os CPFs de todos os provedores participantes.
  - **Solução:** A cadeia de auditoria jurídica (`user_cpf`, `collaborator_cpf`, `isp_cnpj`, `partner_cpfs` + assinaturas) será lacrada com **chave pública assimétrica `X25519` de Custódia Offline** (cuja chave privada fica guardada fora do servidor, usada apenas se houver disputa/fraude real), mantendo online apenas o índice cego `HMAC-SHA256` (`1 CPF = 1 conta verificada`) e corrente de hash `SHA-256` (`prev_hash -> entry_hash`) imutável.

### 1.3. Sub-Chaves Delegadas por Dispositivo + Fila Offline Multi-Dispositivo (`egm-design` & `egm-codebase-details`)
- **Objeção:** Hoje `OfflineMessageQueueService` faz `LPOP` destrutivo sem `ACK`: quando a conta CNPJ do provedor (ou um usuário verificado) tiver **Celular + Desktop + API** conectados, o primeiro aparelho que conectar apaga as mensagens da fila antes dos outros verem! Além disso, parear Desktop sem copiar a chave raiz do celular exige que o Desktop e a API tenham chaves próprias (`DeviceDelegationCert` assinado pelo celular verificado) com envelopamento da chave da mensagem para até 5 dispositivos ativos (permitindo revogar um PC ou token de API individualmente).

### 1.4. Gateway HTTP Local para ERPs (IXC / SGP / MK-Auth) & Recuperação por Perda de Celular (`egm-comprehension`)
- **Objeção ERP:** ERPs de provedores falam REST JSON + Webhook, não Protobuf/ChaCha20-Poly1305. Expor REST em texto puro na VPS Principal quebraria o E2E.
  - **Solução:** Criar o perfil/container **`ispchat-erp-bridge`** que roda na rede interna do próprio provedor com uma sub-chave `ERP_API` assinada pelo sócio verificado, traduzindo JSON/Webhooks locais para pacotes gRPC E2E na rede.
- **Objeção Perda de Aparelho / Anti-Sequestro:** Se o cliente perder o celular **e** as 12 palavras, ninguém tem a chave antiga (mensagens passadas não são recuperáveis, mantendo o E2E verdadeiro), então a recuperação pelo colaborador faz um **Re-Vínculo Auditado do CPF para um novo `UUIDv7`**, emitindo aviso assinado de troca de chave aos contatos e aplicando uma **janela de retenção/alerta** caso o aparelho antigo estivesse ativo recentemente (impedindo que uma credencial de colaborador comprometida tome uma conta ativa de surpresa).

### 1.5. Quatro Bugs Atuais no Repositório que Travam Subida em VPS (`egm-implementation-readiness`)
1. `node/docker-compose.yml` usa `ISPCHAT_NODE_ID`, mas `KademliaRoutingTable.java` lê `ISPCHAT_DHT_NODE_ID` -> todos os nós subiriam com o mesmo ID padrão e ignorariam uns aos outros.
2. `node/Dockerfile` roda como `USER ispchat` sem permissão para fazer bind direto na porta `443` do container -> precisa mapear `443:8443` no Docker.
3. `app/lib/core/grpc/ispchat_pb.dart` serializa bytes Protobuf manualmente -> precisa migrar para código gerado via `protoc` antes de adicionar as estruturas da Cadeia de Fé.
4. Faltam resolução DNS SRV real em `NodeDiscoveryService.dart` e conexão real do `RTCPeerConnection` (`flutter_webrtc`) + credenciais efêmeras HMAC do `Coturn`.
