---
name: prova-sul-telefono
description: Come compilare Caelum, installarla sul telefono collegato via adb e verificare davvero una correzione - riprodurre un crash, pilotare un widget senza toccare lo schermo, leggere logcat e confrontare gli scatti. Usala ogni volta che una modifica va provata sul dispositivo invece che dedotta, e prima di dichiarare risolto un difetto che si vede solo in mano.
---

# Provare Caelum sul telefono collegato

Su questa macchina si compila e si prova sul dispositivo: il giro dura una
manciata di secondi, quindi **conviene misurare invece di dedurre**. Questa
skill e' il giro verificato, non una procedura generica.

`CONTESTO.md` §1 ha i vincoli d'ambiente e §11 il giro dei rami: leggili, non
sono ripetuti qui.

## Prima di tutto: due trappole dell'ambiente

Stanno in `CONTESTO.md` §1 e fanno perdere mezz'ora a chi non le conosce.

- **La shell POSIX qui non crea file o cartelle nuove.** `sed -i`, `perl -i` e
  ogni redirezione verso un file nuovo falliscono con *Permission denied* o
  *Cannot make temp name*. Per modificare file usa gli strumenti di scrittura
  dell'agente; per i comandi, PowerShell. Leggere, cercare e `git` funzionano
  normalmente da bash.
- **`adb` da Git Bash storpia i percorsi assoluti del dispositivo**:
  `/data/local/tmp` diventa `C:/Program Files/Git/data/local/tmp`. **Chiama adb
  da PowerShell.**

`adb` non e' nel PATH. Sta qui:

```
$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe
```

Il pacchetto e' `io.github.noximiliencoxen.caelum`.

## Compila e installa

```bash
./gradlew :app:assembleDebug
```

poi, da PowerShell:

```
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

**La build di debug non e' debuggabile.** `isDebuggable = false` in
`app/build.gradle.kts` e' una scelta voluta e commentata: questa app fa
geometria in tempo reale, e misurare la fluidita' su una build debuggabile
vorrebbe dire misurare un'app che non esiste. Conseguenza pratica: **`run-as`
non funziona**, quindi il DataStore non si legge da fuori. Per sapere cosa c'e'
dentro si mette un `Log` temporaneo — e **lo si toglie prima del commit**, che
e' il punto di `CONTESTO.md` §37.

## La controprova, che e' la parte che vale

Una correzione non e' dimostrata da "adesso non succede". Succede o non succede
anche per caso, e i difetti di temporizzazione sono quasi tutti intermittenti.

**Ricostruisci la build senza la correzione e mostra che il difetto torna.**
E' cosi' che il crash del pulsante TROVAMI e' passato da ipotesi a fatto:
build di controllo, crash al primo tocco; build corretta, cinque giri su cinque
puliti, stesso identico script. Senza il primo dei due numeri il secondo non
vuol dire niente.

Il giro: metti da parte il file corretto, annulla la sola riga della correzione,
`assembleDebug`, installa, esegui lo script, verifica che il difetto ci sia,
poi ripristina e ripeti.

## Riprodurre il crash della schermata di benvenuto

Due condizioni, e **la seconda e' quella che non si indovina**.

1. La schermata compare solo con `welcomed = false`: serve
   `adb shell pm clear io.github.noximiliencoxen.caelum`. **Cancella le
   localita' salvate e i preferiti: chiedi prima di farlo.**
2. Il permesso di posizione dev'essere **gia' concesso**:
   `adb shell pm grant io.github.noximiliencoxen.caelum android.permission.ACCESS_COARSE_LOCATION`
   (`pm clear` lo revoca, quindi va in quest'ordine).

Il perche' della seconda: se il permesso va chiesto, si apre la finestra di
sistema, che ruba il fuoco e **ferma la ricomposizione** — i fotogrammi del
difetto non vengono mai composti. Misurato: dieci pressioni senza il permesso,
zero crash; una sola pressione con il permesso concesso, crash. E' anche la
ragione per cui quel difetto sembrava colpire un telefono solo.

Il tocco va dato come **pressione con rilascio**, non come tap: molte animazioni
partono al rilascio.

```
adb shell input swipe 640 1967 640 1967 450
```

Le coordinate si prendono da `uiautomator dump` (sotto), non a memoria.

## Pilotare un widget senza toccare lo schermo

Gli identificativi dei widget piazzati stanno in `dumpsys`, nella sezione
`Widgets:` (non in quella `provider`, che elenca i tipi disponibili):

```
adb shell dumpsys appwidget
```

La configurazione di **una** istanza si apre direttamente:

```
adb shell "am start -n io.github.noximiliencoxen.caelum/.widget.WidgetConfigActivity -a android.appwidget.action.APPWIDGET_CONFIGURE --ei appWidgetId <id>"
```

Da li' si legge la schermata e si ricavano i riquadri su cui battere:

```
adb shell "uiautomator dump /sdcard/ui.xml"
adb shell "cat /sdcard/ui.xml"
```

Cerca gli attributi `text="…"` e `bounds="[x1,y1][x2,y2]"`, e batti al centro
del riquadro con `adb shell input tap <cx> <cy>`.

### Quello che da adb non si puo' fare

Due cose, e sapere che sono impossibili evita di perderci tempo:

- **posare un widget sulla Home**: non esiste un comando di shell
  (`cmd appwidget` risponde *No shell command implementation*);
- **inviare `APPWIDGET_DELETED`**: e' un broadcast protetto, `am broadcast`
  viene rifiutato.

Per entrambe servono le mani di una persona. Chiedile esplicitamente, e nel
frattempo prepara i buffer.

## Leggere gli esiti

```
adb logcat -c -b crash -b main      # azzera prima della prova
adb logcat -b crash -d              # gli schianti, dopo
adb shell pidof io.github.noximiliencoxen.caelum   # il processo e' sopravvissuto?
```

Il buffer `crash` e' piccolo e mirato: guardalo per primo. Un `FATAL EXCEPTION`
su un thread di coroutine uccide comunque il processo.

**Gli scatti sono l'unico modo per certi difetti.** Un nome di localita' che
sborda dal riquadro e finisce sotto un altro elemento non compare in nessun
log: si vede e basta.

```
adb shell screencap -p /data/local/tmp/shot.png
adb pull /data/local/tmp/shot.png
```

Usa `/data/local/tmp` e non `/sdcard`, come fa gia' `scripts/capture.sh`:
`/sdcard` e' storage emulato, montato tardi nel boot e soggetto allo scoped
storage, ed era la causa degli scatti mancati in CI. Su un telefono acceso da un
pezzo `/sdcard` funziona lo stesso — ma e' fortuna, non una garanzia.

Per **provare il disegno con un caso difficile**, configura una localita' dal
nome lungo (`Aoraki / Monte Cook` e' quella che ha fatto emergere il difetto
della sezione 47) invece di una corta come Milano.

## Cosa esiste gia' e non va riscritto

- **`scripts/capture.sh`** — installazione, scatti nei due temi, logcat in
  streaming, attesa che la previsione arrivi. E' scritto per l'emulatore della
  CI, ma le sue soluzioni valgono anche qui: in particolare il timeout su ogni
  chiamata (`adbt() { timeout 60 adb "$@"; }`), senza il quale una adb su
  dispositivo caduto resta appesa per sempre.
- **`MainActivity`** ha gia' degli agganci per la verifica automatica:
  `adb shell am start -n …/.MainActivity --ei ora 2 --ei meteo 63` impone ora e
  condizione, perche' certi stati dal meteo vero non arrivano mai.

## Prima di chiudere

- Togli i `Log` temporanei: `grep -rn "WidgetTrace\|Log.d" app/src/main/kotlin`.
- `./gradlew :app:lintDebug` — dev'essere a 0 errori.
- Racconta cosa hai verificato **e cosa no**. "Compila" non e' "funziona", e
  "non ho visto il crash" non e' "il crash non c'e'".
