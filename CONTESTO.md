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

`.github/workflows/build.yml` ha quattro job: `probe-api` (interroga Open-Meteo
e pubblica il JSON reale), `build`, `rilascio` (pubblica l'APK sul tag fisso
`apk-latest`), `screenshots` (emulatore API 34). Gli output finiscono sul branch
`ci-artifacts`, separato da quello di sviluppo:

```bash
git fetch origin ci-artifacts
git show origin/ci-artifacts:screenshots/scuro-1-temp.png > /tmp/x.png
git show origin/ci-artifacts:api/hourly.json          # contratto API reale
```

**L'APK sta sempre a**
<https://github.com/NoximilienCoxen/test-weather/releases/tag/apk-latest>
(tag fisso, chiave di debug fissa versionata cosi' le build si installano una
sopra l'altra senza disinstallare).

---

## 2. Toolchain, verificata alle fonti

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

**Il tipo di build `debug` ha `isDebuggable = false`.** Non e' una svista: una
app debuggabile gira con ottimizzazioni ridotte, e questa schermata fa geometria
in tempo reale a ogni fotogramma. Misurato sullo stesso identico codice:
**38 ms per fotogramma con il flag, 16 ms senza**. Chi lo rimette per usare il
debugger deve sapere che sta misurando un'app che non esiste.

---

## 3. Cosa fa l'app oggi

**Si apre sulla schermata principale** (`ui/home/HomeScreen.kt`): pulsante
impostazioni a sinistra, nome della localita' al centro, scultura meteo,
temperatura dell'ora scelta, condizione, barra delle 24 ore colorata per meteo,
e sotto l'ora mostrata.

**Il dettaglio sale trascinando verso l'alto** (`ui/MeteoApp.kt`): un foglio che
segue il dito, reversibile a meta' corsa. **Le impostazioni entrano da
sinistra**, da dove sta il loro pulsante.

**Le impostazioni** (`ui/settings/SettingsScreen.kt`) hanno tre sezioni:
localita' (ricerca per nome piu' un elenco di scorciatoie), unita' della
temperatura, e da dove arrivano i dati. Le prime voci dell'elenco sono fra i
posti piu' piovosi che esistano, e ci sono apposta: con una citta' sola non
c'era modo di vedere la pioggia se non aspettando che piovesse.

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
10. Le **ombre** si disegnano tutte prima di tutti i corpi (trappola #12), sul
   **piano mediano** e non su una delle due basi (trappola #21), e cadono nel
   verso opposto alla luce, che e' letto da `Light.Standard` invece di essere
   una seconda impostazione libera di contraddirla.
11. Vicino al quarto di giro **base e ombra si spengono**. Li' i quattro angoli
   del riquadro finiscono quasi in fila e la matrice che li segue e' quasi
   singolare: esiste, ed e' fatta di numeri che divergono (trappola #20).
12. Il **valore cambia rotolando**, e rotolano **solo le colonne che cambiano**:
   ventotto che diventa ventinove muove il nove, non il due. Lo scostamento e'
   in coordinate del modello e va sommato **prima** della proiezione: applicato
   alla tela sposterebbe un'immagine gia' piatta, e la cifra scorrerebbe come un
   adesivo invece di muoversi nello spazio - lo stesso errore gia' bocciato qui
   una volta. Le cifre in movimento stanno dentro un ritaglio e **senza ombra
   portata**: e' la voce piu' cara del disegno, qui i corpi sono due, e il
   ritaglio la taglierebbe di netto proprio dove sfuma. Se il numero cambia
   lunghezza le colonne non si corrispondono e rotola tutto insieme; se due
   valori arrivano a meno di un decimo e mezzo di secondo l'uno dall'altro si
   sta scorrendo la barra e non si rotola affatto - due numeri per fotogramma
   proprio mentre se ne guarda uno solo di sfuggita.
13. Il **grado** e' l'ultimo carattere del testo, in corpo ridotto e allineato in
   alto (`smallTail` in `NumberSpec`). Fa parte del prisma apposta: viene
   estruso, illuminato e girato con le cifre. Un simbolo sovrapposto in
   coordinate di schermo resterebbe fermo mentre l'oggetto gira.
   **Anche lo spessore si riduce con lui** (`Part.depthScale`): il suo anello e'
   largo novanta pixel e lo spessore comune ne misura centoventi, quindi lasciato
   spesso come una cifra non era piu' un simbolo ma un pezzo di tubo appoggiato
   accanto al numero. Ridotto nella stessa proporzione resta la stessa lastra,
   ritagliata piu' piccola.
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
adb shell am start -n com.forli.meteo/.MainActivity --ei ora 2 --ei meteo 63
```

| Extra | Effetto |
|---|---|
| `--ei ora` | fissa l'ora mostrata (ricordata se i dati non sono ancora arrivati) |
| `--ei meteo` | impone il codice WMO |
| `--ei giro` | blocca la scena a un angolo, in gradi (accetta lo zero) |
| `--ez lento` | porta il rotolamento della cifra a quattro secondi |

Il rotolamento si fotografa avviando con `--ez lento true` e poi rimandando un
`am start -f 0x20000000` con un'ora diversa: **SINGLE_TOP**, se no l'attivita'
riparte da capo e con lei riparte anche il numero, invece di riceverne uno
nuovo mentre e' viva.

L'aggancio sul giro c'e' perche' **i difetti che si vedono girando vanno
fotografati girati**, e un trascinamento simulato non ci arriva: per portare la
cifra di taglio servono quattrocento pixel, per vederla da dietro piu' di
ottocento, e uno schermo e' largo mille. Senza, il quarto di giro - che e'
esattamente dove le matrici degenerano e le pareti si scavalcano - non era
fotografabile, e infatti quei difetti li ha trovati l'utente e non la CI.

L'aggancio sul tema non c'e' piu' perche' non c'e' piu' un tema da scegliere:
giorno e notte li decide l'ora mostrata, e per fotografare la notte basta
chiedere un'ora notturna.

Per leggere lo stato senza guardare le immagini, la struttura di accessibilita'
espone i testi:

```powershell
adb shell uiautomator dump /sdcard/ui.xml; adb shell cat /sdcard/ui.xml
```

Per le prestazioni, `dumpsys gfxinfo com.forli.meteo framestats`. **Attenzione
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

**16. Chiedere l'intensita' della vibrazione non basta a ottenerla.** Su questo
telefono `hasAmplitudeControl()` risponde di no e un'ampiezza dichiarata viene
ignorata: la pioggia usciva forte quanto il tuono. `WeatherHaptics` prova in
ordine le primitive componibili, l'ampiezza, gli effetti gia' pronti del sistema
e infine la sola durata. Qui finisce su `EFFECT_TICK` contro `EFFECT_HEAVY_CLICK`,
che sono tarati bene e si distinguono davvero.

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

1. **La schermata di dettaglio e' rimasta indietro.** Compila e non e' rotta, ma
   non ha ricevuto ne' la rotazione (il pager si contende il gesto orizzontale)
   ne' una passata di composizione. Guardandola: la cifra e' piccola in mezzo a
   un vuoto enorme, il titolo estruso ("Temp.") e' un residuo, non c'e' segno di
   quante pagine ci siano, e in fondo `DayStrip` e `ScrubBar` fanno la stessa
   identica cosa. Il pezzo grosso da fare li' e' dare senso al selettore
   GIORNO/SETTIMANA: ora che i dati orari ci sono, la stessa barra della
   principale puo' scorrere 24 ore in GIORNO e 7 giorni in SETTIMANA, e cosi'
   sparisce anche la ridondanza. Nota: in GIORNO servirebbero grandezze orarie
   che oggi non si chiedono all'API (vento, umidita', punto di rugiada).
2. **Transizioni continue** — cifre a contachilometri al cambio valore, tabella
   scaglionata, curve che si deformano invece di saltare.
3. **Posizione del dispositivo** — `LocationManager` di piattaforma, **non**
   `play-services-location`: sarebbe una dipendenza nuova. Permesso solo
   approssimato.
4. Ridondanza da sanare: `DayStrip` e `ScrubBar` nel dettaglio fanno la stessa
   cosa.

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
