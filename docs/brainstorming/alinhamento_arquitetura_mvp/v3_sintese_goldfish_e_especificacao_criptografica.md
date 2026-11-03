# Síntese da Revisão Multi-Lente Goldfish e Especificação Criptográfica (v3)

* **Autor:** Ruy / Antigravity (Síntese das 6 Lentes EGM Goldfish)
* **Data:** 2026-11-02
* **Tema:** Refinamento das 4 decisões arquiteturais após auditoria pelas 6 lentes (`egm-design`, `egm-codebase-details`, `egm-zoom-out`, `egm-comprehension`, `egm-implementation-readiness`, `egm-structural`).

---

## 1. Achados Críticos Levantados pelos Revisores Goldfish

### 1.1. Lacuna de Identidade na Stream do App (`ClientEnvelope` e `AUTH_HANDSHAKE`)
* **Problema detectado:** Em `ispchat.proto`, a mensagem `ClientEnvelope` usada no stream bidirecional `ClientStreamService.Connect` possuía apenas `message_id`, `receiver_uuid`, `type`, `encrypted_payload` e `signature`. Faltavam **`sender_uuid`**, **`sender_public_key`** e **`timestamp`**.
* **Impacto no Store-and-Forward (Redis):** Quando o usuário abre o aplicativo apenas para receber mensagens pendentes da fila offline (`Redis List`) ou aguardar chamadas (`RING`), o servidor gRPC precisa saber imediatamente qual `user_uuid` é dono daquela stream aberta e validar que ele possui a chave privada correspondente.
* **Solução:**
  1. Incluir `sender_uuid`, `sender_public_key` e `timestamp` no `ClientEnvelope` (e repassar `sender_public_key` no `RoutePayloadRequest` e `ServerEnvelope`).
  2. Adicionar o tipo `AUTH_HANDSHAKE` (e `CALL_DECLINE`) ao enum `MessageType`. Ao abrir a stream `Connect`, o primeiro pacote enviado pelo App é um `AUTH_HANDSHAKE` assinado; o nó valida matematicamente `deriveUuidV7(sender_public_key) == sender_uuid` + assinatura Ed25519 (janela anti-replay de $\pm 5$ minutos), associa a sessão ativa e descarrega imediatamente a fila offline do Redis para o aparelho.

### 1.2. Especificação Bit-a-Bit da Derivação (`BIP39 -> Ed25519 + X25519 -> UUIDv7`)
* **Problema detectado:** `Ed25519` é um algoritmo exclusivo de assinatura digital, enquanto a criptografia E2E de mensagens de texto exige acordo de chaves `X25519` (ECDH) + cifra autenticada (`ChaCha20-Poly1305` ou `AES-256-GCM`). Além disso, Dart (`package:cryptography`) e Java 21 precisam seguir exatamente o mesmo layout de bytes para gerar o mesmo `UUIDv7` e validar assinaturas.
* **Solução Padronizada:**
  1. **Derivação de Chaves a partir do BIP39:** As 12 palavras geram a seed padrão BIP39 de 64 bytes (`PBKDF2-HMAC-SHA512`):
     * `seed[0..31]` (primeiros 32 bytes): semente da chave privada **Ed25519** (Identidade e Assinatura Digital).
     * `seed[32..63]` (últimos 32 bytes): semente da chave privada **X25519** (Criptografia E2E de payloads).
  2. **Algoritmo Exato do `UUIDv7` Determinístico de Identidade:**
     * Calcular `hash = SHA-256(ed25519_public_key_32_bytes)`.
     * Tomar os primeiros 16 bytes: `bytes = hash[0..15]`.
     * Forçar bits de versão 7 (RFC 9562): `bytes[6] = (bytes[6] & 0x0F) | 0x70`.
     * Forçar bits de variante IETF (RFC 9562): `bytes[8] = (bytes[8] & 0x3F) | 0x80`.
     * Formatar como string UUID lowercase (`8-4-4-4-12`), preservando **122 bits de entropia SHA-256** e total compatibilidade com validadores UUIDv7.
  3. **Interoperabilidade Ed25519 Nativa no Java 21:** O pacote `cryptography` do Flutter exporta a chave pública Ed25519 em 32 bytes puros, enquanto o `KeyFactory.getInstance("Ed25519")` nativo do Java 21 exige formato ASN.1 `SubjectPublicKeyInfo` (SPKI, 44 bytes). O backend Java prefixará os 12 bytes fixos do cabeçalho RFC 8410 (`30 2a 30 05 06 03 2b 65 70 03 21 00`) aos 32 bytes recebidos, sem precisar de dependências externas pesadas (como BouncyCastle).
  4. **Serialização Canônica para Assinatura (Dart <-> Java):** Como a serialização binária do Protobuf não é canônica entre linguagens, a assinatura Ed25519 será calculada sobre bytes determinísticos com separação de domínio:
     * **Registro na DHT (`RegisterSession` / `Store`):** UTF-8 de `"ISPCHAT-DHT-V1|" + user_uuid + "|" + node_address + "|" + timestamp_millis`.
     * **Envelopes de Mensagem (`ClientEnvelope` / `RoutePayload`):** UTF-8 de `"ISPCHAT-MSG-V1|" + message_id + "|" + sender_uuid + "|" + receiver_uuid + "|" + message_type_number + "|" + timestamp_millis + "|"` concatenado aos bytes brutos de `encrypted_payload`.

### 1.3. Hardening Adicional no DNS Master e Proteção Anti-SSRF no Pool gRPC
* **DNS Master (`dns/master/pdns.conf`):** Além de remover `allow-axfr-ips=0.0.0.0/0` e `also-notify=0.0.0.0/0` e adicionar o alias `db.local` no `dns/master/docker-compose.yml`, os revisores apontaram que a linha 26 (`webserver-allow-from=0.0.0.0/0`) também estava aberta. Ela deve ser restrita às sub-redes internas/Docker (`127.0.0.1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16`), já que o acesso externo passa exclusivamente pelo Caddy.
* **Proteção Anti-SSRF no `GrpcChannelPool`:** Como os endereços de nós (`node_address`) vêm de registros na DHT, o pool de conexões de saída mTLS do nó Java validará que o destino é um IP público roteável na porta 443 (bloqueando conexões para `127.0.0.1`, `localhost` ou faixas privadas RFC 1918, a menos que uma flag explícita de desenvolvimento local esteja ativa).
