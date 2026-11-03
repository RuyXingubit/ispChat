# Decisões Arquiteturais Consolidadas Pré-Revisão Goldfish (v2)

* **Autor:** Ruy / Antigravity
* **Data:** 2026-11-02
* **Tema:** Consolidação das 4 decisões arquiteturais e de segurança aprovadas pelo usuário antes da revisão multi-lente (EGM Goldfish).

---

## 1. Decisões Aprovadas pelo Usuário

### 1.1. Identidade Determinística e Anti-Spoofing (`BIP39 -> Ed25519 -> UUIDv7`)
* **Decisão:** O `UUIDv7` da identidade do usuário terá seus bits derivados deterministicamente do hash criptográfico (SHA-256) da `PublicKey` Ed25519 (que por sua vez é derivada da frase semente de 12 palavras BIP39).
* **Motivação e Garantias:**
  1. **Recuperação sem estado externo:** Ao digitar as 12 palavras em um novo aparelho no futuro, o aplicativo deriva a mesma `PrivateKey`/`PublicKey` e reconstrói exatamente o mesmo `UUIDv7` de identidade, sem depender do timestamp físico do dia em que a conta foi criada.
  2. **Anti-Spoofing matemático na DHT:** Qualquer nó Java na rede DHT que receber um `RegisterSessionRequest` ou `StoreRequest` contendo `(user_uuid, public_key, signature)` poderá verificar matematicamente se `deriveUuidV7FromPublicKey(public_key) == user_uuid` antes de validar a assinatura Ed25519. Isso impede que um atacante use sua própria chave para sequestrar o UUID de outro usuário.
  3. **IDs de Eventos e Mensagens:** Para `message_id` e eventos de roteamento, mantém-se o `UUIDv7` temporal padrão (RFC 9562 via `uuid-creator` no Java e `uuid` v7 no Dart) para ordenação cronológica.

### 1.2. Fronteira de Segurança na Porta 443 (`ClientAuth.OPTIONAL` + Interceptors)
* **Decisão:** Manter todo o tráfego gRPC na porta `443` única (preservando a camuflagem contra DPI/bloqueio de portas não-padrão), configurando o servidor Netty/TLS com `ClientAuth.OPTIONAL` e aplicando um `ServerInterceptor` rigoroso por serviço:
  1. **Tráfego Nó-a-Nó (`NodeRoutingService` e `DhtPeerService`):** O Interceptor exige obrigatoriamente que a sessão TLS possua um certificado de cliente (`SSLSession.getPeerCertificates()`) válido e assinado pela Autoridade Certificadora (`ca.crt`) do ispChat. Caso contrário, rejeita imediatamente com `Status.UNAUTHENTICATED`.
  2. **Tráfego App-a-Nó (`ClientStreamService` e `DhtClientService`):** Ocorre sobre o túnel TLS 1.3 da porta 443 e o Interceptor/Serviço valida a assinatura digital `Ed25519` em cada envelope enviado pelo aplicativo Flutter, verificando a correspondência com o `user_uuid`.

### 1.3. Unificação dos Contratos Protobuf e Roteamento Direto (Sem `io.grpc.xds`)
* **Decisão:**
  1. Remover a duplicidade entre `ispchat.proto`, `dht.proto` e `messaging.proto`, consolidando os contratos com separação explícita entre serviços de borda (App <-> Nó) e serviços internos da federação (Nó <-> Nó, incluindo `Ping`, `Store` com `public_key`, `FindNode`, `FindValue` e `RoutePayload`).
  2. Substituir o Control Plane `io.grpc.xds` previsto inicialmente no Tech Spec por um gerenciador/pool direto de `ManagedChannel` mTLS no backend Java, reduzindo superfície de ataque, consumo de memória e complexidade operacional.

### 1.4. Blindagem do DNS Master (`dns/master/`)
* **Decisão:**
  1. Remover `allow-axfr-ips=0.0.0.0/0` e `also-notify=0.0.0.0/0` do arquivo `dns/master/pdns.conf` para impedir transferência de zona aberta para a internet.
  2. Adicionar o network alias `db.local` ao serviço `db` no `dns/master/docker-compose.yml` para alinhar com `gpgsql-host=db.local` do `pdns.conf`.
