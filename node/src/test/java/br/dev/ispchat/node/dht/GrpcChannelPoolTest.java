package br.dev.ispchat.node.dht;

import br.dev.ispchat.node.grpc.GrpcChannelPool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrpcChannelPoolTest {

    @Test
    @DisplayName("Deve bloquear enderecos internos/loopback e portas diferentes de 443 em modo producao (Anti-SSRF)")
    void shouldBlockInternalIpsAndNon443PortsInProductionMode() {
        GrpcChannelPool prodPool = new GrpcChannelPool(
                false, "../ca/ca.crt", "../ca/dev-node.crt", "../ca/dev-node.key");

        assertThrows(SecurityException.class, () -> prodPool.validateAndParseTarget("127.0.0.1"),
                "Deve bloquear loopback IPv4 em producao");
        assertThrows(SecurityException.class, () -> prodPool.validateAndParseTarget("localhost"),
                "Deve bloquear localhost em producao");
        assertThrows(SecurityException.class, () -> prodPool.validateAndParseTarget("192.168.1.10"),
                "Deve bloquear rede privada RFC 1918 (192.168.x.x) em producao");
        assertThrows(SecurityException.class, () -> prodPool.validateAndParseTarget("10.0.0.5"),
                "Deve bloquear rede privada RFC 1918 (10.x.x.x) em producao");
        assertThrows(SecurityException.class, () -> prodPool.validateAndParseTarget("172.16.0.1"),
                "Deve bloquear rede privada RFC 1918 (172.16.x.x) em producao");
        assertThrows(SecurityException.class, () -> prodPool.validateAndParseTarget("[::1]:443"),
                "Deve bloquear loopback IPv6 em producao");
        assertThrows(SecurityException.class, () -> prodPool.validateAndParseTarget("1.1.1.1:8443"),
                "Deve bloquear portas diferentes de 443 em producao");
    }

    @Test
    @DisplayName("Deve aceitar IP publico roteavel na porta 443 em modo producao")
    void shouldAllowPublicRoutableIpOnPort443InProductionMode() {
        GrpcChannelPool prodPool = new GrpcChannelPool(
                false, "../ca/ca.crt", "../ca/dev-node.crt", "../ca/dev-node.key");

        GrpcChannelPool.HostAndPort target = prodPool.validateAndParseTarget("1.1.1.1");
        assertEquals("1.1.1.1", target.host());
        assertEquals(443, target.port());
    }
}
