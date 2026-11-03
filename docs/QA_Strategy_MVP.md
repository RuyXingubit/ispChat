# Estratégia de Qualidade (QA Plan) - ISP Chat MVP

## 1. Diretriz Global de Qualidade (Regra de Ouro)
> **"Segurança em primeiro lugar."** e **"Sempre que uma feature for criada, faz um teste unitário (cobertura de testes) para certificar que alterações futuras não estão quebrando as passadas."**

Esta regra definida pela Liderança Técnica é a base inegociável do desenvolvimento. Nenhuma feature é aceita sem a devida cobertura de testes automatizados.

## 2. Níveis de Teste

### 2.1. Testes Unitários (Obrigatórios)
- **Criptografia e Identidade Determinística (App Dart & Backend Java 25):**
  - Geração da frase de 12 palavras (BIP39) e derivação determinística dos pares de chaves `Ed25519` (`seed[0..31]`, assinatura) e `X25519` (`seed[32..63]`, criptografia E2E).
  - Derivação determinística do `UUIDv7` de identidade a partir de `SHA-256(ed25519_public_key)` com bits de versão `0x70` e variante `0x80` (garantindo que os mesmos vetores de teste gerem o exato mesmo UUID no Dart e no Java 25).
  - Geração de `UUIDv7` temporal para mensagens usando `uuid-creator` (`UuidCreator.getTimeOrderedEpoch()`).
  - Testes de encriptação/decriptação E2E (`X25519`) e assinatura canônica (`Ed25519` com prefixos `ISPCHAT-DHT-V1|` e `ISPCHAT-MSG-V1|`).
- **Backend (Java 25 LTS):**
  - **Segurança DHT (Anti-Spoofing e Anti-Replay):** Garantir que registros onde `deriveUuidV7(public_key) != user_uuid`, com assinatura inválida ou fora da janela de timestamp (`±5 min`) sejam rejeitados imediatamente.
  - **Fronteira de Segurança na Porta 443:**
    - Testar o `MtlsFederationInterceptor`: chamadas para `DhtPeerService` e `NodeRoutingService` sem certificado de cliente assinado pela CA do ispChat (`ca.crt`) devem falhar com `Status.UNAUTHENTICATED`.
    - Testar o `ClientStreamService`: abertura de stream só deve associar a sessão e drenar a fila do Redis após validação do pacote `AUTH_HANDSHAKE` assinado.
  - **Proteção Anti-SSRF:** Validar que o `GrpcChannelPool` bloqueia tentativas de conexão de saída para IPs de loopback/redes privadas quando fora do modo de desenvolvimento.
- **Frontend (Flutter):**
  - Testes na lógica do "Ping Fallback": garantir que a matriz de IPs públicos/DNS SRV seja percorrida e o nó com a menor latência seja eleito.
  - Testes na **Blocklist Local**: garantir que o recebimento de `RING` gRPC seja ignorado silenciosamente se o `UUIDv7` do remetente estiver bloqueado localmente.

### 2.2. Testes de Integração
- **Fila Offline (Store and Forward):**
  - Simular envio de texto E2E para um `UUIDv7` offline. Validar persistência na `Redis List`. Conectar o cliente enviando `AUTH_HANDSHAKE` e verificar o descarregamento e limpeza imediata da fila.
- **Broadcast Multi-dispositivo (Redis Pub/Sub):**
  - Registrar 2 ou mais sessões ativas para o mesmo `UUIDv7` e verificar o disparo simultâneo de chamada (`RING`) e mensagens para todas as streams abertas.

### 2.3. Testes End-to-End (E2E)
1. Conexão mTLS validada entre Nó A e Nó B com certificados gerados pela CA (`extendedKeyUsage = serverAuth, clientAuth`).
2. Aparelho 1 criptografa texto e envia; Aparelho 2 recebe, valida assinatura do remetente e descriptografa com sua chave privada.
3. Fluxo completo de sinalização WebRTC entre dois nós autônomos.
