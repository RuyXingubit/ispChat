#!/bin/bash
# Test script to insert a zone via API and resolve it

# Carrega as variáveis de ambiente do arquivo .env
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
else
  echo "Arquivo .env não encontrado. Copie .env.example para .env e preencha as senhas."
  exit 1
fi

API_KEY="${PDNS_API_KEY}"
API_URL="http://127.0.0.1:8081/api/v1/servers/localhost/zones"

echo "1. Criando a zona ispchat.dev.br no PowerDNS..."
curl -X POST "$API_URL" \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ispchat.dev.br.",
    "kind": "Native",
    "masters": [],
    "nameservers": ["ns1.ispchat.dev.br.", "ns2.ispchat.dev.br."]
}'

echo ""
echo "2. Adicionando o registro A para ns1.ispchat.dev.br apontando para 127.0.0.1..."
curl -X PATCH "http://127.0.0.1:8081/api/v1/servers/localhost/zones/ispchat.dev.br." \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "rrsets": [
      {
        "name": "ns1.ispchat.dev.br.",
        "type": "A",
        "ttl": 3600,
        "changetype": "REPLACE",
        "records": [
          {
            "content": "127.0.0.1",
            "disabled": false
          }
        ]
      }
    ]
}'

echo ""
echo "3. Testando a resolução via DIG..."
dig @127.0.0.1 ns1.ispchat.dev.br
