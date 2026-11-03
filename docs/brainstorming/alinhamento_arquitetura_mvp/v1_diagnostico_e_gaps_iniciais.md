# Diagnóstico de Alinhamento e Stress-Test Arquitetural (v1)

* **Autor:** Ruy / Antigravity (EGM Brainstorm)
* **Data:** 2026-11-02
* **Tema:** Verificação de rumo do projeto atual contra a especificação original do MVP (`PRD_MVP.md`, `Tech_Spec_MVP.md`, `QA_Strategy_MVP.md`).

---

## 1. Visão Geral (Overview)

O objetivo desta revisão é auditar se a implementação iniciada nos módulos `dns/`, `ca/`, `node/` e `app/` está fiel à proposta original do ISP Chat (rede P2P federada entre provedores, sem servidor central, com identidade auto-soberana e criptografia ponta a ponta) e identificar falhas estruturais ou de segurança antes de escrevermos a lógica pesada de backend e frontend.

A direção macro está alinhada com o objetivo do produto, mas a auditoria técnica revelou **5 contradições críticas** entre a especificação e o que foi estruturado até agora. Se seguirmos sem corrigi-las, o sistema terá brechas de sequestro de identidade (spoofing na DHT), bloqueio de conexão dos clientes Flutter por conflito de mTLS e exposição de zona no DNS Master.

---

## 2. Os 5 Pontos Críticos Identificados (Stress-Test)

### 2.1. O Paradoxo Criptográfico: `UUIDv7` + `BIP39` (12 palavras) + Anti-Spoofing na DHT
* **O que diz o projeto original:** A identidade pública é um `UUIDv7`. As 12 palavras (BIP39) restauram a exata mesma Chave Privada e o mesmo `UUIDv7` em qualquer aparelho novo. Além disso, a DHT valida a assinatura digital para impedir que alguém falsifique o IP de outro usuário.
* **Onde está a falha técnica:**
  1. **Restauração determinística do UUIDv7:** Um `UUIDv7` padrão (RFC 9562) usa os primeiros 48 bits para o timestamp Unix em milissegundos do momento da criação. Se o usuário perder o celular e digitar apenas as 12 palavras BIP39 daqui a um ano em outro aparelho, de onde virá o timestamp original para reconstruir o mesmo `UUIDv7`? Se as 12 palavras geram apenas uma semente criptográfica estática, o timestamp precisa ser derivado deterministicamente ou codificado na estrutura, caso contrário cada restauração geraria um UUIDv7 diferente.
  2. **Sequestro de Identidade (Account Takeover) na DHT:** Em `ispchat.proto`, o `RegisterSessionRequest` envia `user_uuid`, `public_key` e `signature`. Se o `user_uuid` não for matematicamente derivado da `public_key` (por exemplo, embutindo parte do hash SHA-256/BLAKE3 da `public_key` nos bits de entropia do UUIDv7), um atacante pode enviar o `user_uuid` de uma vítima junto com a `public_key` e `signature` do próprio atacante. O nó Java verá uma assinatura válida para a chave enviada e sobrescreverá a rota da vítima na DHT.

### 2.2. Conflito de Fronteira mTLS na Porta 443 (Nó-a-Nó vs. App-a-Nó)
* **O que diz o projeto original:** Toda comunicação gRPC ocorre na porta 443 com mTLS obrigatório assinado pela Autoridade Certificadora (`ca.crt`) do ispChat, rejeitando no socket qualquer conexão sem certificado válido de provedor.
* **Onde está a falha técnica:** Os aplicativos Flutter dos usuários finais (`ClientStreamService`) também se conectam ao nó do provedor via gRPC, mas celulares de usuários finais **não possuem** certificado assinado pela CA de infraestrutura dos ISPs (apenas os servidores dos provedores têm). Se o `GrpcServerRunner` exigir `ClientAuth.REQUIRE` no socket da porta 443 única, nenhum usuário conseguirá conectar pelo aplicativo.
* **Caminhos de solução:**
  * **Opção A:** Separar em duas portas gRPC (ex: porta `443` com TLS padrão de servidor + autenticação por assinatura Ed25519 para Apps Flutter; e porta dedicada ex: `8443` com mTLS estrito `ClientAuth.REQUIRE` para tráfego entre Nós de Provedores e DHT).
  * **Opção B:** Manter porta única `443` com `ClientAuth.OPTIONAL` no TLS, e aplicar um `ServerInterceptor` gRPC que exige certificado mTLS da CA obrigatoriamente nos serviços `NodeRoutingService` e `DhtPeerService`, enquanto exige assinatura Ed25519 do usuário no `ClientStreamService`.

### 2.3. Fragmentação e Lacunas nos Contratos Protobuf (`node/src/main/proto/`)
* **Estado atual:** Existem 3 arquivos `.proto` concorrentes e incompatíveis entre si:
  * `ispchat.proto`: Define `DhtService` (apenas chamadas Cliente->Nó `RegisterSession` e `FindUserSessions`), `NodeRoutingService` e `ClientStreamService`. Não possui as chamadas Kademlia entre nós.
  * `dht.proto`: Define outro `DhtService` com as primitivas Kademlia (`Ping`, `Store`, `FindNode`, `FindValue`), mas o `StoreRequest` não envia a `public_key` do usuário, tornando impossível para o nó remoto validar a assinatura `signature`.
  * `messaging.proto`: Define `MessagingService` (`DeliverMessage` e `SignalCall`), duplicando a função do `NodeRoutingService` de `ispchat.proto`.

### 2.4. Inconsistência e Exposição no DNS Master (`dns/master/`)
* **Estado atual:**
  1. Em `dns/master/pdns.conf` (linha 16), está configurado `allow-axfr-ips=0.0.0.0/0` e `also-notify=0.0.0.0/0`. Como o arquivo `pdns.conf` é montado via volume sobre `/etc/powerdns/pdns.conf`, manter `0.0.0.0/0` no arquivo físico cria risco de transferência de zona aberta (AXFR) para qualquer IP da internet na porta 53 do Master.
  2. Em `dns/master/pdns.conf` (linha 6), o host do banco está configurado como `gpgsql-host=db.local`, porém no `dns/master/docker-compose.yml` o serviço se chama `db` e não possui o network alias `db.local` (que só foi adicionado no `slave/docker-compose.yml`).

### 2.5. Sobrecarga Arquitetural do `io.grpc.xds` no Backend Java
* **O que diz o Tech Spec:** Usar `io.grpc.xds` alimentado localmente pela DHT para rotear chamadas gRPC entre provedores.
* **Onde está o problema (YAGNI):** O protocolo xDS foi desenhado para malhas estáticas/semi-estáticas gerenciadas por um Control Plane central (como Istio/Envoy). No ISP Chat, um nó A descobre sob demanda na DHT que o usuário destino está no IP do nó B e precisa enviar pacotes diretamente para B. Manter um pool/cache local de `ManagedChannel` gRPC com mTLS por endereço de nó destino resolve 100% do problema com uma fração da memória e sem complexidade de Control Plane xDS.
