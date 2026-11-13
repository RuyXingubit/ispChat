package br.dev.ispchat.principal;

import br.dev.ispchat.principal.cli.OfflineGovernanceCli;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicacao Principal de Governanca e Fallback Home Node (ispchat-principal).
 *
 * Escaneia tanto br.dev.ispchat.node quanto br.dev.ispchat.principal para que o GrpcServerRunner
 * exponha 100% dos servicos de roteamento/DHT/TURN do Fallback Home Node junto aos servicos
 * primarios PrincipalGovernanceGrpcService e DirectoryGrpcService (substituindo os proxies
 * @ConditionalOnMissingBean de borda).
 */
@SpringBootApplication(scanBasePackages = {"br.dev.ispchat.node", "br.dev.ispchat.principal"})
public class PrincipalApplication {

    public static void main(String[] args) {
        if (OfflineGovernanceCli.isOfflineCliInvocation(args)) {
            int exitCode = OfflineGovernanceCli.runCli(args, System.out, System.err);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
            return;
        }
        SpringApplication.run(PrincipalApplication.class, args);
    }
}
