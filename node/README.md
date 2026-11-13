# Manual de Deploy: Nó de Provedor Parceiro (ISP Chat Partner Node + Coturn STUN/TURN)

Este guia detalha como um Provedor de Internet (ISP) parceiro implanta um **Nó ISP Chat (`ispchat-node`)** completo em uma VPS ou servidor de borda local.

Cada Nó ISP executa **3 serviços containerizados leves**:
1. **`ispchat-node` (Java 25 LTS + gRPC/Netty publicado em `443:8443/tcp`, non-root):** Roteador cego E2E Dual-Tier, tabela DHT Kademlia federada via mTLS X.509 SAN, fila offline ring-buffer com confirmação por dispositivo (`DELIVERY_ACK`), emissor de credenciais TURN e proxy mTLS para o `ispchat-principal`.
2. **`ispchat-redis` (`redis:7-alpine` interno):** Persistência de registros DHT e `HomeNodeClaim` (TTL 30 dias) e filas criptografadas offline multi-dispositivo (ring-buffer de 1.000 mensagens, TTL 7 dias). Não exposto à internet.
3. **`ispchat-coturn` (`coturn/coturn` stateless nas portas `3478` e `5349`):** Servidor STUN/TURN local blindado contra SSRF e protegido por cotas stateless (`max-bps=64000`, `user-quota=2`, `total-quota=200`) para chamadas de voz e vídeo WebRTC de baixíssima latência dentro da rede do provedor (com travessia de CGNAT).

---

## 1. Pré-Requisito: Certificado mTLS Assinado pela CA Oficial (com SAN)

Para ingressar na federação Kademlia e autenticar sua origem perante o `ispchat-principal`, o Nó precisa de um certificado RSA 4096-bit assinado pela **Autoridade Certificadora (CA)** contendo o domínio ou IP público no `subjectAltName` (SAN):

1. Na máquina administrativa que possui acesso à CA (`ca/`), gere o certificado informando os **dois argumentos obrigatórios** (`<node_name>` e `<node_ip_or_domain>`):
   ```bash
   cd ca
   ./generate_node_cert.sh node-provedorX node1.provedorx.com.br
   ```
2. O script gerará `node-provedorX.crt` e `node-provedorX.key`.
3. Você enviará **apenas** os 3 arquivos abaixo para a pasta `node/certs/` da VPS do provedor:
   - `ca.crt` (Certificado público da CA Raiz)
   - `node.crt` (renomeado a partir de `node-provedorX.crt`)
   - `node.key` (renomeado a partir de `node-provedorX.key`)

> **SEGURANÇA CRÍTICA:** Jamais envie a chave privada da CA (`ca.key`) ou chaves Raiz Nível 0 para nenhum servidor ou provedor parceiro. Os nós de provedores não custodiam chaves privadas `Ed25519`/`X25519` de contas.

---

## 2. Passo a Passo na VPS do Provedor

### 2.1. Instalar o Docker e Docker Compose
```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER
```
*(Faça logout e login novamente para aplicar a permissão do grupo `docker`)*

### 2.2. Baixar Apenas a Pasta `node/` (Sparse Clone)
Para baixar exclusivamente o código do backend sem clonar o aplicativo Flutter:
```bash
git clone --depth 1 --filter=blob:none --sparse https://github.com/RuyXingubit/ispChat.git
cd ispChat
git sparse-checkout set node
cd node/
```

### 2.3. Posicionar os Certificados mTLS
Crie o diretório `certs/` com permissão restrita e copie os certificados gerados no Passo 1:
```bash
mkdir -p certs
chmod 700 certs
# Copie ca.crt, node.crt e node.key para ./certs/
chmod 600 certs/node.key
chmod 644 certs/ca.crt certs/node.crt
```

### 2.4. Configurar o Arquivo `.env`
Copie o arquivo de exemplo e gere o segredo criptográfico do TURN:
```bash
cp .env.example .env
openssl rand -hex 32   # Copie a saída para TURN_STATIC_AUTH_SECRET no .env
```

Edite o `.env` com os dados reais da VPS do Provedor:
- `ISPCHAT_DHT_NODE_ID`: UUIDv7 único deste nó na malha Kademlia.
- `ISPCHAT_NODE_PUBLIC_ADDRESS`: Endereço público `host:443` correspondente ao SAN do certificado (ex: `node1.provedorx.com.br:443`).
- `ISPCHAT_ROOT_PUBLIC_KEY`: Chave pública Ed25519 Raiz Nível 0 da rede.
- `ISPCHAT_PRINCIPAL_ADDRESS`: Endereço `host:443` do servidor `ispchat-principal`.
- `ISP_DOMAIN`: Domínio do provedor (ex: `provedorx.com.br`).
- `ISP_EXTERNAL_IP`: IPv4 público da VPS (usado pelo Coturn para travessia de CGNAT).
- `TURN_STATIC_AUTH_SECRET`: Segredo hex de 32 bytes compartilhado entre `ispchat-node` e `ispchat-coturn`.

### 2.5. Subir o Nó e Verificar os Logs
```bash
docker compose up -d --build
docker compose logs -f
```
O container executa como usuário sem privilégios (`ispchat:ispchat`) na porta interna `8443`, publicada externamente pelo Docker em `443:8443/tcp`.

---

## 3. Portas de Firewall (UFW / Security Group)

Abra apenas as portas estritamente necessárias no firewall da VPS:

| Porta | Protocolo | Serviço | Finalidade |
| :--- | :--- | :--- | :--- |
| `443` | TCP | `ispchat-node` | Tráfego gRPC TLS 1.3 (Apps Clientes + Federação mTLS DHT / Home Node) |
| `3478` | TCP / UDP | `ispchat-coturn` | Descoberta de IP público (STUN) e Relay WebRTC (TURN) |
| `5349` | TCP / UDP | `ispchat-coturn` | Relay WebRTC sob TLS/DTLS (TURNS) |
| `49152-50152` | UDP | `ispchat-coturn` | Portas efêmeras de mídia SRTP (Áudio/Vídeo E2E) |

---

## 4. Descoberta Automática no DNS do Provedor (Zero-Config)

Para que todos os assinantes do provedor e demais nós Kademlia descubram automaticamente o Nó via DNS-over-HTTPS / JNDI SRV, adicione o registro `SRV`:

```text
_ispchat._tcp.ispchat.dev.br. 300 IN SRV 10 10 443 node1.provedorx.com.br.
```
