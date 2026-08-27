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
  # /data/local/tmp e' sempre scrivibile dall'utente shell ed esiste da subito.
  # /sdcard no: e' storage emulato, viene montato tardi nel boot ed e' soggetto
  # allo scoped storage. Era la causa degli scatti mancati.
  local dev="/data/local/tmp/shot.png"
  local out="$OUT/$name.png"
  local err

  err=$(adbt shell screencap -p "$dev" 2>&1)
  [ -n "$err" ] && echo "    screencap: $err"
  err=$(adbt pull "$dev" "$out" 2>&1)
  adbt shell rm -f "$dev" >/dev/null 2>&1 || true

  if [ -s "$out" ]; then
    echo "  $name.png ($(stat -c%s "$out") byte)"
    return 0
  fi
  echo "    pull: $err"

  # Ripiego: flusso diretto. Su alcuni emulatori headless si chiude a meta',
  # ma quando il percorso su file fallisce vale la pena provarlo.
  adbt exec-out screencap -p > "$out" 2>/dev/null
  if [ -s "$out" ]; then
    echo "  $name.png ($(stat -c%s "$out") byte, via exec-out)"
    return 0
  fi

  echo "  $name.png NON catturato"
  rm -f "$out"
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
  # `--ez benvenuto false`: al primo avvio la schermata di benvenuto si mette
  # davanti a tutto, e senza saltarla ogni scatto ritrarrebbe quella invece
  # della scena.
  adbt shell am start -W -n "$ACT" --ez benvenuto false 2>&1 | sed 's/^/    /' || true
  sleep 10
  alive || { echo "dispositivo caduto subito dopo l'avvio dell'app"; return; }

  shoot "${slug}-1-temp"

  # Prova della parallasse: l'emulatore sa simulare l'accelerometro, quindi
  # l'inclinazione e' verificabile qui e non solo a mano sul telefono. La linea
  # di base insegue la posa in qualche secondo, percio' lo scatto va preso
  # subito dopo aver mosso il sensore.
  if [ "$tema" = "SCURO" ]; then
    adbt emu sensor set acceleration 3.2:9.2:0 >/dev/null 2>&1 || true
    sleep 2
    shoot "${slug}-1b-inclinato-destra"
    adbt emu sensor set acceleration -3.2:9.2:0 >/dev/null 2>&1 || true
    sleep 2
    shoot "${slug}-1c-inclinato-sinistra"
    adbt emu sensor set acceleration 0:9.81:0 >/dev/null 2>&1 || true
    sleep 1

    # Rotazione col dito. "input motionevent" tiene premuto fra un comando e
    # l'altro, quindi lo stato intermedio del gesto e' fotografabile: con una
    # swipe non lo era, perche' finisce prima dello scatto.
    CX=$(( W / 2 ))
    CY=$(( H * 30 / 100 ))
    adbt shell input motionevent DOWN "$CX" "$CY" >/dev/null 2>&1 || true
    adbt shell input motionevent MOVE "$(( CX + 260 ))" "$CY" >/dev/null 2>&1 || true
    sleep 1
    shoot "${slug}-1d-ruotato-col-dito"
    adbt shell input motionevent UP "$(( CX + 260 ))" "$CY" >/dev/null 2>&1 || true
    sleep 1

    # Stati che i dati veri oggi non offrono: a Forli' e' sereno tutte le
    # ventiquattro ore, quindi nuvole e pioggia non comparirebbero mai in uno
    # scatto. L'app accetta di imporli, come gia' fa per il tema.
    restart_with() {
      adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
      sleep 1
      # shellcheck disable=SC2086
      adbt shell am start -n "$ACT" --ez benvenuto false $1 >/dev/null 2>&1 || true
      # Ogni riavvio rifa' la richiesta di rete: otto secondi non bastavano e
      # gli scatti coglievano i trattini invece dei dati.
      sleep 14
    }

    restart_with "--ei ora 2"
    shoot "${slug}-2-notte-luna"

    restart_with "--ei meteo 63"
    shoot "${slug}-3-pioggia"

    restart_with "--ei ora 2 --ei meteo 63"
    shoot "${slug}-4-pioggia-di-notte"

    restart_with "--ei meteo 3"
    shoot "${slug}-5-coperto"

    # Gli stati nuovi. La neve va fotografata dopo qualche secondo di caduta,
    # altrimenti la coltre sopra la cifra non ha ancora avuto tempo di posarsi:
    # i quattordici secondi di restart_with bastano.
    restart_with "--ei meteo 75"
    shoot "${slug}-7-neve"

    # E subito dopo con un giro di dito, per vedere se la coltre si stacca.
    adbt shell input motionevent DOWN "$CX" "$CY" >/dev/null 2>&1 || true
    adbt shell input motionevent MOVE "$(( CX + 300 ))" "$CY" >/dev/null 2>&1 || true
    sleep 1
    shoot "${slug}-7b-neve-che-scivola"
    adbt shell input motionevent UP "$(( CX + 300 ))" "$CY" >/dev/null 2>&1 || true

    restart_with "--ei meteo 45"
    shoot "${slug}-8-nebbia"

    restart_with "--ei meteo 95"
    shoot "${slug}-9-temporale"

    # L'ora dorata: la finestra e' di quarantacinque minuti attorno all'alba e
    # al tramonto, quindi l'ora giusta dipende dalla stagione. Due scatti a
    # cavallo del tramonto d'agosto a Forli' (circa le 20).
    restart_with "--ei ora 20"
    shoot "${slug}-10-tramonto"

    # Il foglio di dettaglio: mai verificato finora.
    restart_with ""
    adbt shell input swipe "$CX" "$(( H * 78 / 100 ))" "$CX" "$(( H * 20 / 100 ))" 420 >/dev/null 2>&1 || true
    sleep 2
    shoot "${slug}-6-dettaglio"
  fi

  adbt shell input swipe "$FROM_X" "$MID_Y" "$TO_X" "$MID_Y" 320 >/dev/null 2>&1 || true
  sleep 2
  shoot "${slug}-2-precip"
  adbt shell input swipe "$FROM_X" "$MID_Y" "$TO_X" "$MID_Y" 320 >/dev/null 2>&1 || true
  sleep 2
  shoot "${slug}-3-vento"
}

session SCURO
session CHIARO

# ---------------------------------------------------------------------------
# Fotogrammi disegnati a riposo
# ---------------------------------------------------------------------------
#
# La misura che conta e' **quanti** fotogrammi l'app disegna con nessun dito
# sullo schermo, non quanto ci mette a disegnarli: l'emulatore usa swiftshader,
# e i suoi millisecondi non dicono niente su un telefono vero. Il conteggio si'.
#
# La regola del progetto e' che da fermo l'app deve disegnare zero fotogrammi.
# Adesso quella regola vale solo negli stati davvero fermi - coperto, aria
# ferma, niente che cada - perche' neve, nebbia, uccelli e stelle cadenti sono
# animazioni, e un'animazione costa fotogrammi per definizione. Qui si verifica
# che negli stati fermi lo zero ci sia ancora, e si scrive quanto costano gli
# altri.
misura() {
  local nome="$1" extra="$2" gesto="${3:-fermo}"
  alive || return
  adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
  sleep 1
  # shellcheck disable=SC2086
  adbt shell am start -n "$ACT" --ez benvenuto false $extra >/dev/null 2>&1 || true
  sleep 14
  adbt shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1 || true

  if [ "$gesto" = "rotazione" ]; then
    # Sotto il dito e' l'unico momento in cui la cifra ridisegna la geometria a
    # ogni fotogramma: e' li' che si misura il caso peggiore, non da fermo.
    local cx=$(( W / 2 )) cy=$(( H * 30 / 100 ))
    adbt shell input motionevent DOWN "$cx" "$cy" >/dev/null 2>&1 || true
    local k
    for k in 60 120 180 240 300 240 180 120 60; do
      adbt shell input motionevent MOVE "$(( cx + k ))" "$cy" >/dev/null 2>&1 || true
    done
    adbt shell input motionevent UP "$(( cx + 60 ))" "$cy" >/dev/null 2>&1 || true
  else
    sleep 5
  fi

  # Due letture, perche' rispondono a due domande diverse.
  #
  # Le percentuali dicono **quanto si nota**: la mediana com'e' di solito, il
  # novantacinquesimo quanto va male quando va male. Il conteggio da solo non
  # distingue fluido da lento.
  #
  # Le colonne di framestats dicono **da che parte sta il costo**. Da DrawStart
  # a SyncQueued c'e' il lavoro del thread di interfaccia, cioe' il codice
  # Kotlin che registra i comandi; da IssueDrawCommandsStart a SwapBuffers c'e'
  # quello del thread di rendering, cioe' i pixel davvero riempiti. Ottimizzare
  # senza sapere quale dei due pesa vuol dire tirare a indovinare, ed e' gia'
  # costato caro (vedi la trappola numero 9 in CONTESTO.md).
  adbt shell dumpsys gfxinfo "$PKG" framestats 2>/dev/null | tr -d '\r' \
    | awk -F, -v nome="$nome" '
        # La funzione sta prima delle regole: e la collocazione consueta, e
        # mawk non sempre digerisce una funzione dichiarata in mezzo.
        function mediana(v, count,   i, j, key) {
          # Ordinamento per inserzione scritto a mano: asort e di gawk, e il
          # runner monta mawk. Su centoventi fotogrammi la differenza non si
          # misura, e cosi lo script gira ovunque.
          for (i = 1; i < count; i++) {
            key = v[i]; j = i - 1
            while (j >= 0 && v[j] > key) { v[j + 1] = v[j]; j-- }
            v[j + 1] = key
          }
          return v[int(count / 2)]
        }
        # n va azzerata esplicitamente. In awk gli indici degli array sono
        # STRINGHE: con n non inizializzata, ui[n] scrive alla chiave "" e non
        # alla chiave "0", e il primo campione finisce in un posto che nessuno
        # rilegge. La mediana usciva sbagliata di un campione su tre, e sui dati
        # veri sarebbe passata inosservata perche resta un numero plausibile.
        BEGIN { n = 0 }
        /^---PROFILEDATA---/ { dentro = !dentro; next }
        dentro && $1 ~ /^[0-9]+$/ && NF >= 17 {
          # Le colonne documentate sono 8 DrawStart, 12 SyncQueued,
          # 14 IssueDrawCommandsStart, 15 SwapBuffers - contate da ZERO, mentre
          # awk conta i campi da UNO: qui diventano 9, 13, 15 e 16. Sbagliare
          # quello scarto non da errore, da numeri: la prima scrittura misurava
          # linizio delle traversate invece del disegno e riportava mediane
          # negative. E la stessa trappola numero 9 di CONTESTO.md, presa una
          # seconda volta e stanata solo perche provata su dati finti.
          ui[n] = ($13 - $9) / 1000000
          rt[n] = ($16 - $15) / 1000000
          n++
        }
        END {
          # Zero fotogrammi non segnala un guasto: negli stati fermi e il
          # risultato che si cerca, ed e la regola del progetto.
          if (n == 0) { printf "  %-22s nessun fotogramma disegnato\n", nome; exit }
          printf "  %-22s interfaccia %.1f ms   rendering %.1f ms   (mediane su %d fotogrammi)\n",
                 nome, mediana(ui, n), mediana(rt, n), n
        }' | tee -a "$OUT/prestazioni.txt"

  # Le percentuali di gfxinfo qui non si usano, e non e' una dimenticanza.
  # Su swiftshader l'emulatore non aggancia il vsync nemmeno a schermo fermo:
  # riporta ogni fotogramma "in ritardo" e mediane da centocinquanta
  # millisecondi, che misurano il rasterizzatore software del runner e non
  # l'app. Le colonne di framestats invece misurano il lavoro svolto, ed e'
  # quello che si voleva sapere.
}

echo "== prestazioni ==" | tee "$OUT/prestazioni.txt"
echo "-- a riposo: qui il totale deve essere zero --" | tee -a "$OUT/prestazioni.txt"
misura "coperto (fermo)"   "--ei meteo 3 --ei vento 0"
misura "notte (fermo)"     "--ei ora 2 --ei meteo 3 --ei vento 0"
echo "-- animato: qui contano i millisecondi --" | tee -a "$OUT/prestazioni.txt"
misura "coperto ventoso"   "--ei meteo 3 --ei vento 10"
misura "sereno di giorno"  "--ei meteo 0 --ei vento 0"
misura "pioggia"           "--ei meteo 63"
misura "neve"              "--ei meteo 75"
misura "nebbia"            "--ei meteo 45"
misura "temporale"         "--ei meteo 95"
echo "-- sotto il dito: il caso peggiore --" | tee -a "$OUT/prestazioni.txt"
misura "rotazione, sereno" "--ei meteo 0 --ei vento 0" rotazione
misura "rotazione, neve"   "--ei meteo 75" rotazione

sleep 2
pkill -f "adb logcat -v time" >/dev/null 2>&1 || true
grep -iE "forli|meteo|AndroidRuntime|FATAL|Exception|OutOfMemory|died|crash" \
  "$OUT/logcat-streaming.txt" 2>/dev/null | tail -120 > "$OUT/logcat-rilevante.txt" || true

echo "== file prodotti =="
ls -la "$OUT"

shots=$(find "$OUT" -name "*.png" -size +0 | wc -l)
echo "scatti riusciti: $shots"
[ "$shots" -gt 0 ] || { echo "nessuno scatto prodotto"; exit 1; }
