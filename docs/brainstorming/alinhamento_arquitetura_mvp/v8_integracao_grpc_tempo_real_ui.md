# v8 — Integração gRPC em Tempo Real na UI (`IspChatNodeClient` + `ContactChatScreen` + `IspChatHomeScreen`)

**Data:** 03/11/2026
**Status:** Implementado, Testado (18/18 testes passando), Comitado e Enviado para `origin/main`

---

## 1. Entregas Realizadas (Caminho B)

1. **Codec Binário Protobuf Expandido (`app/lib/core/grpc/ispchat_pb.dart`):**
   - Adicionadas as classes `FindUserRequestProto` e `FindUserResponseProto` e o método `IspChatGrpcMethods.findUser` (`/br.dev.ispchat.grpc.DhtClientService/FindUser`).
   - Adicionado suporte bidirecional `fromBuffer` / `writeToBuffer` em `RegisterSessionRequestProto` e `RegisterSessionResponseProto`.

2. **Cliente gRPC com CA Embutida e Envio Direto (`app/lib/core/grpc/ispchat_node_client.dart`):**
   - Carregamento automático do certificado raiz `assets/certs/ca.crt` via `rootBundle` quando conectado em produção na porta 443 TLS 1.3.
   - Adicionados `findUserInDht(targetUuid)` e `sendSignedEnvelope(envelope)`.

3. **Integração Ao Vivo na Interface (`app/lib/main.dart` e `app/lib/ui/contact_chat_screen.dart`):**
   - Botão **Eleger & Conectar ao Melhor Nó** (`btn_elect_and_connect_node`) na seção de Descoberta de Nós, executando `NodeDiscoveryService.selectFastestNode()` e conectando via `IspChatNodeClient.connectAndAuthenticate(...)`.
   - Transmissão em tempo real de pacotes `ClientEnvelopeProto` (mensagens de texto E2E e sinais WebRTC `RING` / `CALL_DECLINE`) pela stream gRPC ativa com fallback local transparente.
   - Recebimento em tempo real de mensagens e sinais WebRTC na `ContactChatScreen` e no `IspChatHomeScreen` com persistência idempotente por `messageId` no `SecureLocalVault`.

4. **Testes Integrados gRPC Reais e de Widget (`app/test/grpc_live_node_integration_test.dart`):**
   - Teste integrado levantando um servidor `grpc.Server` real em `127.0.0.1` validando `RegisterSession`, `AUTH_HANDSHAKE`, `FindUser`, envio e recebimento de envelopes criptografados E2E entre Alice e Bob.
   - Teste de Widget validando a eleição de nó, conexão gRPC, envio e recebimento de mensagens em tempo real na `ContactChatScreen` sem overflow (`18/18 testes passando`).
