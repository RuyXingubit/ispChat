package br.dev.ispchat.common.crypto;

import br.dev.ispchat.grpc.DocumentType;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Memory-hard document blind indexer protecting the finite 10^9 Brazilian CPF keyspace against
 * offline GPU brute-force enumeration in the event of a Principal database + pepper compromise.
 *
 * Formula:
 *   doc_blind_index = HMAC-SHA256(
 *       principal_pepper,
 *       Argon2id(
 *           input = UTF8("ISPCHAT-DOC-V1:" || doc_type || ":" || normalized_doc),
 *           salt  = SHA256(principal_pepper || UTF8(":blind-salt")),
 *           m     = 65536 KiB (64 MiB),
 *           t     = 3,
 *           p     = 1
 *       )
 *   )
 */
public final class BlindIndexService {

    public static final int MIN_PEPPER_LENGTH_BYTES = 32;
    public static final int DEFAULT_MEMORY_KIB = 65_536; // 64 MiB
    public static final int DEFAULT_ITERATIONS = 3;
    public static final int DEFAULT_MAX_CONCURRENCY = 4;
    public static final int BLIND_INDEX_LENGTH_BYTES = 32;

    private static final String DOC_DOMAIN_PREFIX = "ISPCHAT-DOC-V1:";
    private static final byte[] SALT_SUFFIX = ":blind-salt".getBytes(StandardCharsets.UTF_8);

    private final byte[] principalPepper;
    private final byte[] derivedSalt;
    private final int memoryKiB;
    private final int iterations;
    private final Semaphore concurrencyLimiter;

    public BlindIndexService(byte[] principalPepper) {
        this(principalPepper, DEFAULT_MEMORY_KIB, DEFAULT_ITERATIONS, DEFAULT_MAX_CONCURRENCY);
    }

    public BlindIndexService(byte[] principalPepper, int memoryKiB, int iterations, int maxConcurrency) {
        Objects.requireNonNull(principalPepper, "principalPepper cannot be null");
        if (principalPepper.length < MIN_PEPPER_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "principalPepper must be at least " + MIN_PEPPER_LENGTH_BYTES + " bytes");
        }
        if (memoryKiB < 1024) {
            throw new IllegalArgumentException("memoryKiB must be at least 1024 KiB");
        }
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be >= 1");
        }
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be >= 1");
        }
        this.principalPepper = principalPepper.clone();
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.concurrencyLimiter = new Semaphore(maxConcurrency, true);

        ByteBuffer saltInput = ByteBuffer.allocate(this.principalPepper.length + SALT_SUFFIX.length);
        saltInput.put(this.principalPepper);
        saltInput.put(SALT_SUFFIX);
        this.derivedSalt = CanonicalTranscripts.sha256(saltInput.array());
    }

    /**
     * Normalizes and validates rawDocument for docType, then computes the 32-byte Argon2id + HMAC-SHA256 blind index.
     */
    public byte[] computeBlindIndex(DocumentType docType, String rawDocument) {
        String normalizedDoc = CanonicalTranscripts.normalizeAndValidateDocument(docType, rawDocument);
        byte[] inputBytes = (DOC_DOMAIN_PREFIX + docType.name() + ":" + normalizedDoc)
                .getBytes(StandardCharsets.UTF_8);

        boolean acquired = false;
        byte[] argon2Output = new byte[BLIND_INDEX_LENGTH_BYTES];
        try {
            concurrencyLimiter.acquire();
            acquired = true;

            Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withMemoryAsKB(memoryKiB)
                    .withIterations(iterations)
                    .withParallelism(1)
                    .withSalt(derivedSalt)
                    .build();

            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(params);
            generator.generateBytes(inputBytes, argon2Output, 0, argon2Output.length);
            return CanonicalTranscripts.hmacSha256(principalPepper, argon2Output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while computing Argon2id blind index", e);
        } finally {
            Arrays.fill(inputBytes, (byte) 0);
            Arrays.fill(argon2Output, (byte) 0);
            if (acquired) {
                concurrencyLimiter.release();
            }
        }
    }
}
