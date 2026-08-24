#!/usr/bin/env bash
# Installa l'APK sull'emulatore e fotografa le tre pagine nei due temi.
# Volutamente senza set -e: se uno scatto fallisce voglio comunque gli altri
# e il logcat, non un job che muore al primo intoppo.
set -uo pipefail

PKG="com.forli.meteo"
ACT="$PKG/.MainActivity"
OUT="/tmp/ciout"   # publish.sh copia il contenuto di questa cartella
mkdir -p "$OUT"

APK=$(find apk -name "*.apk" | head -1)
echo "installo $APK"
adb install -r -g "$APK" || { echo "installazione fallita"; exit 1; }

adb wait-for-device
for _ in $(seq 1 30); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 2
done

for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global "$scale" 0 >/dev/null 2>&1 || true
done

SIZE=$(adb shell wm size | tr -d '\r' | awk '{print $NF}')
W=${SIZE%x*}
H=${SIZE#*x}
FROM_X=$(( W * 82 / 100 ))
TO_X=$(( W * 18 / 100 ))
MID_Y=$(( H * 42 / 100 ))
echo "schermo ${W}x${H}"

shoot() {
  local name="$1"
  sleep 2
  # "adb exec-out screencap" chiude lo stream sugli emulatori headless:
  # passo per un file sul dispositivo, che e' la via affidabile.
  if adb shell screencap -p /sdcard/shot.png >/dev/null 2>&1 &&
     adb pull /sdcard/shot.png "$OUT/$name.png" >/dev/null 2>&1; then
    echo "  $name.png ($(stat -c%s "$OUT/$name.png") byte)"
  else
    echo "  $name.png NON catturato"
  fi
  adb shell rm -f /sdcard/shot.png >/dev/null 2>&1 || true
}

session() {
  local tema="$1"
  local slug
  slug=$(echo "$tema" | tr '[:upper:]' '[:lower:]')
  echo "tema $tema"
  adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
  sleep 1
  adb shell am start -n "$ACT" --es tema "$tema" >/dev/null 2>&1 || true
  sleep 9

  shoot "${slug}-1-temp"
  adb shell input swipe "$FROM_X" "$MID_Y" "$TO_X" "$MID_Y" 320 >/dev/null 2>&1 || true
  shoot "${slug}-2-precip"
  adb shell input swipe "$FROM_X" "$MID_Y" "$TO_X" "$MID_Y" 320 >/dev/null 2>&1 || true
  shoot "${slug}-3-vento"
}

session SCURO
session CHIARO

adb logcat -d -t 800 > "$OUT/logcat-completo.txt" 2>/dev/null || true
grep -iE "forli|meteo|AndroidRuntime|FATAL|Exception" "$OUT/logcat-completo.txt" \
  | tail -80 > "$OUT/logcat-app.txt" 2>/dev/null || true

echo "--- file prodotti ---"
ls -la "$OUT"
echo "cattura completata"
