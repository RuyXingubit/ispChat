# Consolidação Final Aprovada: Arquitetura, Segurança e Upgrade Java 25 LTS (v5)

* **Autor:** Ruy / Antigravity
* **Data:** 2026-11-02
* **Status:** Aprovado pelo Usuário para Execução e Consolidação Oficial

---

## 1. Resumo das Decisões Finais Aprovadas

1. **Upgrade para Java 25 LTS + Gradle 8.14 + Spring Boot 3.5:**
   * Migração da toolchain do módulo `node/` de Java 21 para **Java 25 LTS** (aproveitando o fim do *pinning* em Virtual Threads via JEP 491, Compact Object Headers via JEP 519 e KDF API nativa via JEP 510).
   * Remoção do `spring-boot-starter-web` (Tomcat HTTP na porta 8080) em favor de `spring-boot-starter` com `spring.main.web-application-type=none`, mantendo apenas a porta gRPC `443` ativa.
   * Inclusão de `com.github.f4b6a3:uuid-creator:6.0.0`, atualização do gRPC (`1.71.0`) e Protobuf (`4.30.2`).

2. **Identidade Determinística (`BIP39 -> Ed25519 + X25519 -> UUIDv7`) vs. `UUIDv7` Temporal:**
   * A seed BIP39 (64 bytes) deriva `Ed25519` (`seed[0..31]`, assinatura digital) e `X25519` (`seed[32..63]`, criptografia E2E).
   * O `UUIDv7` da identidade do usuário (`user_uuid`) é derivado deterministicamente dos primeiros 16 bytes de `SHA-256(ed25519_public_key)` com os bits de versão `0x70` e variante `0x80` (RFC 9562), garantindo recuperação exata pelas 12 palavras e verificação matemática anti-spoofing na DHT.
   * O `UUIDv7` de mensagens (`message_id`) usa o gerador temporal padrão (`UuidCreator.getTimeOrderedEpoch()`).

3. **Fronteira mTLS na Porta 443 (`ClientAuth.OPTIONAL` + Interceptor) e `AUTH_HANDSHAKE`:**
   * Servidor gRPC único na porta 443 com TLS 1.3 e `ClientAuth.OPTIONAL`.
   * Serviços Nó-a-Nó (`DhtPeerService`, `NodeRoutingService`) exigem certificado mTLS assinado pela CA do ispChat validado via `ServerInterceptor`.
   * Serviços App-a-Nó (`ClientStreamService`, `DhtClientService`) autenticam via assinatura digital `Ed25519` canônica (`ISPCHAT-DHT-V1|...` e `ISPCHAT-MSG-V1|...`), iniciando a stream com um pacote `AUTH_HANDSHAKE`.

4. **Autoridade Certificadora (`ca/`) e Blindagem do DNS (`dns/`):**
   * Certificados de nós em `ca/generate_node_cert.sh` atualizados para RSA 4096 bits com `extendedKeyUsage = serverAuth, clientAuth`.
   * DNS Master (`dns/master/pdns.conf` e `docker-compose.yml`) corrigido com alias `db.local`, remoção de `allow-axfr-ips=0.0.0.0/0`, restrição de `webserver-allow-from` e atualização para `postgres:17-alpine` e `powerdns/pdns-auth-49:latest`.
