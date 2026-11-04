# Checkpoint v9 — Conclusão do `/egm-review` de `docs/Tech_Spec_MVP.md` e Alinhamento Fino de Contrato Wire/Runtime

**Data:** 03/11/2026
**Status:** Concluído e Validado (`./gradlew test` + `flutter analyze` + `flutter test`)

---

## 1. Resumo das Ações Executadas

1. **Consolidação Final de `docs/Tech_Spec_MVP.md` e `docs/QA_Strategy_MVP.md`:**
   - Reestruturação da Seção 1 em `### 1.1. Problema`, `### 1.2. Objetivos do MVP (Goals)`, `### 1.3. Não-Objetivos (Non-Goals - Fases 2, 3 e 4)` e `### 1.4. Stack Tecnológica`.
   - Documentação explícita do fluxo de troca de contatos e *key pinning* out-of-band (`ispchat://contact?uuid=<user_uuid>&ed25519=<base64url>&x25519=<base64url>[&ipv6=<addr>]`) no `SecureLocalVault` para neutralizar ataques MitM de substituição de chave `X25519` na DHT.
   - Especificação exata da construção criptográfica E2E (`X25519` -> `HKDF-SHA256` com `salt = UTF8("ISPCHAT-E2E-X25519-HKDF-SALT-V1")` e `info = UTF8("ISPCHAT-CHACHA20-POLY1305-KEY")` -> `ChaCha20-Poly1305` empacotado como `[nonce (12B) | mac (16B) | ciphertext]`).
   - Especificação do transcript binário de assinatura `UTF8("ISPCHAT-MSG-V1|" + message_id + "|" + sender_uuid + "|" + receiver_uuid + "|" + type_number + "|" + timestamp_millis + "|") || encrypted_payload`.
   - Substituição das referências obsoletas a "Redis Pub/Sub" pelo pipeline real implementado (`LocalSessionRegistry` em memória + `MessageRouterService` mTLS `RoutePayload` + `OfflineMessageQueueService` via `Redis List` com limite de `1.000` mensagens e TTL de `7 dias`).
   - Substituição de "Hardcoded Seed IPs no App Flutter" pela descoberta limpa sem dados fictícios (`NodeDiscoveryService` + `SecureLocalVault` + `ispchat://seed?ip=X.X.X.X`).
   - Adição das seções `## 8. Alternativas Consideradas` e `## 9. Riscos, Limites Operacionais e Mitigações`.

2. **Alinhamento Fino 1:1 no Código-Fonte (Backend Java 25 & Frontend Flutter):**
   - **`app/lib/core/grpc/ispchat_pb.dart` & `ispchat_node_client.dart`:** Alinhamento do método gRPC `/br.dev.ispchat.grpc.DhtClientService/FindUserSessions` e das mensagens `FindUserSessionsRequestProto`, `UserSessionInfoProto` e `FindUserSessionsResponseProto` em conformidade 1:1 com `node/src/main/proto/ispchat.proto`.
   - **`node/src/main/java/br/dev/ispchat/node/grpc/GrpcServerRunner.java`:** Configuração explícita de `.executor(Executors.newVirtualThreadPerTaskExecutor())` no `NettyServerBuilder`.
   - **`node/src/main/java/br/dev/ispchat/node/messaging/ClientStreamGrpcService.java`:** Enforcement estrito exigindo que o primeiro pacote de uma stream não autenticada seja `MessageType.AUTH_HANDSHAKE` com `sender_uuid == receiver_uuid`, com teste unitário dedicado em `MessagingAndRoutingTest.java`.
   - **`node/build.gradle.kts`:** Remoção do plugin não utilizado `org.graalvm.buildtools.native` (YAGNI).
