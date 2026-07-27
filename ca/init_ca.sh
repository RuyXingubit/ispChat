#!/bin/bash
set -e

BACKUP_DIR="/Users/ruy/Library/Mobile Documents/com~apple~CloudDocs/XinguBit/Autoridade CA backup"

echo "Criando diretório de backup no iCloud: $BACKUP_DIR"
mkdir -p "$BACKUP_DIR"

echo "Gerando Chave Privada da CA (ca.key)..."
openssl genrsa -out ca.key 4096
chmod 400 ca.key

echo "Gerando Certificado Raiz da CA (ca.crt)..."
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 -out ca.crt \
    -subj "/C=BR/ST=SP/L=Sao Paulo/O=ispChat/OU=Engineering/CN=ispChat Root CA"

echo "Copiando chaves e certificados para o backup no iCloud..."
cp ca.key "$BACKUP_DIR/"
cp ca.crt "$BACKUP_DIR/"

echo "Autoridade Certificadora gerada com sucesso!"
