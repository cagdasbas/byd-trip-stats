#!/bin/bash
# Generate libs/dilink5-sdk.jar (the real DiLink-5 bydauto SDK) for the `dilink5` flavor.
# OEM artifact — gitignored, not redistributed. Regenerate from the car's com.byd.data.collect.
#
# Needs: the dilink5 SDK dex (from byd-apps) + dex2jar (https://github.com/pxb1988/dex2jar).
#   APK source:  byd-apps/device-dump/apks/com.byd.data.collect.apk
# Usage:        tools/make-dilink5-sdk-jar.sh [path-to-com.byd.data.collect.apk]
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
APK="${1:-$HERE/../../byd-apps/device-dump/apks/com.byd.data.collect.apk}"
OUT="$HERE/../app/libs/dilink5-sdk.jar"
TMP="$(mktemp -d)"

[ -f "$APK" ] || { echo "ERROR: data.collect apk not found at $APK (pass the path as arg 1)"; exit 1; }
command -v d2j-dex2jar.sh >/dev/null 2>&1 || command -v d2j-dex2jar >/dev/null 2>&1 \
  || { echo "ERROR: dex2jar not on PATH (install from github.com/pxb1988/dex2jar)"; exit 1; }
DEX2JAR=$(command -v d2j-dex2jar.sh || command -v d2j-dex2jar)

echo "Extracting classes.dex from $APK ..."
unzip -o -q "$APK" classes.dex -d "$TMP"
echo "dex -> jar ..."
"$DEX2JAR" "$TMP/classes.dex" -o "$TMP/all.jar" --force
# Keep ONLY the bydauto SDK classes (drop the app's own code, okhttp, gson, androidx, etc.)
mkdir -p "$TMP/x" && (cd "$TMP/x" && unzip -o -q "$TMP/all.jar" 'android/hardware/bydauto/*' 'a/a/*' 2>/dev/null || true)
mkdir -p "$(dirname "$OUT")"
(cd "$TMP/x" && jar cf "$OUT" .)
echo "Wrote $OUT ($(unzip -l "$OUT" 2>/dev/null | tail -1 | awk '{print $2}') entries)"
rm -rf "$TMP"
