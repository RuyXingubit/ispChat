# Especificação Técnica (Tech Spec) - ISP Chat MVP

## 1. Arquitetura Macro e Filosofia

### 1.1. Problema
Plataformas tradicionais de mensageria dependem de servidores centrais, números de telefone (SIM/SMS) e diretórios controlados por uma única entidade. Essa centralização cria pontos únicos de falha, permite bloqueios por DNS/IP e expõe metadados e chaves públicas a ataques de substituição (Man-in-the-Middle) no diretório central.

### 1.2. Objetivos do MVP (Goals)
A rede abandona a topologia Cliente-Servidor e adota uma malha P2P Federada operada por Provedores de Internet (ISPs). Os provedores fornecem descoberta e roteamento cego de pacotes na porta `443`, enquanto a identidade e o sigilo End-to-End (E2E) pertencem exclusivamente aos clientes (Flutter):
- Identidade criptográfica determinística baseada em 12 palavras (`BIP39 -> Ed25519 + X25519 -> UUIDv7`) sem cadastro centralizado.
- Diretório distribuído Kademlia DHT com validação matemática anti-spoofing (`deriveUuidV7(public_key) == user_uuid`) e proteção anti-replay (`±5 minutos`).
- Transporte unificado na porta `443` com TLS 1.3 (`ClientAuth.OPTIONAL`), isolando chamadas Nó <-> Nó (mTLS obrigatório) de chamadas App <-> Nó (assinatura `Ed25519` por pacote).
- Mensageria E2E (`X25519 + HKDF-SHA256 + ChaCha20-Poly1305`) com fila offline (`Redis List`), suporte multi-dispositivo e chamadas P2P WebRTC (áudio/vídeo) apoiadas por STUN/TURN (Coturn).
- Operação resiliente via Bootstrap Social (`ispchat://seed`), troca de contatos autenticada (`ispchat://contact`) e sinalização direta IPv6 (`ispchat://p2p`).

### 1.3. Não-Objetivos (Non-Goals - Fases 2, 3 e 4)
Para preservar a simplicidade operacional do MVP (YAGNI), os seguintes itens estão explicitamente fora do escopo de implementação do MVP e pertencem às fases subsequentes do roadmap:
- **Fase 2:** Eleição automatizada de servidores DNS Slave via API do Registro.br e anúncio de blocos IPv4/IPv6 próprios via BGP Anycast.
- **Fase 3:** Rotação automatizada de IPs de borda e *Domain Fronting* via CDNs.
- **Fase 4:** Transporte em rede Mesh local via Bluetooth Low Energy (BLE) e Wi-Fi Direct.

### 1.4. Stack Tecnológica
- **Backend (`node/`):** Java 25 LTS (`Executors.newVirtualThreadPerTaskExecutor()` sem *pinning* via JEP 491, *Compact Object Headers* via JEP 519), Spring Boot 3.5.0 (Headless, sem servidor HTTP/Tomcat na porta 8080), gRPC 1.71.0 com Netty TLS 1.3 (Porta 443 única), `uuid-creator:6.0.0` (`UUIDv7`), Redis (Store-and-Forward via `Redis List`).
- **Frontend (`app/`):** Flutter (Mobile/Desktop), `cryptography` (`Ed25519`, `X25519`, `HKDF-SHA256`, `ChaCha20-Poly1305`), `bip39`, `uuid` (`v7`), `flutter_secure_storage` (`SecureLocalVault`), `BlocklistService`, `grpc`, WebRTC (`flutter_webrtc`).
- **Infraestrutura Global (`dns/` e `ca/`):** PowerDNS Authoritative 4.9, PostgreSQL 17, Caddy 2, Autoridade Certificadora (CA) privada RSA 4096 bits, Coturn (STUN/TURN com bloqueio anti-SSRF).

---

## 2. Identidade Criptográfica Determinística (`BIP39 -> Ed25519 + X25519 -> UUIDv7`)

Para permitir recuperação completa da conta usando apenas as 12 palavras (BIP39) sem depender de servidores centrais e impedir sequestro de identidade (*spoofing*) na DHT, o sistema separa **UUIDv7 Determinístico de Identidade** de **UUIDv7 Temporal de Eventos**:

1. **Derivação de Chaves a partir do BIP39 (12 palavras):**
   - A frase mnemônica de 12 palavras gera a seed padrão BIP39 de 64 bytes (`PBKDF2-HMAC-SHA512`, 2048 iterações, salt `"mnemonic"`):
     - `seed[0..31]` (primeiros 32 bytes): semente da chave privada **Ed25519** (Identidade e Assinatura Digital).
     - `seed[32..63]` (últimos 32 bytes): semente da chave privada **X25519** (Acordo de chaves ECDH para Criptografia E2E de payloads).
2. **Derivação Determinística do `UUIDv7` do Usuário (`user_uuid`):**
   - Calcula-se `hash = SHA-256(ed25519_public_key_32_bytes)`.
   - Tomam-se os primeiros 16 bytes (`bytes = hash[0..15]`).
   - Aplicam-se os bits de versão 7 da RFC 9562: `bytes[6] = (bytes[6] & 0x0F) | 0x70`.
   - Aplicam-se os bits de variante IETF da RFC 9562: `bytes[8] = (bytes[8] & 0x3F) | 0x80`.
   - Formata-se como UUID padrão lowercase (`8-4-4-4-12`). Isso preserva **122 bits de entropia criptográfica SHA-256**, garante que as 12 palavras sempre recriem o mesmo `UUIDv7` em qualquer aparelho e permite que qualquer nó da DHT verifique matematicamente `deriveUuidV7(public_key) == user_uuid`.
3. **IDs Temporais de Mensagens e Eventos (`message_id`):**
   - Mensagens e eventos utilizam `UUIDv7` temporal padrão (RFC 9562 com 48 bits de timestamp Unix em ms) gerado via `com.github.f4b6a3:uuid-creator` (`UuidCreator.getTimeOrderedEpoch()`) no Java 25 e `Uuid().v7()` no Dart.
4. **Interoperabilidade Ed25519 Nativa (Flutter 32-byte Raw <-> Java 25 JCA):**
   - O cliente Flutter exporta chaves públicas `Ed25519` em formato binário puro de 32 bytes. O backend Java 25 reconstrói a chave pública no provedor nativo JCA (`KeyFactory.getInstance("Ed25519")`) prefixando o cabeçalho fixo de 12 bytes `SubjectPublicKeyInfo` da RFC 8410 (`30 2a 30 05 06 03 2b 65 70 03 21 00`).
5. **Verificação Out-of-Band de Contatos e Pinning de Chave `X25519` (`ispchat://contact`):**
   - Como o `user_uuid` é derivado de `SHA-256(ed25519_public_key)`, a chave `Ed25519` é auto-autenticada pelo próprio UUID, mas a chave `X25519` exige vínculo explícito para impedir substituição por um nó DHT malicioso.
   - Os usuários trocam identidades via QR Code ou Link Mágico no formato `ispchat://contact?uuid=<user_uuid>&ed25519=<base64url>&x25519=<base64url>[&ipv6=<addr>]`. O App verifica matematicamente `deriveUuidV7(ed25519) == uuid` e fixa (*pins*) o par `(ed25519, x25519)` no cofre local (`SecureLocalVault`).

---

## 3. Fundação e Descoberta (Bootstrap da Rede)

### 3.1. Arquitetura DNS Semente (MVP)
- **Domínio Raiz:** `ispchat.dev.br`.
- **Topologia Master/Slave:** Infraestrutura autoritativa do PowerDNS 4.9 com PostgreSQL 17. Existe 1 servidor **Master Oculto** (`local-address=127.0.0.1` protegido por proxy reverso Caddy com whitelist de IP e `allow-axfr-ips` restrito aos IPs dos Slaves) e servidores **Slaves** públicos (`ns1` e `ns2`, com `disable-axfr=yes`) apontados no Registro.br.
- **Resolução DNS SRV:** Os Slaves respondem a consultas SRV (`_ispchat._tcp.ispchat.dev.br`) entregando os endereços dos nós dos provedores para que o App Flutter eleja o nó de menor latência TCP (`TcpLatencyProbe`) e a DHT realize o bootstrap.

### 3.2. Escalabilidade Futura (Fase 2 — BGP Anycast)
- **Eleição de Slaves:** Até 6 provedores parceiros com maior uptime serão eleitos dinamicamente via API do Registro.br.
- **ASN e BGP Anycast:** Anúncio de blocos IPv4 (`/24`) e IPv6 via BGP Anycast para tráfego UDP curto (DNS, STUN/TURN e bootstrap Kademlia).

---

## 4. A Rede DHT (Distributed Hash Table - Kademlia)
- **Topologia e Parâmetros Kademlia:** Diretório distribuído baseado na distância XOR de 128 bits sobre os UUIDs (`160` buckets, `K = 20` nós por bucket) com saltos `O(log N)`.
- **Anti-Spoofing Criptográfico:** Toda escrita na DHT (`RegisterSession` ou `Store`) exige:
  1. Verificação matemática de posse: `deriveUuidV7(public_key) == user_uuid`.
  2. Janela de frescor de timestamp (`±5 minutos` / `300.000 ms`) contra *replay attacks*.
  3. Validação da **Assinatura Digital Ed25519** sobre os bytes UTF-8 do payload canônico `"ISPCHAT-DHT-V1|" + user_uuid + "|" + node_address + "|" + timestamp_millis`.
  Pacotes inválidos são descartados imediatamente.
- **Array de Dispositivos (Multi-device):** O registro na DHT mapeia `[Chave: UUIDv7] -> [Lista de Sessões/Nós Ativos]` (limite de até `8` sessões simultâneas por `UUIDv7` com TTL de expiração de `24 horas`), permitindo que celular e desktop do mesmo usuário recebam chamadas e mensagens simultaneamente.

---

## 5. Sinalização, Transporte Interno e Fronteira de Segurança na Porta 443

### 5.1. Porta 443 Única (`ClientAuth.OPTIONAL` + Interceptors de Fronteira)
Para manter 100% do tráfego na porta `443` (camuflagem contra bloqueios e DPI) e permitir a conexão simultânea de clientes Flutter (sem certificado de provedor) e de outros Nós ISP (que exigem mTLS estrito), o servidor gRPC opera sobre Virtual Threads do Java 25 (`Executors.newVirtualThreadPerTaskExecutor()`) com TLS 1.3 (`ClientAuth.OPTIONAL`) e separa os serviços em duas fronteiras:

1. **Fronteira de Federação (Nó <-> Nó via mTLS Obrigatório):**
   - Serviços: `DhtPeerService` (`Ping`, `Store`, `FindNode`, `FindValue`) e `NodeRoutingService` (`RoutePayload`).
   - Protegidos pelo `MtlsFederationInterceptor`, que exige certificado X.509 de cliente válido (`extendedKeyUsage = serverAuth, clientAuth` e SAN `DNS`/`IP` conforme RFC 5280) assinado pela Autoridade Certificadora (`ca.crt`) do ispChat na sessão TLS (`Grpc.TRANSPORT_ATTR_SSL_SESSION`). Conexões sem certificado válido da CA são rejeitadas com `Status.UNAUTHENTICATED`.
   - O roteamento entre nós utiliza um pool direto de canais gRPC mTLS (`GrpcChannelPool`) com proteção anti-SSRF (rejeitando conexões para IPs privados, loopback ou link-local em produção).
2. **Fronteira de Borda (App Flutter <-> Nó Local via TLS + Assinatura Ed25519):**
   - Serviços: `DhtClientService` (`RegisterSession`, `FindUserSessions`) e `ClientStreamService` (`Connect`).
   - O App valida o certificado TLS do servidor contra o `ca.crt` embutido nos assets.
   - Todo pacote `ClientEnvelope` e `RoutePayloadRequest` é assinado com a chave privada `Ed25519` do remetente sobre o transcript binário: `UTF8("ISPCHAT-MSG-V1|" + message_id + "|" + sender_uuid + "|" + receiver_uuid + "|" + type_number + "|" + timestamp_millis + "|") || encrypted_payload`.
   - Ao abrir o stream bidirecional `ClientStreamService.Connect`, o primeiro pacote enviado pelo App deve obrigatoriamente ter `type == AUTH_HANDSHAKE` e `sender_uuid == receiver_uuid`. Pacotes iniciais de outro tipo ou com assinatura inválida derrubam o stream com `Status.UNAUTHENTICATED`.

### 5.2. Roteamento de Sessões Ativas e Fila Offline (Store-and-Forward)
O roteamento de mensagens e sinais no `MessageRouterService` executa três etapas determinísticas sem necessidade de barramento Pub/Sub externo:
1. **Fan-Out Local em Memória (`LocalSessionRegistry`):** Entrega imediata do `ServerEnvelope` para todas as streams gRPC ativas do `receiver_uuid` conectadas ao próprio nó, garantindo toque simultâneo (`RING`) e sincronia multi-dispositivo.
2. **Fan-Out Federado Inter-Provedores (`NodeRoutingService.RoutePayload`):** Consulta a DHT Kademlia para localizar outros nós ISP onde o `receiver_uuid` possua sessões ativas e encaminha o pacote via mTLS na porta `443`.
3. **Fila Offline no Redis (`OfflineMessageQueueService`):** Caso o destinatário não possua nenhuma sessão online local ou remota e o pacote seja do tipo `TEXT`, o envelope é persistido em uma `Redis List` (`ispchat:offline:queue:<receiver_uuid>`, limitada a `1.000` mensagens por usuário e TTL de `7 dias`). Sinais efêmeros de chamada (`RING`, `SDP_*`, `ICE_CANDIDATE`) não são enfileirados offline. Assim que o usuário abre o stream `Connect` e valida o `AUTH_HANDSHAKE`, a fila é drenada atomicamente (`LRANGE` + `DEL`) e entregue ao cliente.

---

## 6. Criptografia E2E (Texto, Áudio e Vídeo), Blocklist Local e NAT Traversal
- **Construção Criptográfica E2E (`IdentityCryptoService`):**
  - Calculado o segredo compartilhado ECDH de 32 bytes via `X25519(sender_private_x25519, recipient_public_x25519)`.
  - Derivada uma chave simétrica de 32 bytes via `HKDF-SHA256` com `salt = UTF8("ISPCHAT-E2E-X25519-HKDF-SALT-V1")` e `info = UTF8("ISPCHAT-CHACHA20-POLY1305-KEY")`.
  - O payload é cifrado com `ChaCha20-Poly1305` (nonce aleatório seguro de 12 bytes e tag MAC de 16 bytes) e serializado no formato binário `[nonce (12 bytes) | mac (16 bytes) | ciphertext]`.
- **Blocklist Local (`BlocklistService`):** Antes de descriptografar qualquer payload ou acionar o alerta de chamada (`RING`), o `E2eSessionManager` consulta a blocklist persistida no `SecureLocalVault`. Pacotes provenientes de `UUIDv7` bloqueados são descartados silenciosamente na borda do cliente.
- **Mídia WebRTC e Coturn (NAT Traversal):** O gRPC trafega apenas a sinalização criptografada E2E (`RING`, `SDP_OFFER`, `SDP_ANSWER`, `ICE_CANDIDATE`, `CALL_DECLINE`). A mídia de áudio e vídeo flui P2P via WebRTC (`DTLS-SRTP`), apoiada por servidores STUN/TURN (`Coturn`) dos provedores quando sob CGNAT estrito, endurecidos com `no-loopback-peers`, `no-multicast-peers` e bloqueio de faixas de IP privadas (`denied-peer-ip`) contra SSRF.

---

## 7. Resiliência e Operação Out-of-Band

### 7.1. Mecanismos Implementados no MVP
- **Descoberta Sem Dados Fictícios e Bootstrap Social:** O `NodeDiscoveryService` inicia sem IPs fixos (*hardcoded*) no código, resolvendo `_ispchat._tcp.ispchat.dev.br` via DNS SRV e utilizando nós sementes reais salvos no `SecureLocalVault` ou importados via URI `ispchat://seed?ip=X.X.X.X`.
- **Conexão P2P Direta via IPv6 (`IdentityQrDialog`):** Quando não há nós ISP acessíveis, dois dispositivos com conectividade IPv6 ou na mesma LAN podem trocar ofertas/respostas SDP e endereços via QR Code / Link Mágico (`ispchat://p2p?...`).

### 7.2. Evolução Futura (Fases 3 e 4 — Não-Objetivos do MVP)
- **Fase 3:** Rotação dinâmica de IPs pelos provedores e *Domain Fronting* via CDNs.
- **Fase 4:** Rede Mesh Local via Bluetooth Low Energy (BLE) e Wi-Fi Direct.

---

## 8. Alternativas Consideradas

| Decisão Arquitetural | Alternativa Rejeitada | Motivo da Rejeição |
| :--- | :--- | :--- |
| **Java 25 LTS** (JEP 491 Virtual Threads sem *pinning* + JEP 519 Compact Object Headers) | Java 21 LTS | No Java 21, blocos `synchronized` internos a bibliotecas de I/O/TLS podiam causar *pinning* de *carrier threads*. O JEP 491 elimina esse gargalo para milhares de streams gRPC simultâneas, e o JEP 519 reduz o consumo de RAM na tabela Kademlia em memória. |
| **UUIDv7 Determinístico de Identidade** (`SHA-256(ed25519_pub)[0..15]` com máscara `0x70`/`0x80`) | `UUIDv7` temporal aleatório ou `UUIDv8` customizado | Um `UUIDv7` temporal mudaria a cada recuperação das 12 palavras (exigindo servidor central de vínculo), enquanto `UUIDv8` perderia compatibilidade com validadores estritos de `UUIDv7`. A derivação SHA-256 com bits RFC 9562 v7 garante recuperação determinística e verificação `deriveUuidV7(pub) == uuid` sem estado. |
| **Porta 443 Única** com `ClientAuth.OPTIONAL` + `MtlsFederationInterceptor` | Portas separadas (`443` para clientes e `8443` para federação mTLS) | Portas não-padrão (`8443`) são frequentemente bloqueadas em redes corporativas ou sujeitas a filtragem de trânsito entre ASNs. |
| **Pool Direto de Canais gRPC (`GrpcChannelPool`)** | Plano de controle `io.grpc.xds` (Envoy/Istio) | `xds` adiciona complexidade operacional e superfície de dependências desnecessária para roteamento ponto-a-ponto Kademlia onde cada nó já descobre os endereços diretamente. |
| **`LocalSessionRegistry` em Memória + `Redis List`** | Redis Pub/Sub para entrega local de mensagens | Como cada nó ISP gerencia suas conexões gRPC ativas em processo único, o fan-out em memória (`ConcurrentHashMap`) elimina serialização redundante no Redis, reservando o Redis exclusivamente para a fila offline (`Store-and-Forward`). |

---

## 9. Riscos, Limites Operacionais e Mitigações

| Risco / Vetor de Ataque | Limite Operacional / Impacto | Mitigação Implementada no MVP |
| :--- | :--- | :--- |
| **Substituição de Chave `X25519` na DHT (MitM)** | Um nó DHT malicioso não consegue falsificar a chave `Ed25519` (pois está amarrada ao `UUIDv7`), mas poderia tentar substituir `x25519_public_key`. | Troca de contatos out-of-band via `ispchat://contact` (QR Code / Link Mágico) com verificação matemática de `UUIDv7` e *pinning* das chaves `Ed25519` + `X25519` no `SecureLocalVault`. |
| **Comprometimento da Frase BIP39 (12 palavras)** | Como a identidade é determinística e sem servidor central de revogação, o vazamento das 12 palavras compromete permanentemente aquele `UUIDv7`. | Armazenamento local criptografado via Keychain/Keystore (`flutter_secure_storage`) e geração rápida de nova identidade + notificação aos contatos via QR Code. |
| **Exaustão de Memória no Redis (Flood Offline)** | Envio massivo de mensagens para um `UUIDv7` offline. | `OfflineMessageQueueService` aplica cota estrita de `MAX_QUEUE_SIZE_PER_USER = 1000` mensagens por destinatário, TTL de `7 dias` e enfileiramento restrito a pacotes `TEXT` já autenticados com `Ed25519`. |
| **Poluição de Sessões na DHT e Replay Attacks** | Injeção de sessões falsas ou repetição de pacotes antigos capturados. | Janela estrita de timestamp de `±5 minutos` (`300.000 ms`), limite de `8` sessões por `UUIDv7` e TTL de `24 horas` na `DhtStoreService`. |
| **SSRF via Endereços de Nós na DHT ou Relay TURN** | Um par malicioso anuncia `127.0.0.1` ou IP de LAN (`10.x`, `192.168.x`) como `node_address` ou peer WebRTC. | `GrpcChannelPool.validateTargetHost` bloqueia IPs privados/loopback/link-local fora de desenvolvimento, e `turnserver.conf` bloqueia faixas privadas no Coturn. |
