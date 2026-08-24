#!/usr/bin/env bash
# Installa l'APK sull'emulatore e fotografa le tre pagine nei due temi.
#
# Ogni chiamata adb ha un timeout: senza, una adb su dispositivo caduto resta
# appesa per sempre. Niente set -e, perche' voglio comunque il logcat; ma alla
# fine lo script fallisce se non ha prodotto nemmeno uno scatto: un job verde
# che non produce nulla e' peggio di un job rosso.
set -uo pipefail

PKG="com.forli.meteo"
ACT="$PKG/.MainActivity"
OUT="/tmp/ciout"
mkdir -p "$OUT"

adbt() { timeout 60 adb "$@"; }

# Logcat in streaming da subito: se il dispositivo muore lanciando l'app,
# questo file e' l'unica testimonianza del perche'.
adb logcat -c >/dev/null 2>&1 || true
( adb logcat -v time > "$OUT/logcat-streaming.txt" 2>&1 & ) || true

shoot() {
  local name="$1"
  if adbt shell screencap -p /sdcard/shot.png >/dev/null 2>&1 &&
     adbt pull /sdcard/shot.png "$OUT/$name.png" >/dev/null 2>&1 &&
     [ -s "$OUT/$name.png" ]; then
    echo "  $name.png ($(stat -c%s "$OUT/$name.png") byte)"
    adbt shell rm -f /sdcard/shot.png >/dev/null 2>&1 || true
    return 0
  fi
  echo "  $name.png NON catturato"
  rm -f "$OUT/$name.png"
  return 1
}

# Esperimento di controllo: fotografo il launcher PRIMA di toccare l'app.
# Se questo scatto riesce e i successivi no, la catena di cattura e' sana e
# il problema sta nell'app; se fallisce anche questo, e' l'emulatore.
echo "== scatto di riferimento, app non ancora installata =="
shoot "00-launcher" || echo "ATTENZIONE: la cattura non funziona nemmeno sul launcher"

APK=$(find apk -name "*.apk" | head -1)
echo "== installo $APK =="
adbt install -r -g "$APK" || { echo "installazione fallita"; exit 1; }

SIZE=$(adbt shell wm size 2>/dev/null | tr -d '\r' | awk '{print $NF}')
case "$SIZE" in
  *x*) W=${SIZE%x*}; H=${SIZE#*x} ;;
  *)   W=1080; H=2400 ;;
esac
FROM_X=$(( W * 82 / 100 )); TO_X=$(( W * 18 / 100 )); MID_Y=$(( H * 42 / 100 ))
echo "schermo ${W}x${H}"

alive() {
  local state
  state=$(timeout 20 adb get-state 2>/dev/null | tr -d '\r')
  [ "$state" = "device" ]
}

session() {
  local tema="$1" slug
  slug=$(echo "$tema" | tr '[:upper:]' '[:lower:]')
  echo "== tema $tema =="
  alive || { echo "dispositivo non raggiungibile, salto"; return; }

  adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
  sleep 1
  # -W attende che l'attivita' sia effettivamente in primo piano e riporta
  # l'esito, invece di lasciarmi indovinare con una sleep fissa.
  adbt shell am start -W -n "$ACT" --es tema "$tema" 2>&1 | sed 's/^/    /' || true
  sleep 6
  alive || { echo "dispositivo caduto subito dopo l'avvio dell'app"; return; }

  shoot "${slug}-1-temp"
  adbt shell input swipe "$FROM_X" "$MID_Y" "$TO_X" "$MID_Y" 320 >/dev/null 2>&1 || true
  sleep 2
  shoot "${slug}-2-precip"
  adbt shell input swipe "$FROM_X" "$MID_Y" "$TO_X" "$MID_Y" 320 >/dev/null 2>&1 || true
  sleep 2
  shoot "${slug}-3-vento"
}

session SCURO
session CHIARO

sleep 2
pkill -f "adb logcat -v time" >/dev/null 2>&1 || true
grep -iE "forli|meteo|AndroidRuntime|FATAL|Exception|OutOfMemory|died|crash" \
  "$OUT/logcat-streaming.txt" 2>/dev/null | tail -120 > "$OUT/logcat-rilevante.txt" || true

echo "== file prodotti =="
ls -la "$OUT"

shots=$(find "$OUT" -name "*.png" -size +0 | wc -l)
echo "scatti riusciti: $shots"
[ "$shots" -gt 0 ] || { echo "nessuno scatto prodotto"; exit 1; }
