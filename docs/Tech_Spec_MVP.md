# Especificação Técnica (Tech Spec) - ISP Chat MVP

## 1. Arquitetura Macro e Filosofia
A rede abandona a topologia Cliente-Servidor e adota uma malha P2P Federada de Microsserviços. Os provedores (ISPs) fornecem o roteamento, mas o sigilo dos dados E2E (End-to-End) pertence exclusivamente aos clientes (Flutter).

### Stack Tecnológica
- **Backend (`node/`):** Java 25 LTS (Virtual Threads sem pinning via JEP 491, Compact Object Headers via JEP 519), Spring Boot 3.5 (Headless, sem servidor HTTP/Tomcat na porta 8080), gRPC com Netty TLS 1.3 (Porta 443 única), `uuid-creator` (UUIDv7), Redis (Estado/Fila Offline e Pub/Sub).
- **Frontend (`app/`):** Flutter (Mobile/Desktop), `cryptography` (Ed25519 + X25519), `bip39`, `uuid` (v7), WebRTC.
- **Infraestrutura Global (`dns/` e `ca/`):** PowerDNS Authoritative 4.9, PostgreSQL 17, Caddy 2, Autoridade Certificadora (CA) privada RSA 4096 bits, Coturn (STUN/TURN).

---

## 2. Identidade Criptográfica Determinística (`BIP39 -> Ed25519 + X25519 -> UUIDv7`)

Para permitir recuperação completa da conta usando apenas as 12 palavras (BIP39) sem depender de servidores centrais e impedir sequestro de identidade (spoofing) na DHT, o sistema separa **UUIDv7 Determinístico de Identidade** de **UUIDv7 Temporal de Eventos**:

1. **Derivação de Chaves a partir do BIP39 (12 palavras):**
   - A frase mnemônica de 12 palavras gera a seed padrão BIP39 de 64 bytes (`PBKDF2-HMAC-SHA512`):
     - `seed[0..31]` (primeiros 32 bytes): semente da chave privada **Ed25519** (Identidade e Assinatura Digital).
     - `seed[32..63]` (últimos 32 bytes): semente da chave privada **X25519** (Acordo de chaves ECDH para Criptografia E2E de payloads).
2. **Derivação Determinística do `UUIDv7` do Usuário (`user_uuid`):**
   - Calcula-se `hash = SHA-256(ed25519_public_key_32_bytes)`.
   - Tomam-se os primeiros 16 bytes (`bytes = hash[0..15]`).
   - Aplicam-se os bits de versão 7 da RFC 9562: `bytes[6] = (bytes[6] & 0x0F) | 0x70`.
   - Aplicam-se os bits de variante IETF da RFC 9562: `bytes[8] = (bytes[8] & 0x3F) | 0x80`.
   - Formata-se como UUID padrão lowercase (`8-4-4-4-12`). Isso preserva **122 bits de entropia criptográfica SHA-256**, garante que as 12 palavras sempre recriem o exato mesmo `UUIDv7` em qualquer aparelho e permite que qualquer nó da DHT verifique matematicamente `deriveUuidV7(public_key) == user_uuid`.
3. **IDs Temporais de Mensagens e Eventos (`message_id`):**
   - Mensagens e eventos utilizam `UUIDv7` temporal padrão (RFC 9562 com 48 bits de timestamp Unix em ms) gerado via `com.github.f4b6a3:uuid-creator` (`UuidCreator.getTimeOrderedEpoch()`) no Java 25 e `Uuid().v7()` no Dart.
4. **Interoperabilidade Ed25519 Nativa (Flutter 32-byte Raw <-> Java 25 JCA):**
   - O pacote `cryptography` do Flutter exporta chaves públicas Ed25519 em 32 bytes puros. O backend Java 25 utiliza o provedor nativo `KeyFactory.getInstance("Ed25519")` prefixando os 12 bytes fixos do cabeçalho ASN.1 `SubjectPublicKeyInfo` (RFC 8410: `30 2a 30 05 06 03 2b 65 70 03 21 00`) aos 32 bytes da chave pública.

---

## 3. Fundação e Descoberta (O Bootstrap da Rede)

### 3.1. Arquitetura DNS Semente (MVP)
- **Domínio Raiz:** `ispchat.dev.br`.
- **Topologia Master/Slave:** Infraestrutura autoritativa do PowerDNS 4.9 com PostgreSQL 17. Existe 1 servidor **Master Oculto** (protegido por Caddy com whitelist de IP e AXFR restrito exclusivamente aos IPs dos Slaves) e servidores **Slaves** públicos (`ns1` e `ns2`) apontados no Registro.br.
- **Resolução DNS SRV:** Os Slaves respondem a consultas SRV (`_ispchat._tcp.ispchat.dev.br`) entregando os endereços dos nós comunitários/provedores para que o App Flutter faça o Onboarding e a DHT realize o Bootstrap.

### 3.2. O Futuro da Escalabilidade (Fase 2 / BGP Anycast)
- **Eleição de Slaves:** Até 6 provedores parceiros com maior uptime serão eleitos dinamicamente via API do Registro.br.
- **ASN e BGP Anycast:** Anúncio de blocos IPv4 (/24) e IPv6 via BGP Anycast para tráfego UDP curto (DNS, STUN/TURN e bootstrap Kademlia).

---

## 4. A Rede DHT (Distributed Hash Table - Kademlia)
- **Lookup Logarítmico:** Diretório distribuído Kademlia com saltos `O(log N)`.
- **Anti-Spoofing Criptográfico:** Toda escrita na DHT (`RegisterSession` ou `Store`) exige:
  1. Verificação matemática de posse: `deriveUuidV7(public_key) == user_uuid`.
  2. Janela de frescor de timestamp (`±5 minutos`) contra *replay attacks*.
  3. Validação da **Assinatura Digital Ed25519** sobre o payload canônico `"ISPCHAT-DHT-V1|" + user_uuid + "|" + node_address + "|" + timestamp_millis`.
  Pacotes inválidos ou forjados são descartados imediatamente.
- **Array de Dispositivos (Multi-device):** O registro na DHT mapeia `[Chave: UUIDv7] -> [Lista de Sessões/Nós Ativos]`, permitindo que celular e desktop do mesmo usuário recebam chamadas e mensagens simultaneamente.

---

## 5. Sinalização, Transporte Interno e Fronteira de Segurança na Porta 443

### 5.1. Porta 443 Única (`ClientAuth.OPTIONAL` + Interceptors de Fronteira)
Para manter 100% do tráfego na porta `443` (camuflagem contra bloqueios e DPI) e ao mesmo tempo permitir a conexão de clientes Flutter (que não possuem certificado de provedor) e de outros Nós ISP (que exigem mTLS estrito), o servidor gRPC opera com TLS 1.3 (`ClientAuth.OPTIONAL`) e separa os serviços em duas fronteiras:

1. **Fronteira de Federação (Nó <-> Nó via mTLS Obrigatório):**
   - Serviços: `DhtPeerService` (`Ping`, `Store`, `FindNode`, `FindValue`) e `NodeRoutingService` (`RoutePayload`).
   - Protegidos por um `MtlsFederationInterceptor` que exige um certificado X.509 de cliente válido (com `extendedKeyUsage = serverAuth, clientAuth`) assinado pela Autoridade Certificadora (`ca.crt`) do ispChat na sessão TLS (`Grpc.TRANSPORT_ATTR_SSL_SESSION`). Conexões sem certificado válido da CA são rejeitadas com `UNAUTHENTICATED`.
   - O roteamento entre nós utiliza um pool direto de canais gRPC mTLS (`GrpcChannelPool`) com proteção anti-SSRF (rejeitando conexões para IPs privados/loopback em produção).
2. **Fronteira de Borda (App Flutter <-> Nó Local via TLS + Assinatura Ed25519):**
   - Serviços: `DhtClientService` (`RegisterSession`, `FindUserSessions`) e `ClientStreamService` (`Connect`).
   - O App valida o certificado do servidor contra o `ca.crt` embutido.
   - Ao abrir o stream bidirecional `ClientStreamService.Connect`, o App envia um primeiro pacote `AUTH_HANDSHAKE` assinado com sua chave privada Ed25519 sobre o formato canônico `"ISPCHAT-MSG-V1|" + message_id + "|" + sender_uuid + "|" + receiver_uuid + "|" + type_number + "|" + timestamp_millis + "|" + encrypted_payload`.

### 5.2. Redis (Estado, Pub/Sub e Store-and-Forward)
- **Pub/Sub e Toque Simultâneo:** Ao receber um pacote para um `UUIDv7` com múltiplas sessões ativas, o nó faz o broadcast para disparar o toque (`RING`) ou entregar a mensagem em todos os aparelhos conectados.
- **Fila Offline (Store and Forward):** Mensagens enviadas para usuários offline são persistidas em uma `Redis List` no nó responsável. Assim que o usuário abre o stream `Connect` e valida o `AUTH_HANDSHAKE`, a fila é descarregada e limpa do Redis.

---

## 6. Criptografia E2E (Texto, Áudio e Vídeo) e NAT Traversal
- **Mídia (WebRTC):** O gRPC trafega apenas a sinalização criptografada (`RING`, `SDP_OFFER`, `SDP_ANSWER`, `ICE_CANDIDATE`, `CALL_DECLINE`). A mídia de áudio e vídeo flui P2P via WebRTC (DTLS-SRTP), apoiada por servidores STUN/TURN (Coturn) dos provedores quando sob CGNAT estrito.
- **Mensagens de Texto:** Criptografadas no dispositivo remetente via acordo de chaves `X25519` + cifra autenticada (`ChaCha20-Poly1305` / `AES-256-GCM`) e assinadas com `Ed25519`.

---

## 7. Resiliência Extrema e Anti-Censura (Fases 3 e 4)
- **Sobrevivência ao Bloqueio de DNS:** Hardcoded Seed IPs no App Flutter e Bootstrap Social via links `ispchat://seed?ip=X.X.X.X`.
- **Sobrevivência ao Bloqueio de IP/Trânsito:** Tráfego exclusivamente em porta 443 TLS 1.3, rotação dinâmica de IPs pelos provedores e Domain Fronting via CDNs em cenários extremos.
- **Modo Doomsday (Zero-Infra):** Conexão WebRTC P2P direta via IPv6 (QR Code / Link Mágico) e Rede Mesh Local (Bluetooth LE / Wi-Fi Direct).
