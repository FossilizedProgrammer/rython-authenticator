#!/usr/bin/env bash
# Build helper for environments where the default proxy setup breaks Gradle.
#
# The global ~/.gradle/gradle.properties on this machine still points at a dead
# SOCKS proxy (127.0.0.1:1080), so those flags are cleared here. Repositories are
# reached directly by default; set RYTHON_PROXY=host:port to route through an
# HTTP proxy instead.
#
# Usage: ./build.sh [gradle args...]        e.g. ./build.sh assembleDebug
set -euo pipefail

cd "$(dirname "$0")"

PROXY_FLAGS=(
  -DsocksProxyHost=
  -DsocksProxyPort=
  -Dhttp.nonProxyHosts=
)

if [ -n "${RYTHON_PROXY:-}" ]; then
  PROXY_HOST="${RYTHON_PROXY%%:*}"
  PROXY_PORT="${RYTHON_PROXY##*:}"
  PROXY_FLAGS+=(
    -Dhttp.proxyHost="$PROXY_HOST"
    -Dhttp.proxyPort="$PROXY_PORT"
    -Dhttps.proxyHost="$PROXY_HOST"
    -Dhttps.proxyPort="$PROXY_PORT"
  )
fi

# Prefer a locally installed Gradle when there is one (this machine keeps 8.7 in
# ~/gradle-dist); fall back to the checked-in wrapper, which is what a fresh clone
# (and CI) uses.
LOCAL_GRADLE="${GRADLE_BIN:-$HOME/gradle-dist/gradle-8.7/bin/gradle}"
if [ -x "$LOCAL_GRADLE" ]; then
  GRADLE_CMD=("$LOCAL_GRADLE")
elif [ -x "./gradlew" ]; then
  GRADLE_CMD=("./gradlew")
else
  echo "error: no Gradle found; run ./gradlew (downloads Gradle 8.7)" >&2
  exit 1
fi

exec "${GRADLE_CMD[@]}" "${PROXY_FLAGS[@]}" "$@" --console=plain
