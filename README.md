# 🌐 ISP Chat

Bem-vindo ao **ISP Chat** — Uma rede de comunicação descentralizada, anti-censura e mantida exclusivamente por Provedores de Internet (ISPs).

> **A Internet de volta para as mãos de quem a constrói.**
> O ISP Chat é um ecossistema P2P (Ponto a Ponto) de código aberto que permite comunicação de áudio, vídeo e texto com privacidade absoluta, projetado para sobreviver a perseguições estatais e sem depender de servidores centrais.

---

## 🚀 Visão Geral e Filosofia
Em vez de todos os usuários do mundo se conectarem a um servidor central, no ISP Chat, **cada provedor de internet hospeda um nó da rede**. Os provedores se comunicam entre si através de uma rede distribuída e permissionada no nível de infraestrutura.

### 🛡️ Privacidade e Identidade Auto-Soberana
- **Sem senhas (UUIDv7 + Criptografia):** Sua identidade é um `UUIDv7` matematicamente atrelado a Chaves Criptográficas locais.
- **Frase Semente:** O backup da conta é feito por uma Mnemônica de 12 palavras (padrão BIP39).
- **Sem Aliases Globais:** Para evitar spoofing (falsidade ideológica), identidades são trocadas fisicamente via QR Code ou Link Mágico.
- **Criptografia E2E Total:** Chamadas (WebRTC) e Mensagens de Texto são 100% criptografadas de ponta a ponta. O Provedor roteia apenas lixo criptográfico.

### ⚔️ Resiliência Extrema e Anti-Censura
Projetado como uma arquitetura de trincheira para sobreviver a bloqueios judiciais ou de trânsito (BGP Blackholing):
1. **Fallback de Onboarding:** Se o provedor do usuário não possuir um nó, o App acha automaticamente o nó público mais próximo via Ping.
2. **Hardcoded IPs e Links Sociais:** Ignora o bloqueio nacional de DNS (`ispchat.dev.br`) conectando direto nos IPs.
3. **Domain Fronting e Rotação de IP:** Escudos contra bloqueio em massa da infraestrutura.
4. **Modo Doomsday (Zero-Infra):** Se todos os servidores caírem, os aparelhos fecham túneis P2P diretos na internet aproveitando a ausência de NAT do **IPv6**, ou via Mesh Local (Bluetooth/Wi-Fi).

## 🏗️ Arquitetura Técnica
A stack é baseada em performance nativa e descentralização validada:
*   **Rede DHT Kademlia:** "Diretório global" distribuído onde toda atualização exige Assinatura Digital do cliente (Anti-Spoofing).
*   **Segurança de Infraestrutura (mTLS + CA):** Conexões gRPC entre provedores exigem um Certificado Digital assinado pela Autoridade Certificadora do ispChat, bloqueando sumariamente invasores e nós falsos.
*   **Sinalização (Backend Java):** Microsserviços de roteamento local via `io.grpc.xds`.
*   **Estado e Mensageria (Redis):** Fila Offline (Store and Forward) e Broadcast de Toque Multi-dispositivos.
*   **Aplicativo (Frontend Flutter):** App ultra leve que encarcera a Chave Privada do usuário.

## 📖 Documentação Detalhada
Consulte a pasta `/docs` para mergulhar nos pormenores técnicos da arquitetura P2P:
- [PRD (Requisitos e User Stories)](docs/PRD_MVP.md)
- [Tech Spec (Especificação Técnica de Redes)](docs/Tech_Spec_MVP.md)
- [QA Plan (Testes e Qualidade)](docs/QA_Strategy_MVP.md)

## ⚖️ Licença de Código Aberto
O Frontend (App Flutter) é licenciado sob a **GPLv3**.
O Backend (Nós Java/Redis) é estritamente licenciado sob a **AGPLv3 (Affero GPL)**. 
A licença AGPLv3 garante que qualquer entidade (provedor) que modifique o código do servidor para uso na rede seja legalmente obrigada a publicar suas modificações de código aberto, protegendo a rede contra software espião corporativo.

---
*Segurança em primeiro lugar. Inquebrável por design.*
