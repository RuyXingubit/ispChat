# Análise de Atualização Tecnológica: Java 25 LTS, Backend, DNS e CA (v4)

* **Autor:** Ruy / Antigravity
* **Data:** 2026-11-02
* **Tema:** Avaliação de upgrade para Java 25 LTS e modernização de dependências, imagens Docker e certificados mTLS antes da implementação.

---

## 1. Java 21 $\rightarrow$ Java 25 LTS (Altamente Recomendado)

A máquina de desenvolvimento já possui o **OpenJDK 25 LTS** instalado (`Temurin-25+36` e `ms-25.0.2`).

### Motivos técnicos para subir para o Java 25 LTS no `node/`:
1. **Fim do Pinning de Virtual Threads em blocos `synchronized` (JEP 491):**
   * No Java 21, quando uma Virtual Thread bloqueia em rede dentro de um método `synchronized` (comum em bibliotecas de terceiros e camadas de I/O), ela prende (*pins*) a Carrier Thread do sistema operacional, reduzindo a concorrência sob milhares de conexões gRPC simultâneas. No Java 25 LTS, esse gargalo foi resolvido na própria JVM.
2. **Menor Consumo de Memória nas VPSs dos Provedores (JEP 519 - Compact Object Headers):**
   * Reduz o cabeçalho de objetos na heap de 96–128 bits para 64 bits, economizando de 10% a 20% de RAM — ideal para manter os k-buckets da DHT Kademlia e milhares de sessões ativas em VPSs enxutas.
3. **KDF API Nativa (JEP 510):**
   * Padroniza funções de derivação de chaves (HKDF) nativamente na JCA do Java 25 sem bibliotecas externas.

### Requisitos de compatibilidade para o Java 25:
* **Gradle Wrapper:** Subir de `8.12.1` para **`8.14.x` ou `9.0.0`** (pois o Gradle 8.12 não reconhece o classfile major version 69 do Java 25).
* **Spring Boot:** Subir de `3.4.1` para **`3.5.x`** (compatível com bytecode Java 25).

---

## 2. O que mais podemos (e devemos) atualizar no projeto

### 2.1. Backend (`node/build.gradle.kts`)
1. **Remover `spring-boot-starter-web` e usar `spring-boot-starter` puro:**
   * **Por quê (Segurança + Performance):** Hoje o projeto puxa o Tomcat Web e abre a porta HTTP `8080` sem necessidade, pois toda a comunicação do ISP Chat é 100% gRPC na porta `443`. Remover o starter web fecha uma porta HTTP desnecessária (reduzindo superfície de ataque) e economiza memória e tempo de boot.
2. **Atualizar gRPC (`1.69.0` $\rightarrow$ `1.71.0+`) e Protobuf (`3.25.5` $\rightarrow$ `4.30.x`):**
   * **Por quê:** O app Flutter já está configurado com a geração nova (`protobuf: ^6.0.0` e `grpc: ^5.1.0`), enquanto o backend Java ainda usa o `protoc` da série `3.25.x` e uma dependência legada do Tomcat 6 (`org.apache.tomcat:annotations-api:6.0.53`). Podemos substituir essa dependência legada por `javax.annotation:javax.annotation-api:1.3.2`.
3. **Incluir `com.github.f4b6a3:uuid-creator:6.0.0`:**
   * Obrigatório para geração dos `UUIDv7` temporais de `message_id`.

### 2.2. Scripts da Autoridade Certificadora (`ca/generate_node_cert.sh`)
1. **Adicionar `extendedKeyUsage = serverAuth, clientAuth` no certificado X.509:**
   * **Por quê (Crítico para mTLS):** Hoje o `extfile.cnf` gerado em `ca/generate_node_cert.sh` define apenas `keyUsage`, mas omite `extendedKeyUsage = serverAuth, clientAuth`. Validadores TLS 1.3 modernos (Java 25 `PKIXValidator` e BoringSSL no Flutter) rejeitam certificados mTLS que não declaram explicitamente `serverAuth` e `clientAuth`.
2. **Subir a chave dos nós de RSA 2048 para ECDSA P-256 (ou RSA 4096):**
   * **Por quê:** A CA raiz já usa 4096 bits, mas o script do nó gera chaves RSA 2048. Migrar para ECDSA P-256 (`prime256v1`) ou RSA 4096 eleva a segurança criptográfica e reduz o tamanho dos pacotes no handshake TLS.

### 2.3. Infraestrutura DNS (`dns/master/` e `dns/slave/`)
1. **PostgreSQL `15-alpine` $\rightarrow$ `17-alpine`:**
   * Mais rápido, menor consumo de I/O e janela de suporte de segurança mais longa.
2. **PowerDNS Authoritative `pdns-auth-48` $\rightarrow$ `pdns-auth-49`:**
   * Branch estável mais recente da linha 4.x com patches de segurança atualizados.
3. **Remover a diretiva obsoleta `version: '3.8'` dos `docker-compose.yml`:**
   * Elimina os avisos de depreciação do Docker Compose V2.

### 2.4. Limpeza da Raiz do Repositório
1. **Remover `spring.zip` da raiz do repositório:**
   * Arquivo residual de 189 bytes deixado na criação inicial.
