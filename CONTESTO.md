# Contesto per riprendere il lavoro

Documento di passaggio. Leggilo prima di toccare qualsiasi cosa: contiene
vincoli d'ambiente che non si deducono dal codice e diagnosi gia' pagate care.

---

## 1. L'ambiente: qui NON si compila

**Questo container non puo' costruire l'app.** Non e' una mancanza da colmare,
e' una condizione permanente da cui discende tutto il resto.

- Nessun Android SDK, nessun `adb`.
- `dl.google.com` risponde **403 al CONNECT** (policy di rete
  dell'organizzazione). E' l'unico punto di distribuzione sia dell'SDK sia di
  **tutti gli artefatti AndroidX/Compose/AGP**: `maven.google.com` e' solo un
  redirector verso di esso.
- Nessun `/dev/kvm`: nessun emulatore in locale.
- `api.open-meteo.com` bloccato.

**Raggiungibili invece**: Maven Central, `services.gradle.org`, `github.com`,
`fonts.googleapis.com` / `fonts.gstatic.com`.

### Conseguenza: la CI e' il banco di prova

GitHub Actions gira sull'infrastruttura di GitHub e **non passa da questo
proxy**, quindi raggiunge tutto. Il ciclo di lavoro e':

```
scrivi -> push -> CI compila -> emulatore -> screenshot -> li guardi -> correggi
```

Un giro completo dura **circa 8-9 minuti**. Non e' veloce: conviene raggruppare
piu' correzioni per giro e rileggere il diff prima di pushare.

`.github/workflows/build.yml` ha quattro job:

| Job | Cosa fa |
|---|---|
| `probe-api` | interroga Open-Meteo e pubblica il JSON reale. **Indipendente dal build**, cosi' il contratto API arriva anche se la compilazione fallisce |
| `build` | `assembleDebug` |
| `rilascio` | pubblica l'APK sul tag fisso `apk-latest` |
| `screenshots` | emulatore API 34, cattura, pubblica |

**Gli output finiscono sul branch `ci-artifacts`**, separato da quello di
sviluppo per non generare mai conflitti:

```bash
git fetch origin ci-artifacts
git show origin/ci-artifacts:screenshots/scuro-1-temp.png > /tmp/x.png
git show origin/ci-artifacts:api/hourly.json          # contratto API reale
git show origin/ci-artifacts:screenshots/logcat-streaming.txt
```

**L'APK sta sempre a**
<https://github.com/NoximilienCoxen/test-weather/releases/tag/apk-latest>
(tag fisso, link stabile, chiave di debug fissa versionata cosi' le build si
installano una sopra l'altra senza disinstallare).

---

## 2. Toolchain, verificata alle fonti

Non a memoria: interrogata da Maven Central e dalle note di rilascio AGP.

| | |
|---|---|
| AGP | 9.2.1 — **ha Kotlin integrato**, il plugin `kotlin.android` NON va applicato |
| Gradle | 9.4.1 (minimo e default di AGP 9.2) |
| JDK | 17 |
| compileSdk | **37** — Compose 1.12 lo pretende; 36 non basta |
| targetSdk / minSdk | 36 / 26 |
| Kotlin | 2.4.10 |
| Compose BOM | 2026.08.00 |
| Glance | 1.1.1 |

`kotlin { compilerOptions }` sta a **livello radice**, non dentro `android {}`.
`jvmTarget` non si dichiara: eredita da `compileOptions.targetCompatibility`.

---

## 3. Cosa fa l'app oggi

**Si apre sulla schermata principale** (`ui/home/HomeScreen.kt`): citta',
scultura meteo, temperatura dell'ora corrente, condizione, barra delle 24 ore
colorata per meteo.

**Il dettaglio sale trascinando verso l'alto** (`ui/MeteoApp.kt`): non e' una
schermata diversa ma un foglio che segue il dito, reversibile a meta' corsa,
con la principale che arretra e si smorza dietro. Il dettaglio
(`ui/WeatherScreen.kt`) e' il pager a tre pagine Temp./Precip./Vento gia'
esistente, invariato nella struttura.

Dati: Open-Meteo senza chiave, `HttpURLConnection` + `kotlinx.serialization`.
Una sola richiesta porta `current`, `daily` (7 giorni) e `hourly`.
Citta' cablata: **Forli' 44.2226, 12.0407**.

---

## 4. Il pezzo importante: come e' fatta la cifra

`ui/render/GlyphGeometry.kt` + `ui/render/CanvasRenderer.kt`

Questa e' la terza implementazione. Le prime due sono state bocciate, e il
motivo va capito prima di toccarla.

**Cosa NON fare**: ristampare il glifo lungo un vettore. Produce un volume ma
non produce **facce**: nessun punto della superficie sa com'e' orientato,
quindi nessuno puo' essere illuminato, e il risultato si legge come strati
piatti sovrapposti. L'utente lo ha bocciato con queste parole esatte.

**Come funziona ora**:

1. `Paint.getTextPath()` da' il contorno del testo come tracciato.
2. `PathMeasure` con `nextContour()` percorre i contorni — inclusi i vuoti di
   0, 6, 8 — e li campiona in polilinee.
3. Ogni spigolo genera una faccia con la propria **normale**.
4. Prova di silhouette: si disegna solo se la normale concorda con la
   direzione dell'estrusione.
5. Illuminazione **Lambert dimezzato** (vedi trappola #4).
6. Smussi: seconda corona di facce con normale a meta' strada fra frontale e
   laterale.
7. Facce con la stessa esposizione finiscono nello stesso tracciato: una
   decina di chiamate di disegno invece di millecinquecento.
8. I vertici non cambiano con la luce, cambia solo la fascia: gli orientamenti
   sono quantizzati a 2 gradi e tenuti in cache.

**Il verso dei contorni dipende dal font**, quindi non si assume: si deduce
misurando se sul contorno piu' grande le normali puntano fuori.

**Font**: Archivo variabile in `assets/fonts/`, assi `wght` 100-900 e `wdth`
62-125. Le proporzioni si regolano in `NumberType` senza cambiare file.
Sta negli assets e non in `res/font` perche' `Typeface.Builder` **non accetta
un identificativo di risorsa**: vuole l'`AssetManager` e un percorso.

### Il tetto, dichiarato all'utente

E' un'estrusione **ortografica**: il contorno visto di fronte non cambia mai.
Ruotando cambiano luce e direzione dell'estrusione, non la prospettiva. Non si
vedra' mai la faccia frontale accorciarsi. **L'utente sa che potrebbe voler
cambiare impianto per questo.** Se lo chiede, servono geometria 3D vera e
proiezione prospettica, non un aggiustamento.

---

## 5. Movimento

- `ui/motion/DeviceTilt.kt`: accelerometro, **non** vettore di rotazione (che
  porterebbe imbardata e deriva). Il valore non e' l'inclinazione assoluta ma
  **lo scostamento da una linea di base che insegue lentamente la posa**:
  nessuno tiene il telefono verticale, e senza questo la cifra resterebbe
  stabilmente storta. Sensore spento fuori dal primo piano.
- `ui/motion/PhysicalNumber.kt`: il dito **ruota** l'oggetto, non lo sposta.
  Usa `detectHorizontalDragGestures`, e questo e' essenziale (trappola #5).
  L'angolo resta dove lo lasci.

---

## 6. Agganci di verifica

L'app accetta extra dell'intent per rendere fotografabili stati che i dati
veri non offrono. Nulli in uso normale.

```bash
adb shell am start -n com.forli.meteo/.MainActivity \
  --es tema SCURO --ei ora 2 --ei meteo 63
```

| Extra | Effetto |
|---|---|
| `--es tema` | AUTO / CHIARO / SCURO |
| `--ei ora` | fissa l'ora mostrata (ricordata se i dati non sono ancora arrivati) |
| `--ei meteo` | impone il codice WMO |

In CI si simula anche l'inclinazione (`adb emu sensor set acceleration`) e la
rotazione col dito (`input motionevent DOWN/MOVE/UP`, che tiene premuto: con
una `swipe` lo stato intermedio non e' fotografabile).

---

## 7. Trappole gia' pagate — non ripeterle

**1. `--stacktrace` nasconde gli errori Kotlin.** Seppelliva le righe `e:`
sotto trecento righe di stack Gradle. Il workflow ora filtra e mette gli
errori in coda al log.

**2. `/sdcard` non esiste all'inizio del boot.** Gli screenshot vanno su
`/data/local/tmp`, che appartiene all'utente shell ed esiste da subito. Costo
di questa lezione: due giri di diagnosi sbagliata.

**3. La cache del misuratore di testo di Compose ignora colore e pennello.**
Due misurazioni dello stesso testo che differiscono solo per la pittura
possono restituire lo stesso oggetto, e vince lo stile della prima. Ha
prodotto una cifra ridotta a contorno vuoto. Se si torna a usare
`TextMeasurer`, costruirlo con cache a zero.

**4. Lambert troncato a zero appiattisce tutto.** La prova di silhouette tiene
le facce rivolte come l'estrusione, che e' la direzione opposta alla luce:
erano **tutte esattamente 0.00** e il volume era una massa unica. Serve il
Lambert dimezzato. Verificalo numericamente prima di credere a un'ipotesi
sull'illuminazione.

**5. `detectDragGestures` consuma qualunque direzione.** La rotazione della
cifra ingoiava la trascinata verso l'alto, cioe' **il gesto piu' importante
dell'app veniva bloccato da quello decorativo**. Orizzontale ruota, verticale
apre.

**6. L'ombra e le facce mostrano cuciture.** Contorni chiusi adiacenti vengono
sfumati singolarmente dall'antialiasing. Si risolve sovrapponendoli di mezzo
pixel.

**7. La trasparenza non rappresenta la quantita'.** Una nuvola al venti per
cento di opacita' su fondo nero non legge come nuvola leggera, legge come
sporco. La copertura cambia numero di masse, dimensione e tono.

**8. Non fidarti della prima diagnosi.** Oggi ne ho sbagliate tre di fila su
uno stesso sintomo. Quando un difetto e' visivo, **misura** invece di dedurre:
calcola i valori, confronta gli screenshot numericamente, isola con un
esperimento di controllo.

---

## 8. Stato: fatto / non fatto

**Verificato su emulatore**: schermata principale, cifra con facce vere,
inclinazione (misurata: le due inclinazioni opposte differiscono fra loro piu'
che dal centro), rotazione col dito, foglio di dettaglio, notte con luna,
nuvola con pioggia, cielo coperto, entrambi i temi.

**Mai verificato**:
- **come si sente** il movimento su un telefono vero — solo l'utente puo' dirlo
- il **widget Glance** su una home reale
- prestazioni con `dumpsys gfxinfo`

**Non fatto, in ordine di valore** (piano completo in
`/root/.claude/plans/dunque-ho-bisogno-di-melodic-backus.md`, che pero' vive
fuori dal repository):

1. **Transizioni continue** — cifre a contachilometri al cambio valore,
   tabella scaglionata, curve che si deformano invece di saltare, striscia dei
   titoli guidata dall'offset del pager.
2. **Scelta del luogo** — oggi Forli' e' cablata. Geocodifica Open-Meteo senza
   chiave per la ricerca, `LocationManager` di piattaforma per la posizione
   (**non** `play-services-location`: sarebbe una dipendenza nuova), permesso
   solo approssimato. Il nome della citta' in cima e' gia' il punto da toccare.
   Nota: sull'emulatore AOSP il `Geocoder` non risolve i nomi, si vedranno le
   coordinate.
3. **Atmosfera notturna** — velatura dal codice WMO e da `is_day`, alpha
   massima 0.10. **Attenzione**: giorno e notte devono governare l'atmosfera,
   **non il tema**, altrimenti chi ha scelto "chiaro" vedrebbe una schermata
   scura e penserebbe a un guasto.
4. Ridondanza da sanare: `DayStrip` e `ScrubBar` nel dettaglio fanno la stessa
   cosa. Andrebbero fusi in un controllo trascinabile solo.
5. Il toggle GIORNO/SETTIMANA nel dettaglio ha semantica debole: ora che i dati
   orari ci sono, puo' finalmente distinguere 24 ore da 7 giorni.

---

## 9. Preferenze dell'utente, dette esplicitamente

- Stile **minimal e compatto**, **pochi spazi vuoti**, movimenti responsive.
- La grafica e l'intuitivita' contano piu' della parte tecnica.
- Lavorare **una schermata alla volta**, facendone il modello per le altre.
- Riferimento estetico: *(not boring) weather app*. Le immagini di
  riferimento stanno in `design/riferimento/`.
- Da evitare: cromato saturo, alone neon, geometria a tubo. Il target e'
  **plastica bianca opaca fresata** con smussi netti e iridescenza confinata
  al 10-15% della superficie.
- Vuole essere avvisato in anticipo dei limiti, non dopo.
