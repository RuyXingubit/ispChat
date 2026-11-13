# ISP Chat Dual-Tier Identity Governance, Chain-of-Trust Verification, Corporate ERP Gateway, and Home-Node Routing

* **Author:** Ruy / Antigravity
* **Date:** 2026-11-11

## Overview

Internet Service Providers (ISPs) pay recurring per-conversation fees to WhatsApp Business to send invoices, outage notices, and customer support messages. ISP Chat replaces this toll with an end-to-end encrypted (E2E) messaging and voice/video network operated across partner ISP nodes and a Root Governance Principal Server.

The current MVP codebase (`node/` in Java 25 LTS and `app/` in Flutter) treats all accounts as anonymous 12-word BIP39 identities, strands cross-ISP offline messages inside the sender node's Redis, lacks active `flutter_webrtc` media/TURN integration and DNS SRV lookup, and provides no corporate Desktop or ERP integration boundary. This feature introduces:
1. **Dual-Tier Identity Governance:** Open Unverified onboarding restricted to single-device mobile capabilities with strict rate limits, paired with a CPF/CNPJ-backed Verified tier carrying an exclusive Verified badge and unlocking multi-device Desktop QR pairing and ISP ERP API access.
2. **Cryptographic Chain of Faith:** An Ed25519-signed accountability chain (`Root Governance -> Partner ISP CNPJ & Managing Partners -> Collaborator CPF -> End-User CPF/CNPJ + UUIDv7`) logged in an append-only Principal Server ledger with offline-escrowed CPF encryption and blind-indexed anti-scraping protection.
3. **Home-ISP Routing & Portability:** Deterministic routing of offline payloads to the recipient's assigned Home ISP Node (or Principal Fallback Node) and signed Home-Node migration when a subscriber switches providers.
4. **Production VPS Readiness:** Fixes for existing container port/node-ID defects, DNS SRV bootstrap, Coturn ephemeral credential issuance, `flutter_webrtc` media streams, and a local HTTP-to-gRPC E2E bridge for ISP ERPs (IXC, SGP, MK-Auth).

## Analysis

### 1. Tier Boundary Enforcement & YAGNI Reality
* **Open-Source Client Spoofing Limit:** Because the Flutter client (GPLv3) and Java node (AGPLv3) are open source, a gRPC server cannot rely on client-reported OS flags (`platform = MOBILE`) to block custom desktop scripts from using Unverified keys, and Google/Apple hardware attestation would reintroduce Big Tech dependency. The protocol boundary (`ClientStreamGrpcService` and `DhtClientGrpcService`) enforces the Unverified tier through **capability restrictions**: maximum 1 active gRPC stream per `UUIDv7`, zero delegated sub-device keys, strict token-bucket rate limits (messages/minute and distinct new recipients/day), capped TURN relay bitrate, and exclusion from the public directory. Only accounts presenting a valid Principal-signed Verification Credential unlock multi-device delegation (`DESKTOP` and `ERP_API` subkeys), concurrent streams, and corporate rate limits.
* **Signature Reuse Over X.509 Overhead:** `CryptoService.java` and `IdentityCryptoService.dart` already validate 32-byte Ed25519 keys. Introducing ASN.1 X.509 certificates for Collaborators and End Users adds unnecessary dependency weight. Signed Protobuf attestation chains verified with existing JCA/Dart Ed25519 primitives satisfy non-repudiation directly.

### 2. Principal Server Scale, Privacy & Multi-Device E2E Architecture
* **Keeping the Principal Server Off the Real-Time Hot Path:** Synchronous Principal Server lookups during `Connect` or `RoutePayload` create a network-wide single point of failure during fiber-cut reconnect waves or DDoS events. Instead, verification issues a self-contained **Signed Verification Credential** (`Ed25519` chain + `tier` + `home_node_address` + `routing_epoch` + `expires_at`) stored on the client and validated locally by any ISP node in memory using the pinned Principal Root Ed25519 public key. Revocations propagate asynchronously via a signed delta feed cached in each node's Redis.
* **Competitor Anti-Scraping & LGPD Breach Containment:** Storing symmetrically encrypted CPFs alongside a live decryption key on the Principal VPS creates high LGPD exposure if the server is breached. Plaintext audit payloads (`user_cpf`, `collaborator_cpf`, `isp_cnpj`, `partner_cpfs`, and signed attestations) are sealed using **asymmetric `X25519 + ChaCha20-Poly1305` encryption** addressed to an **offline Root Governance Escrow Public Key** whose private key never resides on the VPS. Online uniqueness (`1 CPF = 1 active Verified UUIDv7`) uses a keyed `HMAC-SHA256(pepper, normalized_document)` blind index. To prevent a rogue Collaborator at a rival ISP from probing leaked CPF lists, the Principal Server rejects any verification submission unless accompanied by a fresh consent signature from the user's mobile `Ed25519` key (`ISPCHAT-VERIFY-REQ-V1`). Meanwhile, Verified `CNPJ` accounts are published in a signed public directory so subscribers can look up their ISP by name or CNPJ.
* **Delegated Device Subkeys for Desktop QR Pairing & ERP Gateways:** Currently, `IdentityCryptoService` uses a single static `Ed25519` + `X25519` keypair per BIP39 seed, and `CryptoService` requires `deriveIdentityUuidV7(sender_public_key) == sender_uuid`. To pair Desktop clients and ERP gateways without transmitting mobile private keys over the network and while preserving individual device revocation:
  1. Each Desktop or ERP Gateway generates its own local `(device_ed25519, device_x25519)` keypair.
  2. The Verified Mobile scans the Desktop/ERP pairing QR challenge and signs a `DeviceDelegationCert` binding `device_ed25519_pub`, `device_x25519_pub`, `device_role` (`DESKTOP` | `ERP_API`), and `expires_at` under the root `user_ed25519` key.
  3. Outgoing E2E messages encrypt the body once with an ephemeral 32-byte `ChaCha20-Poly1305` content key and wrap that 32-byte key via `X25519` ECDH for each active device `x25519_pub` of the sender (for multi-device sent history sync) and recipient (capped at 5 devices). Revoking a lost laptop or rotated ERP token revokes only that `device_id`.
* **Per-Device Offline Queue Delivery:** `OfflineMessageQueueService.drainQueue` currently executes an unacknowledged `leftPop` loop, which deletes messages on the first connected device (starving paired Desktops/ERPs) and loses messages if the TCP stream drops mid-drain. Offline queues on the Home Node must track per-device delivery cursors with explicit signed `DELIVERY_ACK` frames (bounded by atomic Redis Lua scripts at 1,000 messages / 7 days).

### 3. UX, ERP Ergonomics & Recovery Semantics
* **Local Corporate HTTP Gateway for IXC / SGP / MK-Auth:** ISP ERPs integrate via REST JSON and webhooks, not bidirectional Protobuf streams with ChaCha20-Poly1305. Exposing a central plaintext REST API on the Principal Server would violate E2E encryption. Instead, an **ISP Corporate Gateway** profile/container runs inside the ISP's own environment, authenticates to the ISP Node using a Partner-signed `ERP_API` delegated subkey, and exposes a local-only HTTP JSON + webhook interface for billing and support automation while keeping all payloads E2E-encrypted across the network.
* **Device-Loss Recovery & Anti-Hijack Hold:** Because neither the ISP nor the Principal Server holds user private keys, losing both the mobile device and the 12 BIP39 words prevents decryption of prior E2E messages. Collaborator-signed recovery operates as **Verified CPF Re-Binding (UUIDv7 Rotation)**: binding the validated CPF blind index to a newly generated mobile `UUIDv7`, revoking the old `UUIDv7` Verified credential, migrating the Home Node pointer, and publishing a signed `IdentityReplacementNotice` for contacts. To block unauthorized port-out or account takeover by a compromised Collaborator credential, Collaborator-forced recovery/portability on a recently active account triggers an alert and a mandatory hold window (cancellable by the active mobile `Ed25519` key) unless signed directly by the active mobile device.

### 4. Existing Codebase Defects, Migration & Operational Debuggability
* **Generated Protobuf Stubs Before Schema Expansion:** `app/lib/core/grpc/ispchat_pb.dart` hand-encodes wire tags (`CodedBufferWriter`). Before adding nested attestation, key-wrapping, and directory messages to `ispchat.proto`, `app/` will migrate to `protoc`-generated Dart stubs while keeping existing field numbers backward-compatible.
* **Cross-ISP Offline Routing Fix:** `MessageRouterService.routeClientEnvelope` currently falls back to `offlineQueueService.enqueue` on the **sender's** node whenever in-memory `DhtStoreService` lacks an active session. Every account will maintain a signed `HomeNodeClaim` (`user_uuid -> home_node_address, routing_epoch`) persisted in Redis/DHT (and Principal Directory for Verified accounts), defaulting non-partner users to the Principal Fallback Node. Undelivered `TEXT` envelopes forward via `NodeRoutingService.routePayload` over mTLS to the recipient's `home_node_address` Redis rather than landing in a foreign sender node's local queue.
* **Four Production VPS Blockers in Current Repo:**
  1. `node/docker-compose.yml` sets `ISPCHAT_NODE_ID`, whereas `KademliaRoutingTable.java` reads `ISPCHAT_DHT_NODE_ID` (`ispchat.dht.node-id`), causing all production nodes to share the same default ID (`01950000-0000-7000-8000-000000000001`) and drop all peers as self.
  2. `node/Dockerfile` runs as non-root `USER ispchat` while attempting to bind privileged port `443` directly inside the container; Docker must map host `443:8443/tcp` (`GRPC_SERVER_PORT=8443` internally) while advertising `:443` externally.
  3. `NodeDiscoveryService.dart` contains no DNS SRV lookup implementation for `_ispchat._tcp.ispchat.dev.br`.
  4. `WebRtcCallCoordinator.dart` exchanges JSON state without binding `flutter_webrtc` `RTCPeerConnection` audio/video tracks or issuing ephemeral HMAC-SHA1 TURN credentials matching `node/turnserver.conf` (`use-auth-secret`).
* **Observability & Tamper-Evident Audit Ledger:** `ClientStreamGrpcService` currently discards routing failures without notifying the sender, and `IspChatNodeClient.dart` swallows stream errors (`onError: (_) {}`). The protocol requires explicit `DELIVERY_ACK` / `DELIVERY_ERROR` frames correlated by temporal `UUIDv7` `message_id`, client auto-reconnect with backoff, Micrometer counters on `node/`, and an append-only SHA-256 hash chain (`prev_hash || attestation_bytes`) over Principal Server verification rows.
* **Modular Decoupling:** Partner ISPs deploy `ispchat-node` with only Java 25 + Redis + Coturn (no PostgreSQL dependency), isolating PostgreSQL ledger storage inside `ispchat-principal` and REST webhook translation inside `ispchat-erp-bridge`.

## Goals vs. Non-Goals

* **In-Scope Goals:**
  * Enforce Dual-Tier governance (`UNVERIFIED` mobile-capability tier vs. `VERIFIED` CPF/CNPJ tier with Verified badge, Desktop QR pairing, and ERP API delegation).
  * Implement the 4-level Ed25519 Chain of Faith (`Root -> ISP CNPJ + Partners -> Collaborator CPF -> User CPF/CNPJ + UUIDv7`) with append-only hash-chained Principal Server storage, offline asymmetric CPF escrow, keyed HMAC-SHA256 uniqueness (`1 CPF = 1 Verified Account`), and public CNPJ directory search.
  * Implement multi-device delegated subkeys (`DESKTOP`, `ERP_API`) and per-device `X25519` content-key wrapping so mobile private keys never cross the network in cleartext and individual devices/tokens can be revoked.
  * Provide a self-hosted ISP Corporate Gateway (HTTP JSON + webhooks -> E2E gRPC) for IXC, SGP, and MK-Auth integration.
  * Fix cross-ISP offline routing via signed `HomeNodeClaim` forwarding, per-device ACKed Redis queues, and signed Home-Node portability migration.
  * Resolve VPS deployment blockers (`ISPCHAT_DHT_NODE_ID`, container port `443:8443`, DNS SRV lookup, `flutter_webrtc` `RTCPeerConnection` + Coturn HMAC credentials, `protoc` Dart codegen, and unit tests).

* **Out-of-Scope Non-Goals:**
  * Server-side custody or escrow of user/corporate E2E private keys (`Ed25519` / `X25519`).
  * Public search or enumeration of individual `CPF` records on the network.
  * Large-group chats / Kafka infinite history, BGP Anycast automation, CDN Domain Fronting, or BLE/Wi-Fi Direct mesh (deferred per roadmap).
  * Google Play Integrity or Apple App Attest lock-in.

## Alternatives Weighed

| Option Rejected | Reason for Rejection |
| :--- | :--- |
| **Block Unverified accounts completely at onboarding** | Breaks household adoption (multiple family members under one broadband holder CPF) and prevents external contacts from joining before visiting an ISP. |
| **Rely on client-reported OS headers to enforce "Mobile-Only" for Unverified users** | Spoofable in an open-source (GPLv3/AGPLv3) protocol; capability enforcement (1 stream, 0 delegated subkeys, strict rate limits) achieves the security goal without trusting client metadata. |
| **Transfer raw BIP39 mnemonic or root private keys to Desktop and ERP servers** | Prevents per-device revocation if a desktop or ERP server is compromised and exposes the root identity key outside the verified mobile device. |
| **Synchronous Principal Server RPC lookup on every node handshake or message** | Creates a central QPS bottleneck and single point of failure that halts all federated ISP nodes if the Principal VPS is unreachable. |
| **Symmetric AES encryption of CPFs with keys stored on the Principal VPS** | A Principal VPS breach would expose plaintext CPFs across competing ISPs; offline-escrowed asymmetric `X25519` sealing prevents bulk plaintext extraction even under full VPS compromise. |
| **Centralized REST API on the Principal Server holding ISP keys for ERP webhooks** | Breaks E2E confidentiality by exposing corporate-to-subscriber plaintext to the Principal Server; a local ISP Corporate Gateway sidecar keeps encryption at the ISP edge. |
| **Keep hand-written `ispchat_pb.dart` binary serialization** | High defect risk when adding nested attestation chains, multi-device key envelopes, and directory messages. |

## Key Decisions & Staging for Next Steps

1. **Phase 0 — Protocol Codegen & Existing VPS Bug Fixes (`ispchat-proto`, `node/`, `app/`):**
   * Replace hand-maintained `app/lib/core/grpc/ispchat_pb.dart` with `protoc`-generated Dart/Java stubs using length-safe canonical Protobuf signing payloads.
   * Fix `ISPCHAT_DHT_NODE_ID` in `node/docker-compose.yml`, map container port `443:8443` for non-root execution, implement DNS SRV resolution (`_ispchat._tcp.ispchat.dev.br`) in `NodeDiscoveryService`, and wire `flutter_webrtc` `RTCPeerConnection` + `GetTurnCredentials` HMAC-SHA1 issuance for Coturn.
2. **Phase 1 — Principal Governance Service & Chain of Faith (`ispchat-principal`):**
   * Build the Principal gRPC/admin service backed by PostgreSQL 17 with an append-only hash-chained audit log, `HMAC-SHA256` CPF blind index (`1 CPF = 1 Verified UUIDv7`), offline-escrowed `X25519` audit payload sealing, user mobile consent signature verification, signed `VerificationCredential` issuance, asynchronous Redis revocation feed, and public `CNPJ` directory RPCs.
3. **Phase 2 — Dual-Tier Boundary, Multi-Device Subkeys & Desktop QR Pairing (`node/`, `app/`):**
   * Enforce Unverified capability limits (1 stream, no subkeys, token-bucket rate limits) vs. Verified multi-device capabilities in `ClientStreamGrpcService` and `DhtClientGrpcService`.
   * Implement WhatsApp-Web-style QR Desktop pairing (`DeviceDelegationCert`), per-device `X25519` content-key wrapping (up to 5 devices), Verified badges in Flutter UI, and Collaborator-signed CPF Re-Binding (`UUIDv7` rotation with hold protection for lost devices).
4. **Phase 3 — Home-ISP Offline Routing, Per-Device Queues & Portability (`node/`):**
   * Persist signed `HomeNodeClaim` records, update `MessageRouterService` to forward offline `TEXT` envelopes to the recipient's Home ISP Node (or Principal Fallback Node), replace destructive `LPOP` with per-device ACKed Redis queues + `DELIVERY_ACK` frames, and implement signed Home-Node migration.
5. **Phase 4 — ISP Corporate ERP Gateway & Public VPS E2E Validation (`ispchat-erp-bridge`):**
   * Deliver the self-hosted HTTP JSON + Webhook gateway backed by a Partner-delegated `ERP_API` subkey for IXC/SGP/MK-Auth, complete unit/integration test suites across Java 25 and Flutter, and validate end-to-end across public VPS nodes.
