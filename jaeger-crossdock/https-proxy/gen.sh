#!/usr/bin/env bash

set -e

if [[ $# -ne 2 ]]; then
    echo usage: $0 '<proxy-name> <forward-target>'
    exit 1
fi

SERVER=$1
FORWARD=$2
TARGET=build/proxy

# generate secrets
cd $(dirname $0)
mkdir -p ./build
openssl ecparam -genkey -name prime256v1 -out ${TARGET}.key
openssl req -new -sha256 -key ${TARGET}.key -out ${TARGET}.csr -subj \
    "/CN=${SERVER}/"
openssl req -x509 -sha256 -days 1 -key ${TARGET}.key -in ${TARGET}.csr -out ${TARGET}.crt
chmod 644 ${TARGET}.key

# generate nginx settings
SERVER=${SERVER} FORWARD=${FORWARD} envsubst < "proxy.template.conf" > build/proxy.conf
