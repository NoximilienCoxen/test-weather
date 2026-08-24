#!/usr/bin/env bash
# Installa l'APK sull'emulatore, lo avvia e cattura gli screenshot nei due temi.
set -euo pipefail

PKG="com.forli.meteo"
ACT="$PKG/.MainActivity"
OUT="/tmp/ciout/screenshots"
mkdir -p "$OUT"

APK=$(find apk -name "*.apk" | head -1)
echo "installo $APK"
adb install -r -g "$APK"

capture() {
  local night="$1" label="$2"
  adb shell "cmd uimode night $night" >/dev/null || true
  sleep 2
  adb shell am force-stop "$PKG"
  sleep 1
  adb shell am start -n "$ACT" >/dev/null
  sleep 7
  adb exec-out screencap -p > "$OUT/${label}.png"
  echo "catturato ${label}.png ($(stat -c%s "$OUT/${label}.png") byte)"
}

capture yes "01-scuro"
capture no  "02-chiaro"

# Se l'app e' crashata durante la sessione voglio saperlo dallo stesso ciclo.
adb logcat -d -t 400 "*:E" > "$OUT/logcat-errori.txt" 2>/dev/null || true
echo "fatto"
