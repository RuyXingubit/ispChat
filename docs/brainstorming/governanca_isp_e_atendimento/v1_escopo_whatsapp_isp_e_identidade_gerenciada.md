# v1 — Redirecionamento de Produto: Alternativa ao WhatsApp para ISPs & Identidade Administrada pelos Provedores

**Data:** 11/11/2026
**Status:** Em Brainstorming (`/egm-brainstorm` — Etapa 1: Definição e Estresse do Problema)

---

## 1. Diretrizes Declaradas pelo Usuário

1. **Motivação Econômica e Estratégica (Alternativa ao WhatsApp):**
   - Os Provedores de Internet (ISPs) ficaram reféns do WhatsApp (Meta): todo o relacionamento e contato com a base de assinantes passa por uma plataforma que antes era gratuita e hoje cobra das empresas por conversa/mensagem, mantendo-se gratuita apenas para o usuário final.
   - Os ISPs precisam de uma ferramenta própria e sem custo por mensagem para se conectarem diretamente com seus clientes.
2. **Cenário Prático Alvo (Validação Real):**
   - Instalar o aplicativo em 1 ou 2 celulares e rodar também no Desktop, permitindo conversa real entre o time do provedor e os usuários.
3. **Regra Inegociável de Governança e Identidade:**
   - **Apenas usuários identificados** podem usar o sistema.
   - **Somente os provedores participantes** podem administrar esses usuários e suas respectivas contas.

---

## 2. Choque Arquitetural com o Estado Atual do Repositório (Análise Crítica)

O código e a especificação atuais (`PRD_MVP.md`, `Tech_Spec_MVP.md`, `IdentityCryptoService`, `DhtClientGrpcService`) foram desenhados sob uma premissa oposta (**auto-soberania anônima sem permissão**):

| Dimensão | Como Está Hoje no Código | Conflito com a Nova Regra (#1, #2 e #3) |
| :--- | :--- | :--- |
| **Criação de Conta** | Qualquer pessoa abre o app offline, gera 12 palavras BIP39 sozinha e já ganha um `UUIDv7` válido na rede. | Viola a regra #3: usuários anônimos não identificados pelo ISP conseguem usar a rede sem qualquer aprovação ou vínculo cadastral. |
| **Administração pelo ISP** | Inexistente. O nó Java (`node/`) não tem cadastro de contas, status ativo/suspenso, revogação nem API/painel de administração para o provedor. | Viola a regra #3: o provedor não consegue criar, vincular, suspender, bloquear ou recuperar a conta de um assinante. |
| **Nó Comunitário de Fallback** | Previsto no `PRD_MVP.md` para aceitar usuários de fora dos ISPs participantes. | Conflita com a regra #3 se permitir entrada sem identificação por um ISP participante. |
| **Relacionamento ISP <-> Cliente** | Toda conta é pessoa física 1:1 adicionada manualmente por QR Code (`ispchat://contact`) e seed manual (`ispchat://seed`). | No uso real como substituto do WhatsApp do provedor, o cliente não vai colar IP de seed nem escanear QR Code de cada atendente do suporte/financeiro; e o provedor opera no Desktop atendendo múltiplos clientes. |
| **Recuperação de Conta** | Depende 100% do usuário guardar 12 palavras em papel. Se perder, perde o `UUIDv7` para sempre. | Assinantes residenciais perdem/trocam de aparelho com frequência e acionam o provedor para recuperar a conta. |
| **Fila Offline Inter-Provedores** | Bug aberto: grava no Redis do nó do remetente em vez do nó casa do destinatário. | Com contas administradas por cada ISP (*Home Node / Home ISP*), cada usuário passa a ter um provedor de origem conhecido, resolvendo naturalmente onde a fila offline deve residir. |

---

## 3. Pontos de Estresse e Riscos a Esclarecer na Etapa 1

1. **Fronteira de Segurança (Administração de Conta pelo ISP vs. Sigilo E2E):**
   - O provedor administrar o **acesso/identidade** (quem pode entrar, ativar novo aparelho, suspender inadimplente/ex-cliente) mantém a segurança alta se as chaves privadas E2E continuarem nascendo nos dispositivos e sendo apenas *certificadas/autorizadas* pelo ISP.
   - Se o provedor gerar ou custodiar chaves privadas de conversas entre terceiros no servidor, o sigilo E2E cai (redução grave de segurança e risco jurídico/LGPD).
2. **Dilema de Adoção (Canal Exclusivo Provedor-Cliente vs. Mensageiro Entre Clientes):**
   - Se o usuário só puder conversar com o próprio provedor (ou apenas com clientes de ISPs que já rodam nós), qual o incentivo para ele manter o app aberto no celular no dia a dia em vez de tratá-lo apenas como "app de segunda via de boleto"? E como ficam dependentes da família do titular do contrato?
3. **Fluxo de Provisionamento e Login do Assinante e do Atendente (Desktop/Mobile):**
   - Como o ISP cria e entrega o acesso para o cliente e para seus próprios funcionários no Desktop (painel próprio no nó, integração via API/Webhook com ERP tipo IXC/SGP/MK-Auth, código de ativação único)?
4. **Descoberta de Contatos Internos e Inter-Provedores:**
   - Quando o cliente entra no app, o canal do provedor dele já aparece pronto para conversa? E para conversar com outros usuários identificados (do mesmo ISP ou de outro ISP participante), a busca pode ocorrer por telefone/identificador cadastrado ou continua restrita a convite/QR?
