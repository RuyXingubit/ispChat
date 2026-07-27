

---

## **Documentação de Arquitetura e Requisitos \- Projeto: ISP Chat (MVP)**

**Visão Geral do Projeto**

O ISP Chat é um ecossistema de comunicação descentralizado construído para Provedores de Internet (ISPs). O objetivo é criar uma rede onde cada provedor hospeda e gerencia seu próprio nó (em uma VPS), rodando um conjunto de microsserviços. O sistema prioriza o anonimato e a privacidade dos usuários finais, mas implementa um mecanismo de consenso rigoroso para permitir a quebra de sigilo e rastreamento exclusivamente em casos de crimes graves, mediante ordem judicial.

### **1\. Infraestrutura e Descoberta de Serviços**

* **Modelo de Arquitetura:** Microsserviços descentralizados e autônomos. Não há um servidor central controlando todo o ecossistema; cada ISP faz parte de uma rede par-a-par (P2P) de provedores.  
* **Service Discovery e Roteamento:** Para substituir soluções centralizadas (como o Netflix Eureka), o sistema utilizará o protocolo **xDS**. No backend, construído em Java, será utilizada a biblioteca io.grpc.xds para gerenciar a descoberta de serviços e o balanceamento de carga de forma dinâmica.  
* **Comunicação:** A comunicação interna entre os microsserviços e a comunicação externa entre os nós de diferentes provedores será feita utilizando **gRPC**.

### **2\. Fluxo de Dados e Mensageria**

* **Redis (Baixa Latência e Estado):** Será utilizado como banco de dados em memória para gerenciar operações que exigem resposta imediata (microssegundos). Ficará responsável pelo controle de sessões e pelo status de presença dos usuários (online/offline).  
* **Apache Kafka (Streaming de Eventos):** Cada provedor rodará o seu próprio cluster Kafka. Ele será o responsável pelo processamento do fluxo pesado de mensagens e eventos, garantindo a entrega confiável e ordenada.  
* **Interligação de Clusters:** Para que os usuários de um ISP conversem com os de outro ISP, os clusters Kafka individuais se comunicarão utilizando pontes desenvolvidas via gRPC (ou soluções como Kafka MirrorMaker), garantindo que os dados transitem de forma fluida sem centralizar o serviço.

### **3\. Segurança, Privacidade e Governança**

* **Criptografia de Limiar (Threshold Cryptography):** Para resolver o dilema entre anonimato e rastreabilidade legal, o sistema utilizará um modelo de segurança descentralizado. A chave de descriptografia de ponta a ponta é dividida em várias frações, que são distribuídas entre os ISPs participantes.  
* **Sistema de Quórum e Votação Anônima:** Nenhum provedor sozinho tem o poder de quebrar o sigilo de um usuário. Em caso de ordem judicial ou emergência (ex: sequestro), os ISPs realizam uma votação anônima.  
* **Rastreamento Controlado:** Apenas se o quórum mínimo definido pelo ecossistema for atingido, as partes da chave se unem, permitindo descriptografar e revelar dados cruciais do alvo, como CPF, último endereço e as coordenadas geográficas da última ligação.

### **4\. Cliente Mobile e Comunicação P2P**

* **Frontend:** O aplicativo para o usuário final será desenvolvido utilizando o framework **Flutter**.  
* **Comunicação de Áudio/Vídeo:** As chamadas de voz e vídeo ocorrerão de forma direta entre os usuários finais via **WebRTC**.  
* **Servidor STUN:** Como muitos usuários ainda não possuem IPv6 e estão atrás de NAT (CGNAT, comum em provedores de internet), o ecossistema contará com servidores **STUN**. Eles serão essenciais para que os aplicativos Flutter descubram seus endereços IP públicos e consigam estabelecer as conexões ponto a ponto (P2P) com sucesso.

---

**Instruções para o Desenvolvimento (Antigravity):**

A partir deste documento, precisamos planejar o desenvolvimento do MVP (Minimum Viable Product). Devemos iniciar quebrando a implementação em fases lógicas: configuração da infraestrutura base, setup da comunicação gRPC/xDS entre dois nós de teste, integração do Kafka/Redis e, posteriormente, a camada de segurança com a criptografia de limiar.

---

Pode copiar esse bloco inteiro e mandar para ele\! Se precisar adicionar mais algum detalhe ou ajustar algum termo antes de enviar, é só falar.

