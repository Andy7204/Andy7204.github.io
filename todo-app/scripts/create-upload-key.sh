#!/usr/bin/env bash
# Creates the private upload key used to sign Play Store builds, and prints the values to
# store as GitHub repository secrets. Keep upload-key.jks and the password somewhere safe
# (a password manager) and never commit them.
set -euo pipefail

KEYSTORE=${1:-upload-key.jks}
ALIAS=upload

if [ -e "$KEYSTORE" ]; then
  echo "$KEYSTORE already exists; refusing to overwrite it." >&2
  exit 1
fi

read -r -s -p "Choose a keystore password (min 6 chars): " PASS
echo
keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$ALIAS" -keyalg RSA -keysize 4096 \
  -validity 10000 -storepass "$PASS" -keypass "$PASS" -dname "CN=Todo upload key"

echo
echo "Add these as GitHub secrets (Settings → Secrets and variables → Actions):"
echo "  UPLOAD_KEYSTORE_BASE64   = contents of upload-key.b64 (created next to the keystore)"
echo "  UPLOAD_KEYSTORE_PASSWORD = the password you just chose"
echo "  UPLOAD_KEY_ALIAS         = $ALIAS"
echo "  UPLOAD_KEY_PASSWORD      = the password you just chose"
base64 < "$KEYSTORE" | tr -d '\n' > "${KEYSTORE%.jks}.b64"
echo
echo "Wrote ${KEYSTORE%.jks}.b64. Delete it after copying it into the secret."
