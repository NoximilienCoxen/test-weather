#!/usr/bin/env bash
# Installa l'APK sull'emulatore e fotografa le schermate nei due temi:
# principale, benvenuto, impostazioni, e le sei pagine del dettaglio piu'
# il dettaglio di un giorno.
#
# Ogni chiamata adb ha un timeout: senza, una adb su dispositivo caduto resta
# appesa per sempre. Niente set -e, perche' voglio comunque il logcat; ma alla
# fine lo script fallisce se non ha prodotto nemmeno uno scatto: un job verde
# che non produce nulla e' peggio di un job rosso.
set -uo pipefail

PKG="io.github.noximiliencoxen.caelum"
ACT="$PKG/.MainActivity"
OUT="/tmp/ciout"
mkdir -p "$OUT"

# Quanti scatti sono stati chiesti e non sono usciti.
#
# Serve perche' un job verde con meta' degli scatti mancanti e' **peggio** di
# un job rosso: sembra una verifica fatta. E' successo davvero - l'emulatore e'
# morto a meta' corsa, il logcat dell'app finiva pulito senza un solo errore, e
# il job ha riportato successo con dodici scatti in meno. Il controllo finale
# guardava solo che ce ne fosse almeno uno.
MANCATI=0

# Secondi massimi di attesa perche' la previsione arrivi dopo un riavvio.
# E' un tetto, non una durata: si esce appena l'app dice di averla (vedi
# attendi_previsione), quindi tenerlo largo non costa niente.
ATTESA_DATI=45

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
  MANCATI=$(( MANCATI + 1 ))
  return 1
}

# Aspetta che la previsione sia arrivata davvero.
#
# Per tre giri il rimedio era stato alzare l'attesa: otto secondi, poi
# quattordici, poi diciannove. A diciannove uno scatto su undici e' uscito lo
# stesso "IN ATTESA DEI DATI", ed e' li' che si vede che il numero non era mai
# il problema: aspettare una **durata** e' scommettere sulla rete del runner, e
# ogni tanto la scommessa si perde. L'app scrive una riga quando la previsione
# atterra, quindi qui si aspetta quella. Il tetto di tempo serve solo a non
# restare appesi per sempre.
#
# `uiautomator dump` sarebbe la via ovvia e qui non si puo' usare: aspetta che
# la finestra sia ferma, e questa schermata anima in continuazione per scelta.
attendi_previsione() {
  local i
  for i in $(seq 1 "$ATTESA_DATI"); do
    if adbt shell logcat -d -s meteo:I 2>/dev/null | grep -q "previsione pronta"; then
      # Un istante perche' la composizione coi dati arrivi a schermo.
      sleep 1
      return 0
    fi
    sleep 1
  done
  echo "    previsione non arrivata in ${ATTESA_DATI}s: lo scatto uscira' senza dati"
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

# Il benvenuto viene prima di tutto, e non solo perche' e' la prima cosa che si
# vede: finche' non lo si chiude **l'app lo rimostra a ogni avvio**, e ogni altro
# scatto di questo script ritrarrebbe lui invece della schermata che dice di
# ritrarre. Chiuderlo scrive la preferenza, e da li' in poi non torna piu'.
welcome() {
  echo "== benvenuto =="
  adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
  sleep 1
  # Con le animazioni accese (trappola #28), se no il pupazzo sta sempre nella
  # stessa posa e non si vede che si guarda intorno.
  adbt shell settings put global animator_duration_scale 1 >/dev/null 2>&1 || true
  adbt shell am start -n "$ACT" --ez benvenuto true >/dev/null 2>&1 || true
  sleep 12
  shoot "00-benvenuto"
  sleep 1
  shoot "00-benvenuto-guarda"
  adbt shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true

  # "SCELGO IO" chiude il benvenuto per sempre e apre le impostazioni: due cose
  # con un tocco solo, e le impostazioni non avevano ancora nessuno scatto.
  adbt shell input tap "$(( W / 2 ))" "$(( H * 71 / 100 ))" >/dev/null 2>&1 || true
  sleep 3
  shoot "00-impostazioni"
  # E si richiudono dal loro pulsante, in alto a sinistra.
  adbt shell input tap 65 "$(( H * 8 / 100 ))" >/dev/null 2>&1 || true
  sleep 2
}

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
  # Il secondo tema gira dopo il primo: senza svuotare, l'attesa troverebbe
  # la riga del tema precedente e passerebbe all'istante.
  adbt shell logcat -c >/dev/null 2>&1 || true
  # -W attende che l'attivita' sia effettivamente in primo piano e riporta
  # l'esito, invece di lasciarmi indovinare con una sleep fissa.
  # `--es tema` l'app non lo legge piu' (vedi il blocco del dettaglio): resta
  # solo perche' `-W` vuole un comando da attendere, e toglierlo cambierebbe la
  # riga senza cambiare nulla di cio' che si vede. Il cielo di questo primo
  # scatto e' quello dell'ora vera del runner, ed e' voluto: e' l'unico scatto
  # che ritrae l'app come la si trova aprendola.
  adbt shell am start -W -n "$ACT" 2>&1 | sed 's/^/    /' || true
  # -W dice che l'attivita' e' in primo piano, non che ha qualcosa da mostrare:
  # la richiesta di rete parte dopo. Il primo scatto e' quello che si guarda
  # per primo, quindi vale l'attesa vera come per tutti gli altri.
  attendi_previsione
  alive || { echo "dispositivo caduto subito dopo l'avvio dell'app"; return; }

  shoot "${slug}-1-temp"

  # ── Prima il nuovo, poi il gia' verificato ──────────────────────────────────
  #
  # Il cielo, il dettaglio e le ore di contrasto vengono **prima** delle prove
  # del motore 3D, e non e' l'ordine naturale: e' che l'emulatore della CI se ne
  # va a meta' corsa, in modo riproducibile ma in punti diversi, e quel che
  # resta fuori e' sempre la coda. Le prove del motore hanno una galleria alle
  # spalle e possono permettersi di saltare un giro; queste schermate no, non
  # hanno mai avuto uno scatto.
  #
  # **E il cielo viene per primo di tutti**, prima ancora del foglio di
  # dettaglio. Non e' una gerarchia di importanza: e' che negli ultimi due giri
  # il dispositivo e' morto **dentro** il carosello del dettaglio, alla pagina
  # dell'aria, e da li' in poi non e' arrivato piu' niente - ne' le ore di
  # contrasto, ne' le allerte. Quattro riavvii veloci messi qui costano meno di
  # un minuto e sono gli unici scatti che dicono se il fondo e' un cielo.

  # ── Il cielo alle sue ore ───────────────────────────────────────────────────
  #
  # Alba e tramonto vanno fotografati **tutti e due**: hanno la stessa altezza
  # del sole e tavolozze diverse - rosa e freddo l'una, arancio e caldo l'altra
  # - e con un solo scatto non c'e' modo di accorgersi se la distinzione
  # funziona o se le due si somigliano.
  #
  # Il sereno di mezzogiorno dice se il fondo e' azzurro invece che grigio, ed
  # e' lo scatto che risponde alla richiesta. Il coperto alla stessa ora e' il
  # suo controllo: adesso il grigio c'e' **solo** quando vuol dire qualcosa, e
  # senza i due affiancati non si distingue una regola da una coincidenza.
  #
  # Solo nella sessione scura: sono scatti della schermata principale a un'ora
  # imposta, e farli due volte darebbe due file identici.
  if [ "$tema" = "SCURO" ]; then
    cielo() {
      adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
      sleep 1
      adbt shell logcat -c >/dev/null 2>&1 || true
      adbt shell am start -n "$ACT" --ei ora "$1" --ei meteo "$2" >/dev/null 2>&1 || true
      attendi_previsione
      # **Il cielo va aspettato anche dopo che i dati sono arrivati**, ed e' il
      # rovescio della trappola #28: quella dice che con le animazioni spente
      # tutto salta alla fine, questa dice cosa succede quando invece sono
      # vive. Ad app appena avviata l'altezza del sole parte dal ripiego
      # diurno, e la molla ci mette piu' del secondo che aspetta
      # `attendi_previsione` ad arrivare a un'ora notturna: il primo scatto
      # dell'alba e' uscito con il cielo di mezzogiorno e il sole alto, cioe'
      # ritraeva il viaggio invece della destinazione.
      #
      # Si vedeva anche dal testo: "IN ATTESA DEI DATI" sotto una riga di
      # minima e massima gia' piene - due stati che non possono coesistere se
      # non a meta' di una dissolvenza.
      sleep 3
      shoot "cielo-$3"
    }
    cielo  6 0 alba
    cielo 12 0 mezzogiorno-sereno
    cielo 20 0 tramonto
    cielo 12 3 mezzogiorno-coperto
    alive || { echo "dispositivo caduto dopo gli scatti del cielo"; return; }
  fi

  # ── Il foglio di dettaglio ──────────────────────────────────────────────────
  #
  # **Finora non e' mai stato fotografato davvero.** Lo scatto che si chiamava
  # "dettaglio" ritraeva la schermata principale: la trascinata che doveva
  # aprire il foglio partiva dal settantotto per cento dell'altezza, dove la
  # barra delle ore intercetta il gesto, e non apriva niente. Nessuno se n'e'
  # accorto perche' le due schermate, a colpo d'occhio, cominciano uguali.
  #
  # Qui si apre col **tocco sulla cifra**, che e' deterministico: la schermata
  # principale distingue un tocco fermo da un trascinamento
  # (`detectTapOrRotate`), e il tocco apre il dettaglio.
  #
  # E si fotografa **a due ore diverse**, non solo al buio: il punto di questa
  # passata e' che ogni scritta si legga a qualunque ora, e finora non c'era
  # uno scatto che lo mostrasse.
  #
  # L'ora si impone con `--ei ora`, non con `--es tema`: quell'aggancio **non
  # esiste piu'** - `MainActivity` legge soltanto `ora`, `meteo`, `giro` e
  # `benvenuto`, e giorno e notte li decide l'ora mostrata. Le due sessioni
  # continuavano a passarselo e a fotografare due volte lo stesso cielo,
  # qualunque fosse, senza che il nome del file lo lasciasse sospettare.
  local ora_dettaglio
  case "$tema" in
    SCURO) ora_dettaglio=2  ;;   # notte piena
    *)     ora_dettaglio=13 ;;   # mezzogiorno passato, fondo grigio chiaro
  esac

  echo "  -- dettaglio (ora $ora_dettaglio) --"
  adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
  sleep 1
  adbt shell logcat -c >/dev/null 2>&1 || true
  adbt shell am start -n "$ACT" --ei ora "$ora_dettaglio" >/dev/null 2>&1 || true
  attendi_previsione
  alive || { echo "dispositivo caduto prima del dettaglio"; return; }

  local cx=$(( W / 2 ))
  adbt shell input tap "$cx" "$(( H * 52 / 100 ))" >/dev/null 2>&1 || true
  sleep 2
  shoot "${slug}-d1-temperatura"

  # Le altre cinque pagine, raggiunte scorrendo sul contenuto sotto la cifra:
  # e' il gesto vero, quello che usa chi guarda. Sulla cifra invece il gesto
  # orizzontale gira la scena, e i due non si contendono niente proprio perche'
  # stanno in due zone diverse.
  #
  # La luna chiude la fila: il suo eroe non e' una cifra ma la sfera, quindi e'
  # l'unico scatto del giro in cui si vede se il corpo e' arrivato al posto
  # della cifra invece che accanto.
  local pager_y=$(( H * 72 / 100 ))
  local n=2
  for pagina in sole pioggia vento aria luna; do
    adbt shell input swipe "$FROM_X" "$pager_y" "$TO_X" "$pager_y" 320 >/dev/null 2>&1 || true
    sleep 2
    shoot "${slug}-d${n}-${pagina}"
    n=$(( n + 1 ))
  done

  # Si esce dal foglio con l'indietro di sistema: e' anche una prova che il
  # BackHandler sia agganciato.
  adbt shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 2
  shoot "${slug}-d7-tornato-alla-principale"

  # ── Il dettaglio di un giorno ───────────────────────────────────────────────
  #
  # Si apre con l'aggancio `--ei giorno` e **non col dito**, e non e' una
  # scorciatoia: la settimana sta in coda a una pagina che scorre, quindi per
  # toccarla bisogna prima scorrere, e la trascinata lunga che ci vuole **fa
  # morire l'emulatore**. Provato due volte, stesso punto esatto: il logcat
  # dell'app finisce pulito - nessuna eccezione, nessun ANR - e sparisce la
  # macchina virtuale, non l'app. E' la stessa ragione per cui esiste
  # l'aggancio sul giro: certi stati, qui, col dito non si raggiungono.
  adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
  sleep 1
  adbt shell logcat -c >/dev/null 2>&1 || true
  adbt shell am start -n "$ACT" --ei ora "$ora_dettaglio" --ei giorno 2 >/dev/null 2>&1 || true
  attendi_previsione
  sleep 1
  shoot "${slug}-d7-giorno"

  # ── La fascia delle allerte ─────────────────────────────────────────────────
  #
  # Con un'allerta imposta, non aspettando che ne arrivi una vera: la fascia
  # compare solo quando la Protezione Civile ha diramato qualcosa sulla
  # localita' mostrata, cioe' quasi mai e mai su richiesta. Fotografarla solo
  # nei giorni di maltempo vuol dire non fotografarla, e un riquadro che non e'
  # mai stato visto in uno scatto e' un riquadro che nessuno ha verificato.
  #
  # Due scatti: la principale, dove la fascia sta in cima, e il dettaglio, dove
  # deve convivere con le pillole senza spingerle fuori.
  adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
  sleep 1
  adbt shell am start -n "$ACT" --ei ora "$ora_dettaglio" --ei allerta 2 >/dev/null 2>&1 || true
  attendi_previsione
  sleep 1
  shoot "${slug}-d8-allerta-principale"

  # Il tocco sulla cifra apre il dettaglio: stessa coordinata usata sopra.
  alive || { echo "dispositivo caduto prima dello scatto delle allerte"; return; }
  adbt shell input tap "$cx" "$(( H * 52 / 100 ))" >/dev/null 2>&1 || true
  sleep 2
  shoot "${slug}-d9-allerta-dettaglio"

  # ── L'allerta ridotta a pallino ─────────────────────────────────────────────
  #
  # Lo stato ridotto si raggiunge con un tocco sulla croce, e qui non c'e' un
  # dito: l'aggancio `--ez allertaridotta` lo impone. E' l'unico modo di
  # fotografarlo, e senza scatto sarebbe l'unico pezzo dell'interfaccia che
  # nessuno ha mai verificato.
  #
  # Due cose da guardare in questo scatto, e sono le due che possono rompersi:
  # il nome della localita' deve restare **nella stessa identica posizione**
  # dello scatto d8 - il pallino sta nei 48dp che erano gia' riservati - e il
  # triangolo deve leggersi sul fondo del contenitore d'errore.
  alive || { echo "dispositivo caduto prima dello scatto del pallino"; return; }
  adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
  sleep 1
  adbt shell am start -n "$ACT" --ei ora "$ora_dettaglio" \
    --ei allerta 2 --ez allertaridotta true >/dev/null 2>&1 || true
  attendi_previsione
  sleep 1
  shoot "${slug}-d10-allerta-pallino"

  # Il pallino deve riportare alle allerte per esteso: e' tutto il suo mestiere.
  # Sta in alto a destra, nei 48dp simmetrici al pulsante delle impostazioni.
  alive || { echo "dispositivo caduto prima dello scatto del bollettino"; return; }
  adbt shell input tap "$(( W - 44 ))" "$(( H * 7 / 100 ))" >/dev/null 2>&1 || true
  sleep 2
  shoot "${slug}-d11-allerta-riaperta"

  # ── Le ore in cui il contrasto era peggiore ─────────────────────────────────
  #
  # Il fondo del cielo e il colore del testo si interpolano su due scale
  # diverse e a meta' mattina si incrociano: misurato sulla matematica di
  # `skyColors`, li' il contrasto scendeva a **1,01:1**, cioe' testo della
  # stessa luminanza del fondo. Non l'aveva mai visto nessuno perche' gli
  # scatti coprivano le due ore estreme, che sono le due in cui il contrasto e'
  # al meglio.
  #
  # Solo nella sessione scura: sono scatti della schermata principale, e farli
  # due volte darebbe due file identici.
  if [ "$tema" = "SCURO" ]; then
    for ora in 9 17; do
      adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
      sleep 1
      adbt shell logcat -c >/dev/null 2>&1 || true
      adbt shell am start -n "$ACT" --ei ora "$ora" >/dev/null 2>&1 || true
      attendi_previsione
      shoot "contrasto-ora-${ora}"
    done
  fi

  # Prova della parallasse: l'emulatore sa simulare l'accelerometro, quindi
  # l'inclinazione e' verificabile qui e non solo a mano sul telefono. La linea
  # di base insegue la posa in qualche secondo, percio' lo scatto va preso
  # subito dopo aver mosso il sensore.
  if [ "$tema" = "SCURO" ]; then
    # Il blocco qui sopra lascia l'app sul dettaglio di un giorno: le prove
    # dell'inclinazione vogliono la schermata principale.
    adbt shell am force-stop "$PKG" >/dev/null 2>&1 || true
    sleep 1
    adbt shell logcat -c >/dev/null 2>&1 || true
    adbt shell am start -n "$ACT" >/dev/null 2>&1 || true
    attendi_previsione

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
      # Il buffer va svuotato prima dell'avvio, altrimenti la riga del giro
      # precedente farebbe passare l'attesa all'istante. Il logcat in
      # streaming ha gia' scritto su file quello che ha visto, quindi qui non
      # si perde niente.
      adbt shell logcat -c >/dev/null 2>&1 || true
      # shellcheck disable=SC2086
      adbt shell am start -n "$ACT" --es tema SCURO $1 >/dev/null 2>&1 || true
      attendi_previsione
    }

    # Di notte serve anche un cielo poco nuvoloso: col coperto vero di
    # stanotte la luna non si vedrebbe, ed e' proprio lei che va guardata.
    restart_with "--ei ora 2 --ei meteo 1"
    shoot "${slug}-2-notte-luna"

    restart_with "--ei meteo 63"
    shoot "${slug}-3-pioggia"

    restart_with "--ei ora 2 --ei meteo 63"
    shoot "${slug}-4-pioggia-di-notte"

    restart_with "--ei meteo 3"
    shoot "${slug}-5-coperto"

    # Neve e sereno non avevano nessuno scatto, quindi si scrivevano alla
    # cieca: il fiocco che sbanda e l'uccello che batte le ali si giudicano
    # guardandoli, non rileggendo il codice. Il sereno va forzato a mezzogiorno,
    # se no all'ora della CI il cielo e' notte e gli uccelli non volano.
    restart_with "--ei meteo 73"
    shoot "${slug}-5b-neve"

    restart_with "--ei ora 12 --ei meteo 0"
    shoot "${slug}-5c-sereno-uccelli"

    restart_with "--ei meteo 96"
    shoot "${slug}-5d-grandine"

    # Il quarto e il mezzo giro. E' li' che le matrici della base e dell'ombra
    # degenerano e che le pareti dei vuoti si scavalcano, ed e' proprio li' che
    # col dito non si arriva: per portare la cifra di taglio servono quattrocento
    # pixel di trascinamento, per vederla da dietro piu' di ottocento, e lo
    # schermo e' largo mille.
    restart_with "--ei giro 90"
    shoot "${slug}-7-di-taglio"

    restart_with "--ei giro 135"
    shoot "${slug}-8-tre-ottavi"

    restart_with "--ei giro 180"
    shoot "${slug}-9-da-dietro"

    # La luna deve poter passare davanti alla nuvola: e' tutto il punto
    # dell'ordinamento in profondita' dei corpi tondi.
    restart_with "--ei ora 2 --ei meteo 1 --ei giro 155"
    shoot "${slug}-10-luna-girata"

    restart_with "--ei meteo 63 --ei giro 45"
    shoot "${slug}-11-pioggia-girata"

  fi


}

welcome
session SCURO
session CHIARO

sleep 2
pkill -f "adb logcat -v time" >/dev/null 2>&1 || true
grep -iE "forli|meteo|AndroidRuntime|FATAL|Exception|OutOfMemory|died|crash" \
  "$OUT/logcat-streaming.txt" 2>/dev/null | tail -120 > "$OUT/logcat-rilevante.txt" || true

echo "== file prodotti =="
ls -la "$OUT"

shots=$(find "$OUT" -name "*.png" -size +0 | wc -l)
echo "scatti riusciti: $shots, mancati: $MANCATI"
[ "$shots" -gt 0 ] || { echo "nessuno scatto prodotto"; exit 1; }

# Il segnale vero non e' il numero di scatti mancati - la soglia si azzecca
# sempre per difetto, e infatti con esattamente tre mancati questo controllo
# lasciava passare un giro monco - ma **se il dispositivo e' ancora vivo alla
# fine**. Un emulatore che se n'e' andato a meta' corsa non e' un intoppo
# passeggero: e' un giro da rifare, e il job deve dirlo invece di consegnare
# mezza galleria con la faccia di una verifica completa.
if ! alive; then
  echo "il dispositivo non c'e' piu': il giro e' incompleto ($MANCATI scatti mancati)"
  exit 1
fi
[ "$MANCATI" -eq 0 ] || echo "attenzione: $MANCATI scatti non catturati"
