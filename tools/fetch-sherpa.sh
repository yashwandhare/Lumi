#!/usr/bin/env bash
#
# Fetches the pinned sherpa-onnx AAR into libs/sherpa/ and verifies it.
#
# The AAR is not committed — it is a 49MB binary, kept out of git for the same reason the
# 2.6GB model and the 180MB embedder are: tools/seed-model.sh and MediaPipeEmbedder's
# first-use download handle those, and this handles the ASR runtime.
#
# Usage: bash tools/fetch-sherpa.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_DIR="$REPO_ROOT/libs/sherpa"
AAR_NAME="sherpa-onnx-1.13.5.aar"
AAR="$DEST_DIR/$AAR_NAME"

URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.5/$AAR_NAME"
SIZE=49095090
SHA256="6419cd8bc983e0c4fab06067f0fe0313fdc0f7103818ac1e7a08d50787b7a82b"

if [[ -f "$AAR" ]] && [[ "$(wc -c < "$AAR" | tr -d ' ')" == "$SIZE" ]]; then
    ACTUAL="$(sha256sum "$AAR" | cut -d' ' -f1)"
    if [[ "$ACTUAL" == "$SHA256" ]]; then
        echo "sherpa-onnx AAR already present and verified: $AAR"
        exit 0
    fi
    echo "Existing AAR failed its digest check; re-downloading." >&2
    rm -f "$AAR"
fi

mkdir -p "$DEST_DIR"
echo "Downloading $AAR_NAME (~47 MB)..."
curl -fL --connect-timeout 30 -C - -o "$AAR.part" "$URL"
mv "$AAR.part" "$AAR"

ACTUAL="$(sha256sum "$AAR" | cut -d' ' -f1)"
if [[ "$ACTUAL" != "$SHA256" ]]; then
    echo "Digest mismatch: expected $SHA256, got $ACTUAL" >&2
    rm -f "$AAR"
    exit 1
fi

if [[ "$(wc -c < "$AAR" | tr -d ' ')" != "$SIZE" ]]; then
    echo "Size mismatch after download." >&2
    rm -f "$AAR"
    exit 1
fi

echo "Verified: $AAR ($SIZE bytes)"
