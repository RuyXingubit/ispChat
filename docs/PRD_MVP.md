# PRD (Product Requirements Document) - ISP Chat MVP

## 1. Visão do Produto e Filosofia
O **ISP Chat** é um ecossistema de comunicação descentralizado projetado para Provedores de Internet (ISPs). O objetivo primário é devolver a infraestrutura da internet para quem a constrói, eliminando a dependência de "Big Techs" e servidores centrais corporativos. A rede será P2P (Ponto a Ponto), federada entre os provedores, priorizando privacidade absoluta, segurança criptográfica e identidade auto-soberana.

## 2. Escopo do MVP (Minimum Viable Product)
O MVP tem a missão de provar a viabilidade da comunicação inter-provedores (roteamento descentralizado) e a criptografia E2E sem um servidor central.

**O que ESTÁ no escopo:**
- Identidade de usuário auto-soberana (UUIDv7 + Chaves Criptográficas).
- Onboarding sem atrito via Descoberta DNS SRV e nós de fallback.
- Rede de roteamento baseada em DHT (Kademlia).
- Chamadas de Áudio e Vídeo (WebRTC) e Sinalização (gRPC).
- Mensagens de Texto E2E (Online e Store-and-Forward Offline).
- Bloqueio de contatos (Local Anti-Spam).

**O que NÃO ESTÁ no escopo (Fase 2):**
- Apache Kafka para chat em grupo/histórico infinito.
- Criptografia de Limiar (Sistema de votação legal para quebra de sigilo por quórum).
- Migração automatizada e portabilidade de dados entre provedores.
- Backup Zero-Knowledge da lista de contatos/bloqueios no servidor.

## 3. Identidade e Segurança (Criptografia)
A identidade no ISP Chat resolve o problema do "Triângulo de Zooko", priorizando descentralização e segurança em detrimento de "aliases globais" fáceis, que poderiam ser fraudados em uma rede sem controle central.

- **UUIDv7 (O Número da Conta):** A identidade pública do usuário. É o endereço que as outras pessoas usam para encontrá-lo.
- **Par de Chaves (A Senha do Banco):** A verdadeira segurança reside na Chave Privada (gerada localmente no Secure Enclave/Keystore do celular). O UUID está matematicamente atrelado à Chave Pública. Nenhuma ação na rede (como fazer uma ligação ou atualizar o status) é aceita sem uma Assinatura Digital feita pela Chave Privada.
- **Frase Semente (BIP39):** Para evitar a perda permanente da conta se o aparelho quebrar, o app gera uma Mnemônica de 12 palavras. O usuário anota essa frase, permitindo restaurar a exata mesma Chave Privada e UUID em novos dispositivos (celular, desktop).
- **A Ausência de Aliases Globais (@nomes):** O compartilhamento de identidade ocorre de forma segura via **QR Code** ou **Deep Link**. O usuário que escaneia salva o contato *localmente* no seu aparelho com o nome que desejar, impedindo qualquer falsidade ideológica no diretório.

## 4. Jornada do Usuário (Onboarding sem Atrito)
O aplicativo Flutter realiza a descoberta do provedor de forma invisível:
1. Ao abrir o app, ocorre uma consulta DNS SRV (`_ispchat._tcp.ispchat.dev.br`) para mapear os nós sementes da rede.
2. O App testa a latência (ping) para descobrir se a rede Wi-Fi/4G atual possui um nó ISP Chat nativo.
3. **Rede de Segurança (Fallback):** Se o usuário for cliente de um provedor que *ainda não* faz parte do ISP Chat, o aplicativo automaticamente o registra no Nó Público (Community Node) com o *menor ping* absoluto. Isso garante que qualquer pessoa possa usar o aplicativo desde o dia 1.

## 5. Funcionalidades Core (User Stories Detalhadas)
- **Compartilhamento Seguro:** Como usuário, eu quero exibir um QR Code ou enviar um Link Mágico para que amigos possam me adicionar, garantindo que ninguém se passe por mim usando meu nome.
- **Voz e Vídeo E2E:** Como usuário, eu quero fazer chamadas de áudio e vídeo blindadas por WebRTC. A segurança deve ser garantida pelas minhas chaves locais.
- **Chat de Texto (Online):** Como usuário, eu quero enviar mensagens de texto que são criptografadas no meu celular com a Chave Pública do destinatário, e transmitidas quase instantaneamente.
- **Fila Offline:** Como usuário, se eu enviar uma mensagem e meu amigo estiver sem internet, quero que o provedor de destino guarde a mensagem (criptografada) e a entregue assim que ele abrir o aplicativo.
- **Multi-dispositivos:** Como usuário, quero restaurar minhas 12 palavras no meu Celular e PC. Se alguém me ligar, a rede deve fazer os dois aparelhos tocarem simultaneamente, conectando a ligação com aquele que eu atender primeiro.
- **Bloqueio de Assédio (Privado):** Como usuário, quero bloquear UUIDs indesejados. O bloqueio ocorrerá silenciosamente no meu aparelho, derrubando a ligação antes de tocar. Meu provedor não saberá quem eu bloqueei.
