# v6 — Checkpoint de Implementação Completa do MVP (Backend Java 25, App Flutter E2E, CI/CD e Coturn)

**Data:** 03/11/2026
**Status:** Implementado, Testado, Comitado e Enviado para `origin/main`

---

## 1. Resumo de Entregas Concluídas na Sessão

1. **Alinhamento Arquitetural & Hardening de Infraestrutura (`e5e52cb`):**
   - Contrato unificado Protobuf em `node/src/main/proto/ispchat.proto`.
   - Hardening do PowerDNS Master (`dns/master/pdns.conf`) e atualização das stacks Docker (`postgres:17-alpine`, `powerdns/pdns-auth-49:latest`).
   - Script de certificados `ca/generate_node_cert.sh` atualizado para RSA 4096-bit com `extendedKeyUsage = serverAuth, clientAuth`.

2. **Backend do Nó ISP em Java 25 LTS (`e5e52cb`, `d76b815`):**
   - Gradle 9.0.0 + Java 25 LTS + Spring Boot 3.5.0 (headless, sem Tomcat na porta 8080) + gRPC 1.71.0 + Protobuf 4.30.2 + `uuid-creator:6.0.0`.
   - `CryptoService`: Derivação determinística de `UUIDv7` a partir da chave pública `Ed25519` (`SHA-256[0..15]` com bits RFC 9562 v7) e verificação canônica de assinaturas (`ISPCHAT-DHT-V1|` e `ISPCHAT-MSG-V1|`).
   - `MtlsFederationInterceptor` + `GrpcServerRunner` + `GrpcChannelPool`: Porta única `443` com TLS 1.3 (`ClientAuth.OPTIONAL`) exigindo certificado mTLS da CA oficial apenas para RPCs federadas (`DhtPeerService` e `NodeRoutingService`).
   - `KademliaRoutingTable` + `DhtStoreService` + `DhtPeerGrpcService` + `DhtClientGrpcService`: Tabela de roteamento XOR 128-bit e armazenamento Redis com TTL de 24h.
   - `OfflineMessageQueueService` + `LocalSessionRegistry` + `MessageRouterService` + `NodeRoutingGrpcService` + `ClientStreamGrpcService`: Roteamento em tempo real, relay inter-nós mTLS e fila offline Redis com TTL de 7 dias.
   - Suíte completa de testes unitários e de integração mTLS (`./gradlew test` -> `BUILD SUCCESSFUL`).

3. **Aplicativo Cliente Flutter (`025c63d`, `d76b815`, `206a7ed`):**
   - `IdentityCryptoService`: Geração/restauração BIP39 (12 palavras), split determinístico `Ed25519` + `X25519`, derivação `UUIDv7`, assinatura canônica, criptografia `X25519 + HKDF-SHA256 + ChaCha20-Poly1305` e Link Mágico (`ispchat://contact?...`).
   - `NodeDiscoveryService` + `TcpLatencyProbe`: Eleição paralela por menor latência TCP na porta 443, injeção de seed social (`ispchat://seed?ip=...`) e detecção de IPv6 global para o Modo Doomsday.
   - `BlocklistService`: Bloqueio silencioso no dispositivo antes de qualquer alerta sonoro (`RING`) ou descriptografia.
   - `IspChatNodeClient` + `E2eSessionManager` + `WebRtcCallCoordinator`: Transporte gRPC TLS 1.3 cravado na CA (`assets/certs/ca.crt`), orquestração de envelopes E2E e máquina de estados WebRTC (`RING`, `SDP_OFFER`, `SDP_ANSWER`, `ICE_CANDIDATE`, `CALL_DECLINE`).
   - `SecureLocalVault` + `IdentityQrDialog` + `ContactChatScreen` + `IspChatHomeScreen`: Cofre seguro local (`flutter_secure_storage`), diálogo de QR Code com chave Doomsday IPv6 e tela de conversa/chamada E2E com zero overflow (`flutter analyze` 0 issues, `flutter test` 16/16 passando).

4. **CI/CD Otimizado e Servidor Coturn STUN/TURN (`2694332`, `4ff1a59`):**
   - Workflow `.github/workflows/ci.yml` seguindo os 5 pilares de economia de minutos (concorrência com cancelamento, `paths-ignore`, timeouts enxutos, cache Gradle/Flutter e jobs paralelos).
   - `node/turnserver.conf`, `node/.env.example`, `node/docker-compose.yml` e `node/README.md`: Integração do servidor STUN/TURN `coturn` com proteção anti-SSRF (`denied-peer-ip` para faixas privadas) e manual passo a passo de deploy para provedores parceiros.

---

## 2. Próximos Passos Sugeridos para a Próxima Sessão
1. **Teste Integrado Local (Docker Compose + App Flutter):** Subir `redis` + `node` localmente em modo dev/certificados de teste e conectar o App Flutter real na porta local para validar o fluxo ponta a ponta ao vivo.
2. **Scanner Nativo de Câmera para QR Code (Mobile) & Captura de Mídia `flutter_webrtc`:** Conectar o `RTCPeerConnection` nativo do `flutter_webrtc` aos pacotes já cifrados do `WebRtcCallCoordinator` para renderizar vídeo/áudio em dispositivos físicos.
3. **Planejamento de Deploy Controlado (Mediante Autorização Prévia):** Quando desejar, revisar os passos para subir o primeiro Nó ISP de homologação/produção.
