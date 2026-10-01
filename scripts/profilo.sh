#!/usr/bin/env bash
# Genera il baseline profile sull'emulatore gia' acceso (CONTESTO §45).
#
# Lo chiama il job `profilo` della CI. Uno script e non righe nel workflow:
# `android-emulator-runner` esegue ogni riga di `script` per conto suo, e un
# `if` su piu' righe li' dentro non sopravvive.
set -u
mkdir -p /tmp/ciout

# Il simulatore fa girare la raccolta piu' volte e scrive il file alla fine.
# EMULATOR fra gli errori soppressi: la libreria rifiuta gli emulatori per le
# misure di tempo, ma il profilo non e' una misura di tempo.
./gradlew :baselineprofile:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR,DEBUGGABLE,LOW-BATTERY \
  2>&1 | tee /tmp/ciout/raccolta.log
esito=${PIPESTATUS[0]}

# Dove finisce il file dipende dalla versione di AGP e della libreria: si
# cerca in tutti e due i posti noti invece di indovinarne uno.
find baselineprofile/build -name '*baseline-prof*.txt' -exec cp -v {} /tmp/ciout/ \; 2>/dev/null
adb pull /sdcard/Android/media/io.github.noximiliencoxen.caelum.baselineprofile /tmp/ciout/media 2>/dev/null || true
find /tmp/ciout/media -name '*baseline-prof*.txt' -exec cp -v {} /tmp/ciout/ \; 2>/dev/null

echo "--- prodotti ---"
ls -la /tmp/ciout
for f in /tmp/ciout/*baseline-prof*.txt; do
  [ -f "$f" ] || continue
  echo "$f: $(wc -l < "$f") righe"
  head -5 "$f"
done
# Anche i rapporti dei test, che dicono perche' se la raccolta e' fallita.
cp -r baselineprofile/build/outputs/androidTest-results /tmp/ciout/ 2>/dev/null || true
exit "$esito"
