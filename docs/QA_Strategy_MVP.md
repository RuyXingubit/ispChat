# Estratégia de Qualidade (QA Plan) - ISP Chat MVP

## 1. Diretriz Global de Qualidade (Regra de Ouro)
> **"Segurança em primeiro lugar."** e **"Sempre que uma feature for criada, faz um teste unitário (cobertura de testes) para certificar que alterações futuras não estão quebrando as passadas."**

Esta regra definida pela Liderança Técnica é a base inegociável do desenvolvimento. Nenhuma feature é aceita sem a devida cobertura de testes automatizados.

## 2. Níveis de Teste

### 2.1. Testes Unitários (Obrigatórios)
- **Criptografia e Identidade (App/Backend):**
  - Geração correta da frase de 12 palavras (BIP39) e derivação da Chave Privada/Pública.
  - Testes matemáticos: Garantir que a encriptação/decriptação de mensagens de texto com Chave Pública/Privada funcione sem falhas.
- **Backend (Java):** 
  - **Segurança DHT:** Testes rigorosos na verificação de **Assinatura Digital** nos payloads da DHT (garantir que atualizações de IP forjadas sejam barradas se a assinatura não bater com o UUID).
  - **Segurança de Infraestrutura:** Testes no validador do mTLS (simular uma conexão gRPC com um certificado não assinado pela Autoridade Certificadora do ispChat e garantir a rejeição imediata da conexão).
- **Frontend (Flutter):**
  - Testes na lógica do "Ping Fallback": Garantir que a matriz de IPs públicos/DNS seja percorrida e o nó com a menor latência seja eleito.
  - Testes na **Blocklist Local**: Garantir que o evento de recebimento de Ring (Chamada) gRPC seja ignorado silenciosamente se o UUID do remetente estiver no SQLite de bloqueados.
  - Validação da geração do Modo Doomsday (geração do QR Code com o endereço IPv6).

### 2.2. Testes de Integração
- **Fila Offline (Store and Forward):** 
  - Subir uma instância Java + Redis local. Simular o envio de texto E2E para um UUID marcado como Offline. Validar se a mensagem entra corretamente na `Redis List`. Em seguida, alterar o status do UUID para Online na DHT e verificar o esvaziamento imediato da fila.
- **Broadcast Multi-dispositivo (Redis Pub/Sub):**
  - Inserir na DHT um array de 3 sessões/aparelhos ativos para o mesmo UUID. Disparar uma chamada gRPC e verificar se o nó Java e o Redis executam o fan-out paralelo para as 3 rotas.

### 2.3. Testes End-to-End (E2E)
- Validação manual e automatizada do fluxo de ponta a ponta:
  1. Conexão mTLS validada entre o Nó A e o Nó B.
  2. Aparelho 1 criptografa o texto e envia. Aparelho 2 recebe, descriptografa com sua Chave Privada e lê. (Deve-se auditar pacotes com Wireshark para garantir que o payload trafegado no Nó Java seja indecifrável).
  3. Desligar a rede e religar para atestar a conexão direta IPv6 (P2P Puro) com o túnel WebRTC sem servidor STUN.

## 3. Critérios de Aceite para o MVP
- A suíte passa em 100% dos testes criptográficos (geração de chave, mTLS e assinatura da DHT).
- A infraestrutura Java rejeita conexões de roteamento vindo de IPs desconhecidos/sem certificado CA do ispChat (anti-Sybil).
- Dois clientes completam o ciclo WebRTC roteando a sinalização através de dois nós ISPs autônomos.
- Mensagens enviadas a aparelhos sem internet são entregues 100% das vezes quando a rede é restaurada.
