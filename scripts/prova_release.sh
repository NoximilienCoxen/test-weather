#!/usr/bin/env bash
# La build di release, quella che passa da R8, aperta davvero sull'emulatore.
#
# Gira dopo capture.sh, sullo stesso emulatore: la release e' firmata con la
# stessa chiave di debug (vedi il passo "Release firmata per la prova"), quindi
# si installa **sopra** la debug e ne eredita i dati - benvenuto chiuso,
# localita' scelta. Poi si avvia due volte (a freddo e da capo) e si controlla:
#
#   1. che l'app non cada (nessun FATAL EXCEPTION del pacchetto);
#   2. che la previsione arrivi e si legga ("previsione pronta" nel logcat):
#      e' li' che una regola di keep mancante per kotlinx.serialization si
#      vede, perche' la deserializzazione rotta non fa cadere niente;
#   3. che il processo sia ancora vivo dopo.
#
# Se la debug, prima, non aveva ricevuto la previsione nemmeno lei, la rete del
# runner e' giu' (succede: Open-Meteo in timeout dal runner) e la prova non
# puo' dire niente sul punto 2: si avvisa invece di fallire.
set -uo pipefail

PKG="com.caelumweather"
ACT="$PKG/.MainActivity"
OUT="/tmp/ciout"
mkdir -p "$OUT"
adbt() { timeout 60 adb "$@"; }

APK=$(find apk-release -name '*.apk' 2>/dev/null | head -1)
if [ -z "$APK" ]; then
  echo "::error::prova della release: nessun APK in apk-release/"
  exit 1
fi

# La debug ha avuto la previsione? Lo dice il logcat che capture.sh ha raccolto.
rete_ok=1
if ! grep -q "previsione pronta" "$OUT/logcat-streaming.txt" 2>/dev/null; then
  rete_ok=0
  echo "la build di debug non ha ricevuto la previsione: la rete del runner sembra giu'"
fi

echo "== installo la release $APK sopra la debug =="
if ! adbt install -r -g "$APK"; then
  echo "::error::prova della release: installazione fallita"
  exit 1
fi

falliti=0
for giro in freddo dacapo; do
  echo "== avvio $giro =="
  adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adbt logcat -c >/dev/null 2>&1 || true
  adbt shell am start -W -n "$ACT" >/dev/null 2>&1 || true

  pronta=0
  for _ in $(seq 1 60); do
    if adbt shell logcat -d -s meteo:I 2>/dev/null | grep -q "previsione pronta"; then
      pronta=1; break
    fi
    sleep 1
  done
  sleep 3

  adbt shell screencap -p /data/local/tmp/rel.png >/dev/null 2>&1
  adbt pull /data/local/tmp/rel.png "$OUT/release-$giro.png" >/dev/null 2>&1 || true
  adbt logcat -d -v time > "$OUT/logcat-release-$giro.txt" 2>/dev/null || true

  if grep -A30 "FATAL EXCEPTION" "$OUT/logcat-release-$giro.txt" | grep -q "$PKG"; then
    echo "::error::release ($giro): l'app e' caduta. Vedi logcat-release-$giro.txt"
    grep -A30 "FATAL EXCEPTION" "$OUT/logcat-release-$giro.txt" | head -40
    falliti=1
    continue
  fi
  if [ -z "$(adbt shell pidof "$PKG" 2>/dev/null | tr -d '\r')" ]; then
    echo "::error::release ($giro): il processo non e' vivo dopo l'avvio"
    falliti=1
    continue
  fi
  if [ "$pronta" -eq 1 ]; then
    echo "release ($giro): previsione letta"
  elif [ "$rete_ok" -eq 1 ]; then
    echo "::error::release ($giro): la debug ha letto la previsione, la release no in 60 s. Possibile regola R8 mancante: vedi logcat-release-$giro.txt"
    grep -E "Serializ|ClassNotFound|NoSuchMethod|NoSuchField|meteo" "$OUT/logcat-release-$giro.txt" | head -30
    falliti=1
  else
    echo "::warning::release ($giro): previsione non arrivata, ma la rete del runner era giu' anche per la debug: punto non verificato"
  fi
done

if [ "$falliti" -eq 0 ]; then
  echo "::notice::release (R8) avviata sull'emulatore due volte senza cadere$([ "$rete_ok" -eq 1 ] && echo ", previsione letta")"
fi
exit "$falliti"
