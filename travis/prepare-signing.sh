#!/bin/bash

ENCRYPTION_KEY=$1
ENCRYPTION_IV=$2

# some sanity checks first:
if [ "x${ENCRYPTION_KEY}" == "x" ]; then
    echo "The encryption key has not been provided. Skipping signing preparation"
    exit 0
fi

if [ "x${ENCRYPTION_IV}" == "x" ]; then
    echo "The encryption initialization vector has not been provided. Skipping signing preparation"
    exit 0
fi

if [ "${TRAVIS_SECURE_ENV_VARS}" == true ]; then
    openssl aes-256-cbc -K "${ENCRYPTION_KEY}" -iv "${ENCRYPTION_IV}" -in travis/signing-key.asc.enc -out travis/signing-key.asc -d
    if (( $? != 0 )); then
        echo "Failed to decrypt the signing key. Skipping."
        exit 1
    fi

    gpg --no-tty --batch --allow-secret-key-import --import travis/signing-key.asc
    rm -rf "$HOME/.gradle/gradle.properties"
    echo signing.keyId="${SIGNING_KEY_ID}" > "$HOME/.gradle/gradle.properties"
    echo signing.password="${SIGNING_KEY_PASSPHRASE}" >> "$HOME/.gradle/gradle.properties"
    echo ossrhUsername="${SONATYPE_USERNAME}" >> "$HOME/.gradle/gradle.properties"
    echo ossrhPassword="${SONATYPE_PASSWORD}" >> "$HOME/.gradle/gradle.properties"
    echo signing.secretKeyRingFile="${HOME}/.gnupg/secring.gpg" >> "$HOME/.gradle/gradle.properties"
else
    echo "Travis secure env vars not set. Skipping."
    exit 0
fi
