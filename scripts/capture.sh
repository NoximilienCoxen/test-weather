#!/usr/bin/env bash
# Installa l'APK sull'emulatore e fotografa le tre pagine nei due temi.
#
# Volutamente senza set -e: se uno scatto fallisce voglio comunque gli altri e
# il logcat. E ogni chiamata adb ha un timeout: se il dispositivo cade, una
# adb senza limite resta appesa per sempre e il job brucia l'intera quota.
set -uo pipefail

PKG="com.forli.meteo"
ACT="$PKG/.MainActivity"
OUT="/tmp/ciout"   # publish.sh copia il contenuto di questa cartella
mkdir -p "$OUT"

adbt() { timeout 90 adb "$@"; }

APK=$(find apk -name "*.apk" | head -1)
echo "installo $APK"
adbt install -r -g "$APK" || { echo "installazione fallita"; exit 1; }

echo "attendo il boot"
timeout 120 adb wait-for-device || echo "wait-for-device scaduto, proseguo"
for _ in $(seq 1 20); do
  [ "$(adbt shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 3
done

for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
  adbt shell settings put global "$scale" 0 >/dev/null 2>&1 || true
done

SIZE=$(adbt shell wm size 2>/dev/null | tr -d '\r' | awk '{print $NF}')
case "$SIZE" in
  *x*) W=${SIZE%x*}; H=${SIZE#*x} ;;
  *)   W=1080; H=2400; echo "dimensione non leggibile, uso $W x $H" ;;
esac
FROM_X=$(( W * 82 / 100 ))
TO_X=$(( W * 18 / 100 ))
MID_Y=$(( H * 42 / 100 ))
echo "schermo ${W}x${H}"

shoot() {
  local name="$1"
  sleep 2
  # "adb exec-out screencap" chiude lo stream sugli emulatori headless:
  # passo per un file sul dispositivo, che e' la via affidabile.
  if adbt shell screencap -p /sdcard/shot.png >/dev/null 2>&1 &&
     adbt pull /sdcard/shot.png "$OUT/$name.png" >/dev/null 2>&1 &&
     [ -s "$OUT/$name.png" ]; then
    echo "  $name.png ($(stat -c%s "$OUT/$name.png") byte)"
  else
    echo "  $name.png NON catturato"
    rm -f "$OUT/$name.png"
  fi
  adbt shell rm -f /sdcard/shot.png >/dev/null 2>&1 || true
}

session() {
  local tema="$1"
  local slug
  slug=$(echo "$tema" | tr '[:upper:]' '[:lower:]')
  echo "tema $tema"
  adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
  sleep 1
  adbt shell am start -n "$ACT" --es tema "$tema" >/dev/null 2>&1 || true
  sleep 9

  shoot "${slug}-1-temp"
  adbt shell input swipe "$FROM_X" "$MID_Y" "$TO_X" "$MID_Y" 320 >/dev/null 2>&1 || true
  shoot "${slug}-2-precip"
  adbt shell input swipe "$FROM_X" "$MID_Y" "$TO_X" "$MID_Y" 320 >/dev/null 2>&1 || true
  shoot "${slug}-3-vento"
}

session SCURO
session CHIARO

adbt logcat -d -t 800 > "$OUT/logcat-completo.txt" 2>/dev/null || true
grep -iE "forli|meteo|AndroidRuntime|FATAL|Exception" "$OUT/logcat-completo.txt" \
  | tail -80 > "$OUT/logcat-app.txt" 2>/dev/null || true

echo "--- file prodotti ---"
ls -la "$OUT"
echo "cattura completata"
