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

echo "Gerando Chave Privada do Nó RSA 4096 bits ($NODE_NAME.key)..."
rm -f "$NODE_NAME.key" "$NODE_NAME.csr" "$NODE_NAME.crt"
openssl genrsa -out "$NODE_NAME.key" 4096
chmod 400 "$NODE_NAME.key"

echo "Gerando Requisição de Assinatura ($NODE_NAME.csr)..."
openssl req -new -key "$NODE_NAME.key" -out "$NODE_NAME.csr" \
    -subj "/C=BR/ST=SP/L=Sao Paulo/O=ispChat Node/OU=Routing/CN=$NODE_NAME"

echo "Criando arquivo de extensão temporário para SAN e mTLS (serverAuth + clientAuth)..."
if [[ "$NODE_SAN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || [[ "$NODE_SAN" == *:* ]]; then
    SAN_ENTRY="IP.1 = $NODE_SAN"
else
    SAN_ENTRY="DNS.1 = $NODE_SAN"
fi

cat > extfile.cnf << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
extendedKeyUsage = serverAuth, clientAuth
subjectAltName = @alt_names

[alt_names]
$SAN_ENTRY
DNS.2 = localhost
IP.2 = 127.0.0.1
EOF

echo "Gerando Certificado Assinado do Nó ($NODE_NAME.crt)..."
openssl x509 -req -in "$NODE_NAME.csr" -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out "$NODE_NAME.crt" -days 365 -sha256 -extfile extfile.cnf

rm extfile.cnf

echo "Fazendo backup da chave e do certificado do nó para o iCloud..."
mkdir -p "$BACKUP_DIR"
rm -f "$BACKUP_DIR/$NODE_NAME.key" "$BACKUP_DIR/$NODE_NAME.crt"
cp -f "$NODE_NAME.key" "$BACKUP_DIR/"
cp -f "$NODE_NAME.crt" "$BACKUP_DIR/"

echo "Certificado mTLS do nó $NODE_NAME (4096 bits + serverAuth/clientAuth) gerado e assinado com sucesso."
