#!/usr/bin/env bash
# Installa l'APK sull'emulatore, lo avvia e fotografa le tre pagine nei due temi.
set -euo pipefail

PKG="com.forli.meteo"
ACT="$PKG/.MainActivity"
OUT="/tmp/ciout/screenshots"
mkdir -p "$OUT"

APK=$(find apk -name "*.apk" | head -1)
echo "installo $APK"
adb install -r -g "$APK"

# Dimensioni reali dello schermo: la swipe deve funzionare su qualsiasi profilo.
SIZE=$(adb shell wm size | tr -d '\r' | awk -F' ' '{print $NF}')
W=${SIZE%x*}
H=${SIZE#*x}
FROM_X=$(( W * 82 / 100 ))
TO_X=$(( W * 18 / 100 ))
MID_Y=$(( H * 42 / 100 ))

shoot() {
  sleep 2
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "  $1.png ($(stat -c%s "$OUT/$1.png") byte)"
}

session() {
  local night="$1" theme="$2"
  echo "tema $theme"
  adb shell "cmd uimode night $night" >/dev/null || true
  sleep 2
  adb shell am force-stop "$PKG"
  sleep 1
  adb shell am start -n "$ACT" >/dev/null
  sleep 8

  shoot "${theme}-1-temp"
  adb shell input swipe "$FROM_X" "$MID_Y" "$TO_X" "$MID_Y" 320
  shoot "${theme}-2-precip"
  adb shell input swipe "$FROM_X" "$MID_Y" "$TO_X" "$MID_Y" 320
  shoot "${theme}-3-vento"
}

session yes scuro
session no  chiaro

# Se l'app e' andata in crash lo voglio sapere dallo stesso giro.
adb logcat -d -t 500 "*:E" > "$OUT/logcat-errori.txt" 2>/dev/null || true
echo "cattura completata"
