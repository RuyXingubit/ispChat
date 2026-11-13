# PRD — ispChat (Governança Federada, Dual-Tier & Mensageria P2P E2EE)

## 1. Problema & Visão de Produto

O **ispChat** combina soberania criptográfica ponta a ponta com interoperabilidade federada entre Provedores de Serviços de Internet (ISPs), permitindo comunicação direta P2P entre usuários anônimos/livres e empresas ou assinantes verificados sem expor chaves privadas ou documentos pessoais em claro na rede.

## 2. Personas e Jornadas Principais (CUJs)

1. **Usuário Livre (Não Verificado)**: gera identidade BIP39 offline, conecta via DoH SRV (`_ispchat._tcp.ispchat.dev.br`) ou semente social, conversa via `MultiDeviceEncryptedPayload` e faz chamadas WebRTC sem entregar CPF/CNPJ.
2. **Assinante ISP (CPF Verificado Privado)**: gera QR de consentimento assinado (`ISPCHAT-VERIFY-REQ-V1`) em loja/atendimento, recebe `VerificationCredential` (sem jamais publicar o CPF na rede) com selo verificável e Home Node dedicado.
3. **Empresa / ISP (CNPJ Verificado Público)**: valida CNPJ numérico ou alfanumérico (IN RFB 2229/2024), aparece no Diretório Corporativo (`SearchVerifiedCnpj`) e opera múltiplas sessões delegadas (incluindo ERP via `ispchat-erp-bridge`).
4. **Pareamento Multi-Dispositivo (Desktop / WebView / ERP)**: aparelho secundário exibe QR `ispchat://pair-req?...`; o celular Root assina `DeviceDelegationCert` sem nunca exportar a frase mnemônica de 12 palavras; dispositivos comprometidos podem ser revogados individualmente (`RevokeDevice`).
5. **Rebind de CPF e Portabilidade de Provedor**: usuário que perdeu o dispositivo Root re-vincula o CPF presencialmente via colaborador autorizado (`SubmitCpfRebind` -> `IdentityReplacementNotice`) ou migra seu Home Node entre ISPs mantendo sua identidade (`PortAccount`).

## 3. Requisitos Não-Funcionais Inegociáveis

- **Zero Dados Falsos na UI**: estados vazios limpos e neutros enquanto não houver conexão/busca real.
- **Tolerância Zero a Quebras de Layout**: compatibilidade verificada em `320x568` (mobile compacto) e `1024x768` (desktop).
- **Segurança Fail-Closed**: chaves públicas Root injetadas via `--dart-define=ISPCHAT_ROOT_PUBKEY_B64`; credenciais e delegações não verificadas são rejeitadas antes da descriptografia ou persistência de contato.
- **UUIDv7 Universal**: todos os identificadores temporais e determinísticos de identidade seguem RFC 9562 v7.
