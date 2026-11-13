package br.dev.ispchat.node.governance;

import br.dev.ispchat.grpc.DirectoryServiceGrpc;
import br.dev.ispchat.grpc.PrincipalGovernanceServiceGrpc;
import br.dev.ispchat.node.dht.DhtStoreService;
import br.dev.ispchat.node.grpc.GrpcChannelPool;
import br.dev.ispchat.node.security.CryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Auto-configuracao avaliada estritamente APOS o @ComponentScan da aplicacao:
 * - No modulo :ispchat-node, registra os proxies gRPC de borda para governanca e diretorio CNPJ.
 * - No modulo :ispchat-principal, detecta automaticamente os beans primarios (PrincipalGovernanceGrpcService
 *   e DirectoryGrpcService) e omite os proxies sem conflito de registro no GrpcServerRunner.
 */
@AutoConfiguration
@ConditionalOnBean(GrpcChannelPool.class)
public class EdgeProxyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PrincipalGovernanceServiceGrpc.PrincipalGovernanceServiceImplBase.class)
    public EdgeGovernanceProxyGrpcService edgeGovernanceProxyGrpcService(
            GrpcChannelPool channelPool,
            DhtStoreService dhtStoreService,
            @Value("${ispchat.principal.address:}") String principalAddress,
            @Value("${ispchat.node.public-address:}") String localPublicAddress) {
        return new EdgeGovernanceProxyGrpcService(
                channelPool,
                dhtStoreService,
                principalAddress,
                localPublicAddress);
    }

    @Bean
    @ConditionalOnMissingBean(DirectoryServiceGrpc.DirectoryServiceImplBase.class)
    public EdgeDirectoryProxyGrpcService edgeDirectoryProxyGrpcService(
            GrpcChannelPool channelPool,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Value("${ispchat.principal.address:}") String principalAddress,
            CryptoService cryptoService) {
        return new EdgeDirectoryProxyGrpcService(
                channelPool,
                redisTemplate,
                principalAddress,
                cryptoService.getClock());
    }
}
