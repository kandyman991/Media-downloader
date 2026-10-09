#!/usr/bin/env bash
# One-time key provisioning for StreamCatch stable Android APK upgrades.
# Run ONLY on a trusted local machine. The keystore must NEVER be committed to Git.
set -euo pipefail
set +x
umask 077

REPO="kandyman991/Media-downloader"
ALIAS="streamcatch-release"
KEY_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/streamcatch/signing"
KEYSTORE="$KEY_DIR/streamcatch-release.p12"

for tool in gh keytool base64; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Missing $tool. On Ubuntu install GitHub CLI and a JDK: sudo apt install gh openjdk-21-jdk"
    exit 1
  fi
done
if ! gh auth status --hostname github.com >/dev/null 2>&1; then
  echo "Authorize GitHub CLI first: gh auth login"
  exit 1
fi
if ! gh repo view "$REPO" --json nameWithOwner --jq .nameWithOwner >/dev/null; then
  echo "Cannot access the target GitHub repository: $REPO"
  exit 1
fi

mkdir -p "$KEY_DIR"
if [[ -e "$KEYSTORE" ]]; then
  echo "Reusing your existing keystore: $KEYSTORE"
  echo "It will NOT be overwritten. This is required to preserve the Android signing identity."
else
  echo "Generating a new private Android signing key in: $KEYSTORE"
  echo "Choose a strong keystore password and save it in a password manager."
  keytool -genkeypair \
    -alias "$ALIAS" \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -keyalg RSA \
    -keysize 3072 \
    -validity 10000 \
    -dname "CN=StreamCatch Android, O=StreamCatch, C=IT"
  chmod 600 "$KEYSTORE"
fi

printf 'Enter the SAME keystore password to securely configure CI: '
IFS= read -r -s PASSWORD
printf '\n'
if [[ ${#PASSWORD} -lt 8 ]]; then
  echo "Please use a signing password of at least eight characters."
  exit 1
fi

# Validate the password by supplying it on stdin (not in process arguments).
if ! printf '%s\n' "$PASSWORD" | keytool -list \
    -keystore "$KEYSTORE" -storetype PKCS12 -alias "$ALIAS" >/dev/null 2>&1; then
  echo "Signing key verification failed. Keep the keystore and try again with the correct password."
  exit 1
fi

echo "Uploading signing credentials as encrypted GitHub Actions repository secrets..."
# gh secret set encrypts values locally; do not place passwords or private keys in repo source.
base64 -w 0 "$KEYSTORE" | gh secret set STREAMCATCH_KEYSTORE_BASE64 -R "$REPO"
printf '%s' "$PASSWORD" | gh secret set STREAMCATCH_SIGNING_PASSWORD -R "$REPO"
printf '%s' "$ALIAS" | gh secret set STREAMCATCH_SIGNING_ALIAS -R "$REPO"
unset PASSWORD

echo
echo "Credentials uploaded. Back up BOTH this file and its password in a private location:"
echo "  $KEYSTORE"
echo "Losing the key means future versions cannot update apps signed with it."
echo "Never add the key or password to a public repository."
echo
echo "Requesting the first stable-signed APK through GitHub Releases..."
gh workflow run android.yml -R "$REPO" --ref main -f publish_signed_apk=true

echo
echo "Android signing is configured. To download your APK, visit:"
echo "  https://github.com/$REPO/releases"
echo "Uninstall the old ephemeral-debug-key build ONCE; future builds signed by this key can update normally."
