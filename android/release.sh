#!/usr/bin/env bash
# Builds the final release APKs for Rython-Authenticator:
#   - armeabi-v7a (32-bit ARM)
#   - arm64-v8a   (64-bit ARM)
#   - universal   (both ABIs, for any device)
#
# Signing: a 4096-bit RSA key is generated once into app/rython-release.jks and the
# credentials are stored in keystore.properties (0600, git-ignored). The generated
# password is random, so keep keystore.properties safe: without it you cannot ship
# updates signed with the same key.
#
# Usage: ./release.sh [extra gradle args...]
set -euo pipefail
cd "$(dirname "$0")"

KEYSTORE="app/rython-release.jks"
PROPS="keystore.properties"
ALIAS="rython"
DNAME="CN=Rython-Authenticator, OU=Mobile, O=Rython, L=Tehran, C=IR"

if [ -f "$PROPS" ]; then
  STORE_PASS="$(sed -n 's/^storePassword=//p' "$PROPS" | head -1)"
elif [ -n "${RYTHON_STORE_PASSWORD:-}" ]; then
  STORE_PASS="$RYTHON_STORE_PASSWORD"
elif [ -f "$KEYSTORE" ]; then
  echo "error: $KEYSTORE exists but $PROPS is missing." >&2
  echo "       Restore keystore.properties or set RYTHON_STORE_PASSWORD." >&2
  exit 1
else
  STORE_PASS="$(openssl rand -hex 16)"
fi

if [ ! -f "$KEYSTORE" ]; then
  echo ">> generating release keystore $KEYSTORE"
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -storepass "$STORE_PASS" \
    -keypass "$STORE_PASS" \
    -dname "$DNAME"
fi

if [ ! -f "$PROPS" ]; then
  echo ">> writing credentials to $PROPS (keep this file private)"
  umask 077
  cat > "$PROPS" <<EOF
storeFile=$KEYSTORE
storePassword=$STORE_PASS
keyAlias=$ALIAS
keyPassword=$STORE_PASS
EOF
fi

echo ">> building release APKs"
./build.sh assembleRelease "$@"

VERSION="$(sed -n 's/.*versionName = "\(.*\)"/\1/p' app/build.gradle.kts | head -1)"
[ -n "$VERSION" ] || VERSION="1.0"

OUT_DIR="app/build/outputs/apk/release"
mkdir -p dist
rm -f dist/Rython-Authenticator-*.apk

for apk in "$OUT_DIR"/*.apk; do
  [ -e "$apk" ] || continue
  name="$(basename "$apk" .apk)"
  case "$name" in
    *armeabi-v7a*) abi="armeabi-v7a" ;;
    *arm64-v8a*)   abi="arm64-v8a" ;;
    *universal*)   abi="universal" ;;
    *)             abi="$(printf '%s' "$name" | sed 's/^app-//; s/-release$//')" ;;
  esac
  cp "$apk" "dist/Rython-Authenticator-${VERSION}-${abi}.apk"
done

APKSIGNER="$(ls -1 "${ANDROID_HOME:-$HOME/android-sdk}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"

echo
echo ">> release artifacts in $(pwd)/dist"
for out in dist/Rython-Authenticator-*.apk; do
  [ -e "$out" ] || continue
  size="$(du -h "$out" | cut -f1)"
  sum="$(sha256sum "$out" | cut -d' ' -f1)"
  printf '  %-52s %6s  sha256=%s\n' "$out" "$size" "$sum"
done

if [ -n "$APKSIGNER" ]; then
  echo
  echo ">> signature check"
  for out in dist/Rython-Authenticator-*.apk; do
    [ -e "$out" ] || continue
    if "$APKSIGNER" verify --print-certs "$out" >/dev/null 2>&1; then
      echo "  OK   $(basename "$out")"
    else
      echo "  FAIL $(basename "$out")" >&2
      exit 1
    fi
  done
else
  echo "(apksigner not found; skipped signature verification)"
fi
