# ISP Chat MVP Architecture Alignment, Security Remediation & Java 25 Upgrade

* **Author:** Ruy / Antigravity
* **Date:** 2026-11-02

## Overview

ISP Chat is a decentralized, peer-to-peer communication network hosted by Internet Service Providers (ISPs). Users hold self-sovereign identities (`UUIDv7` + cryptographic keypairs derived from 12-word BIP39 seed phrases) and communicate via End-to-End Encrypted (E2E) text and WebRTC voice/video calls without central servers.

This brief records the finalized architectural alignment, security remediations, and stack upgrades (Java 25 LTS, Spring Boot 3.5, Protobuf 4.x, PowerDNS 4.9, PostgreSQL 17, and X.509 mTLS `extendedKeyUsage`) approved for the MVP.

## Analysis

### 1. Java 25 LTS & Headless gRPC Node Runtime (`node/`)
* **Toolchain Upgrade:** Upgraded backend from Java 21 to **Java 25 LTS** (with Gradle 9.0.0 and Spring Boot 3.5.0) to eliminate Virtual Thread pinning inside `synchronized` blocks (JEP 491) and reduce heap footprint via Compact Object Headers (JEP 519).
* **Attack Surface Reduction:** Replaced `spring-boot-starter-web` (which exposed an unused Tomcat HTTP server on port 8080) with headless `spring-boot-starter` (`spring.main.web-application-type=none`), leaving only the port 443 gRPC server exposed.
* **Dependencies:** Added `com.github.f4b6a3:uuid-creator:6.0.0` for RFC 9562 UUIDv7 generation, and upgraded gRPC to `1.71.0` and Protobuf to `4.30.2`.

### 2. Deterministic Identity Binding (`BIP39 -> Ed25519 + X25519 -> UUIDv7`) vs. Temporal Event IDs
* **Seed to Keys (`BIP39`):** The 12-word BIP39 mnemonic produces a 64-byte PBKDF2-HMAC-SHA512 seed:
  * `seed[0..31]` seeds the user's **Ed25519 signing keypair** (`public_key` = 32 raw bytes).
  * `seed[32..63]` seeds the user's **X25519 E2E encryption keypair**.
* **Deterministic Identity `UUIDv7` (`user_uuid`):**
  * Compute `hash = SHA-256(ed25519_public_key_32_bytes)` and take `bytes = hash[0..15]`.
  * Set RFC 9562 Version 7 bits (`bytes[6] = (bytes[6] & 0x0F) | 0x70`) and IETF Variant 2 bits (`bytes[8] = (bytes[8] & 0x3F) | 0x80`).
  * Preserves 122 bits of SHA-256 entropy, enables stateless 12-word account recovery, and allows every DHT node to verify `deriveUuidV7(public_key) == user_uuid`.
* **Temporal Event & Message IDs (`message_id`):** Standard time-ordered RFC 9562 `UUIDv7` via `UuidCreator.getTimeOrderedEpoch()` in Java and `Uuid().v7()` in Dart.

### 3. Unified Port 443 Security Boundary (`ClientAuth.OPTIONAL` + Strict `ServerInterceptor`) & CA Hardening
* **X.509 Certificate Compliance (`ca/`):** Upgraded node keys in `ca/generate_node_cert.sh` to RSA 4096-bit and added `extendedKeyUsage = serverAuth, clientAuth` so strict Java 25 and Flutter BoringSSL TLS 1.3 validators accept the certificates.
* **Port 443 Split Enforcement:**
  * `NodeRoutingService` and `DhtPeerService` require a verified ISP Chat CA client certificate in `Grpc.TRANSPORT_ATTR_SSL_SESSION`.
  * `ClientStreamService` and `DhtClientService` authenticate end-user Flutter apps via canonical Ed25519 signatures (`"ISPCHAT-DHT-V1|..."` and `"ISPCHAT-MSG-V1|..."`), starting streams with a signed `AUTH_HANDSHAKE` frame.

### 4. Unified Protobuf Contract (`ispchat.proto`), Direct `GrpcChannelPool` & PowerDNS Master Hardening
* Consolidated `node/src/main/proto/` into a single `ispchat.proto` (removing `dht.proto` and `messaging.proto`).
* Replaced `io.grpc.xds` with a direct mTLS `ManagedChannel` pool (`GrpcChannelPool`) with SSRF protection.
* Hardened `dns/master/pdns.conf` (removed `0.0.0.0/0` AXFR/notify, restricted webserver ACLs), added `db.local` alias to `dns/master/docker-compose.yml`, and upgraded Docker images to `postgres:17-alpine` and `powerdns/pdns-auth-49:latest`.

## Goals vs. Non-Goals

* **In-Scope Goals:**
  * Align `docs/PRD_MVP.md`, `docs/Tech_Spec_MVP.md`, `docs/QA_Strategy_MVP.md`, and `README.md`.
  * Upgrade `node/` to Java 25 LTS, unify `ispchat.proto`, harden `ca/` and `dns/`, and remove residual `spring.zip`.
* **Out-of-Scope Non-Goals:**
  * Phase 2/3/4 features (Apache Kafka group chat, BGP Anycast, CDN Domain Fronting, Bluetooth LE Mesh).

## Alternatives Weighed

* **Java 21 vs. Java 25 LTS:** Selected Java 25 LTS to eliminate Virtual Thread pinning (`synchronized`) and reduce memory usage via Compact Object Headers.
* **Standard timestamp-based `UUIDv7` for user identities (Rejected):** Cannot be deterministically recovered from 12 BIP39 words alone and prevents stateless mathematical `PublicKey -> UUID` verification in the DHT.
* **Separate ports for Client-to-Node and Node-to-Node (Rejected):** Single port 443 with `ClientAuth.OPTIONAL` + per-service `ServerInterceptor` preserves HTTPS port camouflage.
* **Local `io.grpc.xds` control plane (Rejected):** Replaced with direct mTLS `ManagedChannel` pooling.

## Key Decisions & Staging for Next Steps

1. Execute stack upgrades and security fixes across `docs/`, `ca/`, `dns/`, and `node/`.
2. Verify Java 25 LTS compilation and Protobuf/gRPC stub generation via `./gradlew build`.
