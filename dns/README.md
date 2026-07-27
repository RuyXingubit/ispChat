# Manual de Deploy: Cluster DNS (PowerDNS Master/Slave)

Este guia detalha como configurar a infraestrutura de DNS do projeto, estrategicamente dividida em **3 VPSs distintas**. Essa arquitetura foi desenhada para facilitar o deploy por humanos ou sistemas automatizados, garantindo redundância, alta disponibilidade e **segurança**. 

Temos dois ambientes separados em pastas:
- `master/`: Contém o DNS Oculto (Banco de dados, PowerDNS-Admin e API) bloqueado atrás do Caddy.
- `slave/`: Contém o Servidor Autoritativo Público (PowerDNS e um banco local leve) que replica as zonas via AXFR.

A ordem de execução é fundamental e deve ser seguida exatamente como abaixo:

---

## 1. Passo a Passo: VPS 1 (DNS Oculto - Master)

Acesse a **VPS 1** via SSH. Ela é o cérebro da operação.

### 1.1. Instalar o Docker
```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER
```
*(Dica: Faça logout e login novamente para aplicar as permissões)*

### 1.2. Baixar Apenas a Pasta Master (Sparse Clone)
Para não baixar o repositório inteiro na VPS, use o `sparse-checkout` do Git:
```bash
git clone --depth 1 --filter=blob:none --sparse https://github.com/RuyXingubit/ispChat.git
cd ispChat
git sparse-checkout set dns/master
cd dns/master/
```
Gere senhas seguras e configure o arquivo `.env`:
```bash
cp .env.example .env

# Para gerar as senhas criptograficamente seguras, você pode rodar:
openssl rand -hex 24     # Para PDNS_DB_PASSWORD
openssl rand -hex 32     # Para PDNS_API_KEY
openssl rand -base64 32  # Para PDNS_ADMIN_SECRET_KEY
```

**ATENÇÃO no `.env` e no Caddy:**
1. No arquivo `.env`, preencha `SLAVE_IPS` com o IP da VPS 2 e da VPS 3 separados por vírgula (Ex: `SLAVE_IPS=1.2.3.4,5.6.7.8`). Isso autoriza os slaves a baixarem as zonas de DNS via porta 53.
2. Edite o `Caddyfile` e substitua os IPs de exemplo `192.0.2.1` e `192.0.2.2` pelo IP da sua própria máquina (de onde você acessará o painel web). Isso bloqueia o acesso à interface administrativa contra a internet pública.

Inicie os serviços:
```bash
docker compose up -d
docker compose logs -f
```
Se tudo subiu corretamente, acesse o painel web (ex: `https://dns-admin.ispchat.dev.br`).

---

## 2. Passo a Passo: VPS 2 e VPS 3 (Servidores NS1 e NS2 - Slaves)

Acesse a **VPS 2** (para o ns1) ou a **VPS 3** (para o ns2) via SSH. Elas responderão as requisições do mundo.

### 2.1. Instalar o Docker
Da mesma forma que a Master:
```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER
```

### 2.2. Baixar Apenas a Pasta Slave e Schemas (Sparse Clone)
Para o slave, precisamos baixar a pasta `slave` e também a pasta `master` (pois os scripts SQL de schema do banco são reaproveitados de lá):
```bash
git clone --depth 1 --filter=blob:none --sparse https://github.com/RuyXingubit/ispChat.git
cd ispChat
git sparse-checkout set dns/slave dns/master
cd dns/slave/
```
Configure o `.env` local:
```bash
cp .env.example .env
```
Neste `.env` de slave, basta definir senhas locais para o PostgreSQL (não precisam ser as mesmas do Master).

Inicie os serviços:
```bash
docker compose up -d
```
*(A configuração de Supermaster/Slave já está habilitada pelo Docker Compose. Para o slave receber os domínios novos automaticamente, basta adicionar os IPs dos slaves na interface do PowerDNS-Admin (na VPS 1) em "Supermasters" ou configurar as opções globais).*

---

## 3. Configuração no Registro.br (Apenas no Final)

Neste ponto, temos a VPS 1 (Master) orquestrando e as VPSs 2 e 3 (Slaves) recebendo zonas e escutando na porta 53.

Agora sim vamos avisar à internet.

1. Acesse o **Registro.br** e entre na administração do seu domínio.
2. Vá até a seção de **DNS** e clique em **Alterar Servidores DNS**.
3. Preencha os campos com os nomes e IPs exatos das suas VPSs públicas:
   - **Master 1:** `ns1.ispchat.dev.br`
   - **IPv4:** `[IP_DA_VPS_2]`
   - **Master 2:** `ns2.ispchat.dev.br`
   - **IPv4:** `[IP_DA_VPS_3]`
4. Salve as alterações. O Registro.br pingará as VPSs 2 e 3 e o teste passará.
