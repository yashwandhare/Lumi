#!/usr/bin/env bash
# Seed the Gemma model onto the device from a host cache.
#
# Why this exists: `connectedAndroidTest` uninstalls the app, and an uninstall wipes app-private
# storage — which is where the 2.6GB model lives. That has cost this project its model three times.
# Re-downloading takes minutes of mobile data; pushing from a host cache takes about 75 seconds
# over USB and costs nothing.
#
# The cache is populated once, either from a completed in-app download or from a Trace v1 install
# (v1 ships the byte-identical artefact — same SHA-256).
#
# Usage:
#   tools/seed-model.sh            # push the cached model to the device
#   tools/seed-model.sh --pull     # populate the cache from the device, then push nothing
set -euo pipefail

PKG="com.lumi"
FILE="gemma-4-E2B-it.litertlm"
SHA="181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
SIZE=2588147712
CACHE_DIR="${LUMI_MODEL_CACHE:-$HOME/dev/.lumi-model-cache}"
CACHE="$CACHE_DIR/$FILE"
# run-as can read /data/local/tmp; it cannot read /sdcard under scoped storage.
STAGE="/data/local/tmp/$FILE"

die() { echo "error: $*" >&2; exit 1; }

command -v adb >/dev/null || die "adb not on PATH"
adb get-state >/dev/null 2>&1 || die "no device over adb"

if [[ "${1:-}" == "--pull" ]]; then
  mkdir -p "$CACHE_DIR"
  echo "Pulling model from $PKG into the cache..."
  # Copy out through run-as, since adb pull cannot read app-private storage directly.
  adb shell "run-as $PKG cat files/models/$FILE" > "$CACHE"
  actual=$(sha256sum "$CACHE" | cut -d' ' -f1)
  [[ "$actual" == "$SHA" ]] || die "digest mismatch after pull: $actual"
  echo "Cached and verified: $CACHE"
  exit 0
fi

[[ -f "$CACHE" ]] || die "no cached model at $CACHE — run with --pull first, or let the app download once"

actual=$(sha256sum "$CACHE" | cut -d' ' -f1)
[[ "$actual" == "$SHA" ]] || die "cached model digest mismatch: $actual"

adb shell "pm list packages" | grep -q "^package:$PKG$" || die "$PKG is not installed; run ./gradlew installDebug first"

echo "Staging to the device..."
adb push "$CACHE" "$STAGE" >/dev/null

echo "Copying into app-private storage..."
adb shell "run-as $PKG sh -c 'mkdir -p files/models && cat $STAGE > files/models/$FILE'"
# The receipt is what makes ModelStore.isReady() true without re-hashing 2.6GB on every launch.
adb shell "run-as $PKG sh -c 'printf %s $SHA > files/models/$FILE.sha256'"
# A stale .part would be resumed from and corrupt the next real download.
adb shell "run-as $PKG rm -f files/models/$FILE.part"
adb shell "rm -f $STAGE"

landed=$(adb shell "run-as $PKG stat -c%s files/models/$FILE" | tr -d '\r')
[[ "$landed" == "$SIZE" ]] || die "landed size is $landed, expected $SIZE"

echo "Seeded $FILE ($landed bytes) into $PKG."
echo "Note: the compilation caches are not seeded — the first load will be slow while they rebuild."
