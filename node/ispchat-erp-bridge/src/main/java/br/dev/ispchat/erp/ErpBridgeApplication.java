package br.dev.ispchat.erp;

import br.dev.ispchat.common.crypto.CanonicalTranscripts;
import br.dev.ispchat.common.crypto.CanonicalTranscripts.RawKeyPair;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

/**
 * Sidecar HTTP-to-gRPC E2E para integracao de ERPs de Provedores (IXC, SGP, MK-Auth)
 * rodando na LAN interna do ISP (127.0.0.1:8090 por padrao).
 *
 * Suporta --print-pairing-uri para gerar (ou ler) o par de subchaves ERP_API e exibir
 * a URI ispchat://pair?... para pareamento no celular raiz do Socio/Admin do Provedor.
 */
@SpringBootApplication
public class ErpBridgeApplication {

    @Value("${server.address:127.0.0.1}")
    private String bindAddress;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${server.ssl.client-auth:none}")
    private String sslClientAuth;

    public static void main(String[] args) throws IOException {
        if (hasFlag(args, "--print-pairing-uri")) {
            Path keystoreDir = resolveKeystoreDirArg(args);
            String uri = generateOrLoadPairingUri(keystoreDir, System.currentTimeMillis(), System.out);
            System.out.println(uri);
            return;
        }
        SpringApplication.run(ErpBridgeApplication.class, args);
    }

    @PostConstruct
    public void verifyLanBindBoundary() {
        validateLanBindAddress(bindAddress, sslEnabled, sslClientAuth);
    }

    /**
     * Garante que o sidecar HTTP nunca exponha a API sem mTLS fora de loopback / RFC 1918 / ULA IPv6.
     */
    public static void validateLanBindAddress(String rawBindAddress, boolean sslEnabled, String sslClientAuth) {
        if (sslEnabled && "need".equalsIgnoreCase(sslClientAuth != null ? sslClientAuth.trim() : "")) {
            return;
        }
        if (rawBindAddress == null || rawBindAddress.isBlank()) {
            throw new SecurityException(
                    "Recusado bind em wildcard (0.0.0.0/::) sem mTLS (server.ssl.client-auth=need). Defina server.address=127.0.0.1 ou IP RFC 1918.");
        }
        try {
            InetAddress addr = InetAddress.getByName(rawBindAddress.trim());
            if (addr.isAnyLocalAddress()) {
                throw new SecurityException(
                        "Recusado bind em wildcard (" + rawBindAddress + ") sem mTLS. Use loopback ou RFC 1918.");
            }
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || isIpv6UniqueLocalAddress(addr)) {
                return;
            }
            throw new SecurityException(
                    "Recusado bind em IP publico (" + rawBindAddress + ") sem mTLS (server.ssl.client-auth=need).");
        } catch (UnknownHostException e) {
            throw new SecurityException("Endereco de bind invalido: " + rawBindAddress, e);
        }
    }

    private static boolean isIpv6UniqueLocalAddress(InetAddress addr) {
        if (addr instanceof Inet6Address ipv6) {
            byte[] bytes = ipv6.getAddress();
            return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC; // fc00::/7
        }
        return false;
    }

    public static String generateOrLoadPairingUri(Path keystoreDir, long timestampMs, PrintStream infoOut)
            throws IOException {
        Files.createDirectories(keystoreDir);
        Path edPrivFile = keystoreDir.resolve("erp_device_ed25519.key");
        Path edPubFile = keystoreDir.resolve("erp_device_ed25519.pub");
        Path xPrivFile = keystoreDir.resolve("erp_device_x25519.key");
        Path xPubFile = keystoreDir.resolve("erp_device_x25519.pub");

        byte[] edPub;
        byte[] xPub;

        if (Files.exists(edPrivFile) && Files.exists(edPubFile)
                && Files.exists(xPrivFile) && Files.exists(xPubFile)) {
            edPub = Base64.getDecoder().decode(Files.readString(edPubFile, StandardCharsets.UTF_8).trim());
            xPub = Base64.getDecoder().decode(Files.readString(xPubFile, StandardCharsets.UTF_8).trim());
        } else {
            RawKeyPair edPair = CanonicalTranscripts.generateEd25519KeyPair();
            RawKeyPair xPair = CanonicalTranscripts.generateX25519KeyPair();
            edPub = edPair.publicKey();
            xPub = xPair.publicKey();

            writeSecret0600(edPrivFile, Base64.getEncoder().encodeToString(edPair.privateKey()));
            Files.writeString(edPubFile, Base64.getEncoder().encodeToString(edPub) + "\n", StandardCharsets.UTF_8);
            writeSecret0600(xPrivFile, Base64.getEncoder().encodeToString(xPair.privateKey()));
            Files.writeString(xPubFile, Base64.getEncoder().encodeToString(xPub) + "\n", StandardCharsets.UTF_8);
            if (infoOut != null) {
                infoOut.println("# Subchaves ERP_API geradas em: " + keystoreDir.toAbsolutePath());
            }
        }

        String deviceId = CanonicalTranscripts.deriveIdentityUuidV7(edPub).toString();
        Base64.Encoder b64Url = Base64.getUrlEncoder().withoutPadding();
        return "ispchat://pair?device_id=" + deviceId
                + "&ed25519=" + b64Url.encodeToString(edPub)
                + "&x25519=" + b64Url.encodeToString(xPub)
                + "&role=ERP_API"
                + "&ts=" + timestampMs;
    }

    private static void writeSecret0600(Path file, String content) throws IOException {
        Files.writeString(file, content + "\n", StandardCharsets.UTF_8);
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (flag.equals(arg != null ? arg.trim() : "")) {
                return true;
            }
        }
        return false;
    }

    private static Path resolveKeystoreDirArg(String[] args) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String token = args[i];
                if (token.startsWith("--keystore-dir=")) {
                    return Path.of(token.substring("--keystore-dir=".length()).trim());
                }
                if ("--keystore-dir".equals(token) && i + 1 < args.length) {
                    return Path.of(args[i + 1].trim());
                }
            }
        }
        return Path.of("./erp-keystore");
    }
}
