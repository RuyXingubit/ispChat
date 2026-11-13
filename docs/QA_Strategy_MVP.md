# Estratégia de QA e Validação — ispChat

## 1. Matriz de Testes Automatizados

### Backend Java 25 LTS (`node/`)
Execução local e CI:
```bash
cd node && ./gradlew test jacocoTestReport build --no-daemon
```
- **ispchat-common**: validação de CPF e CNPJ alfanumérico Mod-11 (IN RFB 2229/2024), UUIDv7, `TranscriptWriter`, cadeia 4 níveis, multi-device `ChaCha20-Poly1305` + `X25519` e escrow cifrado.
- **ispchat-node**: tabela de roteamento Kademlia DHT, verificação de assinaturas V2, interceptor mTLS de federação, rate-limiting Dual-Tier, expiração/revogação de sessões, streaming de mensagens e geração de credenciais TURN HMAC-SHA256.
- **ispchat-principal**: migrações Flyway + PostgreSQL embarcado, ledger encadeado SHA-256, autorização/revogação de colaboradores, atestação de assinantes e CNPJs, rebind de CPF, portabilidade de conta, diretório CNPJ e fluxo offline CLI.
- **ispchat-erp-bridge**: integração HTTP REST autenticada traduzindo solicitações de ERP para `PrincipalGovernanceService`.

### Cliente Flutter (`app/`)
Execução local e CI:
```bash
cd app && flutter analyze && flutter test --coverage
```
- `identity_crypto_service_test.dart`: vetores binários idênticos ao Java para transcrições canônicas, decifragem cruzada de vetor real gerado pelo Java, CPF/CNPJ alfanumérico, cadeia de confiança V2 e URIs assinados (`contact`, `verify-req`, `pair-req`, `pair-grant`).
- `dns_srv_and_directory_test.dart`: parser DoH JSON + fallback binário RFC 8484 com guarda contra loops de ponteiro DNS, ordenação RFC 2782 e busca real no diretório corporativo gRPC.
- `multi_device_delegation_and_badge_test.dart`: fluxo completo de pareamento Mobile Root <-> Desktop + revogação de dispositivo, selos Verificado/Não Verificado, consentimento de assinante e auditoria de zero overflow em `320x568` e `1024x768`.
- `e2e_session_and_proto_test.dart`: renovação automática 60d, persistência autenticada de primeiro contato, frames de controle antes da decifragem AEAD, bloqueio local, `REVOCATION_NOTICE` e migração de histórico em `IDENTITY_REPLACED`.
- `grpc_live_node_integration_test.dart`: comunicação multi-dispositivo real sobre HTTP/2 gRPC loopback (`Alice Mobile + Alice Desktop <-> Bob`).
- `webrtc_and_probe_test.dart`: negociação completa de chamadas WebRTC (`RING`, `TURN`, `SDP_OFFER`, `SDP_ANSWER`, `ICE_CANDIDATE`, `CALL_DECLINE`, mute/câmera) e parser IPv4/IPv6.
- `vault_and_chat_ui_test.dart`, `node_discovery_and_blocklist_test.dart` e `widget_test.dart`: persistência segura, descoberta/latência, UI de chat e estado inicial livre de dados falsos.

## 2. Pipeline de CI ([`.github/workflows/ci.yml`](../.github/workflows/ci.yml))

- Cancelamento automático de execuções redundantes (`concurrency.cancel-in-progress: true`).
- Filtro `paths-ignore` para documentação e metadados.
- `timeout-minutes` enxutos (`10`–`15` min) e serviços Docker `postgres:17-alpine` e `redis:7-alpine` no job `backend-test`.
- Jobs paralelos fail-fast para Backend (Gradle + Java 25) e Frontend/App (Flutter analyze + tests).
