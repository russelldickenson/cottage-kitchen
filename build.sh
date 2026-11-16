#!/bin/zsh
# Builds a signed release APK of Cottage Kitchen.
#
# The credentials below are PLACEHOLDERS. Replace them here, or export the COTTAGEKITCHEN_* variables
# before running, e.g.:  COTTAGEKITCHEN_KEYSTORE_PASSWORD='...' COTTAGEKITCHEN_KEY_PASSWORD='...' ./build.sh
#
# If the keystore file does not exist yet, one is created from these values. If you change the
# credentials later, delete the old keystore file first, or the build will fail on a password mismatch.

set -euo pipefail

ROOT="${0:A:h}"
cd "$ROOT"

# ---- placeholder credentials: replace these ---------------------------------------------------
: "${COTTAGEKITCHEN_KEYSTORE:=$ROOT/cottagekitchen.jks}"
: "${COTTAGEKITCHEN_KEYSTORE_PASSWORD:=CHANGE_ME_STORE_PASSWORD}"
: "${COTTAGEKITCHEN_KEY_ALIAS:=cottagekitchen}"
# The key password defaults to the keystore password: a keystore this script creates (PKCS12) cannot
# have two different passwords. Only set COTTAGEKITCHEN_KEY_PASSWORD separately for an existing keystore that does.
: "${COTTAGEKITCHEN_KEY_PASSWORD:=$COTTAGEKITCHEN_KEYSTORE_PASSWORD}"
: "${COTTAGEKITCHEN_KEY_DNAME:=CN=Your Name, OU=Your Team, O=Your Company, L=Your City, ST=Your State, C=US}"
# -----------------------------------------------------------------------------------------------

export COTTAGEKITCHEN_KEYSTORE COTTAGEKITCHEN_KEYSTORE_PASSWORD COTTAGEKITCHEN_KEY_ALIAS COTTAGEKITCHEN_KEY_PASSWORD
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

for tool in java keytool; do
  command -v "$tool" >/dev/null || { print -u2 "error: '$tool' not found. Install a JDK (17 or newer)."; exit 1; }
done
[[ -d "$ANDROID_HOME" ]] || { print -u2 "error: Android SDK not found at $ANDROID_HOME (set ANDROID_HOME)."; exit 1; }

if [[ "$COTTAGEKITCHEN_KEYSTORE_PASSWORD" == CHANGE_ME* ]]; then
  print -u2 "warning: signing with PLACEHOLDER passwords. Fine for testing, not for publishing."
fi

if [[ ! -f "$COTTAGEKITCHEN_KEYSTORE" ]]; then
  if [[ "$COTTAGEKITCHEN_KEY_PASSWORD" != "$COTTAGEKITCHEN_KEYSTORE_PASSWORD" ]]; then
    print -u2 "error: a new PKCS12 keystore needs the same store and key password. Use one value, or point COTTAGEKITCHEN_KEYSTORE at an existing keystore."
    exit 1
  fi
  print "Creating keystore at $COTTAGEKITCHEN_KEYSTORE"
  keytool -genkeypair \
    -keystore "$COTTAGEKITCHEN_KEYSTORE" -storepass "$COTTAGEKITCHEN_KEYSTORE_PASSWORD" \
    -alias "$COTTAGEKITCHEN_KEY_ALIAS" \
    -storetype PKCS12 -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "$COTTAGEKITCHEN_KEY_DNAME" >/dev/null
fi

print "Building release APK..."
./gradlew --console=plain -q assembleRelease

APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
[[ -f "$APK" ]] || { print -u2 "error: build finished but $APK was not produced."; exit 1; }

# Check the signature if the SDK has apksigner (newest build-tools first).
APKSIGNER=("$ANDROID_HOME"/build-tools/*/apksigner(N.On[1]))
if [[ -n "${APKSIGNER[1]:-}" ]]; then
  "${APKSIGNER[1]}" verify --verbose "$APK" 2>&1 | head -3
fi

print "\nSigned APK: $APK"

cp app/build/outputs/apk/release/app-release.apk cottagekitchen.apk
