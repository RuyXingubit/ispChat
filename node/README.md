# Manual de Deploy: Nó de Provedor (ISP Chat Supernode + Coturn STUN/TURN)

Este guia detalha como um Provedor de Internet (ISP) parceiro ou administrador da malha implanta um **Nó ISP Chat** completo em uma VPS ou servidor de borda local.

Cada Nó ISP executa **3 serviços containerizados leves**:
1. **`ispchat-node` (Java 25 LTS + gRPC/Netty na porta `443/tcp`):** Roteador cego E2E, tabela DHT Kademlia federada via mTLS e fila temporária de mensagens offline.
2. **`ispchat-redis` (`redis:7-alpine` interno):** Persistência rápida de registros DHT (TTL 24h) e envelopes criptografados offline (TTL 7 dias). Não exposto à internet.
3. **`ispchat-coturn` (`coturn/coturn` nas portas `3478` e `5349`):** Servidor STUN/TURN local blindado contra SSRF para chamadas de voz e vídeo WebRTC de baixíssima latência dentro da rede do provedor (com travessia de CGNAT).

---

## 1. Pré-Requisito: Certificado mTLS Assinado pela CA Oficial

Para ingressar na federação Kademlia e se comunicar com outros provedores, o Nó precisa de um par de chaves RSA 4096-bit assinado pela **Autoridade Certificadora (CA)** do projeto:

1. Na máquina administrativa que possui acesso à CA (`ca/`), gere o certificado do novo nó:
   ```bash
   cd ca
   ./generate_node_cert.sh node-provedorX
   ```
2. O script gerará `node-provedorX.crt` e `node-provedorX.key`.
3. Você enviará **apenas** os 3 arquivos abaixo para a pasta `node/certs/` da VPS do provedor:
   - `ca.crt` (Certificado público da CA Raiz)
   - `node.crt` (renomeado a partir de `node-provedorX.crt`)
   - `node.key` (renomeado a partir de `node-provedorX.key`)

> **SEGURANÇA CRÍTICA:** Jamais envie a chave privada da CA (`ca.key`) para nenhum servidor ou provedor parceiro.

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
Para baixar exclusivamente o código do Nó sem clonar o aplicativo Flutter ou a infraestrutura de DNS:
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
- `ISPCHAT_NODE_ID`: Nome único ou UUIDv7 do nó (ex: `node1.provedorx.com.br`).
- `ISP_DOMAIN`: Domínio do provedor (ex: `provedorx.com.br`).
- `ISP_EXTERNAL_IP`: IPv4 público da VPS (usado pelo Coturn para travessia de CGNAT).
- `TURN_STATIC_AUTH_SECRET`: Segredo hex de 32 bytes gerado acima.

### 2.5. Subir o Nó e Verificar os Logs
```bash
docker compose up -d --build
docker compose logs -f
```
Você verá a mensagem no log do `ispchat-node`:
`Servidor gRPC ISP Chat iniciado na porta 443 (TLS 1.3 + mTLS condicional ativo).`

---

## 3. Portas de Firewall (UFW / Security Group)

Abra apenas as portas estritamente necessárias no firewall da VPS:

| Porta | Protocolo | Serviço | Finalidade |
| :--- | :--- | :--- | :--- |
| `443` | TCP | `ispchat-node` | Tráfego gRPC TLS 1.3 (Apps Clientes + Federação mTLS DHT) |
| `3478` | TCP / UDP | `ispchat-coturn` | Descoberta de IP público (STUN) e Relay WebRTC (TURN) |
| `5349` | TCP / UDP | `ispchat-coturn` | Relay WebRTC sob TLS/DTLS (TURNS) |
| `49152-49252` | UDP | `ispchat-coturn` | Portas efêmeras de mídia SRTP (Áudio/Vídeo E2E) |

---

## 4. Descoberta Automática no DNS do Provedor (Zero-Config)

Para que todos os assinantes do provedor descubram automaticamente o Nó local com **1ms de latência**, adicione o registro `SRV` no DNS recursivo/autoritativo do provedor (ou na zona global `ispchat.dev.br`):

```text
_ispchat._tcp.ispchat.dev.br. 300 IN SRV 10 10 443 node1.provedorx.com.br.
```
