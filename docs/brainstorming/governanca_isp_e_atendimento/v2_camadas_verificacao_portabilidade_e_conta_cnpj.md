# v2 — Modelo Híbrido (Anônimos vs. Verificados), Portabilidade e Conta CNPJ (Desktop + API)

**Data:** 11/11/2026
**Status:** Em Brainstorming (`/egm-brainstorm` — Etapa 1: Estresse de Segurança e Fronteiras)

---

## 1. Evolução das Regras Definidas pelo Usuário nesta Rodada

1. **Modelo de Dois Níveis de Identidade (Adoção Aberta + Confiança Verificada):**
   - **Usuários Não-Verificados (Anônimos):** Qualquer pessoa pode baixar e usar o app sem depender de aprovação prévia de um provedor, guardando suas próprias chaves locais. Isso preserva o efeito de rede (familiares, amigos em operadoras móveis/outros provedores).
   - **Usuários Verificados (Selo Exclusivo):** Usuários cadastrados pelos provedores participantes mediante validação de documentos recebem um **Selo Exclusivo de Verificado**. Proposta inicial do usuário: cadastrá-los no "servidor principal".
2. **Custódia de Chaves (Ponto em Esclarecimento de Segurança):**
   - Frase do usuário: *"sobre isso, para os verificados, outros usuarios guardam suas proprias chaves"*.
   - Exige confirmação explícita sob a regra *Segurança em Primeiro Lugar* para garantir que chaves privadas E2E de conversas pessoais nunca fiquem custodiadas em servidor.
3. **Portabilidade de Conta entre Provedores:**
   - Quando um usuário verificado mudar de provedor de internet:
     - Migra para o **novo provedor participante**; ou
     - Migra para o **provedor/nó principal** caso vá para uma operadora fora da rede, sem perder sua identidade/contato.
4. **Conta Corporativa do Provedor (CNPJ + Desktop + API):**
   - Cada provedor participante terá uma conta/número vinculado ao seu **CNPJ**, operando tanto pela **aplicação Desktop** (atendimento humano) quanto por **API** (integração com ERPs de provedores, cobrança, abertura de OS, notificações sem custo por mensagem).

---

## 2. Análise de Segurança e Contra-Argumentos Arquiteturais (Stress-Test)

### 2.1. Alerta de Segurança #1: O Risco do "Servidor Principal" com Cadastro de Todos os Clientes
Concentrar os documentos/cadastros dos usuários verificados de todos os provedores em um único "servidor principal" **diminui a segurança e cria três problemas graves**:
1. **Ponto Único de Falha e Bloqueio:** Se o servidor principal cair ou sofrer bloqueio, a validação de contas verificadas e a portabilidade param na rede inteira (contrariando a arquitetura descentralizada em `Tech_Spec_MVP.md`).
2. **Barreira Comercial entre Concorrentes:** Provedores regionais concorrem entre si; um ISP dificilmente entregará a lista de CPFs/contratos de seus clientes para o banco de dados central operado por terceiros.
3. **Alvo Central de Vazamento (LGPD):** Um banco único com documentos validados de milhares de assinantes atrai ataques direcionados e responsabilidade solidária sob a LGPD.

**Alternativa que Aumenta a Segurança (Zero-PII na Rede + Nó Principal apenas como Fallback/Portabilidade):**
- Cada Provedor já possui um certificado X.509 assinado pela `CA` do ispChat (`ca/ca.crt`), que pode trazer o `CNPJ` do provedor no certificado.
- Os documentos/CPF ficam **apenas no banco privado do próprio Provedor** que fez a validação.
- Para dar o **Selo de Verificado**, o Nó do Provedor assina digitalmente um *Atestado de Verificação* (`CA ispChat -> Certificado CNPJ do ISP -> Chave Pública/UUIDv7 do Cliente`). Qualquer celular confere o selo offline matematicamente, sem consultar servidor central.
- O **Nó Principal** continua existindo, mas apenas como **Nó Casa de Fallback/Portabilidade** (hospedando usuários anônimos ou portados que saíram de um ISP participante) e registro público mínimo de roteamento, sem guardar documentos sensíveis de clientes de outros provedores.

### 2.2. Alerta de Segurança #2: Chaves Privadas de Usuários Verificados vs. Conta CNPJ
- **Pessoas Físicas Verificadas:** Se o servidor guardar a chave privada `X25519`/`Ed25519` do usuário verificado, **perde-se o E2E** (redução de segurança). Se a chave privada continuar nascendo exclusivamente no celular do usuário, e o provedor apenas emitir/renovar o *Atestado de Verificação* para a nova chave pública quando o cliente trocar de aparelho (revogando o aparelho antigo), mantém-se **100% do E2E** com recuperação assistida pelo ISP.
- **Conta CNPJ do Provedor (Desktop + API):** Aqui o destinatário da mensagem **é a própria empresa (CNPJ)**. Portanto, o Nó daquele Provedor deter a chave da conta CNPJ dele para expor API/Webhook ao ERP e sessões autenticadas aos seus atendentes Desktop preserva o transporte E2E entre o celular do cliente e a infraestrutura do provedor.

---

## 3. Questões em Aberto para Fechar a Hipótese Ancorada

1. **Papel do Servidor Principal (Consulta de Segurança):** Manter documentos/dados pessoais isolados em cada Provedor (usando assinatura criptográfica do ISP para o Selo de Verificado e deixando o Servidor Principal apenas para fallback/portabilidade sem dados pessoais), ou assumir um cadastro centralizado?
2. **Confirmação sobre Chaves dos Verificados (Consulta de Segurança):** Confirmar se usuários verificados também mantêm suas chaves privadas exclusivamente no próprio aparelho (com o ISP gerenciando apenas o selo e a reativação em novo aparelho), sem custódia de chave privada no servidor.
3. **Endereçamento ("Número") e Recorte do Primeiro Teste Real (Celulares + Desktop):** Como o "número no CNPJ" e o endereço dos usuários devem funcionar para busca/contato, e qual fatia entra já neste primeiro ciclo prático (2 celulares + Desktop)?
