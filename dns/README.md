# Manual de Deploy: Servidor DNS (PowerDNS) na VPS

Este guia passo-a-passo detalha como preparar sua VPS (Ubuntu/Debian) do zero para rodar o nosso servidor DNS autoritativo.

## 1. Instalar o Docker CE (Script Oficial)
O método mais rápido e oficial de instalar a engine do Docker no Linux é usando o script automatizado fornecido pela própria Docker.

No terminal da sua VPS, rode:
```bash
# Baixa o script oficial
curl -fsSL https://get.docker.com -o get-docker.sh

# Executa o script (pode levar alguns minutos)
sudo sh get-docker.sh

# Adiciona seu usuário ao grupo docker para não precisar usar "sudo" toda vez
sudo usermod -aG docker $USER
```
*(Dica: Faça logout e login novamente na VPS para que o grupo `docker` seja aplicado ao seu usuário).*

## 2. Instalar o Docker Compose via GitHub
Embora as versões mais recentes do Docker já tragam o `docker compose` embutido, se você precisar do executável clássico `docker-compose` diretamente do GitHub, use os comandos abaixo (eles baixam a versão estável v2.29.1):

```bash
# Baixa o binário do docker-compose direto do GitHub
sudo curl -L "https://github.com/docker/compose/releases/download/v2.29.1/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose

# Dá permissão de execução ao binário
sudo chmod +x /usr/local/bin/docker-compose

# Verifica se instalou corretamente
docker-compose --version
```

## 3. Preparando o Ambiente e Clonando o Projeto
Com o Docker instalado, leve os arquivos desta pasta `dns/` para a VPS (via `git clone`, `scp`, etc) e entre nela:

```bash
cd dns/
```

### 3.1. Crie o arquivo `.env`
Nunca rode diretamente, primeiro copie o arquivo de exemplo:
```bash
cp .env.example .env
```

### 3.2. Gere as Senhas Fortes
Edite o arquivo `.env` (usando `nano .env` ou `vim .env`) e troque os valores das chaves por senhas novas. 

Você pode gerar essas senhas no próprio terminal da VPS copiando e colando os seguintes comandos:

```bash
# Para gerar o PDNS_DB_PASSWORD (copie o resultado e cole no .env)
openssl rand -hex 24

# Para gerar o PDNS_API_KEY (copie o resultado e cole no .env)
openssl rand -hex 32

# Para gerar o PDNS_ADMIN_SECRET_KEY (copie o resultado e cole no .env)
openssl rand -base64 32
```

### 3.3. Restrinja os IPs no Caddy
Edite o arquivo `Caddyfile`:
```bash
nano Caddyfile
```
Troque os IPs de exemplo `192.0.2.1` e `192.0.2.2` pelos seus IPs reais de onde você acessará o painel.

## 4. Subindo o Projeto ("Teste Valendo")
Tudo configurado! Agora é só subir os containers em modo daemon (background):

```bash
docker-compose up -d
```

### Verificando os Logs
Para garantir que o banco e o PowerDNS subiram e se comunicaram sem erros de senha:
```bash
docker-compose logs -f
```

Se o banco e a API subiram corretamente, você já pode acessar a interface web pelo navegador no endereço configurado no `Caddyfile` (ex: `https://dns-admin.ispchat.dev.br`) a partir do seu IP autorizado.

## 5. Configuração no Registro.br e Lógica das 3 Partes

Nesta arquitetura, estamos dividindo o nosso DNS em **3 partes lógicas**:

1. **A Parte Oculta (Gerenciamento):** 
   São os subdomínios `dns-admin.ispchat.dev.br` (Painel Web) e `dns-api.ispchat.dev.br` (API). Graças à configuração do Caddy, essas áreas ficam **totalmente ocultas e bloqueadas** para a internet pública, acessíveis apenas pelos IPs de confiança que você definiu.
2. **Servidor Autoritativo 1 (ns1):** 
   O servidor de nomes primário (`ns1.ispchat.dev.br`) que responderá na porta 53 para o mundo todo perguntando "Qual o IP do chat?".
3. **Servidor Autoritativo 2 (ns2):**
   O servidor de nomes secundário (`ns2.ispchat.dev.br`), que é uma exigência do protocolo DNS para redundância. Inicialmente, ele pode apontar para o mesmo IP da sua VPS ou para uma VPS secundária, mas o Registro.br exige que existam dois nomes.

### Passo-a-passo no Registro.br:

Para que a internet saiba que a sua VPS é quem manda no domínio `ispchat.dev.br`, você precisa fazer a **Delegação de DNS** no Registro.br:

1. Acesse sua conta no **Registro.br** e clique no domínio `ispchat.dev.br`.
2. Vá até a seção **DNS** e clique em **Alterar Servidores DNS**.
3. Preencha os campos com os nomes dos nossos servidores e o IP da sua VPS:
   - **Master 1:** `ns1.ispchat.dev.br`
   - **IPv4:** `[IP_DA_SUA_VPS]`
   - **Master 2:** `ns2.ispchat.dev.br`
   - **IPv4:** `[IP_DA_SUA_VPS]` (ou o IP da segunda VPS, se você tiver).
4. Salve as alterações. 
5. *(Atenção)*: O Registro.br vai testar se a sua VPS está respondendo na porta 53. Como nosso Docker Compose já subiu o PowerDNS, o teste do Registro.br passará com sucesso!

Após isso, qualquer subdomínio que você criar no painel (`dns-admin.ispchat.dev.br`) passará a funcionar magicamente na internet (lembrando que a propagação global do DNS pode levar algumas horas).
