# Especificação Técnica (Tech Spec) - ISP Chat MVP

## 1. Arquitetura Macro e Filosofia
A rede abandona a topologia Cliente-Servidor e adota uma malha P2P Federada de Microsserviços. Os provedores (ISPs) fornecem o roteamento, mas o sigilo dos dados E2E (End-to-End) pertence exclusivamente aos clientes (Flutter).

### Stack Tecnológica
- **Backend:** Java (Microsserviços de Roteamento), gRPC (Transporte Inter-Nós), Redis (Estado/Fila).
- **Frontend (Mobile/Desktop):** Flutter, WebRTC.
- **Infraestrutura Global:** PowerDNS, Coturn (STUN/TURN).

## 2. Fundação e Descoberta (O Bootstrap da Rede)
O problema do "Nó Semente" (como as partes se encontram) é resolvido sem criar centralização através de tecnologias clássicas.

### 2.1. Arquitetura DNS Semente (MVP)
- **Domínio Raiz:** `ispchat.dev.br`.
- **Topologia Master/Slave:** O DNS não será descentralizado por Gossip, mas via infraestrutura autoritativa do PowerDNS. Existirá 1 servidor **Master Oculto** (A fonte da verdade, hiper-seguro e inacessível ao público).
- **Slaves no Registro.br:** Teremos 2 servidores **Slaves** (ex: `ns1` e `ns2`) hospedados em datacenters de provedores parceiros robustos. Apenas estes estarão apontados no Registro.br.
- **Resolução DNS SRV:** Esses Slaves responderão a consultas SRV entregando os IPs dos "Nós Comunitários" mais rápidos para que o App Flutter inicie o processo de Onboarding e a DHT inicie o Bootstrap.

### 2.2. O Futuro da Escalabilidade (Fase 2 / BGP Anycast)
Para garantir resiliência contra ataques de estados-nação e quedas em massa, a arquitetura Semente evoluirá radicalmente:
- **Limitações Físicas:** Não podemos colocar 15 IPs no Registro.br devido ao limite físico de registradores e ao tamanho máximo de pacotes DNS UDP (512 bytes, que forçaria TCP Fallback se excedido).
- **Eleição de Slaves:** Um dashboard avaliará o uptime dos ISPs parceiros. Os 6 provedores mais estáveis serão eleitos, e a API do Registro.br será atualizada automaticamente para refletir esses 6 Slaves.
- **ASN e BGP Anycast:** A Organização solicitará um ASN (Sistema Autônomo) e blocos IPv4 (/24) e IPv6. Os Provedores "Eleitos" anunciarão esses IPs globalmente via BGP Anycast. Isso garante latência ultrabaixa para tráfego UDP (DNS, requisições STUN/TURN, e nós de entrada Kademlia). O tráfego TCP longo (gRPC) não usará Anycast para evitar queda de sessão em re-roteamentos BGP.

## 3. A Rede DHT (Distributed Hash Table)
Em vez de um protocolo "Gossip" (que inunda a rede com fofocas e consome banda excessiva), usamos **Kademlia**.
- **Lookup Logarítmico:** Uma estrutura de diretório onde o ID do ISP e o UUIDv7 do usuário ditam sua posição na rede. Encontrar qualquer usuário no mundo leva poucos milissegundos através de saltos progressivos `O(log N)`.
- **Anti-Spoofing Criptográfico:** A vulnerabilidade crítica de redes descentralizadas é o roubo de identidade. O ISP Chat exige que **toda atualização na DHT** (ex: "Meu novo IP é X") venha acompanhada de uma **Assinatura Digital** gerada pela Chave Privada atrelada ao UUID. O nó Java valida a assinatura usando a Chave Pública do remetente. Pacotes forjados são dropados silenciosamente.
- **Array de Dispositivos (Multi-device):** O registro na DHT armazena `[Chave: UUIDv7] -> [Valor: Array de Sessões Ativas]`. Se o usuário estiver logado no celular e no PC, a DHT retorna os endereços dos servidores onde as duas conexões estão vivas.

## 4. Sinalização e Transporte Interno

### 4.1. gRPC e xDS (O Roteador Local)
- Cada nó Java roda com `io.grpc.xds`. O backend faz a consulta na DHT e alimenta o Control Plane xDS localmente. Quando o App faz a ligação, o gRPC roteia o pacote diretamente da VPS do Provedor A para a VPS do Provedor B usando **mTLS** (garantindo que apenas ISPs autênticos se conectem).
- **Proteção contra Nós Falsos (Autoridade Certificadora):** Para evitar que hackers subam "nós falsos" na rede (Sybil Attack), a organização ispChat atuará como uma Autoridade Certificadora (CA) privada. 
  - Quando um provedor deseja ingressar na rede, ele comprova a posse de um ASN (Autonomous System Number) ou CNPJ válido.
  - Após a verificação, a organização assina o Certificado TLS do provedor.
  - Durante o Handshake do mTLS, se um nó tentar se conectar sem um certificado assinado pela CA oficial, a conexão é rejeitada no nível do socket. Isso blinda a rede no nível de infraestrutura contra invasores.

### 4.2. Redis (O Coração Pulsante)
- **Pub/Sub e Toque Simultâneo:** O Redis gerencia o estado "Online/Offline". Ao receber um *Ring* (chamada) via gRPC para um UUID que possui múltiplas sessões no Array, o Redis faz um broadcast local para disparar o toque em todos os aparelhos ao mesmo tempo.
- **Fila Offline (Store and Forward):** Mensagens de texto gRPC enviadas para usuários offline são salvas em uma `Redis List` persistente no nó de destino. Assim que a DHT registrar o usuário online novamente, as mensagens são descarregadas e apagadas do Redis.

## 5. Criptografia E2E (Texto, Áudio e Vídeo)
- **Mídia (WebRTC):** O canal gRPC trafega apenas os metadados (SDP e ICE Candidates). Uma vez trocados, o WebRTC estabelece um túnel criptografado direto (P2P) entre os dispositivos. O Provedor roteia zero bytes de áudio/vídeo.
- **Mensagens de Texto:** O Payload da mensagem é **criptografado no App Flutter do remetente** utilizando a Chave Pública do destinatário. O provedor enxerga apenas um blob de bytes incompreensíveis e o roteia para o destinatário, que o descriptografa com sua Chave Privada.

## 6. NAT Traversal (Firewalls e CGNAT)
- Cada ISP deverá hospedar um servidor **STUN/TURN** (Coturn).
- A chamada de áudio/vídeo utilizará STUN para descobrir os IPs públicos. Só passará pelo túnel de *Relay* (TURN) se a conexão direta P2P falhar definitivamente.

## 7. Resiliência Extrema e Anti-Censura (Fase 3)
Para garantir que a rede sobreviva a bloqueios estatais agressivos (como DNS RPZ, BGP Blackholing ou interceptação de Trânsito Tier 1), a arquitetura incorpora táticas de guerrilha cibernética:

### 7.1. Sobrevivência ao Bloqueio de DNS
Se a Anatel forçar o bloqueio do domínio `ispchat.dev.br` em todo o país:
- **Hardcoded IPs (Rota Primária de Fuga):** O App Flutter trará em seu código-fonte uma lista física de endereços de IP de 10 a 15 Provedores parceiros altamente resilientes. Se a resolução DNS falhar, o App ignora o DNS e estabelece conexão gRPC direta com esses IPs para iniciar o Bootstrap na DHT.
- **Bootstrap Social (Links de Proxy):** Se todos os IPs embutidos forem bloqueados, os provedores poderão gerar novos nós e compartilhar links no formato `ispchat://seed?ip=X.X.X.X` nas redes sociais. O usuário clica no link, o App injeta o novo IP e a rede inteira ressuscita no dispositivo.

### 7.2. Sobrevivência ao Bloqueio de IP e Trânsito (BGP Blackhole)
Se o Estado escalar para bloqueios de IP direto nos roteadores de borda (Trânsito de São Paulo/PTT-SP):
- **Camuflagem de Protocolo (Obfuscação):** Como a comunicação entre os nós é feita exclusivamente via **gRPC com mTLS na porta 443**, qualquer inspeção profunda de pacotes (DPI) dos links de trânsito enxergará apenas tráfego HTTPS padrão. Eles não podem bloquear a porta 443 sem derrubar a economia nacional.
- **Rotação Dinâmica (IP Hopping / Whack-a-mole):** Provedores possuem blocos de IPs grandes (ex: /22). Scripts automatizados detectarão quedas de conexão e trocarão instantaneamente o IP físico da VPS do servidor. O novo IP é anunciado à DHT, restabelecendo as rotas em minutos e tornando a censura estatal uma caçada inútil e cara.
- **Domain Fronting (A Arma Final - O Escudo CDN):** Em um cenário de perseguição letal, os nós dos Provedores serão colocados atrás de grandes CDNs (Cloudflare, AWS, Fastly). O App enviará o tráfego roteado para os IPs destas CDNs (que também hospedam bancos e governos), mas usando a "tampa" (SNI) disfarçada. Para o Estado bloquear o tráfego nesse nível, ele precisaria dar um Blackhole na própria Cloudflare, o que quebraria a internet do país (tática que derrotou a censura russa ao Telegram em 2018).

## 8. O Cenário "Fim do Mundo" (Fase 4: Zero-Infra e P2P Puro)
Se o Estado vencer a guerra física, prender todos os administradores e desligar 100% dos servidores do ecossistema, o aplicativo ainda funcionará como uma ferramenta de resistência P2P pura (sem nenhum servidor), adotando duas táticas finais de Fallback:

### 8.1. Conexão Direta IPv6 (O Túnel Invisível)
Como o IPv6 não possui NAT (Network Address Translation), cada dispositivo 4G/5G possui um IP globalmente roteável.
- O App possuirá um "Modo de Fuga" que gera um Código QR (ou Link Mágico) contendo o IPv6 atual do dispositivo e a Chave Pública do usuário.
- O remetente envia esse código por vias alternativas (SMS, e-mail) ou exibe fisicamente a tela.
- O App do destinatário escaneia o código e atira o túnel WebRTC **diretamente contra o IPv6 do remetente**. Os pacotes navegam cegamente pelos backbones de fibra ótica das operadoras (que não têm como bloquear tráfego P2P cifrado e aleatório). A comunicação E2E ocorre sem que nenhuma infraestrutura do ispChat precise existir.

### 8.2. Rede Mesh Local (Bluetooth LE e Wi-Fi Direct)
Se a própria Internet (backbones e 4G) for desligada pelo Governo em áreas de protesto:
- O App ativará o rádio físico do aparelho (Bluetooth Low Energy e Wi-Fi Direct).
- Usuários próximos (na mesma praça ou prédio) poderão fechar túneis P2P locais. Se houver densidade suficiente de usuários, os celulares formarão uma malha (Mesh), repassando as mensagens cifradas de telefone em telefone até o destinatário (similar ao app Briar).
