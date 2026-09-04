# Contesto per riprendere il lavoro

Documento di passaggio. Leggilo prima di toccare qualsiasi cosa: contiene
vincoli d'ambiente che non si deducono dal codice e diagnosi gia' pagate care.

---

## 1. L'ambiente

**Su questa macchina si compila e si prova sul telefono.** SDK Android
installato, `adb` funzionante, un dispositivo collegato. Il giro di lavoro e':

```
scrivi -> ./gradlew assembleDebug -> adb install -r -> screenshot -> correggi
```

Dura una manciata di secondi, quindi conviene misurare invece di dedurre.

Due accorgimenti per gli strumenti da riga di comando:

- La shell POSIX qui non riesce a **creare** file o cartelle nuove: usa
  PowerShell, o gli strumenti di scrittura dell'agente. Leggere e cercare
  funziona normalmente.
- `adb` da Git Bash si mangia i percorsi assoluti del dispositivo
  (`/data/local/tmp` diventa `C:/Program Files/Git/data/...`). Chiamalo da
  PowerShell.

**Il container in cui e' nato il progetto invece non compilava**: niente SDK,
`dl.google.com` bloccato al CONNECT, niente `/dev/kvm`, `api.open-meteo.com`
irraggiungibile. Da li' viene la CI, che resta valida come banco di prova
indipendente e come punto di pubblicazione.

`.github/workflows/build.yml` ha cinque job: `probe-api` (interroga Open-Meteo,
MeteoAlarm, i modelli numerici e le versioni delle dipendenze, e pubblica le
risposte vere), `test` (`lintDebug` e `testDebugUnitTest`), `build` (`assembleDebug` **e**
`assembleRelease`, cosi' R8 gira davvero), `rilascio` (pubblica l'APK sul tag
fisso `apk-latest`), `screenshots` (emulatore API 34). Gli output finiscono sul
branch `ci-artifacts`, separato da quello di sviluppo:

```bash
git fetch origin ci-artifacts
git show origin/ci-artifacts:screenshots/scuro-1-temp.png > /tmp/x.png
git show origin/ci-artifacts:api/hourly.json          # contratto API reale
```

**Il nome dell'app e' Caelum**, `io.github.noximiliencoxen.caelum`. Era
`com.forli.meteo` con l'etichetta `Weather`, e nessuna delle due cose andava:
l'app cerca qualunque localita' del mondo, quindi una citta' nel nome e' una
smentita in cima allo schermo, e un'etichetta inglese in un'app tutta italiana
era un segnaposto. *Caelum* e' il cielo in latino, ed e' anche lo scalpello del
bulinatore - c'e' pure una costellazione: cielo e oggetto fresato, che e'
esattamente cio' che l'app disegna.

**Chi aveva la vecchia app deve reinstallarla una volta**: cambiando
`applicationId` l'APK nuovo non si sovrappone al vecchio. Le preferenze
sopravvivono comunque a un aggiornamento normale, perche' il nome del DataStore
(`impostazioni`) e le sue chiavi non sono cambiati.

**L'APK sta sempre a**
<https://github.com/NoximilienCoxen/test-weather/releases/tag/apk-latest>
(tag fisso, chiave di debug fissa versionata cosi' le build si installano una
sopra l'altra senza disinstallare).

---

## 2. Toolchain, verificata alle fonti

| | |
|---|---|
| AGP | 9.4.0 — **ha Kotlin integrato**, il plugin `kotlin.android` NON va applicato |
| Gradle | 9.4.1 |
| JDK | 17 |
| compileSdk | **37** — Compose 1.12 lo pretende; 36 non basta |
| targetSdk / minSdk | 36 / 26 |
| Kotlin | 2.4.10 |
| Compose BOM | 2026.08.00 |
| Glance | 1.2.0 |
| core-ktx / activity-compose / lifecycle | 1.19.0 / 1.13.0 / 2.11.0 |
| Test | JUnit 4.13.2, Robolectric 4.16.1 — solo `testImplementation` |

**Le versioni non si scrivono a memoria: le misura la CI.** Da questo container
`dl.google.com` e Maven Central rispondono 403 al CONNECT del proxy, quindi qui
la domanda non ha modo di ricevere una risposta vera. Il passo *Quali versioni
esistono davvero* (`scripts/probe_deps.py`) legge le coordinate da
`libs.versions.toml`, chiede il `maven-metadata.xml`, scarta alpha, beta, rc e
istantanee, e pubblica l'elenco:

```bash
git show origin/ci-artifacts:api/dipendenze.txt
```

Leggendo dalla toml e non da una lista sua, una dipendenza aggiunta domani
finisce nel controllo da sola. E' cosi' che si e' scoperto che il progetto
teneva una Compose BOM del 2026 accanto a un core-ktx, un activity-compose e un
lifecycle di fine 2024, **senza che niente lo segnalasse**.

**AGP e' passata da 9.2.1 a 9.4.0 per ultima e da sola**, dopo che tutto il
resto era verde: un salto di toolchain puo' portarsi dietro il wrapper di
Gradle, e messo in mezzo ad altro non si saprebbe chi ha rotto cosa. Con questa
in `dipendenze.txt` non resta piu' una riga `DA AGGIORNARE`.

`kotlin { compilerOptions }` sta a **livello radice**, non dentro `android {}`.
`jvmTarget` non si dichiara: eredita da `compileOptions.targetCompatibility`.

**La release passa da R8** (`isMinifyEnabled`, `isShrinkResources`) con
`app/proguard-rules.pro` accanto. Flag e regole vanno insieme: con
`kotlinx.serialization` in gioco, minificare senza dire cosa tenere rompe la
deserializzazione **in silenzio**. La CI compila anche `assembleRelease`, se no
il flag non lo verifica nessuno. Sul telefono continua ad andare la build di
debug, che non e' minificata.

**Il tipo di build `debug` ha `isDebuggable = false`.** Non e' una svista: una
app debuggabile gira con ottimizzazioni ridotte, e questa schermata fa geometria
in tempo reale a ogni fotogramma. Misurato sullo stesso identico codice:
**38 ms per fotogramma con il flag, 16 ms senza**. Chi lo rimette per usare il
debugger deve sapere che sta misurando un'app che non esiste.

---

## 3. Cosa fa l'app oggi

**Al primo avvio si apre sul benvenuto** (`ui/welcome/WelcomeScreen.kt`): chiede
dove ti trovi, con un esploratore che appoggia una mano sul tasto e con l'altra
sopra gli occhi si guarda intorno. Prima non c'era **nessun momento** in cui
l'app chiedesse la posizione - si cambiava solo dalle impostazioni, quindi chi
non ci entrava restava per sempre sull'ultima impostata. C'e' sempre una via
d'uscita: un permesso negato non e' un vicolo cieco.

**Poi si apre sulla schermata principale** (`ui/home/HomeScreen.kt`): pulsante
impostazioni a sinistra, nome della localita' al centro, scultura meteo,
temperatura dell'ora scelta, condizione, barra delle 24 ore colorata per meteo.
**L'ora sta in una bolla sopra il cursore della barra**, non piu' scritta sotto:
sotto la barra sta il pollice che la scorre, e l'unica cosa che dicesse quale ora
si era trovata era coperta dalla mano proprio mentre la si cercava. Nello spazio
che si e' liberato sotto c'e' il solo tasto TORNA AD ADESSO, che prima era
appiccicato all'etichetta dell'ora - un bersaglio con due mestieri, e per giunta
spento quando diceva la cosa piu' utile.

**Il fondo e' un cielo, non piu' un grigio** (`ui/theme/Colors.kt`). Era una
tinta piatta sola, interpolata fra antracite e grigio chiaro: mezzogiorno usciva
grigio per costruzione, alba e tramonto viravano su un malva fangoso, e sereno e
coperto avevano lo stesso identico fondo - il grigio c'era sempre, quindi non
diceva niente. Adesso sono tre decisioni:

- **Una sfumatura verticale**, zenit sopra e orizzonte sotto, ricavata da due
  tabelle di fermate ordinate per altezza del sole. Le fermate cadono dove
  cadono le soglie di `SkyState.of`, cosi' fondo, sole e luna cambiano negli
  stessi punti.
- **Alba e tramonto sono diversi**: rosa e freddo l'una, arancio e caldo
  l'altro. L'altezza del sole non li distingue - e' simmetrica attorno a
  mezzogiorno - e nemmeno `journey`, che al tramonto riparte da zero perche'
  descrive l'arco della luna. Serve `SunClock.eveningness`, che e' la frazione
  del giorno **senza limiti**: negativa prima dell'alba, oltre uno dopo il
  tramonto, quindi monotona lungo tutta la giornata.
- **Il grigio adesso significa coperto.** Il fondo di prima e' diventato la
  tavolozza della nuvolosita', e ci si scivola dentro con `Wmo.cloudiness`; il
  decile alto - pioggia forte e temporale - scurisce ancora.

**Il dettaglio sale trascinando verso l'alto** (`ui/MeteoApp.kt`): un foglio che
segue il dito, reversibile a meta' corsa. **Le impostazioni entrano da
sinistra**, da dove sta il loro pulsante.

**Le impostazioni** (`ui/settings/SettingsScreen.kt`) hanno tre sezioni:
localita' (ricerca per nome piu' un elenco di scorciatoie), unita' della
temperatura, e da dove arrivano i dati. Le prime voci dell'elenco sono fra i
posti piu' piovosi che esistano, e ci sono apposta: con una citta' sola non
c'era modo di vedere la pioggia se non aspettando che piovesse.

**Quale modello numerico**, verificato dalla CI (`scripts/probe_models.py`, esito
in `ci-artifacts/api/modelli.txt`): sopra l'Emilia-Romagna il `best_match` sceglie
**ICON-D2** (DWD, 2,2 km). Esiste pero' anche `italia_meteo_arpae_icon_2i` -
ItaliaMeteo/ARPAE, 2,2 km, l'agenzia della regione stessa - e copre entrambi i
punti provati con quarantotto ore su quarantotto. Fra i due c'e' oltre un grado
di differenza sulla prima ora, quindi **non e' una scelta neutra**. `arpae_cosmo_5m`
non esiste piu': l'API risponde "not available anymore", e ICON-2I ne e' il
successore.

Da questo container `api.open-meteo.com` **non si raggiunge** (403 al CONNECT del
proxy): domande sull'API si girano alla CI, non alla memoria.

Dati: Open-Meteo senza chiave, `HttpURLConnection` + `kotlinx.serialization`.
Una richiesta porta `current`, `daily` (7 giorni, alba e tramonto compresi) e
`hourly`. La ricerca dei luoghi passa dalla geocodifica di Open-Meteo.
Localita' e unita' vivono in DataStore (`prefs/SettingsPrefs.kt`).

**Le temperature arrivano sempre in Celsius** e si convertono al momento di
scriverle: chiedere i Fahrenheit alla rete vorrebbe dire rifare la richiesta per
cambiare un'unita' di misura.

**L'ora corrente e' quella della localita'**, non quella del telefono: l'API
restituisce `utc_offset_seconds` e `Forecast.nowThere()` lo usa. Da quando il
posto lo sceglie l'utente, i due possono essere mezza giornata distanti.

---

## 4. Il pezzo importante: come e' fatta la cifra

`ui/render3d/` — `Camera.kt`, `TextPrism.kt`, `PrismRenderer.kt`

Questa e' la quarta implementazione. Le prime tre sono state bocciate, e i
motivi vanno capiti prima di toccarla.

**Cosa NON fare, primo**: ristampare il glifo lungo un vettore. Produce un
volume ma non produce **facce**: nessun punto della superficie sa com'e'
orientato, quindi nessuno puo' essere illuminato, e il risultato si legge come
strati piatti sovrapposti.

**Cosa NON fare, secondo**: estrudere in modo ortografico ruotando la luce. Era
l'implementazione precedente, e il difetto lo ha detto l'utente con parole
esatte: *"sembra che si muova lo spessore e non la faccia del numero"*. Il
contorno visto di fronte non cambiava mai. Un oggetto che gira accorcia la
faccia frontale e scopre il fianco, e questo si ottiene **solo** trasformando i
vertici e dividendo per la profondita'.

**Come funziona ora**:

1. `Camera` porta un punto dal sistema dell'oggetto a quello dello schermo:
   rotazione attorno all'asse verticale e a quello orizzontale, poi divisione
   prospettica. L'occhio sta a 2,7 volte la dimensione dell'oggetto — sotto il
   doppio sembra un grandangolo, sopra il quadruplo si torna all'ortografia.
2. `Paint.getTextPath()` **carattere per carattere**, non sull'intera scritta.
   `PathMeasure` campiona i contorni in polilinee.
3. Le **pareti** sono triangoli con colore sui vertici, non sagome riempite
   (vedi trappola #10). Una chiamata sola a `drawVertices`.
4. La luce e' **fissa rispetto allo schermo**: ruotando, una faccia entra nella
   luce e l'altra ne esce, e lo scambio si legge come rotazione ancora prima che
   la sagoma cambi. Lambert **dimezzato**, non troncato (trappola #4).
5. L'esposizione si calcola sul **vertice**, mediando le normali dei due spigoli
   che vi si incontrano: da li' la sfumatura continua sulle curve.
6. Le **pareti si disegnano dalla piu' lontana alla piu' vicina**, non nell'ordine
   in cui stanno scritte. I vuoti di un carattere sono contorni come gli altri,
   e uscendo per ultimi si stampavano sopra il pieno che avrebbero dovuto avere
   davanti: girando l'8 verso il taglio, i due occhielli venivano avanti come
   due cilindri appoggiati sulla cifra. L'ordine e' un `sort` di interi lunghi
   che impacchettano profondita' e indice - niente comparatori, niente
   allocazioni - e sostituisce anche la vecchia regola "prima tutte le pareti,
   poi tutti gli smussi": lo smusso di uno spigolo lontano deve stare **sotto**
   la parete di uno vicino, e ordinare lo dice da solo.
7. La **base** non viene triangolata. Una figura piana sotto una proiezione
   prospettica si trasforma per omografia: si applica alla tela la matrice che
   porta i quattro angoli del riquadro dove finiscono davvero, e si disegna il
   tracciato originale. Curve del font comprese, un solo disegno.
   **Quale delle due basi** dipende da dove si guarda: oltre il quarto di giro
   si vede quella di dietro, e disegnare sempre l'altra la stamperebbe sopra le
   pareti che dovrebbero nasconderla. Un prisma non ha un davanti assoluto.
8. Lo **smusso** e' la prima fetta dell'estrusione dalla parte che si vede, con
   normale a meta' strada. Rientrare la base per ricavarlo fa ripiegare il
   poligono sulle curve strette.
9. L'**iridescenza** e' una tinta sui vertici dello smusso dove la luce li
   sfiora, non piu' una sfumatura stesa sopra.
10. Le **ombre** si disegnano tutte prima di tutti i corpi (trappola #12) e sono
   una **proiezione, non una copia**: ogni angolo del riquadro viene spinto lungo
   la luce fino al piano che sta dietro l'oggetto, e solo allora proiettato
   (trappola #29). I due gradini sono due piani a distanze diverse, non due copie
   traslate.
11. Vicino al quarto di giro **base e ombra si spengono**. Li' i quattro angoli
   del riquadro finiscono quasi in fila e la matrice che li segue e' quasi
   singolare: esiste, ed e' fatta di numeri che divergono (trappola #20).
12. Il **grado** e' l'ultimo carattere del testo, in corpo ridotto e allineato in
   alto (`smallTail` in `NumberSpec`). Fa parte del prisma apposta: viene
   estruso, illuminato e girato con le cifre. Un simbolo sovrapposto in
   coordinate di schermo resterebbe fermo mentre l'oggetto gira.
   **Anche lo spessore si riduce con lui** (`Part.depthScale`): il suo anello e'
   largo novanta pixel e lo spessore comune ne misura centoventi, quindi lasciato
   spesso come una cifra non era piu' un simbolo ma un pezzo di tubo appoggiato
   accanto al numero. Ridotto nella stessa proporzione resta la stessa lastra,
   ritagliata piu' piccola.
   **Il file del font sta nelle risorse, non negli assets**: da li' Compose sa
   costruire una famiglia senza un contesto, e lo stesso mezzo mega serve sia
   alla cifra sia a tutti i testi dell'interfaccia. Gli assi variabili stanno
   sul `Paint` (`fontVariationSettings`), non sul `Typeface`, cosi' chi lo usa
   dichiara le proporzioni che vuole invece di pretendere una copia sua.
13. La cifra **entra salendo** quando i dati arrivano, con lo stesso
   `yOffset` in coordinate del modello. Una volta sola, all'ingresso in scena.
   **Non a ogni ora scorsa**: quella strada e' stata provata (le cifre che
   rotolavano a contachilometri) ed e' stata bocciata in mano - scorrendo la
   barra un numero non fa in tempo a rotolare che l'animazione riparte, e una
   soglia di tempo non basta perche' uno scorrimento a passo moderato ci sta
   appena sopra.
14. Le **geometrie estratte si tengono pronte** (dodici, LRU in `PrismRenderer`):
   estrarre vuol dire campionare tutti i contorni del font, e scorrendo la barra
   la si rifaceva a ogni ora - un lavoro intero dentro un fotogramma solo. La
   larghezza, che serve a decidere il corpo, si chiede ora con `TextPrism.widthOf`
   **senza costruire niente**: prima la cifra si costruiva, si misurava, e se non
   ci stava si ricostruiva da capo.

**Il verso dei contorni dipende dal font**, quindi non si assume: si deduce
misurando se sul contorno piu' grande le normali puntano fuori.

**Font**: Archivo variabile in `assets/fonts/`, assi `wght` 100-900 e `wdth`
62-125. Le proporzioni si regolano in `NumberType` senza cambiare file.

### Il tetto, dichiarato

L'ordine di sovrapposizione e' l'unica cosa che decide chi sta davanti: non c'e'
un buffer di profondita'. Regge perche' la rotazione e' attorno al solo asse
verticale, quindi la profondita' cresce in modo monotono lungo l'asse
orizzontale del modello e basta disegnare i caratteri dal piu' lontano al piu'
vicino. **Se un giorno si aggiunge una rotazione attorno a un secondo asse,
questa garanzia cade** e servira' un ordinamento vero.

---

## 4-bis. Il mappamondo del benvenuto

`ui/render3d/Bodies.kt` (`globe`), `ui/welcome/WelcomeScreen.kt`

**Un personaggio e' stato provato ed e' stato bocciato.** Un esploratore fatto di
sfere, con una gerarchia di trasformazioni per la testa e le braccia disegnate
come file di sfere lungo una curva. Ogni correzione ne scopriva un'altra: le
braccia a collana quando erano lunghe, il cappello che spariva dietro la testa,
la mano che salutava invece di riparare lo sguardo. Il verdetto dell'utente:
*"se il risultato e' questo, lasciamo perdere"*. Con lui sono usciti `Figure.kt`
e `Rig.kt` - stanno nella cronologia se un giorno servissero.

**Al suo posto un mappamondo, cioe' la luna con un'altra pelle.** Sfera, luce di
sempre, macchie sulla superficie che scivolano via girando: e' l'unica cosa di
questo motore che si sa gia' che funziona bene, e dice la stessa identica cosa -
dove sei sulla Terra - senza dover somigliare a nessuno. La differenza con la
luna e' una sola: **i continenti girano per conto loro** invece di stare fermi
rispetto al corpo, quindi la direzione della macchia si ruota prima di darla
alla camera.

Due cose imparate mettendolo a punto:

- **A gruppi, non sparsi.** Otto macchie isolate davano una palla bianca con
  qualche puntino. Sono le masse continue, coi bordi che si toccano, a leggersi
  come terra invece che come sporco.
- **La dissolvenza al bordo va tenuta corta.** Legata direttamente
  all'inclinazione sbiadiva tutto quello che non stava esattamente al centro, e
  una sfera con due smagliature al centro non si legge come un corpo con dei
  segni sopra: si legge come una sfera sporca. Vale anche per i mari della luna,
  che dallo stesso `blot` passano.

**Se un giorno serve davvero un personaggio disegnato**, la strada e' Lottie
(`lottie-compose`, un `.json` in `res/raw`): la parte che manca e' il disegno,
non il codice, e un JSON Lottie scritto a mano non e' una strada seria.

**Il benvenuto e' l'unico posto in cui l'app disegna in continuazione.** Altrove
vale la trappola #8 - zero fotogrammi a schermo immobile. Li' il movimento e' il
contenuto, si vede una volta, e si spegne da solo appena si passa oltre.

---

## 5. Movimento

- `ui/motion/SceneRotation.kt`: un solo orientamento per la scultura e la cifra.
  **Il trascinamento scrive l'angolo sul posto**, in un `MutableFloatState`, e
  solo il rilascio anima (trappola #22). **Nessun limite**: si gira quanto si
  vuole, anche piu' volte.
  **Il segno e' negativo** e non e' un dettaglio: la superficie che si tocca deve
  andare dove va il dito. Col segno positivo, tirando verso destra la cifra
  girava verso sinistra, come una manopola vista da dietro.
  Al rilascio si stima dove finirebbe scorrendo e si punta al **giro intero piu'
  vicino**: un lancio piano riporta l'oggetto dov'era, uno deciso lo fa girare su
  se stesso una volta o due e lo lascia nella stessa posa. In entrambi i casi
  torna a posto, ma quanto gira lo decide la mano. Finita la molla il conto
  torna a zero: un giro intero e' indistinguibile da nessun giro.
- `ui/motion/DeviceTilt.kt`: accelerometro, **non** vettore di rotazione (che
  porterebbe imbardata e deriva). Il valore e' **lo scostamento da una linea di
  base che insegue lentamente la posa**: nessuno tiene il telefono verticale, e
  senza questo la cifra resterebbe stabilmente storta. Sensore spento fuori dal
  primo piano. **Zona morta all'un per cento** (trappola #8).
- Il valore della rotazione e quello dell'inclinazione si leggono **dentro il
  disegno**, mai in composizione: ruotare deve ridipingere, non ricomporre.

---

## 6. Agganci di verifica

```bash
adb shell am start -n io.github.noximiliencoxen.caelum/.MainActivity --ei ora 2 --ei meteo 63
```

| Extra | Effetto |
|---|---|
| `--ei ora` | fissa l'ora mostrata (ricordata se i dati non sono ancora arrivati) |
| `--ei meteo` | impone il codice WMO |
| `--ei giro` | blocca la scena a un angolo, in gradi (accetta lo zero) |
| `--ei giorno` | apre il dettaglio di quel giorno della settimana |
| `--ei allerta` | mette in scena un'allerta finta: 1 gialla, 2 arancione, 3 rossa |
| `--ez benvenuto` | rimostra la schermata di benvenuto |
| `--ei allerta` | mette in scena un'allerta finta: 1 gialla, 2 arancione, 3 rossa |
| `--ez allertaridotta` | riduce subito la fascia al pallino. **Va dopo `--ei allerta`**: ridurre salva gli identificativi di cio' che c'e' in scena, e se l'allerta imposta non ci fosse ancora non ci sarebbe niente da ridurre |

Il benvenuto va imposto perche' si vede **una volta sola nella vita
dell'installazione**, e sull'emulatore quella volta e' gia' passata al primo
avvio dello script di cattura.

Per consegnare un intent a un'app **gia' viva** serve
`am start -f 0x20000000` (SINGLE_TOP): senza, l'attivita' riparte da capo, e
con lei riparte tutto quello che si voleva vedere cambiare.

L'aggancio sul giro c'e' perche' **i difetti che si vedono girando vanno
fotografati girati**, e un trascinamento simulato non ci arriva: per portare la
cifra di taglio servono quattrocento pixel, per vederla da dietro piu' di
ottocento, e uno schermo e' largo mille. Senza, il quarto di giro - che e'
esattamente dove le matrici degenerano e le pareti si scavalcano - non era
fotografabile, e infatti quei difetti li ha trovati l'utente e non la CI.

L'aggancio sull'allerta c'e' per la stessa ragione di quello sul meteo: la
fascia compare solo quando la Protezione Civile ha diramato qualcosa **sulla
localita' mostrata**, cioe' quasi mai e mai su richiesta. Fotografarla solo nei
giorni di maltempo vuol dire non fotografarla. Si applica **in lettura** e non
scrivendo dentro `UiState.alerts`, se no il primo caricamento la cancella prima
dello scatto.

L'aggancio sul tema non c'e' piu' perche' non c'e' piu' un tema da scegliere:
giorno e notte li decide l'ora mostrata, e per fotografare la notte basta
chiedere un'ora notturna.

Per leggere lo stato senza guardare le immagini, la struttura di accessibilita'
espone i testi:

```powershell
adb shell uiautomator dump /sdcard/ui.xml; adb shell cat /sdcard/ui.xml
```

Per le prestazioni, `dumpsys gfxinfo io.github.noximiliencoxen.caelum framestats`. **Attenzione
alle colonne**: su Android 12+ ce ne sono di nuove, e leggere gli indici
sbagliati fa misurare la scadenza del fotogramma invece del lavoro svolto
(trappola #9). Le utili sono `DrawStart`(8) → `SyncQueued`(12) per il thread di
interfaccia e `IssueDrawCommandsStart`(14) → `SwapBuffers`(15) per quello di
rendering.

---

## 7. Trappole gia' pagate — non ripeterle

**1. `--stacktrace` nasconde gli errori Kotlin.** Seppelliva le righe `e:` sotto
trecento righe di stack Gradle.

**2. `/sdcard` non esiste all'inizio del boot.** Gli screenshot vanno su
`/data/local/tmp`.

**3. La cache del misuratore di testo di Compose ignora colore e pennello.** Se
si torna a usare `TextMeasurer`, costruirlo con cache a zero.

**4. Lambert troncato a zero appiattisce tutto.** La prova di silhouette tiene
le facce rivolte come l'estrusione, opposta alla luce: erano **tutte esattamente
0.00**. Serve il Lambert dimezzato.

**5. `detectDragGestures` consuma qualunque direzione.** La rotazione ingoiava
la trascinata verso l'alto, cioe' **il gesto piu' importante dell'app veniva
bloccato da quello decorativo**. Orizzontale ruota, verticale apre.

**6. La trasparenza non rappresenta la quantita'.** Una nuvola al venti per cento
di opacita' non legge come nuvola leggera, legge come sporco. La copertura cambia
numero di masse, dimensione e tono.

**7. `pointerInput` congela quello che cattura.** La lambda viene ricreata solo
quando cambia la sua chiave, quindi confrontava l'indice toccato con l'ora
selezionata **all'apertura**. Risultato: l'ora corrente era l'unica
irraggiungibile della giornata, per sempre, perche' il confronto la dichiarava
gia' scelta. Era questo a far sembrare che la barra "saltasse" un'ora. Si risolve
con `rememberUpdatedState`. **Ogni valore letto dentro un riconoscitore di gesti
va passato cosi'.**

**8. Un accelerometro non sta mai fermo.** Col telefono appoggiato sul tavolo
l'ultima cifra balla. Scrivere ogni lettura teneva l'intera scena a ridisegnarsi
**cinquanta volte al secondo per sempre**, a batteria e a schermo immobile.
Stessa storia per `rememberInfiniteTransition` letto con `by` in composizione:
l'animazione della pioggia faceva ricomporre tutto anche col cielo sereno. Da
fermo l'app deve disegnare **zero** fotogrammi, ed e' verificabile:
`dumpsys gfxinfo ... reset`, quattro secondi, `Total frames rendered: 0`.

**9. Non fidarti della prima diagnosi, e nemmeno della quarta.** Su questo stesso
sintomo di lentezza ho incolpato, misurando male, il numero delle fasce di tono,
l'ombra portata, la trasformazione prospettica del tracciato e la base frontale.
Erano tutte innocenti. **Quando un difetto e' visivo o prestazionale, misura**:
strumenta il codice, leggi le colonne giuste, cambia una variabile per volta.

**10. Riempire sagome costa in proporzione alla superficie.** Le pareti
raggruppate per tono erano una decina di sagome grandi per carattere, piu'
l'ombra: svariate volte lo schermo a ogni fotogramma, dieci millisecondi per
registrare i comandi e altrettanti per eseguirli, su sedici disponibili. Con
`drawVertices` il colore sta sui vertici, non c'e' nulla da rasterizzare, e la
sfumatura diventa pure continua. Il thread di rendering e' passato da 14-20 ms a
4-9 ms. **Nota di compatibilita'**: `drawVertices` su tela accelerata e' certo
dal Pie in poi; su Android 8 (minSdk 26) le pareti potrebbero non comparire,
lasciando la cifra piatta ma leggibile. Non verificato su un dispositivo simile.

**11. L'antialiasing sulle superfici a triangoli apre fessure.** Ogni triangolo
verrebbe sfumato per conto proprio lungo spigoli che condivide col vicino. Il
contorno netto lo da' la base, che e' una sagoma vera.

**12. L'ombra portata qui e' un disegno, non un fenomeno.** Disegnandola insieme
al proprio carattere finiva sulla faccia del carattere accanto e gliela
ingrigiva: bastava girare la scena di mezzo passo perche' una cifra diventasse
sporca senza motivo apparente. Va disegnata **tutta prima di tutti i corpi**, che
e' anche il motivo per cui `TextPrism` espone la sola matrice della base senza
costruire le pareti.

**13. Il materiale non deve cambiare identita' ruotando.** Portare la faccia fino
al tono della parete lontana e' fisicamente corretto e visivamente sbagliato: a
meta' rotazione la plastica bianca diventava ardesia e sembrava un altro oggetto.
La faccia si scurisce di un passo, non di un salto; il contrasto della luce lo
raccontano le pareti, che possono permetterselo.

**14. I millimetri non dicono se piove.** Un temporale previsto all'ottanta per
cento puo' avere zero millimetri in quell'ora esatta, e sotto la scritta
TEMPORALE non cadeva niente - o cadeva una goccia sola, perche' il conto era
`gocce * millimetri` con un minimo di uno. **Se il codice WMO dice che piove,
deve piovere**: i millimetri decidono quanto forte, non se.

**15. Quello che accompagna un oggetto 3D deve stare nel suo spazio.** Gocce e
saette vivevano in coordinate di schermo: non seguivano la nuvola quando la si
girava, non ne rispettavano la larghezza, e da qualunque angolo restavano li'.
Messe nello spazio del modello ruotano con lei, quelle davanti scorrono piu' di
quelle dietro, e sono grandi quanto la distanza impone. Stessa storia per
l'ordine delle masse della nuvola: **ordinarle per la posizione nel modello**
bastava a farle scavalcare al contrario dopo mezzo giro. Si ordina per la
profondita' **in coordinate di vista**.

**17. `rememberInfiniteTransition` qui non anima.** Le gocce sembravano cadere e
invece erano ferme: misurato, con la pioggia accesa e nessun dito sullo schermo
l'app disegnava **zero** fotogrammi. Qualunque ne sia la ragione dentro la
libreria, un'animazione che si legge solo nel disegno e mai in composizione non
e' terreno su cui fidarsi di una comodita'. Il ciclo della pioggia ora e'
esplicito: `withFrameNanos` dentro un `LaunchedEffect` che gira **solo mentre
piove**, e ogni battito scrive un valore che il disegno legge. Da fermo l'app
continua a disegnare zero fotogrammi.

**18. Una sagoma grande sotto una matrice prospettica non passa dalla strada
veloce.** L'ombra portata costava, da sola, piu' di tutto il resto della
schermata: con la pioggia che la faceva ridisegnare a ogni fotogramma si passava
da 18 a 36 millisecondi e dal nessun ritardo al settanta per cento. Basta
costruirne la matrice da **tre** angoli invece che da quattro: quella che ne esce
e' affine, la differenza sulla sagoma e' di qualche pixel sull'angolo piu'
lontano e su una macchia al dodici per cento di nero non si vede. La base
frontale invece la prospettiva ce l'ha per forza - e' tutto il punto - e quella
resta cara.

**19. Committare da Windows rompe la CI in due modi silenziosi.** Il primo
commit fatto da qui l'ha fatta fallire senza toccare una riga di Kotlin:
`gradlew` e gli script sono passati da `100755` a `100644`, e il primo
`./gradlew` del workflow e' morto con permesso negato prima ancora di compilare.
Il secondo: Android Studio genera `gradle/gradle-daemon-jvm.properties` con la
versione di JDK installata **su questa macchina** - qui il 25 - e la CI monta il
17, quindi il demone si trovava a doverselo procurare. Il file ora e' ignorato e
un `.gitattributes` dichiara cosa deve restare eseguibile e con quali fine riga.
Il repository ha anche `core.fileMode=false`, perche' Windows non sa rispondere
alla domanda.

Da qui non si vedono i log della CI senza credenziali, ma l'elenco dei passi si
legge lo stesso e basta a capire dove si e' rotta:

```bash
curl -s "https://api.github.com/repos/NoximilienCoxen/test-weather/actions/runs?per_page=1"
curl -s ".../actions/runs/<id>/jobs" | grep -E '"(name|conclusion)"'
```

**20. Una matrice quasi singolare esiste eccome.** `setPolyToPoly` torna falso
solo quando i punti sono *esattamente* in fila. Un grado prima del taglio netto
sono quasi in fila, la matrice esce fatta di numeri enormi, e la sagoma che ci
passa sotto si stampa come una colata di strisce lunghe mezzo schermo - erano
quelle che spuntavano da sotto la cifra ogni volta che passava di profilo. Non
si chiede alla matrice se esiste: si guarda **quanto la base e' aperta** verso
l'occhio, e sotto soglia non si disegna. Vale per la faccia e per l'ombra.

**21. Un'ombra non puo' seguire la base rivolta all'occhio.** Quale delle due
basi si veda cambia al quarto di giro (punto 6 della sezione 4), ed e' giusto
per la faccia. Per l'ombra no: nello stesso istante in cui la cifra passava di
taglio, l'ombra saltava dall'altra parte dello spessore. Il **piano mediano** non
ha un davanti e un dietro, quindi attraversa il giro intero senza accorgersene.

**22. `Animatable.snapTo` in una coroutine annulla la molla che sta girando.**
Il trascinamento passava da `scope.launch { animated.snapTo(...) }`, uno per
delta. Il dispatcher della composizione consegna **al fotogramma**, non subito:
l'ultimo `snapTo` prima del rilascio finiva quindi *dopo* l'avvio della molla, e
un `Animatable` che riceve un `snapTo` annulla l'animazione in corso. Da fuori si
vedeva la cifra partire e **piantarsi a meta' giro** senza tornare a posto, tanto
piu' spesso quanto piu' era stato deciso il gesto - cioe' proprio quando il
lancio contava. Un gesto continuo non passa da una coda: scrive il valore, e
basta.

**23. Un astro disegnato per primo e' un fondale, non un oggetto.** Sole e luna
uscivano prima delle masse della nuvola, sempre: qualunque cosa facesse la
rotazione, la nuvola restava davanti. Portando la luna di fronte con mezzo giro
di dito la si vedeva comunque sotto le masse bianche, ed era per questo che non
si vedeva mai intera. Ora l'astro sta nella **stessa fila** delle masse, ordinato
per profondita' in coordinate di vista come loro (che e' la trappola #15
applicata anche a lui).

**24. La Luna non la illumina la lampada della stanza.** Sole, nuvole e cifra
condividono una luce sola, ed e' giusto: sono oggetti nello stesso spazio. La
Luna no - la illumina il Sole, e da che parte stia lo dice la fase. Col gradiente
preso dalla luce della scultura, il lembo acceso della falce veniva il punto piu'
scuro del disco e **la mediana ci si perdeva dentro**: si vedeva una palla grigia
storta, non un quarto di luna.

**25. Una vibrazione a tempo non e' una vibrazione responsive.** La pioggia dava
un colpetto per giro di gocce: cadeva anche quando la pioggia passava a fianco
della cifra senza toccarla, e mancava quando ne arrivavano cinque insieme. Ora lo
chiama l'urto - il disegno e' l'unico a sapere dove passa la sagoma - con due
accortezze: si conta solo il **passaggio** da aria a superficie (altrimenti ogni
goccia gia' arrivata ne segnerebbe uno per fotogramma), e c'e' una soglia di un
decimo di secondo fra un colpo e il successivo, perche' un vibratore che non
stacca mai non si legge come pioggia ma come un ronzio.

**29. Un'ombra traslata non e' un'ombra.** Era la faccia dell'oggetto sotto la
stessa matrice, spostata di un tot sulla tela. Ferma sembrava giusta; girata no,
e per un motivo che non si aggira ritoccando i numeri: **un'ombra vera cambia
forma girando** - si accorcia, si inclina, si allarga - e una copia traslata non
cambia niente, quindi si legge come una seconda cifra scura appoggiata dietro la
prima. Serve una proiezione vera, e costa gli stessi quattro angoli: si spingono
lungo la luce fino al piano dietro l'oggetto e li si proietta di li'.

**30. Il corpo della cifra non deve dipendere da quali cifre sono.** La larghezza
dell'**inchiostro** cambia col valore - l'uno ne ha molto meno di uno zero - e il
rimpicciolimento per far stare la scritta nello schermo si calcolava sul testo
vero: "31" usciva percettibilmente piu' grande di "32", e scorrendo la barra la
cifra respirava. Si misura una **sagoma di riferimento** con tutte le cifre
ridotte a uno zero, cosi' ogni valore della stessa lunghezza riceve lo stesso
corpo.

**28. L'emulatore della CI ha le animazioni spente, e Compose gliene da' retta.**
`disable-animations: true` nel workflow azzera `animator_duration_scale`, e
Compose legge quella scala di sistema: con zero, `animate` e `animateTo`
saltano **dritti alla fine**. Il rotolamento della cifra partiva davvero - il
logcat lo diceva, con tanto di valore da cui veniva - e finiva nello stesso
fotogramma in cui cominciava, quindi negli scatti si vedeva sempre e solo il
numero fermo. Vale per tutto quello che si anima: il colore del cielo, la molla
della barra delle ore, la nuvola che si addensa. Per fotografarne uno bisogna
riaccendere la scala prima e rispegnerla dopo, come fa `roll_from_to` in
`scripts/capture.sh`. **Prima di dare la colpa al codice per un'animazione che
"non parte", guarda se sta girando a durata zero.**

**33. Il rovescio della #28: quando le animazioni sono vive, uno scatto puo'
ritrarre il viaggio invece della destinazione.** La #28 dice che con
`animator_duration_scale` a zero `animate` salta alla fine. Il caso opposto e'
altrettanto velenoso e si e' visto subito: ad app appena avviata l'altezza del
sole parte dal **ripiego diurno** (`0.62`), e la molla del cielo ci mette piu'
di un secondo ad arrivare a un'ora notturna. Lo scatto dell'alba, preso col
solo secondo di `attendi_previsione`, e' uscito con il cielo di mezzogiorno e il
sole alto - e la tavolozza era giusta, era la foto a essere presto.

Il segno che lo tradiva stava nel testo, non nel colore: **"IN ATTESA DEI DATI"
sopra una riga di minima e massima gia' piene**. Sono due stati che non possono
coesistere, se non a meta' di una dissolvenza. Quando due parti dello schermo si
contraddicono, la spiegazione e' quasi sempre il momento dello scatto e non la
logica di una delle due.

**34. Chi reagisce al meteo deve leggere `forcedWeatherCode`, non solo l'ora.**
Il fondo del cielo era nato leggendo `hour?.weatherCode` e basta: nello scatto
di verifica del coperto la scultura obbediva al codice imposto e il fondo no, e
usciva la nuvola giusta sopra un cielo da sereno - cioe' proprio la regola che
quello scatto doveva dimostrare non si vedeva. In uso normale non si sarebbe
notato mai, perche' li' il codice imposto non esiste: e' un difetto che **solo
l'aggancio di verifica poteva mostrare, e solo se l'aggancio arriva dappertutto**.

**26. `refresh()` non la richiamava nessuno.** Partiva all'avvio e al cambio di
localita', e basta: nessun ritorno in primo piano, nessun gesto, nessun segno di
quanto fosse vecchio il dato. Un'app meteo lasciata aperta ieri sera mostrava
ieri sera con la stessa faccia di adesso. E il gesto che sarebbe servito a
ricaricare **c'era gia' e veniva buttato via**: col foglio del dettaglio chiuso,
lo scorrimento verso il basso finiva dentro un `coerceIn(0f, 1f)` e non
succedeva niente.

**27. Una ricarica non e' un primo carico.** Il ritorno dei dati riportava
l'ora scelta ad "adesso" e, fallendo, sostituiva la condizione con un errore.
Su un primo carico e' giusto; su una ricarica vuol dire sbalzare altrove chi
stava guardando le sei di sera, e cancellare una giornata di dati validi per
annunciare che la rete non risponde.

**32. Il cielo non deve passare dalla camera.** Le stelle erano corpi come gli
altri, sistemate con `camera.place`: girando la scultura girava anche il cielo.
Misurato su due scatti, giro zero e giro centocinquantacinque, **non c'era una
sola stella nello stesso posto**. Un fondo che ruota con l'oggetto davanti non
si legge come fondo: si legge come una cupola dipinta attaccata alla scultura,
che se la porta dietro. Adesso la posizione la decidono lo schermo e
nient'altro, e con la camera se ne va anche `camera.scale` - senza profondita'
non c'e' prospettiva da applicare.

Vale anche come avvertimento di metodo: il commit che aveva "fermato" le stelle
diceva *"il cielo sta fermo e le cose davanti si muovono"* e aveva tolto **solo
il tremolio**. La frase era vera per la luminosita' e falsa per la posizione, e
nessuno se n'e' accorto per due giri perche' lo scatto che l'avrebbe mostrato -
`scuro-10-luna-girata` - era uno di quelli che uscivano vuoti (trappola #31).
Due difetti che si coprivano a vicenda.

**31. Aspettare una durata e' scommettere sulla rete del runner.** Dopo ogni
riavvio dell'app la cattura aspettava che la previsione arrivasse, e l'attesa
era un numero di secondi. Quando uno scatto usciva "IN ATTESA DEI DATI" il
rimedio era alzarlo: otto, poi quattordici, poi diciannove. A diciannove uno
scatto su undici e' uscito lo stesso vuoto — ed e' li' che si vede che il numero
non era mai il problema. Adesso l'app scrive una riga di log quando la
previsione atterra (`meteo: previsione pronta`, l'unico `Log.i` di tutto il
progetto) e `attendi_previsione` aspetta **quella**, con un tetto di tempo che
serve solo a non restare appesi. Nota per chi cerchera' la via ovvia:
`uiautomator dump` qui non si puo' usare, perche' aspetta che la finestra sia
ferma e la schermata principale anima in continuazione per scelta.

**38. L'emulatore della CI muore a meta' corsa, e muore in silenzio.** Tre giri
di seguito, in tre punti diversi ma sempre dopo qualche minuto: l'emulatore
sparisce e non risponde nemmeno a `emu kill`. **Non e' l'app**: il logcat
finisce pulito sull'ultimo scatto riuscito - nessuna eccezione, nessun ANR,
nessun consumo anomalo. Non c'e' un log da leggere perche' a morire e' il
processo che il log lo ospita.

La prima diagnosi - "e' la trascinata lunga" - era sbagliata, ed e' la trappola
#9 che si ripresenta: il giro dopo e' morto in un punto dove trascinate non ce
n'erano. Quello che si puo' dire e' solo dove **non** sta il problema.

**Quello che si sa, e va detto senza abbellirlo.** Quattro giri morti su
cinque, sempre mentre il foglio di dettaglio e' in scena, in quattro punti
diversi (pagina aria due volte, dettaglio di un giorno, pagina vento).
L'ultimo giro **completo** e' `5ca1ca4`; il primo morto e' quello subito dopo,
che porta cinque cambiamenti insieme - fra cui i pannelli che dipingono sotto
le barre di sistema e le icone di sistema che seguono il fondo, cioe' le uniche
due cose del rifacimento che toccano la finestra invece del contenuto.

**La correlazione c'e', la causa no.** Cambiare quel codice a naso per far
passare la CI sarebbe esattamente la trappola #9 di nuovo: quattro diagnosi
sbagliate di fila su un sintomo che non si e' ancora misurato. Qui non si puo'
misurare - non c'e' un emulatore da strumentare - e da fuori le due cose sono
indistinguibili. **Il modo per chiudere la questione e' il giro di lavoro
normale di questo progetto**: `./gradlew assembleDebug`, installare, aprire il
dettaglio sul telefono e guardare. Se li' non muore niente, e' l'emulatore
della CI; se muore, si e' trovato il pezzo.

Tre conseguenze in `capture.sh`:

- l'aggancio `--ei giorno` apre il dettaglio di un giorno senza gesti, come
  `--ei giro` fa per la cifra di taglio: sono entrambi stati che col dito, qui,
  non si raggiungono in modo affidabile;
- **si fotografa prima cio' che non ha mai avuto uno scatto** e poi le prove del
  motore 3D, che una galleria alle spalle ce l'hanno. Quel che resta fuori e'
  sempre la coda, quindi in coda va messo cio' che si puo' perdere;
- il giro fallisce se il dispositivo non c'e' piu' alla fine. Contare gli scatti
  mancati non bastava: la soglia si azzecca per difetto, e con esattamente tre
  mancati il controllo lasciava passare un giro monco. Prima ancora, il job era
  **verde** con dodici scatti mancanti su trentasette, perche' guardava solo che
  ce ne fosse almeno uno - e un job verde che ha fotografato meta' delle
  schermate e' peggio di uno rosso: sembra una verifica fatta.

**37. Un log di diagnostica lasciato acceso smentisce in silenzio una
dichiarazione su cui si appoggia qualcun altro.** Nei widget erano rimasti
sedici `Log.d("WidgetResolve", ...)` dalle sessioni in cui si inseguiva quale
localita' finisse nel widget. Il guaio non e' il rumore: e' che `capture.sh`
aspetta **quella** riga di log per sapere quando la previsione e' atterrata
(trappola #31), quindi "l'unico Log del progetto" non era un vezzo di stile ma
un invariante di cui qualcosa si fida. In piu' alcune di quelle righe
stampavano in logcat le coordinate di chi usa l'app. Adesso restano un `Log.i`
- quello - e due `Log.w` su guasti veri.

Togliendoli, due cose sono venute a galla da sole. In `resolvePlace` i rami del
`when` erano blocchi **solo** perche' contenevano un log, e tornano espressioni.
In `refreshWidget` invece la rilettura delle preferenze **resta**, col commento
che adesso dice perche': serve la lettura, che sospende finche' il DataStore non
consegna. Aspettare non era un effetto collaterale del logging, e buttarla via
avrebbe cambiato una tempistica costata cara.

**39. Di un'icona adattiva il telefono garantisce solo il cerchio centrale.**
Su 108 unita' di lato, quelle sicure sono i 66 centrali: tutto il resto puo'
essere tagliato, e ogni marca taglia con una maschera sua. Disegnata a grandezza
piena, la cifra stava benissimo nell'anteprima quadrata e sotto la maschera tonda
il trattino di base veniva **affettato** - non mascherato con grazia, proprio
tagliato a meta' - mentre l'ombra usciva del tutto.

Il rimedio non e' stato rifare le coordinate: l'intera composizione (ombra,
parete, smusso e faccia insieme) passa per un `<group>` che la rimpicciolisce
attorno al proprio centro, cosi' i rapporti restano quelli disegnati e il
controllo del raggio si fa una volta sola. Gli stessi tre numeri valgono per il
livello monocromatico, se no la sagoma a tinta unita non starebbe dove sta la
faccia.

Vale come metodo, non solo per questa icona: **un'icona si guarda sotto le
maschere, non nel suo riquadro.** Il cerchio e lo squircle stretto sono i due
casi che bastano.

**16. Chiedere l'intensita' della vibrazione non basta a ottenerla.** Su questo
telefono `hasAmplitudeControl()` risponde di no e un'ampiezza dichiarata viene
ignorata: la pioggia usciva forte quanto il tuono. `WeatherHaptics` prova in
ordine le primitive componibili, l'ampiezza, gli effetti gia' pronti del sistema
e infine la sola durata. Qui finisce su `EFFECT_TICK` contro `EFFECT_HEAVY_CLICK`,
che sono tarati bene e si distinguono davvero.

**35. Un avanzo di scorrimento non e' un dito.** `SheetNestedScroll` riceve un
`NestedScrollSource` a ogni callback e non lo leggeva nessuno. Arrivati in fondo
a una pagina del dettaglio bastava una scorsa decisa perche' il foglio si
chiudesse da solo: quello che il contenuto non consumava - lo slancio che si
esaurisce, l'elastico di fine corsa che si rilassa - entrava nel foglio
indistinguibile da una mano. Il commento sopra la classe descriveva la regola
giusta, *"il contenuto scorre finche' ha strada, e solo l'avanzo muove il
foglio"*, e il codice la applicava all'avanzo **di chiunque**. Ora il foglio
risponde al solo `NestedScrollSource.UserInput`, e in piu' si assesta solo se e'
stato quel gesto a muoverlo.

Nello stesso punto c'era un secondo difetto che si nascondeva dietro il primo:
la guardia era `open >= 1f`, un confronto **esatto** su un numero che viene da
una molla, e `begin()` quella molla la cancella dove la trova. Chi cominciava a
scorrere mentre il foglio stava ancora salendo lo lasciava a 0,997 per sempre;
da li' in poi la guardia non scattava piu' e `onPreScroll` si mangiava anche i
delta verso l'alto, cioe' **il contenuto non scorreva piu' affatto**. Serve una
tolleranza, e serve rimettere il foglio su un'ancora quando una molla e' stata
cancellata senza che un trascinamento le sia subentrato.

**36. La mediana della luna non gira, e non e' un difetto.** Nella pagina LUNA
la sfera si gira col dito: i mari scivolano verso il bordo e spariscono dietro,
perche' stanno sulla sfera e passano dalla camera. Il taglio fra luce e ombra
no, perche' `moon()` lo costruisce in coordinate di schermo - da che parte cada
lo decide il Sole, non chi guarda (e' la trappola #24 vista dall'altro lato).
Una falce che si raddrizza girando il telefono sarebbe una luna che cambia fase
perche' ci si e' spostati di venti centimetri.

---

## 8. Stato: fatto / non fatto

**Verificato sul telefono**: schermata principale, rotazione libera con
prospettiva vera anche oltre il mezzo giro, ritorno alla posa a riposo, sole
giallo e rosso, luna con fase e mari, nuvola bianca e nuvola grigia, **pioggia
che parte davvero** su temporale a zero millimetri, gocce che ruotano con la
nuvola, fulmini con alone e bagliore, vibrazione leggera sulla pioggia e pesante
sul tuono (viste nella cronologia del vibratore), fondo che segue l'ora, **tutte
e ventiquattro le ore raggiungibili una per una**, impostazioni, cambio localita',
cambio unita' (21 °C -> 70 °F), persistenza delle scelte.

**Misurato**: da fermo 0 fotogrammi. Con la pioggia che cade, 19 ms mediani e
nessun fotogramma in ritardo, di giorno come di notte. In rotazione il lavoro
per fotogramma sta fra i 5 e i 17 ms.

La pioggia sa dove trova superficie: `ui/render3d/Skyline.kt` tiene, colonna per
colonna, il punto piu' alto occupato dalla cifra, e la cifra ce lo scrive dentro
mentre si disegna. E' l'unico punto in cui due tele si parlano, e lo fanno con
due origini e un vettore, non con uno stato osservabile: chi legge ridisegna
comunque a ogni fotogramma.

**Mai verificato**:
- **come si sente** il movimento in mano, e **quanto gira** con un lancio vero:
  `adb shell input swipe` non produce una velocita' di rilascio credibile, quindi
  la parte della rotazione che dipende dalla foga della mano l'ha provata solo
  chi ha il telefono
- se la vibrazione della pioggia, adesso **un tocco per goccia che tocca la
  cifra** con un decimo di secondo di soglia fra l'uno e l'altro, risulti
  gradevole o molesta dopo qualche minuto, e se la soglia vada allargata in un
  rovescio
- **come si legge il grado** accanto a una cifra a tre caratteri (`-10`, `100`):
  li' il riadattamento in larghezza scatta e la cifra si rimpicciolisce
- il **widget Glance** su una home reale (legge la localita' scelta, non provato)
- Android 8, per via della nota su `drawVertices`
- la ricerca dei luoghi per nome con la tastiera (provate solo le scorciatoie)

**Non fatto, in ordine di valore**:

1. **Transizioni continue** — cifre a contachilometri al cambio valore, tabella
   scaglionata, curve che si deformano invece di saltare.
2. **Il dettaglio non e' mai stato provato in mano.** Il rifacimento e le
   correzioni alla navigazione (sezioni 8-bis e 8-ter) sono verificati dalla CI
   - compila, e gli scatti mostrano tutte e cinque le pagine nei due temi - ma
   nessuno ha ancora scorso il carosello col pollice ne' girato la cifra da
   dentro il foglio. In particolare **non sono mai state viste in mano** la fila
   di pillole che si porta al centro e la sfumatura del titolo durante il
   trascinamento: sono movimenti, e un movimento in uno scatto non si giudica.
5. **La barra con la bolla, la pagina della luna e l'elenco dei pannelli non
   sono mai stati provati in mano.** Valgono la nota qui sopra e in piu' una
   cosa che gli scatti non possono mostrare: la bolla scivola col dito, e la
   sfera della luna gira col dito. Da guardare per primi: la bolla alle due
   estremita' della barra (li' si ferma al bordo e la codina si inclina per
   continuare a puntare il cursore), e se il tasto indietro con l'elenco aperto
   chiuda l'elenco invece del foglio.
6. **Le allerte non sono mai state viste con un bollettino vero.** Il parser
   adesso ha un test contro la risposta vera del feed (sezione 8-quater) e il
   job `probe-api` controlla che i campi su cui si fida esistano ancora, ma
   nessuno ha ancora aperto l'app in un giorno di allerta arancione: gli scatti
   usano gli agganci `--ei allerta` e `--ez allertaridotta`. La prima allerta
   vera e' anche la prima verifica vera - e con lei si giudica anche il
   passaggio fascia -> pallino, che e' un movimento e in uno scatto non si
   giudica.
8. **I widget si aggiornano con `updatePeriodMillis="1800000"`**, che Android
   limita a mezz'ora e rimanda in Doze: il widget mostra il meteo di un'ora fa
   senza dirlo. La risposta moderna e' `WorkManager` periodico, ed e' una
   dipendenza e un ciclo di vita nuovi - da fare quando i widget saranno stati
   visti almeno una volta su una home vera.
9. **`PredictiveBackHandler`** al posto di `BackHandler` sui quattro strati, per
   il ritorno con animazione di Android 14+.
3. **La qualita' dell'aria non ha una previsione**, solo l'ora corrente: e'
   quello che l'endpoint da'. La pagina lo dichiara invece di disegnare una
   curva piatta.
4. **Il dettaglio in orizzontale** e' adattato nelle misure (`MeteoLayout`) ma
   non nella disposizione: grafico e statistiche restano impilati anche dove
   ci starebbero affiancati.

---

## 8-bis. Il rifacimento delle schermate di dettaglio

`ui/theme/Contrast.kt`, `ui/theme/MeteoColorScheme.kt`, `ui/common/`,
`ui/temperature/`, `ui/temperature/pages/`

Prima di toccare queste schermate, tre regole che sono costate la passata
intera.

**Nessun colore di testo si sceglie a mano.** Si ricava dal fondo su cui
cadra', con [`readableOn`](app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/theme/Contrast.kt)
e la formula di contrasto della WCAG 2.1. Il difetto che questo toglie di mezzo
era esattamente uno scritto a mano: il titolo del dettaglio era `colors.text`,
cioe' quasi nero a mezzogiorno, sopra un pannello antracite fisso. E l'etichetta
grigia delle pillole stava a 4,17:1, sotto la soglia. **Il grigio secondario si
tara contro la superficie piu' chiara su cui puo' finire**, non contro quella
media: tarandolo sul container si ottiene 4,49:1 sulle pillole spente, cioe' lo
stesso difetto spostato di un decimo.

**Il caso peggiore non era il dettaglio: era la schermata principale a meta'
mattina.** Il fondo del cielo e il colore del testo si interpolano su due scale
diverse - il fondo da antracite a grigio chiaro, il testo da bianco sporco a
quasi nero - e a un certo punto del giorno si incrociano. Misurato sulla
matematica di `skyColors`, con la formula WCAG:

| ora | fondo | contrasto prima | dopo `readableOn` |
|---|---|---|---|
| mezzanotte | `#1D2026` | 14,96:1 | invariato |
| mattina (giorno 0,6) | `#727279` | **1,01:1** | 4,75:1 |
| pomeriggio (giorno 0,8) | `#8F9299` | 3,02:1 | 4,77:1 |
| mezzogiorno | `#AEB3BB` | 8,19:1 | invariato |
| alba e tramonto | `#564A51` | 4,53:1 | invariato |

Uno-virgola-zero-uno a uno vuol dire **testo invisibile**: stessa luminanza del
fondo. Capitava ogni giorno, per un'ora buona, e nessuno l'aveva mai visto
perche' la CI fotografava solo le due ore estreme - mezzanotte e mezzogiorno -
che sono le due in cui il contrasto e' migliore. Da qui la regola: se una
tinta e' interpolata, il testo che ci va sopra non si sceglie, si calcola.

**La correzione sta in `skyColors`, non nei chiamanti**, ed e' una distinzione
che e' costata un giro: correggere il solo `onBackground` dello schema Material
non serviva a niente, perche' la schermata principale, la barra delle ore, il
benvenuto e la scultura leggono `LocalMeteoColors.text` e `.label` diretti. Chi
tocca quella funzione tenga presente che la regolazione a mano che c'era prima -
il mixing dell'etichetta verso il fondo tenuto a 0,28 invece che a 0,42 - era la
stessa medicina data a occhio: curava il caso di mezzogiorno, che era quello che
si vedeva negli scatti, e lasciava scoperto quello di meta' mattina, che negli
scatti non c'era.

**Da quando il fondo e' una sfumatura, la soglia si chiede ai due capi e non al
tono medio.** Sotto un testo solo ci sono due colori diversi - in cima lo zenit,
in fondo l'orizzonte - e a meta' pomeriggio distano fra loro piu' di due a uno:
un testo corretto sulla media li regge tutti e due appena appena, che e' come si
torna al difetto di prima con un'altra faccia. Da qui `readableOnBoth` e
`mutedOnBoth` in `Contrast.kt`.

E c'e' un caso in cui **nessun colore di testo regge entrambi i capi**: quando
uno sta sopra e l'altro sotto la luminanza di mezzo, il bianco perde in cima e
il nero perde in fondo, e non esiste una terza risposta. Li' e' **la sfumatura a
cedere, non la leggibilita'**: `legibleSky` la avvicina al proprio tono medio
finche' un testo esiste, al limite fino alla tinta piatta di prima. Misurato su
3168 momenti - tre stagioni per undici nuvolosita' per i quarti d'ora di una
giornata - cede nell'**1,8%** dei casi, quasi sempre di un passo su sei, e sono
il parzialmente nuvoloso attorno all'alba e il coperto attorno al tramonto,
cioe' i momenti gia' meno colorati. Dopo la correzione il caso peggiore su tutta
la giornata e' **4,50:1**, e nessuna lettura sta sotto la soglia.

**Le etichette dentro le tele hanno un fondo.** La scala dell'asse Y cade sempre
sopra l'area riempita del grafico, che sotto la scala dei gradi copre
all'ottantadue per cento: li' un grigio su un arancione non si legge. Ogni
`drawText` di `MeteoChart` passa da `drawLabel`, che gli mette sotto una
pillola.

**La scala di un grafico non inventa valori.** Con una serie piatta il vecchio
grafico allargava l'intervallo a `mid ± 1.5`, e su una giornata asciutta l'asse
delle probabilita' dichiarava "-1" e "2". `ChartBounds` dice cosa la grandezza
puo' davvero valere, e l'allargamento resta dentro.

Tre trappole minori, gia' pagate:

- **`coerceIn` solleva quando il minimo supera il massimo.** Succede: su schermo
  stretto un'etichetta puo' essere piu' larga del grafico che la contiene, e
  `plotRight - larghezza` diventa negativo. Le tele usano `clamp`, che in quel
  caso preferisce il minimo e tira avanti.
- **La falce di luna e' un disco meno un disco**, e il secondo va del colore di
  cio' che sta sotto. `skyMark` non ha piu' un valore di riposo per quel colore:
  il valore di riposo era il fondo delle schede, mentre il grafico del giorno si
  disegna sul fondo del pannello, e il ritaglio si vedeva come una macchia
  scura sopra la luna.
- **`@ReadOnlyComposable` e `remember` non convivono.** La prima dichiara che la
  funzione non scrive nella composizione, la seconda ci scrive.

**Il gesto orizzontale e' spartito per zone, non conteso.** Sulla cifra gira la
scena, sul contenuto sotto cambia pagina. Il carosello era gia' stato tolto una
volta per questa ragione (trappola #5): adesso il confine e' dichiarato invece
che sottinteso, e la cifra vive fuori dal carosello. Il grafico consuma **solo**
la componente orizzontale del trascinamento, se no dentro una colonna che scorre
il dito sul grafico blocca la pagina.

**La settimana sta fuori dal carosello.** E' la stessa informazione per tutte e
cinque le grandezze: dentro la sola pagina della temperatura la rendeva lunga il
doppio delle altre e la nascondeva a chi guardava il vento.

**Una sola sorgente di verita' per la pagina: il carosello.** `detailMode` nello
stato e' la *modalita' d'ingresso*, non una seconda copia di "su quale pagina
sono". C'erano due copie riconciliate da due `LaunchedEffect`, e il secondo era
chiavato su cio' che il primo cambiava: mentre `animateScrollToPage` girava, la
pagina intermedia faceva cambiare la modalita', l'effetto veniva rilanciato e
**cancellava la propria animazione**. Toccare "Aria" da "Temp" lasciava il
carosello a meta' strada. Tre regole da non sciogliere:

- si scrive nello stato **solo su `settledPage`** - un trascinamento annullato
  non e' una scelta e non deve lasciare traccia nello stato globale;
- si legge `currentPage` per **cio' che si vede** (titolo, pillola accesa,
  cifra, tinta): la pagina posata cambia troppo tardi e l'intestazione
  resterebbe indietro per tutto il gesto;
- le pillole animano il pager **direttamente**, non passando dal ViewModel.

Lo stesso schema vale in `DayDetailScreen`, che lo aveva duplicato.

**Cio' che si muove col dito passa per lambda, non per valore.**
`currentPageOffsetFraction` cambia a ogni fotogramma: letto nel corpo di un
composable ricompone l'intera schermata sessanta volte al secondo per spostare
una trasparenza. Letto **dentro** `graphicsLayer` o dentro una tela si ferma
alla fase di disegno. Per la stessa ragione i pallini sono **una tela sola**
invece di cinque `Canvas` larghi in `dp`, che a ogni frame avrebbero rifatto
misura e posizionamento. E per la stessa ragione `snapshotFlow` riceve una
lambda: con un `Float` gia' calcolato dal chiamante non c'e' nessuno stato di
Compose da osservare e il flusso emette **una volta sola**.

**Una fila che scorre deve portare la selezione al centro, e
`animateScrollToItem` non lo fa**: quella si ferma al bordo d'ingresso. Il
residuo si calcola da `layoutInfo` - `centerOn` in `MeteoSurfaces.kt`, usata sia
dalle pillole sia dalle linguette dei giorni. E `spacedBy(..., CenterHorizontally)`
su una `LazyRow` centra quando il contenuto ci sta e scorre quando non ci sta:
con `horizontalScroll` restava incollato a sinistra in entrambi i casi.

**Un'ora precisa sopra un totale del giorno e' una bugia.** Sulla pagina del
sole l'intestazione diceva "OGGI · 15:00" sopra tredici *ore di sole*, che sono
quelle di tutta la giornata. `DetailMode.isDailyTotal` sapeva gia' distinguere i
due casi; adesso il sottotitolo glielo chiede.

**La sesta pagina e' la luna**, e non chiede niente alla rete: la fase la calcola
`MoonPhase` in locale (Open-Meteo non la fornisce), quindi e' l'unica pagina che
ha ancora qualcosa da dire quando la previsione non arriva. Per questo il suo
ramo dentro `Hero` sta **prima** del controllo sulla cifra, che spegne tutte le
altre. Il corpo e' quello della scultura e del widget - stessa sfera, stessa
luce, stessi mari - e gira con il `rotatesScene` che l'eroe aveva gia': niente
secondo riconoscitore da mettere d'accordo con il carosello. Vedi la trappola
\#36 per cosa gira e cosa no.

**Con sei pagine la fila di pillole non basta piu' da sola**, e il pulsante che
apre l'elenco (`PanelPicker.kt`) sta **fuori** dalla `LazyRow`: dentro
scorrerebbe via insieme alle pillole, cioe' sparirebbe proprio quando serve -
quando ci si e' scorsi lontano. Le pillole restano perche' fanno un mestiere che
l'elenco non fa: spostarsi di una posizione e dire dove si e'. L'elenco fa i due
che loro non fanno: mostrare tutto insieme, e portare dalla prima all'ultima in
un tocco. Il suo stato e' locale e non in `UiState`: sopravvivergli alla chiusura
del foglio vorrebbe dire riaprire il dettaglio e trovarsi davanti un elenco che
nessuno ha chiesto.

## 8-ter. Le allerte meteo

`data/WeatherAlert.kt`, `data/WeatherAlertsRepository.kt`,
`data/DerivedAlerts.kt`, `ui/alerts/`

**La fonte, e perche' non l'Aeronautica Militare.** Open-Meteo non ha un
endpoint per gli avvisi. Il servizio meteo dell'Aeronautica pubblica bollettini
su meteoam.it ma **non espone un'API pubblica documentata**: i dati si ottengono
per accordo, non con una GET - verificato, non supposto. Si usa **MeteoAlarm**,
il canale di EUMETNET su cui i servizi nazionali pubblicano in CAP, e per
l'Italia sono i bollettini della Protezione Civile e dei centri funzionali
regionali. Stessa informazione, per una via leggibile. Gli **RSS legacy sono
stati spenti il 14 gennaio 2026**: si legge l'Atom.

**Due strati, e la differenza si dichiara.** MeteoAlarm copre l'Europa; l'app no
- fra le localita' suggerite c'e' la Nuova Zelanda - e un feed puo' non
rispondere. Li' l'alternativa non e' un bollettino migliore, e' il silenzio
davanti a novanta chilometri orari di raffica: le soglie calcolate sui dati gia'
scaricati riempiono il buco senza una richiesta in piu'. L'ufficiale vince
sempre sul derivato dello stesso fenomeno, e `WeatherAlert.official` dice sempre
quale dei due si sta leggendo. **Il rosso non si emette per soglia**: e' una
valutazione del rischio sul territorio, non un confronto fra un numero e una
costante.

**Fuori copertura non e' un guasto.** In Nuova Zelanda MeteoAlarm non *deve*
rispondere: `OutOfCoverage` e' distinto dall'errore, e annunciarlo come tale
insegnerebbe a ignorare l'avviso quando invece e' vero.

**Un avviso che non si sa collocare si mostra lo stesso**, col nome dell'area
scritto sopra. La selezione usa il poligono CAP quando c'e' e il nome della
regione quando non c'e'; scartare cio' che non combacia significherebbe, per
un'allerta, non darla.

**La fascia non disegna niente quando non c'e' niente da dire**, e sta sotto la
barra invece che dentro il carosello: un avviso che si trova solo scorrendo fino
alla sesta pillola non avvisa nessuno.

**La fascia si riduce a un pallino, e il pallino la riporta.** Un'allerta puo'
durare tre giorni, e prima la fascia restava in cima per tutti e tre senza modo
di toglierla ne' di ritrovarla. Adesso ha una croce; chiusa, diventa un cerchio
col triangolo (`ui/alerts/AlertPill.kt`), e toccarlo riapre **insieme** la
fascia e il bollettino - un gesto solo, cosi' chi entra per leggere ritrova la
riga dov'era invece di dover cercare come farla riapparire.

Il pallino **sta nei 48dp che la riga in cima teneva gia' vuoti** per bilanciare
il pulsante delle impostazioni e tenere il nome della localita' al centro dello
schermo. E' esattamente `MinTouchTarget`, cioe' la misura di `MeteoIconButton`:
fra i due stati il nome non si sposta di un pixel, e il pallino non ruba
altezza - che e' precisamente cio' che si cerca chiudendo la fascia. Nel
dettaglio la fascia si riduce lo stesso, ma li' il pallino non compare: quella
barra non ha 48dp liberi, e infilarcelo vorrebbe dire spingere il titolo fuori
centro per un avviso che si e' appena chiesto di togliere.

**Quando la fascia torna intera** lo decide `alertsAreDismissed`, in
`data/WeatherAlert.kt` e non nell'interfaccia: e' una regola sui dati - quando
un avviso archiviato torna a essere una notizia - e da li' si prova senza far
partire niente di Android. Non basta ricordare **che** e' stata chiusa, va
ricordato **cosa**: si salvano gli identificativi e il peso del livello
peggiore, e la fascia resta ridotta se e solo se ogni allerta in scena era gia'
fra quelle **e** la peggiore di adesso non e' piu' grave della peggiore di
allora. Quindi un'allerta nuova la riapre, un peggioramento la riapre (stesso
identificativo, altra notizia), una che scade no - la condizione e' per
inclusione, non per uguaglianza degli insiemi.

Due cose imparate scrivendolo:

- **`clearAndSetSemantics` su tutta la riga non si puo' piu' fare.** Con un
  secondo bersaglio dentro, cancellerebbe anche il pulsante di chiusura, e un
  lettore di schermo resterebbe senza il modo di ridurre la fascia. La semantica
  a blocco sta adesso sulla sola parte leggibile.
- **`fillMaxHeight` li' non fa niente.** Il vincolo di altezza che arriva dal
  genitore e' illimitato e Compose lo ignora: l'area toccabile sarebbe rimasta
  alta quanto il testo invece che quanto la fascia. Ci vuole `heightIn`.

**Com'e' fatto il feed non si deduce: si guarda.** La prima stesura del parser
era scritta su `awareness_level` e `awareness_type`, che sono i campi che la
documentazione di terze parti descrive e che nel feed vero **non esistono** -
zero occorrenze su trentacinquemila byte. Ogni allerta sarebbe uscita come una
gialla generica, senza un errore da nessuna parte. La forma vera:

```xml
<cap:areaDesc>Basilicata</cap:areaDesc>
<cap:event>Yellow High-temperature Warning</cap:event>
<cap:severity>Moderate</cap:severity>
<cap:expires>2026-09-04T17:59:00+00:00</cap:expires>
<link type="application/cap+xml" href="..."/>
```

Da cui: il **colore sta dentro la frase inglese** di `cap:event` e non nella
severita' accanto, che e' piu' grossolana - tutte e ventitre le voci della
cattura dicevano `Moderate`, comprese le gialle. Non c'e' **nessun poligono**:
l'area e' un nome. E descrizione e raccomandazioni non stanno nell'Atom ma nel
documento CAP collegato, che si va a prendere solo per le poche voci che
riguardano la localita' mostrata.

Una copia della risposta vera sta in `ci-artifacts/api/allerte.xml`, ripubblicata
a ogni giro: e' il posto da cui guardare prima di toccare il parser.

Quattro trappole gia' pagate qui:

- **I nomi delle regioni non combaciano, e il confronto per sottostringa
  fallisce in silenzio.** Il feed chiama la regione di Forli' *"Emilia e
  Romagna"*, Open-Meteo la chiama *"Emilia-Romagna"*: nessuna delle due
  contiene l'altra. Forli' sarebbe rimasta senza allerte per sempre, senza un
  errore. Si confrontano **insiemi di parole significative** (tre lettere o
  piu'), cosi' le congiunzioni - che sono esattamente cio' che differisce -
  cadono.
- **`nextText()` solleva su un elemento che ha figli.** `cap:geocode` contiene
  `valueName` e `value`, `author` contiene `name` e `uri`: chiamarla su tutto
  avrebbe fatto fallire la lettura dell'intero feed. Solo le foglie ci passano.
- **I titoli si compongono in italiano, non si copiano.** Il feed scrive
  "Yellow High-temperature Warning": messo in cima a una schermata italiana
  sarebbe stata la traduzione mancante piu' visibile dell'app.
- **L'allerta imposta si applica in lettura, non scrivendo in `alerts`.** Il
  primo caricamento sovrascrive quella lista con le allerte vere, e lo scatto
  usciva senza fascia. Le schermate leggono `UiState.shownAlerts`.

## 8-quater. I test

`app/src/test/`

Il progetto non ne aveva **nessuno**. Sotto c'era solo `probe-api`, che verifica
i contratti delle API ma non una riga di logica.

Trentanove prove, tutte su funzioni pure, nessun emulatore, un job `test` a se'
stante. Sono scelte per cio' che coprono, non per fare numero — e tre di loro
stanno esattamente sopra trappole gia' pagate:

| Cosa | Perche' proprio quello |
|---|---|
| `parseFeed` sul feed vero | `src/test/resources/meteoalarm-italia.xml` e' la cattura da `ci-artifacts/api/allerte.xml`, ventisette voci, **non** un file scritto su come il formato dovrebbe essere. E' il punto in cui la prima stesura era interamente sbagliata senza che niente lo dicesse. |
| Il confronto fra nomi di regione | `"Emilia e Romagna"` vs `"Emilia-Romagna"`: nessuna contiene l'altra, e per sottostringa Forli' sarebbe rimasta senza allerte per sempre, in silenzio. |
| `derivedAlerts` | Le soglie, e il vincolo che **nessuna soglia emette una rossa**. Il test prova con 200 m/s e 900 mm e pretende che non esca. |
| `alertsAreDismissed` | I quattro casi che a mano vorrebbero dire aspettare un'allerta vera, poi una seconda, poi un peggioramento, poi una scadenza. |
| `Wmo.family` / `isWet` | La trappola #14 vive qui: se il codice dice che piove, deve piovere. |
| `readableOn` | Mantiene la soglia che dichiara, su una griglia di fondi che comprende il grigio medio - il caso peggiore, perche' di li' non si scappa ne' verso il bianco ne' verso il nero. |
| `TempUnit.from` | 21 °C -> 70 °F, la coppia gia' verificata sul telefono. |

**Robolectric serve a un file solo**, e va **configurato**: `parseFeed` passa da
`android.util.Xml`, che su una JVM non c'e'. Il progetto compila contro il
compileSdk 37 e Robolectric non ha l'`android-all` corrispondente, quindi senza
un `@Config(sdk = [34])` prova a procurarselo e solleva
`UnsupportedOperationException` **prima ancora del primo test** - fallisce
l'intera classe per una ragione che non c'entra niente col parser. Trentaquattro
e non un altro numero perche' e' un livello che Robolectric copre di sicuro, e
qui della piattaforma serve solo `android.util.Xml`, che da API 1 non cambia. Riscrivere il parser su SAX per togliere la dipendenza
avrebbe voluto dire rifare da capo codice gia' pagato caro contro questa stessa
risposta, e un test non vale quel rischio. Resta fuori dall'APK.

**`isWet()` e' passata da `WeatherSculpture.kt` a `Wmo.kt`.** Parla di codici
WMO, non di come si disegna una nuvola, e stava in millequattrocento righe di
Compose solo perche' la scultura e' stato il primo posto in cui e' servita.

---

## 9. Preferenze dell'utente, dette esplicitamente

- Stile **minimal e compatto**, **pochi spazi vuoti**, movimenti responsive.
- La grafica e l'intuitivita' contano piu' della parte tecnica.
- Lavorare **una schermata alla volta**, facendone il modello per le altre.
- Riferimento estetico: *(not boring) weather app*. Immagini in
  `design/riferimento/`.
- Da evitare: cromato saturo, alone neon, geometria a tubo. Il target e'
  **plastica bianca opaca fresata** con smussi netti e iridescenza confinata al
  10-15% della superficie.
- Vuole essere avvisato in anticipo dei limiti, non dopo.
- **Non vuole spiegazioni sugli errori commessi**: vanno corretti e basta.

---

## 10. Se servissero modelli 3D fatti a mano

Il personaggio del benvenuto (sezione 4-bis) e' stato fatto **senza**, con le
sfere che il motore gia' disegnava: i suoi gesti sono di parti rigide, e per
quelli bastano una gerarchia di trasformazioni e un ordinamento in profondita'.
Quanto segue vale per il caso diverso - una superficie che si deforma, o un
oggetto la cui forma non si riesce a comporre con le primitive che ci sono.

Oggi **non servono**: sole, luna, nuvole e cifre sono generati dal codice, e la
cifra deve restare tale perche' cambia a ogni ora. Se pero' si volesse sostituire
la scultura meteo con modelli veri, questo e' cio' che il motore sa consumare —
e cio' che andrebbe scritto per farglielo consumare.

**Formato**: OBJ o glTF, triangolato, **normali per vertice** incluse. Nessun
materiale, nessuna texture: il colore lo mette l'app dalla tavolozza dell'ora,
altrimenti alba e tramonto non tingerebbero l'oggetto.

**Orientamento e scala**: asse Y in alto, Z verso l'osservatore in negativo,
origine al centro del volume (la rotazione avviene li'). Modello contenuto in un
cubo da -1 a 1: l'app lo riscala.

**Complessita'**: sotto i 1500 triangoli per oggetto. La proiezione e'
software, un vertice per volta.

**Cosa serve, uno per file**: sole (corpo piu' corona di raggi come geometria
separata, cosi' la corona puo' foreschiarsi ruotando), luna (sfera con rilievi
leggeri; la fase la seziona l'app), nuvola bianca e nuvola carica (grappoli di
masse a **profondita' diverse** — se sono complanari, ruotandole si vede che
sono cartone).

**Quello che il motore non fa** e che quindi non va modellato: ombre proprie fra
parti, trasparenze, riflessi. Una sola luce direzionale fissa e Lambert
dimezzato.
