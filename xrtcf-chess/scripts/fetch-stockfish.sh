#!/usr/bin/env bash
# Fetch a legally distributable Stockfish binary for Android APK packaging.
#
# Source: official Stockfish GitHub releases (GPLv3).
#   https://github.com/official-stockfish/Stockfish/releases
#
# Usage:
#   scripts/fetch-stockfish.sh [arm64-v8a|armeabi-v7a]
#
# Writes:
#   app/src/main/assets/stockfish/stockfish
set -euo pipefail

ARCH="${1:-arm64-v8a}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/app/src/main/assets/stockfish"
mkdir -p "$OUT_DIR"

# Pin a release tag that ships android assets
TAG="${STOCKFISH_TAG:-sf_19}"

case "$ARCH" in
  arm64-v8a|arm64)
    ASSET="stockfish-android-arm64-universal.tar.gz"
    ;;
  armeabi-v7a|armv7)
    ASSET="stockfish-android-armv7-neon.tar.gz"
    ;;
  *)
    echo "Unsupported arch: $ARCH (use arm64-v8a or armeabi-v7a)" >&2
    exit 1
    ;;
esac

URL="https://github.com/official-stockfish/Stockfish/releases/download/${TAG}/${ASSET}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Downloading $URL"
curl -fL --retry 3 --retry-delay 2 -o "$TMP/$ASSET" "$URL"

echo "Extracting…"
tar -xzf "$TMP/$ASSET" -C "$TMP"

# Locate the stockfish executable inside the archive
BIN="$(find "$TMP" -type f -name 'stockfish*' ! -name '*.tar.gz' | head -1)"
if [ -z "$BIN" ]; then
  echo "stockfish binary not found in archive" >&2
  exit 1
fi

# Android exec requires the binary named 'stockfish' in assets
cp "$BIN" "$OUT_DIR/stockfish"
chmod +x "$OUT_DIR/stockfish"

echo "Installed: $OUT_DIR/stockfish"
ls -la "$OUT_DIR/stockfish"
