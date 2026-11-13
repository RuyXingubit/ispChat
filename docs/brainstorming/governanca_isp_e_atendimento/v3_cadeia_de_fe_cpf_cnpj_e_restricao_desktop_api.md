# v3 — Cadeia Criptográfica de Fé (CNPJ/Sócios/Colaboradores), Vínculo CPF/CNPJ e Bloqueio Desktop/API

**Data:** 11/11/2026
**Status:** Em Brainstorming (`/egm-brainstorm` — Etapa 1: Fechamento de Blindagem Comercial e Criptográfica)

---

## 1. Definições Estruturais Consolidadas pelo Usuário

1. **Cadeia de Verificação e Responsabilização (Fé e Rastreabilidade Jurídica):**
   - A verificação de dados pessoais não é um booleano solto; é uma **cadeia hierárquica de responsabilidade civil/criminal**:
     1. **Nó/Governança Principal ispChat** valida o **CNPJ do Provedor** e os **CPFs dos Sócios**.
     2. **Sócios Verificados** validam e autorizam os **Colaboradores (CPFs)** daquele Provedor.
     3. **Colaborador Verificado** confere os documentos do **Usuário (CPF ou CNPJ)** e assina o ato de verificação.
   - O **Servidor Principal** armazena de forma auditável essa **cadeia de verificação** (`qual usuário [CPF/CNPJ + UUIDv7] foi validado por qual colaborador [CPF] de qual provedor [CNPJ / Sócios]`).
   - Se uma conta verificada se provar fraudulenta no futuro, a cadeia aponta de forma irrefutável qual CPF (colaborador/sócios) e qual CNPJ deram fé àquela identidade.

2. **Vínculo CPF/CNPJ e Segregação Estrita de Plataformas:**
   - **Sem CPF ou CNPJ validado = Usuário Não-Verificado:**
     - Restrito exclusivamente ao aplicativo **Mobile** (Android/iOS).
     - Sem selo de verificado, sem acesso ao cliente **Desktop** e sem acesso à **API**.
   - **Com CPF ou CNPJ validado na Cadeia de Fé = Usuário Verificado:**
     - Recebe o **Selo Exclusivo de Verificado**.
     - Habilitado para usar **Mobile**, **Desktop** e **API**.
     - Elegível para **Portabilidade** entre provedores participantes ou para o **Servidor/Nó Principal**.

3. **Escopo de Entrega e Infraestrutura Alvo:**
   - Todo o ecossistema definido precisa funcionar de ponta a ponta (Mobile, Desktop, API, Cadeia de Verificação e Portabilidade).
   - O usuário disponibilizará uma **VPS com IPs públicos** para implantação real (sempre respeitando a regra mandatória de autorização prévia explícita antes de qualquer comando em VPS).

---

## 2. Teste de Estresse: Vetores de Ataque e Pontos Cegos Identificados

### 2.1. Espionagem Comercial de Carteira de Clientes entre Provedores (Risco Crítico)
- Se a cadeia `CPF -> Conta Verificada -> CNPJ do Provedor que verificou` puder ser consultada publicamente por CPF na rede ou na API, **um provedor concorrente pode rodar um script testando listas de CPFs de uma cidade para descobrir todos os assinantes do provedor vizinho** (e abordá-los comercialmente).
- **Mitigação Proposta:**
  - Na tela pública do chat, o contato vê o **Selo de Verificado** (ex.: *"Identidade Verificada via Rede ISP Chat / Provedor X"*), mas **nunca** o CPF aberto de pessoa física, nem permite busca pública reversa por CPF de terceiros.
  - No Servidor Principal, o CPF/CNPJ fica cifrado em repouso (auditoria jurídica restrita) e indexado por `HMAC-SHA256` com chave secreta do registro raiz (impedindo enumeração pública e garantindo unicidade `1 CPF = 1 Conta Verificada`).
  - Já contas corporativas (**CNPJ**, como o próprio Provedor) podem optar por listagem/busca pública para que seus clientes os encontrem imediatamente.

### 2.2. Sequestro Malicioso de Portabilidade e Falsificação de Auditoria
- **Não-Repúdio Criptográfico do Colaborador:** Se o registro no Servidor Principal fosse apenas um `INSERT` de banco de dados sem assinatura digital, um administrador de banco ou invasor poderia fabricar um registro acusando falsamente um colaborador/provedor. Por isso, cada validação deve gerar um **Atestado Assinado com a Chave Privada `Ed25519` do próprio Colaborador e co-assinado pelo Nó do Provedor**, guardado no livro de auditoria do Servidor Principal.
- **Proteção contra Portabilidade Forçada:** Um colaborador de um Provedor B não pode migrar a conta verificada de um cliente do Provedor A apenas digitando o CPF dele; a portabilidade deve exigir **confirmação assinada no celular atual do usuário** (ou processo formal de recuperação com revogação auditada na cadeia quando o aparelho foi perdido).

### 2.3. Impedir que um Desktop/Bot Finja ser "Mobile Não-Verificado"
- No protocolo gRPC (`ClientStreamService`), sessões declaradas como `DESKTOP` ou `API` exigirão obrigatoriamente um `VerifiedAccountCertificate` válido e não revogado.
- Sessões não-verificadas (`MOBILE`) terão limites rígidos de taxa de envio (*rate-limiting* anti-spam por IP e por `UUIDv7`) e não poderão abrir sessões simultâneas de Desktop/API.

---

## 3. Últimos Dois Pontos para Fechar a Hipótese antes do Goldfish Review

1. **Privacidade Comercial de CPF vs. Descoberta de CNPJ:** Aprovar que **CPFs jamais sejam buscáveis publicamente** (evitando que um provedor concorrente descubra os clientes do outro), deixando a busca direta aberta apenas para **CNPJs verificados** (como o atendimento do provedor) ou pelo identificador/QR do ispChat?
2. **Pareamento do Desktop e Uso da API para Verificados:** No Desktop, o login do usuário verificado será feito **escaneando um QR Code na tela do PC com o celular verificado** (estilo WhatsApp Web, autorizando uma chave própria do Desktop sem expor a chave do celular), e para a **API**, o titular verificado (especialmente CNPJ do provedor) gera um token/credencial de serviço vinculado à sua conta?
