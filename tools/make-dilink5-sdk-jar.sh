#!/bin/bash
# Generate app/libs/dilink5-sdk.jar — the real DiLink-5 `bydauto` SDK for the `dilink5` flavor.
#
# OEM artifact: gitignored, never redistributed. It is regenerated locally from the system app
# `com.byd.data.collect` that is ALREADY installed on your own DiLink-5 car.
#
# Requirements:
#   - dex2jar on PATH        (https://github.com/pxb1988/dex2jar)
#   - unzip, jar             (jar ships with the JDK)
#   - adb                    (only when pulling the apk from the car; not needed if you pass a path)
#
# Usage:
#   tools/make-dilink5-sdk-jar.sh                       # pull com.byd.data.collect from the car via adb
#   tools/make-dilink5-sdk-jar.sh /path/to/app.apk      # use a local com.byd.data.collect apk instead
#   ADB_SERIAL=10.0.0.5:5555 tools/make-dilink5-sdk-jar.sh   # target a specific adb device
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../app/libs/dilink5-sdk.jar"
PKG="com.byd.data.collect"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

adb() { command adb ${ADB_SERIAL:+-s "$ADB_SERIAL"} "$@"; }

# ── 1. Obtain the apk (arg, or pull it from the connected car) ────────────────
APK="${1:-}"
if [ -z "$APK" ]; then
    command -v adb >/dev/null 2>&1 || { echo "ERROR: adb not on PATH — connect a car or pass an apk path."; exit 1; }
    echo "No apk given — pulling $PKG from the car over adb…"
    adb get-state >/dev/null 2>&1 || { echo "ERROR: no adb device. Run: adb connect <head-unit-ip>:5555"; exit 1; }
    DEVPATH="$(adb shell pm path "$PKG" 2>/dev/null | sed 's/package://' | tr -d '\r' | head -1)"
    [ -n "$DEVPATH" ] || { echo "ERROR: $PKG is not installed on this device."; exit 1; }
    APK="$TMP/$PKG.apk"
    adb pull "$DEVPATH" "$APK" >/dev/null
    echo "Pulled $DEVPATH"
fi
[ -f "$APK" ] || { echo "ERROR: apk not found: $APK"; exit 1; }

# ── 2. Convert the apk's dex(es) to a jar ────────────────────────────────────
command -v d2j-dex2jar.sh >/dev/null 2>&1 || command -v d2j-dex2jar >/dev/null 2>&1 \
    || { echo "ERROR: dex2jar not on PATH (install from https://github.com/pxb1988/dex2jar)."; exit 1; }
DEX2JAR="$(command -v d2j-dex2jar.sh || command -v d2j-dex2jar)"

echo "dex → jar (all dexes)…"
"$DEX2JAR" "$APK" -o "$TMP/all.jar" --force >/dev/null

# ── 3. Keep ONLY the bydauto SDK classes (drop the app, okhttp, gson, androidx…) ──
mkdir -p "$TMP/x"
( cd "$TMP/x" && unzip -o -q "$TMP/all.jar" 'android/hardware/bydauto/*' )
# Some builds put obfuscated bydauto support classes under a/a/* — keep them if present.
( cd "$TMP/x" && unzip -o -q "$TMP/all.jar" 'a/a/*' 2>/dev/null ) || true

[ -d "$TMP/x/android/hardware/bydauto" ] \
    || { echo "ERROR: no android/hardware/bydauto/* classes in $APK — is this really $PKG?"; exit 1; }

mkdir -p "$(dirname "$OUT")"
( cd "$TMP/x" && jar cf "$OUT" . )
echo "Wrote $OUT ($(unzip -l "$OUT" | tail -1 | awk '{print $2}') entries)"
