#!/bin/bash
set -e

if [ "$#" -ne 2 ]; then
    echo "Uso: $0 <node_name> <node_ip_or_domain>"
    echo "Exemplo: $0 node1 192.168.0.100"
    exit 1
fi

NODE_NAME=$1
NODE_SAN=$2
BACKUP_DIR="/Users/ruy/Library/Mobile Documents/com~apple~CloudDocs/XinguBit/Autoridade CA backup"

if [ ! -f "ca.key" ] || [ ! -f "ca.crt" ]; then
    echo "Erro: ca.key ou ca.crt não encontrados. Rode init_ca.sh primeiro."
    exit 1
fi

echo "Gerando Chave Privada do Nó ($NODE_NAME.key)..."
openssl genrsa -out $NODE_NAME.key 2048
chmod 400 $NODE_NAME.key

echo "Gerando Requisição de Assinatura ($NODE_NAME.csr)..."
openssl req -new -key $NODE_NAME.key -out $NODE_NAME.csr \
    -subj "/C=BR/ST=SP/L=Sao Paulo/O=ispChat Node/OU=Routing/CN=$NODE_NAME"

echo "Criando arquivo de extensão temporário para SAN..."
cat > extfile.cnf << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
subjectAltName = @alt_names

[alt_names]
IP.1 = $NODE_SAN
DNS.1 = $NODE_SAN
EOF

echo "Gerando Certificado Assinado do Nó ($NODE_NAME.crt)..."
# Ignora erro de IP mal formatado caso o SAN seja um domínio
openssl x509 -req -in $NODE_NAME.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out $NODE_NAME.crt -days 365 -sha256 -extfile extfile.cnf || \
    (sed -i '' 's/IP.1/DNS.2/g' extfile.cnf && openssl x509 -req -in $NODE_NAME.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out $NODE_NAME.crt -days 365 -sha256 -extfile extfile.cnf)


rm extfile.cnf

echo "Fazendo backup da chave e do certificado do nó para o iCloud..."
mkdir -p "$BACKUP_DIR"
cp $NODE_NAME.key "$BACKUP_DIR/"
cp $NODE_NAME.crt "$BACKUP_DIR/"

echo "Certificado do nó $NODE_NAME gerado e assinado com sucesso."
