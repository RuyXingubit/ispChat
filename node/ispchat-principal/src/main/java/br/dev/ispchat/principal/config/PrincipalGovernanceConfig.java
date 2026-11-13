package br.dev.ispchat.principal.config;

import br.dev.ispchat.common.crypto.BlindIndexService;
import br.dev.ispchat.grpc.PrincipalSigningCert;
import br.dev.ispchat.node.security.CryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PrincipalGovernanceConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper principalObjectMapper() {
        return new ObjectMapper();
    }

    @Bean(destroyMethod = "zeroize")
    @ConditionalOnMissingBean
    public PrincipalKeyMaterial principalKeyMaterial(
            @Value("${ispchat.root.public-key}") String rootPubEncoded,
            @Value("${ispchat.principal.signing-private-key}") String principalPrivEncoded,
            @Value("${ispchat.principal.signing-cert-base64}") String principalCertBase64,
            CryptoService cryptoService) throws InvalidProtocolBufferException {
        byte[] rootPub = PrincipalKeyMaterial.decodeFlexibleBytes(rootPubEncoded, "ispchat.root.public-key");
        byte[] principalPriv = PrincipalKeyMaterial.decodeFlexibleBytes(
                principalPrivEncoded, "ispchat.principal.signing-private-key");
        byte[] certBytes = PrincipalKeyMaterial.decodeFlexibleBytes(
                principalCertBase64, "ispchat.principal.signing-cert-base64");
        PrincipalSigningCert cert = PrincipalSigningCert.parseFrom(certBytes);

        PrincipalKeyMaterial keyMaterial = new PrincipalKeyMaterial(rootPub, principalPriv, cert);
        cryptoService.registerTrustedPrincipalPublicKey(
                keyMaterial.principalEd25519Pub(),
                cert.getExpiresAtMs());
        return keyMaterial;
    }

    @Bean
    @ConditionalOnMissingBean
    public BlindIndexService blindIndexService(
            @Value("${ispchat.principal.blind-index-pepper}") String pepperEncoded,
            @Value("${ispchat.principal.argon2-memory-kib:65536}") int memoryKiB,
            @Value("${ispchat.principal.argon2-iterations:3}") int iterations,
            @Value("${ispchat.principal.argon2-concurrency:4}") int concurrency) {
        byte[] pepper = PrincipalKeyMaterial.decodeFlexibleBytes(
                pepperEncoded, "ispchat.principal.blind-index-pepper");
        return new BlindIndexService(pepper, memoryKiB, iterations, concurrency);
    }
}
