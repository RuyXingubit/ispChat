package br.dev.ispchat.principal.cli;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.common.crypto.CanonicalTranscripts.RawKeyPair;
import br.dev.ispchat.grpc.PartnerSeedEntry;
import br.dev.ispchat.grpc.PrincipalSigningCert;
import br.dev.ispchat.grpc.RegisterPartnerIspRequest;
import br.dev.ispchat.principal.config.PrincipalKeyMaterial;
import br.dev.ispchat.principal.ledger.AuditLedgerService;
import br.dev.ispchat.principal.ledger.AuditLedgerService.AuditAccountabilityPayload;
import br.dev.ispchat.principal.ledger.AuditLedgerService.ExportedLedgerEntryJson;
import br.dev.ispchat.principal.ledger.AuditLedgerService.LedgerEntryRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utilitario CLI Air-Gapped (Nivel 0 Offline Root & Judicial Escrow):
 *   --init-root            Gera par Ed25519 Raiz Nivel 0 + par X25519 Root Escrow (0600)
 *   --sign-principal-cert  Assina PrincipalSigningCert (TTL padrao 365 dias)
 *   --sign-isp-bootstrap   Assina RegisterPartnerIspRequest (ISPCHAT-CHAIN-ISP-V1)
 *   --decrypt-audit-entry  Valida integridade SHA-256 e descriptografa sealed_audit_blob
 *   --verify-ledger-chain  Verifica integridade completa da cadeia verification_audit_ledger
 */
public final class OfflineGovernanceCli {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record PartnerBootstrapInput(
            String partnerCpf,
            String partnerEd25519PubBase64,
            String partnerX25519PubBase64) {
    }

    public record RootKeyBundle(
            Path rootEd25519PubFile,
            Path rootEd25519KeyFile,
            Path escrowX25519PubFile,
            Path escrowX25519KeyFile) {
    }

    private OfflineGovernanceCli() {
    }

    public static void main(String[] args) throws Exception {
        int code = runCli(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    public static boolean isOfflineCliInvocation(String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        String first = args[0].trim();
        return first.equals("--init-root")
                || first.equals("--sign-principal-cert")
                || first.equals("--sign-isp-bootstrap")
                || first.equals("--decrypt-audit-entry")
                || first.equals("--verify-ledger-chain");
    }

    public static int runCli(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length == 0) {
            err.println("Uso: OfflineGovernanceCli [--init-root | --sign-principal-cert | --sign-isp-bootstrap | --decrypt-audit-entry | --verify-ledger-chain]");
            return 2;
        }
        String command = args[0].trim();
        Map<String, String> flags = parseFlags(args, 1);

        try {
            switch (command) {
                case "--init-root" -> {
                    Path outDir = Path.of(requireFlag(flags, "--out-dir"));
                    RootKeyBundle bundle = initRootKeys(outDir);
                    out.println("Chaves Raiz Nivel 0 e Escrow geradas em: " + outDir.toAbsolutePath());
                    out.println("  Root Ed25519 Pub : " + Files.readString(bundle.rootEd25519PubFile()).trim());
                    out.println("  Escrow X25519 Pub: " + Files.readString(bundle.escrowX25519PubFile()).trim());
                    return 0;
                }
                case "--sign-principal-cert" -> {
                    byte[] rootPriv = readKeyFile(Path.of(requireFlag(flags, "--root-key")));
                    byte[] principalPub = readFlexibleKeyParam(requireFlag(flags, "--principal-pub"));
                    byte[] escrowPub = readFlexibleKeyParam(requireFlag(flags, "--escrow-pub"));
                    int ttlDays = Integer.parseInt(flags.getOrDefault("--ttl-days", "365"));
                    long nowMs = flags.containsKey("--now-ms")
                            ? Long.parseLong(flags.get("--now-ms"))
                            : System.currentTimeMillis();

                    PrincipalSigningCert cert = signPrincipalSigningCert(
                            rootPriv, principalPub, escrowPub, nowMs, ttlDays);
                    String encoded = Base64.getEncoder().encodeToString(cert.toByteArray());
                    if (flags.containsKey("--out")) {
                        Files.writeString(Path.of(flags.get("--out")), encoded + System.lineSeparator(), StandardCharsets.UTF_8);
                    }
                    out.println(encoded);
                    return 0;
                }
                case "--sign-isp-bootstrap" -> {
                    byte[] rootPriv = readKeyFile(Path.of(requireFlag(flags, "--root-key")));
                    String ispCnpj = requireFlag(flags, "--isp-cnpj");
                    String tradeName = requireFlag(flags, "--trade-name");
                    byte[] corpEdPub = readFlexibleKeyParam(requireFlag(flags, "--corp-ed25519-pub"));
                    byte[] corpXPub = readFlexibleKeyParam(requireFlag(flags, "--corp-x25519-pub"));
                    String nodeAddress = requireFlag(flags, "--node-address");
                    Path partnersJsonPath = Path.of(requireFlag(flags, "--partners-json"));
                    long nowMs = flags.containsKey("--now-ms")
                            ? Long.parseLong(flags.get("--now-ms"))
                            : System.currentTimeMillis();

                    List<PartnerBootstrapInput> partnerInputs = MAPPER.readValue(
                            Files.readString(partnersJsonPath, StandardCharsets.UTF_8),
                            new TypeReference<>() {
                            });

                    RegisterPartnerIspRequest req = signPartnerIspBootstrap(
                            rootPriv, ispCnpj, tradeName, corpEdPub, corpXPub, nodeAddress, partnerInputs, nowMs);
                    String encoded = Base64.getEncoder().encodeToString(req.toByteArray());
                    if (flags.containsKey("--out")) {
                        Files.writeString(Path.of(flags.get("--out")), encoded + System.lineSeparator(), StandardCharsets.UTF_8);
                    }
                    out.println(encoded);
                    return 0;
                }
                case "--decrypt-audit-entry" -> {
                    byte[] escrowPriv = readKeyFile(Path.of(requireFlag(flags, "--escrow-key")));
                    String entryJson = Files.readString(Path.of(requireFlag(flags, "--entry-json")), StandardCharsets.UTF_8);
                    AuditAccountabilityPayload decrypted = decryptAndVerifyAuditEntryJson(escrowPriv, entryJson);
                    String pretty = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(decrypted);
                    if (flags.containsKey("--out")) {
                        Files.writeString(Path.of(flags.get("--out")), pretty + System.lineSeparator(), StandardCharsets.UTF_8);
                    }
                    out.println(pretty);
                    return 0;
                }
                case "--verify-ledger-chain" -> {
                    String ledgerJson = Files.readString(Path.of(requireFlag(flags, "--ledger-json")), StandardCharsets.UTF_8);
                    int verifiedCount = verifyLedgerChainJson(ledgerJson);
                    out.println("Cadeia integra: " + verifiedCount + " registro(s) verificados com sucesso.");
                    return 0;
                }
                default -> {
                    err.println("Comando desconhecido: " + command);
                    return 2;
                }
            }
        } catch (Exception e) {
            err.println("ERRO: " + e.getMessage());
            return 1;
        }
    }

    public static RootKeyBundle initRootKeys(Path outDir) throws IOException {
        Files.createDirectories(outDir);
        RawKeyPair rootEd = CanonicalTranscripts.generateEd25519KeyPair();
        RawKeyPair escrowX = CanonicalTranscripts.generateX25519KeyPair();

        Path rootPubFile = outDir.resolve("root_ed25519.pub");
        Path rootKeyFile = outDir.resolve("root_ed25519.key");
        Path escrowPubFile = outDir.resolve("escrow_x25519.pub");
        Path escrowKeyFile = outDir.resolve("escrow_x25519.key");

        Files.writeString(rootPubFile, Base64.getEncoder().encodeToString(rootEd.publicKey()) + "\n", StandardCharsets.UTF_8);
        writePrivateKeyFile0600(rootKeyFile, Base64.getEncoder().encodeToString(rootEd.privateKey()));
        Files.writeString(escrowPubFile, Base64.getEncoder().encodeToString(escrowX.publicKey()) + "\n", StandardCharsets.UTF_8);
        writePrivateKeyFile0600(escrowKeyFile, Base64.getEncoder().encodeToString(escrowX.privateKey()));

        return new RootKeyBundle(rootPubFile, rootKeyFile, escrowPubFile, escrowKeyFile);
    }

    public static PrincipalSigningCert signPrincipalSigningCert(
            byte[] rootEd25519Priv,
            byte[] principalEd25519Pub,
            byte[] escrowX25519Pub,
            long issuedAtMs,
            int ttlDays) {
        if (ttlDays <= 0 || ttlDays > 366) {
            throw new IllegalArgumentException("ttlDays do PrincipalSigningCert deve estar entre 1 e 366 dias");
        }
        long expiresAtMs = issuedAtMs + Duration.ofDays(ttlDays).toMillis();
        PrincipalSigningCert unsigned = PrincipalSigningCert.newBuilder()
                .setCertId(CanonicalTranscripts.generateTemporalUuidV7().toString())
                .setPrincipalEd25519Pub(ByteString.copyFrom(principalEd25519Pub))
                .setEscrowX25519Pub(ByteString.copyFrom(escrowX25519Pub))
                .setIssuedAtMs(issuedAtMs)
                .setExpiresAtMs(expiresAtMs)
                .build();
        byte[] sig = CanonicalTranscripts.signEd25519(
                rootEd25519Priv,
                CanonicalTranscripts.buildPrincipalSigningCertTranscript(unsigned));
        return unsigned.toBuilder()
                .setRootSignature(ByteString.copyFrom(sig))
                .build();
    }

    public static RegisterPartnerIspRequest signPartnerIspBootstrap(
            byte[] rootEd25519Priv,
            String ispCnpj,
            String tradeName,
            byte[] corpRootEd25519Pub,
            byte[] corpRootX25519Pub,
            String nodeAddress,
            List<PartnerBootstrapInput> partnerInputs,
            long issuedAtMs) {
        if (partnerInputs == null || partnerInputs.isEmpty()) {
            throw new IllegalArgumentException("Pelo menos 1 socio administrador (managing_partners) e obrigatorio");
        }
        String normalizedCnpj = CanonicalTranscripts.normalizeAndValidateCnpj(ispCnpj);
        String corporateUuid = CanonicalTranscripts.deriveIdentityUuidV7(corpRootEd25519Pub).toString();

        List<PartnerSeedEntry> partners = new ArrayList<>(partnerInputs.size());
        for (PartnerBootstrapInput p : partnerInputs) {
            String normCpf = CanonicalTranscripts.normalizeAndValidateCpf(p.partnerCpf());
            byte[] pEd = PrincipalKeyMaterial.decodeFlexibleBytes(p.partnerEd25519PubBase64(), "partnerEd25519Pub");
            byte[] pX = PrincipalKeyMaterial.decodeFlexibleBytes(p.partnerX25519PubBase64(), "partnerX25519Pub");
            String pUuid = CanonicalTranscripts.deriveIdentityUuidV7(pEd).toString();
            partners.add(PartnerSeedEntry.newBuilder()
                    .setPartnerUuid(pUuid)
                    .setPartnerCpf(normCpf)
                    .setPartnerEd25519Pub(ByteString.copyFrom(pEd))
                    .setPartnerX25519Pub(ByteString.copyFrom(pX))
                    .build());
        }

        byte[] partnersDigest = CanonicalTranscripts.computePartnersDigestSha256(partners);
        byte[] transcript = CanonicalTranscripts.buildPartnerIspBootstrapTranscript(
                normalizedCnpj,
                tradeName.trim(),
                corporateUuid,
                corpRootEd25519Pub,
                corpRootX25519Pub,
                nodeAddress.trim(),
                partnersDigest,
                issuedAtMs);
        byte[] rootSig = CanonicalTranscripts.signEd25519(rootEd25519Priv, transcript);

        return RegisterPartnerIspRequest.newBuilder()
                .setIspCnpj(normalizedCnpj)
                .setTradeName(tradeName.trim())
                .setCorporateUuid(corporateUuid)
                .setCorpRootEd25519Pub(ByteString.copyFrom(corpRootEd25519Pub))
                .setCorpRootX25519Pub(ByteString.copyFrom(corpRootX25519Pub))
                .setNodeAddress(nodeAddress.trim())
                .addAllManagingPartners(partners)
                .setIssuedAtMs(issuedAtMs)
                .setRootSignature(ByteString.copyFrom(rootSig))
                .build();
    }

    public static AuditAccountabilityPayload decryptAndVerifyAuditEntry(
            byte[] escrowX25519Priv,
            LedgerEntryRecord entry) throws GeneralSecurityException, IOException {
        verifySingleEntryHash(entry);
        byte[] decryptedUtf8 = CanonicalTranscripts.openAuditEscrowBlob(
                escrowX25519Priv,
                entry.ephemeralX25519Pub(),
                entry.prevEntryHash(),
                entry.sealedAuditBlob());
        return MAPPER.readValue(decryptedUtf8, AuditAccountabilityPayload.class);
    }

    public static AuditAccountabilityPayload decryptAndVerifyAuditEntryJson(
            byte[] escrowX25519Priv,
            String entryJson) throws GeneralSecurityException, IOException {
        ExportedLedgerEntryJson parsed = MAPPER.readValue(entryJson, ExportedLedgerEntryJson.class);
        return decryptAndVerifyAuditEntry(escrowX25519Priv, parsed.toRecord());
    }

    public static int verifyLedgerChain(List<LedgerEntryRecord> orderedEntries) {
        if (orderedEntries == null || orderedEntries.isEmpty()) {
            return 0;
        }
        byte[] expectedPrevHash = AuditLedgerService.GENESIS_PREV_HASH;
        long expectedSeq = 1L;

        for (LedgerEntryRecord entry : orderedEntries) {
            if (entry.seq() != expectedSeq) {
                throw new SecurityException(
                        "Quebra de sequencia no ledger: esperado seq=" + expectedSeq + ", encontrado seq=" + entry.seq());
            }
            if (!MessageDigest.isEqual(expectedPrevHash, entry.prevEntryHash())) {
                throw new SecurityException(
                        "Adulteracao de prev_entry_hash detectada na linha seq=" + entry.seq());
            }
            verifySingleEntryHash(entry);
            expectedPrevHash = entry.entryHash();
            expectedSeq++;
        }
        return orderedEntries.size();
    }

    public static int verifyLedgerChainJson(String ledgerJson) throws IOException {
        List<ExportedLedgerEntryJson> parsed = MAPPER.readValue(ledgerJson, new TypeReference<>() {
        });
        List<LedgerEntryRecord> records = parsed.stream().map(ExportedLedgerEntryJson::toRecord).toList();
        return verifyLedgerChain(records);
    }

    private static void verifySingleEntryHash(LedgerEntryRecord entry) {
        byte[] canonicalRow = CanonicalTranscripts.buildAuditLedgerRowTranscript(
                entry.seq(),
                entry.entryId(),
                entry.actionType(),
                entry.targetUuid(),
                entry.docBlindIndex(),
                entry.ispCnpj(),
                entry.collaboratorUuid(),
                entry.ephemeralX25519Pub(),
                entry.sealedAuditBlob(),
                entry.createdAtEpochMs());
        byte[] recomputed = CanonicalTranscripts.computeLedgerEntryHash(entry.prevEntryHash(), canonicalRow);
        if (!MessageDigest.isEqual(recomputed, entry.entryHash())) {
            throw new SecurityException("Adulteracao criptografica detectada em entry_hash na linha seq=" + entry.seq());
        }
    }

    private static void writePrivateKeyFile0600(Path file, String base64Content) throws IOException {
        Files.writeString(file, base64Content + "\n", StandardCharsets.UTF_8);
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException ignored) {
            // Sistema de arquivos sem suporte POSIX (ex: Windows)
        }
    }

    private static byte[] readKeyFile(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8).trim();
        return PrincipalKeyMaterial.decodeFlexibleBytes(content, path.toString());
    }

    private static byte[] readFlexibleKeyParam(String valueOrFile) throws IOException {
        Path maybeFile = Path.of(valueOrFile);
        if (Files.exists(maybeFile) && Files.isRegularFile(maybeFile)) {
            return readKeyFile(maybeFile);
        }
        return PrincipalKeyMaterial.decodeFlexibleBytes(valueOrFile, "keyParam");
    }

    private static Map<String, String> parseFlags(String[] args, int startIdx) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = startIdx; i < args.length; i++) {
            String token = args[i];
            if (token.startsWith("--")) {
                int eq = token.indexOf('=');
                if (eq > 0) {
                    map.put(token.substring(0, eq), token.substring(eq + 1));
                } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    map.put(token, args[++i]);
                } else {
                    map.put(token, "true");
                }
            }
        }
        return map;
    }

    private static String requireFlag(Map<String, String> flags, String key) {
        String val = flags.get(key);
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("Parametro obrigatorio ausente: " + key);
        }
        return val.trim();
    }
}
