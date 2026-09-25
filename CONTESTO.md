# Contesto per riprendere il lavoro

Documento di passaggio. Leggilo prima di toccare qualsiasi cosa: contiene
vincoli d'ambiente che non si deducono dal codice e diagnosi gia' pagate care.

**Se apri il progetto per la prima volta, o se ci lavori in parallelo a
qualcun altro, il giro di comandi da seguire dal primo all'ultimo e' la
sezione 11** — come si firma, come si arriva a `main`, e perche' l'APK sulla
release cambia solo passando di li'.

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
git show origin/ci-artifacts:screenshots/scuro-d3-allerta-pallino.png > /tmp/y.png
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
| Gradle | 9.6.0 — **minimo di AGP 9.4**, non una scelta |
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
resto era verde: un salto di toolchain **si porta dietro il wrapper di Gradle**,
e messo in mezzo ad altro non si saprebbe chi ha rotto cosa. Qui e' successo
esattamente: AGP 9.4 pretende Gradle **9.6.0**, e con il 9.4.1 il plugin non si
applica nemmeno.

Non e' stato indovinato. Il messaggio di Gradle dice il numero e dice anche il
file da toccare:

```
Minimum supported Gradle version is 9.6.0. Current version is 9.4.1.
Try updating the 'distributionUrl' property in gradle/wrapper/...
```

Con questa in `dipendenze.txt` non resta piu' una riga `DA AGGIORNARE`.

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

**Sopra la barra delle ore c'e' il diagramma della giornata**, alto ventidue
punti, e **si vede solo mentre il dito e' sulla barra**: la temperatura come
linea, la pioggia come colonnine sotto.

Perche' non sempre acceso: a riposo la schermata deve dire poche cose grandi -
scultura, cifra, condizione - e una curva permanente e' un quarto oggetto che
chiede attenzione a chi ha aperto l'app solo per sapere che tempo fa adesso.
Serve invece **mentre si scorre**, che e' l'unico momento in cui si sta
ragionando sull'andamento della giornata. Lo stato lo alza il riconoscitore di
gesti gia' esistente, dentro un `try/finally` - un gesto puo' finire anche per
annullamento, e senza il `finally` il diagramma resterebbe acceso senza un dito
sopra. La dissolvenza e' **asimmetrica**, 120 ms in entrata e 260 in uscita:
comparendo deve essere gia' li' quando l'occhio arriva, uscendo non deve sbattere
via nell'istante in cui ci si stacca.

**Massima e minima stanno sul diagramma**, in alto a sinistra, a nove punti,
ciascuna col colore che `temperatureTint` da' al proprio valore - la stessa scala
del gradiente della curva. Erano scritte sotto la condizione, come `23° / 37°`:
di li' sono state tolte, perche' ripeterle in due posti sarebbe la stessa
informazione due volte. La riga sotto la condizione resta, ma ci vive solo la
percepita, e ha `minLines = 1` perche' e' vuota per gran parte della giornata e
senza un'altezza garantita farebbe sussultare tutto cio' che le sta sopra.

Due cose provate e scartate sul telefono, che vale la pena non riprovare:

- **Incolonnate**, massima in alto e minima in basso. E' la lettura giusta - alto
  in alto - ma non ci stanno: due righe da nove punti ne occupano quasi
  cinquantotto su ventidue di fascia, i numeri si toccavano e quello di sotto
  finiva a cavallo della curva.
- **Un margine a sinistra** per far loro posto restringendo la curva. Sposta ogni
  punto rispetto all'ora che gli sta sotto: il diagramma direbbe "questa
  temperatura a quest'ora" indicando l'ora sbagliata, e l'allineamento con la
  pista e' l'intera ragione per cui vive nella stessa tela. I numeri passano
  quindi **sopra** al tratto, e l'angolo in alto a sinistra e' libero perche' a
  mezzanotte la temperatura non e' quasi mai il colmo della giornata.

I ventidue punti **restano riservati** anche da spento: si anima la sola
opacita'. Chiuderli farebbe allargare la scultura a ogni tocco, ed e' lo stesso
sussulto che il riquadro di TORNA AD ADESSO evita gia' riservando la propria
altezza. Sotto l'uno per cento di opacita' il blocco non viene disegnato affatto:
a riposo - cioe' quasi sempre - non si costruisce nemmeno la spline, quindi il
caso piu' comune e' piu' leggero di prima, non piu' pesante. E' lo stesso
disegno che il grafico del dettaglio faceva per esteso - stessa spline
(`buildLinePath`), stessa scala di colore (`temperatureRamp`) - ma senza assi,
numeri, griglia e tocco. Qui non e' una cosa da leggere punto per punto, e' la
forma della giornata vista di sfuggita mentre si sceglie un'ora. **Da quando il
grafico non c'e' piu' e' anche l'unica curva rimasta nell'app**, e questo alza
il suo peso: era un accenno accanto a un disegno completo, adesso e' tutto
quello che si dice sull'andamento. Sta **dentro la tela di `HourBar`** e non in un
composable sopra, per la ragione gia' scritta in cima a quel file: la scala
orizzontale dev'essere la stessa della pista, e due tele che si accordano sulla
geometria vanno d'accordo finche' qualcuno non tocca una sola delle due.

**E la pista adesso e' colorata anche dai gradi.** Era gia' "colorata dal meteo",
ma solo il bagnato aveva un colore suo: l'asciutto era `colors.line` e basta,
quindi una giornata di sole - la maggioranza dei giorni - usciva ventiquattro
caselle grigie identiche. Una barra che sul meteo piu' comune non dice niente.
Adesso l'asciutto vira verso `temperatureTint` al 62% e il nuvoloso al 32%,
mentre **pioggia, neve e temporale tengono il loro colore intero**: sono la cosa
che si cerca guardando la barra, e annacquarle coi gradi renderebbe una mattina
di pioggia calda meno azzurra di una fredda, cioe' meno riconoscibile dove conta.

**Al posto di "TORNA AD ADESSO" c'e' un orologio.** La pillola era una parola in
un fondo pieno, larga un terzo di schermo, che compariva e spariva a ogni
scorrimento: pesava come un comando primario per una cosa che si fa di rado, ed
era l'unico rettangolo opaco in una schermata fatta di cielo. Adesso e' un
cerchio con due lancette, disegnato (niente `material-icons-extended`, come la
freccia di `MeteoSurfaces`), senza fondo, al 62% di opacita'.

**Le lancette segnano l'ora vera**, e quella della **localita'**, non del
telefono: la prende da `Forecast.nowThere()`, perche' gli orari della barra sono
nel fuso del posto - col telefono a Los Angeles e la previsione su Forli' un
orologio sul fuso del telefono segnerebbe nove ore diverse da quelle che indica
il pallino sulla pista. La lancetta delle ore avanza anche dentro l'ora, mezzo
grado al minuto, come su un quadrante vero. Il valore si aggiorna con un battito
ogni venti secondi che **scrive solo al cambio di minuto**, la stessa regola di
`rememberFreshness` e per la stessa ragione.

Il **perno** al centro non e' un vezzo: con l'ora vera le due lancette finiscono
spesso nello stesso quadrante - alle nove e trentacinque escono tutte e due a
sinistra - e senza un centro dichiarato il disegno si legge come una spezzata
qualunque. E' il prezzo dell'ora vera rispetto a una posa fissa, ed e' stato
accettato sapendolo: un quadrante che segna davvero l'ora vale una lettura un po'
meno immediata in certi momenti della giornata.

Il **bersaglio**
pero' resta pieno (`MinTouchTarget`): il disegno e' piccolo, la zona che lo
riceve no. Sotto, il margine inferiore e' largo apposta - tutta la colonna vive
dello spazio che avanza alla scultura, quindi allontanarla dal bordo la fa salire
tutta insieme.

**In fondo, ore e settimana si danno il cambio** (`ui/home/WeekBar.kt`). Sopra la
barra ci sono due parole, `ORE · SETTIMANA`, e quella accesa e' quella che si sta
guardando. La spenta e' **`colors.text` al 42%**, non `colors.label`: la prima
versione usava l'accoppiata `text`/`label` che la schermata adopera dappertutto,
e qui non funzionava. Altrove quei due colori separano un titolo da una
didascalia - due cose che si distinguono anche dal posto e dalla dimensione -
mentre qui dovevano dire *quale delle due e' accesa*, e `label` e' `text`
smorzato sul cielo (`mutedOnBoth`): su un cielo diurno chiaro i due grigi
finivano a un soffio l'uno dall'altro. Il risultato e' che `ORE · SETTIMANA` si
leggeva come una didascalia sola, e chi cercava le ore non trovava il comando per
tornarci - **e' successo davvero, alla prima persona che ha provato l'app**.
L'opacita' invece non dipende da quanto il cielo e' chiaro. Regola generale per
questa schermata: ogni tinta fissa va riprovata contro un fondo che cambia tutto
il giorno. Al posto della barra delle ore compare una striscia di giorni -
sigla, icona, massima grande e minima sotto, piu' spenta - fino a otto colonne,
oggi compreso.

Sono due domande diverse sulla stessa previsione: *quando, dentro oggi* e *quale
giorno*. **Non stanno una sotto l'altra** perche' la scultura in mezzo allo
schermo vive dello spazio che le resta, e due strisce impilate gliene toglievano
un'ottantina di punti: la scelta e' stata tenere la scultura grande e far
alternare le due strisce nello stesso posto. Il riquadro che le ospita si
allunga e si accorcia animato (`animateContentSize`), cosi' il passaggio e' un
movimento e non uno scatto.

Due conseguenze da sapere. **Una colonna si tocca e il giorno si sceglie**, e da
li' lo raccontano tutte le schede del feed: il giorno e' un asse, non una
schermata (`selectDay`). Il bersaglio e' la colonna intera - sigla, icona e le due cifre - non il
solo glifo da ventisei punti. Che gli otto bersagli cadano dove passa il pollice
che scorre le ore non e' un conflitto: quando c'e' una striscia l'altra non c'e'.
Attenzione a un punto solo, ed e' scritto anche nel codice: l'indice da passare
e' quello dentro `forecast.days`, non quello della colonna - `withIndex()` viene
**prima** del filtro dei giorni senza temperatura, altrimenti con un modello
corto si aprirebbe il giorno sbagliato. E **TORNA AD ADESSO sparisce
con la settimana in scena**: li' non c'e' un'ora scelta da cui tornare, e il
tasto prometterebbe di riportare dove non si e' andati. La scelta fra le due
sopravvive alla rotazione ma non alla chiusura: riaprendo l'app la domanda torna
a essere "che tempo fa adesso".

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

**Quella che si e' appena descritta e' la prima scheda di sei** (`ui/feed/`).
Scorrendo dal basso verso l'alto si passa alla pioggia, poi all'aria, al vento,
al sole, alla luna: una schermata piena ciascuna, sullo stesso cielo. Vedi la
sezione 8-bis, che e' dove sta il ragionamento per intero.

**Le impostazioni entrano da sinistra**, da dove sta il loro pulsante; **le
allerte da destra**, perche' si scende dentro qualcosa di piu' specifico. Sono
gli unici due strati rimasti sopra il feed.

**Il giorno non e' una schermata**: e' un asse che tutte le schede leggono
(`state.selectedDay`, e da li' `pageDay` e `detailHour`), scelto da una colonna
della striscia della settimana sulla prima scheda.

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

**Che fonti copra davvero l'app, e dove no.** La domanda "a Tokyo si puo' usare
JMA" ha gia' una risposta nel codice, e nessuno l'aveva verificata: **Open-Meteo
non e' un modello, e' un aggregatore**, e `best_match` sceglie da se' quello
nazionale del posto - ICON del DWD sull'Europa centrale, AROME sulla Francia,
**JMA sul Giappone**, GFS/HRRR sugli Stati Uniti. Quindi la previsione a Tokyo
non e' sbagliata. Ma era memoria, e qui la memoria non vale: `probe_models.py`
adesso fa un **giro mondiale** su undici punti e pubblica, per ciascuno, quale
modello risponde sotto `best_match` (ricavato per confronto dei valori, perche'
l'API il nome non lo dichiara) e **quali colonne tornano nulle**. La seconda meta'
vale piu' della prima: una variabile accettata ma non riempita torna come una
colonna di `null`, e chi la disegna ci vede una linea a zero - cioe' una
previsione di niente invece di un "non lo so".

**Misurato, e non piu' supposto** (`ci-artifacts:api/modelli.txt`):

| punto | chi risponde sotto `best_match` |
|---|---|
| Tokyo | `jma_seamless` — **JMA c'e' gia'** |
| New York | `gfs_seamless` (NOAA/NWS) |
| Oslo | `metno_seamless` |
| Noceto | `icon_seamless` (DWD) |
| Nairobi, Citta' del Capo, Sydney, San Paolo, Delhi, Singapore | indistinguibile fra `metno_seamless` e `knmi_seamless`: fuori dai loro domini servono lo stesso modello globale e danno gli stessi identici decimali |

E la risposta che serviva alla scheda della pioggia: su **tutti** i punti tornano
centosessantotto ore e sette giorni, **nessuna variabile assente e nessuna
colonna tutta nulla**. `precipitation_probability` c'e' ovunque, quindi la curva
della fascia non esce vuota da nessuna parte. Reykjavik e' l'unica riga senza
risposta, e per un timeout TLS della CI: e' un guasto di rete, non un dato.

**I buchi veri sono due, e nessuno dei due e' la previsione.**

*L'aria si misurava sempre con la scala europea.* Il dato e' mondiale, la scala
no: applicare l'indice dell'Agenzia europea a Tokyo da' **un numero giusto su una
scala sbagliata**, e cinquanta e' aria mediocre in Europa e aria buona negli Stati
Uniti - due bande di distanza. Adesso si chiedono tutte e due e **a scegliere sono
i dati**: se l'endpoint riempie `european_aqi` si e' dentro il dominio della sua
convenzione, se lo lascia nullo si e' fuori. Un rettangolo di longitudini
disegnato a mano sbaglierebbe sui bordi, e in silenzio. **Quale scala si sta
usando sta scritto sotto la cifra**: due indici con lo stesso nome e soglie
diverse, senza etichetta, sono peggio di uno solo.

*Le allerte fuori Europa non ci sono.* MeteoAlarm copre trentasette paesi
europei, e a Tokyo, New York o Sydney l'app non mostra nessun avviso. **Il parser
di una seconda fonte non si scrive a memoria**: la prima stesura di quello di
MeteoAlarm cercava `awareness_level` e `awareness_type`, che nel feed non
esistono. La sonda nuova interroga il Severe Weather Information Centre della
WMO - CAP, cioe' il formato che l'app **sa gia' leggere** - e pubblica quello che
risponde. Il FOSS Public Alert Server resta fuori apposta: chi lo scrive dichiara
che non e' pronto per la produzione.

**E ha gia' risposto due volte, e le due risposte valgono la sonda.** Al primo
giro: quattro indirizzi provati a memoria, **quattro 404**. Se ne fosse stato
scelto uno e ci si fosse costruito sopra un lettore, adesso ci sarebbe del codice
che deserializza una pagina di errore - la trappola di `awareness_level` presa un
giro prima. Al secondo, letta la pagina che gli indirizzi dovrebbe elencarli:
**nel suo HTML non ce n'e' nessuno**, la lista se la costruisce con JavaScript, e
il titolo della pagina dice "(Demo)".

Quindi **la fonte mondiale non e' agganciabile cosi' com'e'**, e questo e' il
punto in cui C1 sta adesso: non un lettore da scrivere, ma un indirizzo da
trovare. Il candidato che la pagina stessa indica e' il registro delle autorita'
di allertamento (`alertingauthority.wmo.int`), ed e' quello che la sonda chiede
adesso. Chi riprende: **non si scrive il lettore finche' un giro non pubblica una
risposta CAP vera**, ed e' la stessa regola per cui questo passo esiste.

E intanto una cosa che non aspetta nessun indirizzo: fuori copertura la schermata
scriveva "NESSUNA ALLERTA - non risultano avvisi in corso", cioe' **rassicurava
su un posto dove non aveva guardato**. Adesso gli stati sono tre - non c'e' una
fonte, la fonte non risponde, la fonte ha risposto e non c'e' niente. **Un
silenzio non e' una risposta rassicurante: e' un silenzio.**

**Perche' non si aggiungono JMA, NWS e compagnia come sorgenti di previsione.**
Duplicherebbero una strada gia' percorsa - `best_match` quei modelli li porta
gia' - aggiungendo per ciascuna un DTO, una traduzione di codici meteo, un fuso,
un formato di data e un modo nuovo di fallire, per gli stessi numeri. E questo
progetto non ha una libreria di rete, per scelta: ogni fonte e' un client e un
parser scritti a mano. Breezy Weather ne regge una cinquantina perche' ha un
intero strato di astrazione, ed e' la sua ragione sociale; quella di Caelum e'
una schermata disegnata a mano.

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

- `ui/sala/SalaGiro.kt` (era `ui/motion/SceneRotation.kt`, uscito col feed):
  un solo orientamento per la scultura di Sala I e la luna di Sala IV.
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
| `--ei giorno` | sceglie quel giorno della settimana: lo leggono tutte le schede |
| `--ei sezione` | apre il feed su una scheda: 0 temperatura, 1 pioggia, 2 aria, 3 vento, 4 sole, 5 luna |
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

L'aggancio sulla sezione c'e' per la ragione dell'emulatore, non per comodita':
le sei schede stanno una sotto l'altra, e raggiungerle col dito vuol dire cinque
trascinate verticali di fila - **esattamente il carico che ha gia' fatto morire
l'emulatore due volte** (trappola #38), con il logcat dell'app pulito e la
macchina virtuale sparita. Con l'aggancio ogni scheda si fotografa da un avvio,
e un avvio non puo' cadere a meta' come una trascinata. Il prezzo e' che gli
scatti non provano piu' che lo scorrimento funzioni: ma quello e' un movimento,
e un movimento in uno scatto non si giudica comunque - si prova in mano.

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
bloccato da quello decorativo**. Orizzontale ruota, verticale scorre - allora
apriva il foglio, adesso cambia scheda del feed, ed e' la stessa spartizione con
un contenuto diverso dentro.

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
succedeva niente. Il foglio poi e' uscito di scena, il tiro no: adesso vive in
`ui/feed/FeedScreen.kt` e si prende il dito sulla prima scheda, dove sopra non
c'e' nessuna scheda da mostrare.

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

- gli agganci `--ei giorno` e `--ei sezione` mettono in scena un altro giorno e
  un'altra scheda **senza gesti**, come `--ei giro` fa per la cifra di taglio:
  sono stati che col dito, qui, non si raggiungono in modo affidabile;
- **si fotografa prima cio' che non ha mai avuto uno scatto** e poi le prove del
  motore 3D, che una galleria alle spalle ce l'hanno. Quel che resta fuori e'
  sempre la coda, quindi in coda va messo cio' che si puo' perdere;
- il giro fallisce se il dispositivo non c'e' piu' alla fine. Contare gli scatti
  mancati non bastava: la soglia si azzecca per difetto, e con esattamente tre
  mancati il controllo lasciava passare un giro monco. Prima ancora, il job era
  **verde** con dodici scatti mancanti su trentasette, perche' guardava solo che
  ce ne fosse almeno uno - e un job verde che ha fotografato meta' delle
  schermate e' peggio di uno rosso: sembra una verifica fatta.

**Il meccanismo si e' trovato, e non era nel logcat.** "Non c'e' un log da
leggere perche' a morire e' il processo che il log lo ospita" era vero solo per
il logcat: **l'emulatore ha un log suo**, che finisce nel log del job e che
nessuno aveva ancora aperto. Li' dentro, nel giro `33910250383`, c'e' la riga
che mancava:

```
ERROR | Failed to find ColorBuffer: 121
```

e poco dopo `screencap: error: closed`, poi `device 'emulator-5554' not found`
per tutto il resto del giro. Un `ColorBuffer` e' una texture che vive sul lato
host: il guest la nomina per numero e l'host gliela tiene. Quel messaggio dice
che il guest ne ha chiesta una che l'host non aveva piu' - non memoria finita,
proprio i due lati che non sono piu' d'accordo su cosa esiste.

Da qui si spiega anche perche' moriva "sempre nel foglio di dettaglio" senza
che il foglio c'entrasse: **la coda degli scatti e' fatta di riavvii**. Ogni
`restart_with` chiude l'app e la riapre per fissare un'ora o un codice meteo, e
ogni riavvio butta via una superficie GL e ne crea un'altra. Trenta scatti sono
una trentina di cicli, e il conto si rompe intorno al quindicesimo - che e'
esattamente dove sta la coda, cioe' il dettaglio. La correlazione col foglio era
vera e la causa no, come il testo qui sopra sospettava: e' l'ordine, non il
contenuto.

**Cosa si e' cambiato, e con che aspettative.** In `build.yml`: `-no-snapshot`
(nel log lo snapshot `default_boot` falliva gia' il caricamento, quindi era peso
inutile), `ram-size` da 2048M a 4096M e `cores: 4`, che il runner ha e
l'emulatore non stava usando. **Non e' una correzione di cui si conosca l'esito**:
tolgono due condizioni che rendono il disallineamento piu' probabile, non lo
rendono impossibile. Se il giro muore ancora, la leva successiva non e' un'altra
opzione dell'emulatore ma **ridurre i riavvii**: raggruppare gli scatti che
condividono lo stesso stato, cosi' che una sessione sola ne produca piu' d'uno.

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
essere tagliato, e ogni marca taglia con una maschera sua. **Un'icona si guarda
sotto le maschere, non nel suo riquadro** - il cerchio e lo squircle stretto
sono i due casi che bastano, e si vedono cose che nel quadrato non esistono:
una sagoma con gli spigoli viene affettata, e una sagoma tonda dentro la
maschera tonda diventa un bersaglio se non le si lascia aria attorno.

Il livello monocromatico va centrato **per conto suo**: lo spostamento che nel
foreground bilancia l'ombra, li' dove l'ombra non c'e' lascia la sagoma storta.

**40. Un'icona non e' una scelta di forma, e' una scelta di significato.**
La prima stesura era una cifra estrusa - un "2" - scelto perche' curva,
diagonale e base piatta danno tre orientamenti di piano e mostrano bene
l'estrusione. Cioe' scelto per **come si scolpisce**, non per **cosa vuol
dire**: il 2 non c'entra niente con questa app, e su una schermata puo' leggersi
come un badge di notifica.

Poi e' stato il **grado**, un anello estruso: il solo segno che dice
"temperatura" senza una parola, e per giunta una cosa che **l'app estrude
gia'** - `smallTail` in `NumberSpec`, l'ultimo carattere della scritta. Un
anello mostrava anche la parete interna, che una cifra piena non fa vedere mai.

**Adesso e' il cielo tagliato in diagonale** (sezione 50), e il criterio di
questa sezione **non e' cambiato**: sole, nuvola e pioggia sono anch'essi cose
che l'app disegna davvero, ogni giorno, nella scultura e nei widget. Cambia la
frase - da "questa app misura la temperatura" a "questa app dice che tempo fa" -
non la regola.

Quel che si e' perso col grado va detto, perche' non lo si riscopra come una
sorpresa: il grado era **un** segno solo, e un segno solo si legge a qualunque
misura. Il disegno nuovo ne ha quattro, e ha dovuto pagare il prezzo di
starci - lame del sole piu' larghe dell'originale, nessuna sfera illuminata,
niente luna.

**16. Chiedere l'intensita' della vibrazione non basta a ottenerla.** Su questo
telefono `hasAmplitudeControl()` risponde di no e un'ampiezza dichiarata viene
ignorata: la pioggia usciva forte quanto il tuono. `WeatherHaptics` prova in
ordine le primitive componibili, l'ampiezza, gli effetti gia' pronti del sistema
e infine la sola durata. Qui finisce su `EFFECT_TICK` contro `EFFECT_HEAVY_CLICK`,
che sono tarati bene e si distinguono davvero.

**35. Un avanzo di scorrimento non e' un dito.** La classe di allora
(`SheetNestedScroll`) e' uscita di scena col foglio; **la regola no**, e vive in
`PullNestedScroll`, che fa lo stesso mestiere fra il carosello verticale e il
tiro per ricaricare. Il difetto: riceveva un
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
delta verso l'alto, cioe' **il contenuto non scorreva piu' affatto**. Serviva
una tolleranza, e serviva rimettere il foglio su un'ancora quando una molla era
stata cancellata senza che un trascinamento le fosse subentrato.

Questo secondo difetto **non puo' piu' ripresentarsi**, ed e' l'unico guadagno
gratuito del cambio: non c'e' piu' un numero di molla su cui fare da guardia -
a dire dove si e' c'e' il carosello, con `currentPage` e lo scostamento, che
sono valori suoi e non di un'animazione che qualcuno puo' cancellare a meta'.

**36. La mediana della luna non gira, e non e' un difetto.** Nella pagina LUNA
la sfera si gira col dito: i mari scivolano verso il bordo e spariscono dietro,
perche' stanno sulla sfera e passano dalla camera. Il taglio fra luce e ombra
no, perche' `moon()` lo costruisce in coordinate di schermo - da che parte cada
lo decide il Sole, non chi guarda (e' la trappola #24 vista dall'altro lato).
Una falce che si raddrizza girando il telefono sarebbe una luna che cambia fase
perche' ci si e' spostati di venti centimetri.

**42. ICON-2I non arriva a otto giorni, e non e' un guasto.** La striscia della
settimana nasceva vuota da meta' in poi: `37° 33° 33° -- -- -- -- --`. Non era
l'app, ed e' bastata una chiamata a mano per saperlo:

```
models=italia_meteo_arpae_icon_2i  ->  [36.5, 32.8, 32.8, null, null, null, null, null]
(nessun models, cioe' globale)     ->  [36.2, 32.0, 33.0, 33.6, 31.9, 24.8, 26.0, 29.4]
```

**Un modello ad alta risoluzione e' anche un modello a corto raggio**: ICON-2I e'
piu' preciso sull'Italia proprio perche' guarda vicino, e si ferma intorno alle
settantadue ore. Chi lo sceglie ha scelto la precisione al posto della distanza,
e la striscia lo rispecchia mostrando **solo i giorni che hanno una temperatura**
invece di stampare `--` a riempire. Tre colonne piene dicono la verita'; otto
colonne mezze vuote sembrano un difetto dell'app.

Da qui e' saltata fuori una seconda cosa: **`Place.isItaly` non la usa nessuno**.
E' scritta, documentata, e `grep` la trova in un punto solo - la sua definizione.
Doveva servire a forzare ICON-2I in Italia quando il modello e' AUTO, ma nel
codice di `main` quella forzatura non c'e': `modelsQueryValue()` restituisce
`null` per AUTO, quindi AUTO e' il modello globale e basta. Non e' stato toccato
niente qui - una funzione morta non fa danni e cambiarla e' una decisione di
prodotto, non di pulizia - ma chi legge il commit che parla di ICON-2I in Italia
sappia che in `main` non e' attivo.

**41. Lint segnalava undici errori, e nessuno era un difetto vero.** Il referto
di `lintDebug` li portava da tre giri, e chi lo leggeva ci passava sopra perche'
`abortOnError = false`: e' esattamente il debito mai letto di cui parla il
commento in `app/build.gradle.kts`. Sono di tre specie, e vale la pena
distinguerle perche' si correggono in tre modi diversi.

Tre erano **`FullBackupContent`**: in `backup_rules.xml` e
`data_extraction_rules.xml` c'era un `<exclude>` per
`datastore/widget_config.preferences_pb`, che pero' non sta dentro nessun
`<include>`. Appena si scrive un `<include>` esplicito, l'incluso e' l'unica
cosa che parte e tutto il resto e' gia' fuori: quell'`<exclude>` non era
ridondante e basta, era un errore. Tolto - il comportamento non cambia di un
byte, e l'intenzione resta scritta nel commento.

Dieci erano **`NewApi`** in `ui/motion/WeatherHaptics.kt`: primitive componibili
(API 30) ed effetti predefiniti (29) chiamati in un'app che dichiara 26. **Non
si rompe niente**: la scelta la fa `modeOf()`, che guarda `Build.VERSION.SDK_INT`
prima di tutto, e su un telefono vecchio quei rami non si raggiungono. Ma la
garanzia passa per un valore di enum, e lint non la sa seguire fin li'. Risolta
con `@SuppressLint("NewApi")` sui due metodi piu' il perche' in testa alla
classe - la stessa forma che `DeviceLocation` usa gia' per `MissingPermission`,
dove a garantire e' `granted()`.

L'ultimo era **`SuspiciousIndentation`** in `WeatherViewModel.kt`: `outcome` era
rientrato di quattro spazi in piu' e sembrava la continuazione di
`val outcome = repository.load()`, mentre e' l'istruzione dopo. Solo
incolonnatura, nessun cambio di comportamento - ma e' il genere di riga che si
legge male una volta e si capisce al contrario.

Adesso `lintDebug` dice **0 errori, 18 avvisi**. E' la condizione che il commento
in `app/build.gradle.kts` poneva per alzare `abortOnError`: non e' stato alzato
qui - lo si fa quando anche gli avvisi sono stati guardati - ma da adesso il
prossimo errore che compare e' nuovo, e si vede.

**43. La molla che rimbalza passa dallo zero, e il padding non lo perdona.**
L'app terminava toccando TROVAMI: `IllegalArgumentException: Padding must be
non-negative`, da `WelcomeScreen.kt` dove il pulsante affondava di
`(sink * 3).dp`. `sink` e' una `animateFloatAsState` con `dampingRatio = 0.7f`,
cioe' sottosmorzata **apposta** - senza sorpasso non c'e' rimbalzo - ma il
sorpasso e' simmetrico: tornando a zero non si ferma, lo passa. Quattro
centesimi sotto per circa un sesto di secondo, una decina di fotogrammi, e in
uno di quelli `PaddingElement` trova un valore negativo e lancia.

Il valore si legge tagliato a zero alla dichiarazione, non al punto d'uso: il
negativo non lo vuole nessuno dei due consumatori. Anche `pinned`
(`dampingRatio = 0.55f`) e' stato tagliato: non schiantava perche' finisce
dentro un `Canvas`, dove pero' diventava un'opacita' negativa.

**Il "succede solo sul mio telefono" aveva una spiegazione, e non era il
Pixel.** La molla e' matematica in unita' di tempo, non di fotogrammi: gira
uguale ovunque. Quel che cambia e' se il permesso di posizione e' **gia'
concesso**. Se va chiesto, si apre la finestra di sistema, che ruba il fuoco e
ferma la ricomposizione: i fotogrammi negativi non vengono mai composti e non
schianta niente. Se e' gia' concesso, il rilascio del dito si anima tutto dentro
l'app e il fotogramma arriva. Misurato: `input swipe` sul pulsante con il
permesso da concedere, dieci giri, nessun crash; con il permesso concesso,
crash al primo tocco; con il taglio a zero, cinque giri su cinque puliti.

**44. `goAsync()` si consegna una volta sola, e in `onDeleted` lo chiedono in
due.** L'app moriva a ogni widget tolto dalla Home:
`NullPointerException` su `PendingResult.finish()`.
`GlanceAppWidgetReceiver.onDeleted` chiede gia' il permesso per conto suo, e
`goAsync()` azzera il proprio campo dopo averlo dato: il secondo che chiede
riceve `null`. Siccome `PendingResult!` e' un tipo di piattaforma, Kotlin
lasciava passare l'assegnazione e il conto arrivava nel `finally`.

**Invertire l'ordine non risolve, sposta**, ed e' stato provato sul telefono:
chiedendolo noi per primi schianta Glance, in `CoroutineBroadcastReceiver.kt:70`,
dove quel `finish()` non e' protetto. Vale la pena scriverlo perche' una lettura
frettolosa del bytecode dice il contrario, e il logcat ha ragione. Resta quindi
`super` per primo e il nostro `pending` nullabile: la ripulita e' **al meglio
possibile**, e quando salta lascia qualche chiave orfana che la configurazione
riscrive per intero appena quell'identificativo viene riusato.

**45. Il widget prendeva la citta' dell'app, e il difetto era invisibile.** Un
widget senza configurazione cadeva su un ramo che restituiva
`SettingsPrefs.place`: disegnava una citta' plausibile, e da fuori era
**indistinguibile** da un widget che funziona. Chi ne configurava un altro non
aveva modo di sapere se la scelta non fosse stata salvata o non fosse stata
riletta. Adesso quel ramo restituisce `null` e i widget disegnano "TOCCA PER
SCEGLIERE LA CITTA'", con il tocco che apre la configurazione di **quella**
istanza. Il ripiego resta solo dentro la regola del GPS, dove e' una rete
motivata e non un mascheramento.

Attorno a questo sono cadute quattro cose che lo tenevano in piedi:

- **I widget non erano riconfigurabili.** Con il solo `android:configure` la
  schermata gira una volta, al momento del piazzamento: una citta' sbagliata
  restava sbagliata per sempre. Aggiunto `widgetFeatures="reconfigurable"` a
  meteo e aria (non alla luna, che non ha una citta'). Lint lo conta come
  `UnusedAttribute` - vale da API 28, il minimo qui e' 26 - ed e' il motivo per
  cui il referto della sezione 41 e' passato da 18 avvisi a 21. E' la stessa
  nota che vale gia' per `targetCellWidth`, ed e' innocua: su Android 8
  l'attributo viene ignorato e il widget resta configurabile una volta sola,
  cioe' come si comportava prima ovunque.
- **La schermata ripartiva in bianco**, il che rendeva la riconfigurazione
  inutilizzabile: adesso e' seminata con `WidgetPrefs.load()`.
- **`setResult(RESULT_OK)` arrivava dopo il ridisegno.** Se `lifecycleScope`
  moriva in mezzo restava il `RESULT_CANCELED`, il lanciatore cancellava
  l'identificativo e `onDeleted` buttava via le preferenze appena scritte.
  Adesso l'esito si cede appena la scrittura e' a terra.
- **Il `delay(800)` era una premessa sbagliata, non un numero tarato male.** Il
  commento diceva che dopo la chiusura della sessione Glance `update()` non ha
  effetto; da li' venivano il broadcast e l'attesa, spostata da tre commit di
  fila. `GlanceAppWidget.update()` passa per `getOrCreateAppWidgetSession`: se
  una sessione non c'e', la **crea**. Non c'era niente da attendere. Misurato
  sul telefono: dal salvataggio al `provideGlance` con la citta' nuova passano
  280 ms, e il broadcast e' rimasto solo come ripiego.

**46. La cosa giusta da fare era ricopiata a mano in ogni widget.** Leggere
l'identificativo, caricare la configurazione, accorgersi che la citta' non c'e',
disegnare l'invito, agganciarci il tocco: sette passi scritti per intero dentro
ciascuno dei tre, che un widget nuovo doveva ricopiare **sapendo quali**. Chi ne
dimenticava uno otteneva un widget che sembrava funzionare - e' esattamente il
difetto della sezione 45.

Adesso i sette passi stanno in `CaelumWidget`, con `provideGlance` dichiarato
`final`: un widget nuovo non puo' sbagliarli perche' non li scrive. Quel che
scrive e' `paint()`, cioe' il suo disegno. E `WidgetKind` e' diventato l'unico
posto in cui un widget si dichiara - nome esteso, nome corto, se vuole una
citta', il ricevitore, la fabbrica: prima le stesse risposte stavano in
**quattro** elenchi separati (il tipo qui, il titolo nella schermata di
configurazione, il `kind != LUNA` scritto a mano, il ricevitore in
`refreshWidget`), da tenere allineati a mano. I tre widget sono passati da una
sessantina di righe a una trentina, e le tre `ActionCallback` identiche sono
diventate una.

**47. Il nome della citta' non sapeva quanto spazio aveva.** La qualita'
dell'aria configurata su "Aoraki / Monte Cook" scriveva il nome fuori dal
riquadro, sotto il pallino della banda, tagliato dal bordo dell'immagine. Non
era un difetto di quel widget: `AirArt` e `CurrentArt` - quest'ultimo in **due**
punti - scrivevano tutti il nome con un `text()` nudo. Non si era mai visto
perche' le citta' provate erano corte.

L'adattamento sta adesso in `placeName()`, accanto a `text()`, per la stessa
ragione per cui ci sta `text()`: un disegno nuovo non deve ricordarsi di
rimpicciolire. **Stringe di larghezza, non di corpo** - il carattere ha un asse
variabile per la larghezza, e stringendo quello `lineHeight` non cambia, quindi
non balla l'impaginazione di tutto cio' che sta sotto, che e' calcolata proprio
su `lineHeight`. Quando anche la larghezza minima non basta, tronca con i
puntini. Nel meteo il testo si ferma anche prima del disegno del cielo, dove
pure ci finiva sopra.

**48. La ridenominazione del pacchetto era ferma a meta', e il progetto non
compilava.** Nell'indice convivevano due alberi sorgente: i 76 file nuovi sotto
`io/github/noximiliencoxen/caelum`, aggiunti, e i 55 vecchi sotto
`com/forli/meteo`, mai cancellati. `build.gradle.kts` e il manifesto puntavano
gia' al pacchetto nuovo, quindi i vecchi cercavano una `com.forli.meteo.R` che
non esisteva piu': dieci errori di compilazione, e niente da installare su un
telefono per provare qualunque cosa.

I 55 file sono stati rimossi - sono in `HEAD`, quindi recuperabili con
`git checkout HEAD -- app/src/main/kotlin/com` se dovessero servire. Vale la
pena sapere cos'erano, perche' erano piu' pericolosi di un semplice avanzo:
contenevano copie di `WidgetPrefs.kt` e `prefs/SettingsPrefs.kt` che
dichiaravano `preferencesDataStore` con gli **stessi due nomi di file** del
codice vivo (`widget_config`, `impostazioni`). Morti, perche' niente nel
manifesto li raggiungeva - ma se una di quelle proprieta' fosse mai stata toccata
nello stesso processo, `androidx.datastore` avrebbe lanciato *"There are multiple
DataStores active for the same file"*. Contenevano anche i sedici
`Log.d("WidgetResolve", ...)` che la sezione 37 da' per rimossi.

**49. `minSdk` sale a 31, ed e' una decisione presa, non una conseguenza.**
Il piano `docs/superpowers/plans/2026-09-07-foglio-dettaglio-pila.md` la porta
dentro al Task 1 come "la potatura che libera": serve a usare API moderne senza
guardie. Ma alzare il minimo da 26 a 31 **taglia fuori Android 8, 9, 10 e 11**,
e quest'app non si distribuisce da uno store - si installa da un APK diretto,
quindi non c'e' nessun filtro che nasconda l'aggiornamento a chi non lo puo'
installare: lo scarica e fallisce.

E' stata posta come domanda di prodotto e ha avuto risposta: **nessuno usa
l'app sotto Android 12**, quindi si fa. Sta scritto qui perche' la prossima
persona che trova del codice difensivo per API vecchie sappia che e' stato
lasciato indietro di proposito, e non lo rimetta.

**Le tre note che quel cambio manda in soffitta.** Vanno tolte insieme al
cambio, non prima e non "quando capita" - un commento che descrive una guardia
che non c'e' piu' e' peggio di nessun commento:

- le guardie API in `ui/motion/WeatherHaptics.kt`, con i due
  `@SuppressLint("NewApi")` e il perche' in testa alla classe (sezione 41): a 31
  le primitive componibili e gli effetti predefiniti ci sono sempre, e
  `modeOf()` non deve piu' guardare `Build.VERSION.SDK_INT`;
- la nota su `drawVertices` per Android 8, nell'elenco delle cose mai
  verificate della sezione 8: a 31 quel dispositivo non esiste piu';
- il commento in `res/xml/weather_widget_info.xml` che spiega perche'
  `widgetFeatures` (API 28) e `targetCellWidth`/`targetCellHeight` (API 31)
  facciano scattare `UnusedAttribute` con un minimo di 26. A 31 lint smette di
  segnalarli e il referto scende da 21 avvisi a 18 - il che vuol dire che
  **quel commento va tolto, non aggiornato**: non descrive piu' niente.

Il cambio in se' non e' stato fatto qui: `app/build.gradle.kts`,
`WeatherHaptics.kt` e `DeviceLocation.kt` restano al ramo che li ha in piano,
per non spaccare in due un lavoro gia' progettato.

**50. L'icona e' il cielo tagliato in diagonale, e il taglio sta nel fondo.**
Meta' in alto a sinistra il sereno - sole e nuvola bianca - meta' in basso a
destra il tempo brutto - la stessa nuvola, grigia, con la pioggia. Sostituisce
il grado della sezione 40, che resta li' a spiegare perche' c'era.

**Il taglio e' nel livello di fondo, non disegnato sopra.** E' il livello che si
muove in parallasse: la diagonale scorre sotto il disegno invece di essere una
riga incollata. Il fondo dipinge da bordo a bordo, perche' la maschera la decide
il telefono.

**La nuvola e' una sola, ridipinta due volte.** Lo stesso `pathData` compare in
`ic_launcher_foreground.xml` due volte: bianco per intero, poi grigio dentro un
`<group>` con `<clip-path>` sul triangolo cupo. Identico profilo, quindi il
bordo fra le due tinte cade **esattamente** sul taglio del fondo - cosa che due
sagome disegnate a mano non garantirebbero mai. A 48dp una forma sola si legge;
due macchie vicine si leggono come una macchia sporca.

**Tinte piatte, e non e' pigrizia.** E' la lezione che `dayGlyph` aveva gia'
scritto per la striscia oraria - *"una sagoma piena e non una sfera illuminata:
a questa misura il volume diventa poltiglia"* - e l'icona e' una miniatura. Il
solo rilievo e' la falce ambrata del sole, ottenuta con **due dischi sfalsati**
invece che con un gradiente: cio' che avanza sta dalla parte da cui la luce non
viene, coerente con `Light.Standard`.

**Le forme vengono dall'app.** La nuvola ha le proporzioni di `puff()` in
`WidgetParts.kt` - tre gobbe e un ventre piatto, agli stessi rapporti col
raggio - e i raggi sono le otto lame triangolari di `sunRays()`. Le lame sono
pero' **piu' larghe dell'originale**, semilarghezza `0.22r` invece di `0.11r`:
a 48dp una lama sottile non si vede, e un'icona non e' una miniatura del
disegno grande, e' un disegno diverso con lo stesso vocabolario. Le tre gocce
sono ai passi asimmetrici di `drops()`, spostati a destra perche' cadano tutte
nella meta' cupa.

**Perche' la luna non c'e'.** Era stata proposta ("forse ci sta"). Non ci sta:
nel cerchio garantito da 66 (sezione 39) ci sono gia' sole, nuvola bianca,
nuvola grigia e pioggia, che a 48dp si spartiscono una quindicina di dp l'uno.
Il quinto soggetto non si leggerebbe come luna, si leggerebbe come sporco.

**I colori sono quelli dell'app**, non scelti a occhio: `#5A9BD4` e' lo zenit di
mezzogiorno sereno e `#1E222A` e' `Gloom`, entrambi da `ui/theme/Colors.kt`; il
resto viene da `WidgetInk`. Restano scritti nei vector e non in `colors.xml`,
come gia' erano.

**50. Un ciclo che si ripete non e' il tempo che avanza.** La scena della
pioggia aveva un orologio solo: un dente di sega da 0 a 1 ogni secondo e mezzo,
perfetto per le gocce - una esce dal fondo, un'altra nasce in cima, e il ritorno
a zero non si vede. Poi la gente ha cominciato a camminare leggendo **quello
stesso numero** come se fosse il tempo trascorso: ogni passante avanzava un
decimo di traversata e poi scattava indietro insieme a tutti gli altri, **ogni
secondo e mezzo**. Chi si ripete e chi avanza hanno bisogno di due orologi.

E il secondo si chiude cosi': **periodi commensurabili, non tarature**. Se ogni
oggetto compie un numero **intero** di giri per ogni giro dell'orologio, quando
l'orologio cala di uno la posizione cala di un intero e il resto su uno non se
ne accorge - il ciclo e' continuo per costruzione, e nessuno deve cercare a
occhio il punto in cui nascondere il salto. Vale per la posizione e vale per il
passo, che infatti ha un numero intero di battute per traversata. Corollario:
perche' un rientro non si veda, il margine fuori campo si **ricava dalla parte
piu' larga dell'oggetto** - qui l'ombrello, che era piu' largo del margine
scritto a mano, e spuntava.

**51. Le facce proiettate di un solido non si avvolgono tutte nello stesso
verso.** Il fondo della vasca graduata non si riempiva: sei quadrilateri in un
solo `Path`, tre avvolti in un senso e tre nell'altro, e con la regola `NonZero`
si cancellano a vicenda - misurato, il **cinquantotto per cento** della colonna
spariva. Le due correzioni che vengono in mente non funzionano: `EvenOdd` non
aggiusta niente perche' due sovrapposizioni sono parita' pari, cioe' lo stesso
buco; e disegnare ogni faccia per conto suo con un'opacita' fa comporre le
sovrapposizioni - da 0,42 si arriva a 0,66, cioe' tre bande scure che si
muovono. La correzione e' **normalizzare il verso**: area con segno del
quadrilatero, e se e' negativa lo si percorre al contrario. Sta in
`ui/render3d/Facets.kt`, con i suoi test, e vale per qualunque solido
proiettato.

**52. Un'attesa che legge il logcat va rimessa a zero a ogni giro.**
`attendi_previsione` aspetta la riga "previsione pronta"; nel giro delle sei
schede il buffer era svuotato **una volta sola prima del ciclo**, quindi dalla
seconda scheda in poi trovava la riga della scheda precedente e tornava
all'istante - un'attesa che non aspettava niente. Si vedeva come uno scatto ogni
tanto con scritto IN ATTESA DEI DATI, ed e' passata per sfortuna finche' non e'
capitata due volte di fila sullo stesso scatto. `restart_with` aveva gia' il
commento che lo diceva; il ciclo delle schede non lo seguiva. **Un difetto che
si manifesta come rumore casuale e' un difetto che nessuno guarda**: e' il
motivo per cui questa sta scritta.

---

## 8. Stato: fatto / non fatto

> ### ⚠ Questa sezione descrive il **feed**, che non esiste piu'
>
> Tutto cio' che segue - la rotazione libera della cifra, il sole giallo e
> rosso, la nuvola bianca e quella grigia, la via con la gente sotto la
> finestra, le schede del carosello - e' stato **cancellato** quando Sala ha
> preso il posto del feed (sezioni 12, 12-bis e 12-ter).
>
> Resta qui perche' e' la sola traccia di cio' che e' stato provato **in mano su
> un telefono vero**, e quelle prove non si rifanno da un container senza SDK:
> le misure di fotogrammi, i giri su Pixel, la cronologia del vibratore. Chi
> cerca com'e' fatta l'app **oggi** legge la sezione 12 e seguenti.
>
> Il pezzo che vale ancora per intero e' la parte sui **widget**, che Sala non
> ha toccato, e le **trappole** della sezione 7, che sono lezioni e non
> descrizioni.

**Verificato sul telefono** (sul feed, non su Sala): schermata principale, rotazione libera con
prospettiva vera anche oltre il mezzo giro, ritorno alla posa a riposo, sole
giallo e rosso, luna con fase e mari, nuvola bianca e nuvola grigia, **pioggia
che parte davvero** su temporale a zero millimetri, gocce che ruotano con la
nuvola, fulmini con alone e bagliore, vibrazione leggera sulla pioggia e pesante
sul tuono (viste nella cronologia del vibratore), fondo che segue l'ora, **tutte
e ventiquattro le ore raggiungibili una per una**, impostazioni, cambio localita',
cambio unita' (21 °C -> 70 °F), persistenza delle scelte.

Aggiunti in seguito, su Pixel 9 Pro (Android 17 / SDK 37), con le sezioni 43-47:
il **pulsante TROVAMI** premuto e rilasciato con il permesso di posizione gia'
concesso, cinque giri su cinque senza terminare - e la controprova, cioe' la
stessa build senza il taglio a zero che schianta al primo tocco; il **widget
meteo** che disegna una citta' scelta a mano diversa da quella dell'app, e un
secondo widget sulla stessa Home che segue invece il GPS; la **riconfigurazione**
di un widget gia' posato, che si riapre mostrando la citta' corrente e, cambiata
in Milano, ridisegna il riquadro in 280 ms senza che l'app venga aperta; il
**widget della qualita' dell'aria** su "Aoraki / Monte Cook", che e' il nome
lungo con cui e' venuto fuori il difetto della sezione 47; la **rimozione** di un
widget dalla Home, di cui si e' visto l'effetto - le chiavi di quell'istanza
sparite dall'archivio - ma non il percorso, perche' `APPWIDGET_DELETED` e' un
broadcast protetto e da `adb` non si puo' inviare.

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
- il **widget della luna** dopo il tronco comune della sezione 45: e' l'unico
  dei tre che non e' stato posato su una Home vera, quindi di lui si sa che
  compila, non che disegna
- **quanto costa la scena della finestra**, che e' l'unica parte dell'app che si
  muove sempre mentre la si guarda (sezione 8-quinquies): `dumpsys gfxinfo`,
  quattro secondi sulla scheda, mediano e ritardi. Va preso **in mano**: il costo
  per fotogramma di un emulatore non dice niente di un telefono, perche' la resa
  e' software. Se non regge i 16 ms cedono gli strati e non il movimento - prima
  le persone, poi le gocce sul vetro
- **che il ritorno a zero dell'orologio lento non si veda**: e' una cosa che uno
  scatto non puo' dire, per costruzione
- **che lo scorrimento delle ore sulla fascia non rubi il cambio di scheda**: il
  dito per il lungo sceglie l'ora, il dito verso il basso deve ancora portare
  alla scheda dopo. E' il punto piu' esposto del cambio
- Android 8, per via della nota su `drawVertices`
- la ricerca dei luoghi per nome con la tastiera (provate solo le scorciatoie)

**Non fatto, in ordine di valore**:

1. **Transizioni continue** — cifre a contachilometri al cambio valore, tabella
   scaglionata, curve che si deformano invece di saltare.
2. **Il feed non e' mai stato provato in mano, ed e' l'unica prova che conta.**
   Gli scatti ci sono, tutte e sei le schede nei due temi, e hanno gia' fatto il
   loro mestiere: **tre difetti li ha trovati il primo giro**, ed erano tre cose
   che nessuno poteva dedurre leggendo il codice.
   - Lo stato vuoto usava `MeteoEmptyState`, che prende i toni dai pannelli: sul
     cielo il messaggio secondario usciva marroncino su azzurro, illeggibile. E'
     la trappola di questa stessa sezione rientrata dalla porta di servizio -
     chi riusa un componente dei pannelli sul cielo la ripaga.
   - La prima scheda era ristretta di quarantaquattro punti per far posto alla
     colonna, e quindi **tutta spostata a sinistra**: nome, scultura, cifra e
     barra delle ore. Quella schermata ha una regola scritta contro questo
     preciso difetto, e il margine la violava in blocco. Adesso la scheda e' a
     piena larghezza e la colonna galleggia nel margine che c'era gia'.
   - Il corpo della luna riempiva la scheda da bordo a bordo: quaranta centesimi
     del lato erano il valore della vecchia pagina, dove pero' l'eroe era una
     frazione dell'altezza e non tutto lo spazio che avanza.

   Quello che gli scatti **non** possono dire resta tutto, perche' il feed e'
   fatto di movimento. Da guardare per primi in mano, in quest'ordine:
   - come si sente lo scorrimento fra una scheda e l'altra, e se la molla del
     pager e' della stessa famiglia del resto dei movimenti dell'app;
   - che il dito **orizzontale** sulla cifra giri ancora la scena senza che il
     carosello rubi il gesto, e che il **verticale** cambi scheda senza che la
     rotazione lo ingoi: e' la trappola #5 vista dall'altro lato, e i due
     riconoscitori adesso si toccano piu' di prima;
   - che la barra delle ore sulla prima scheda si scorra ancora col pollice
     senza far scattare la scheda - li' il dito sta in orizzontale su una zona
     alta poche decine di punti, ed e' il punto piu' esposto di tutto il cambio;
   - che il tiro per ricaricare parta **solo** dalla prima scheda, e non da un
     avanzo di slancio arrivato dall'alto;
   - se la colonna di icone si tocca davvero, larga quarantotto punti al bordo
     dello schermo, e se i sei glifi si distinguono a diciotto punti - aria e
     vento sono i due che si somigliano, e sono stati separati apposta
     (granelli dentro un contorno contro linee che scorrono).
5. **La barra con la bolla e la sfera della luna non sono mai state provate in
   mano.** Vale la nota qui sopra e in piu' una cosa che gli scatti non possono
   mostrare: la bolla scivola col dito, e la sfera gira col dito. Da guardare
   la bolla alle due estremita' della barra, dove si ferma al bordo e la codina
   si inclina per continuare a puntare il cursore.
5-bis. **Quanto costa la prima scheda mentre e' composta ma fuori vista.** Il
   carosello tiene composta anche la scheda accanto, e il respiro della
   scultura e' un `withFrameNanos` che gira finche' la finestra si vede: stando
   sulla pioggia, la scultura continua a ridisegnarsi. **Non e' un peggioramento
   rispetto a prima** - il foglio aperto copriva la principale senza smontarla,
   quindi si pagava lo stesso, e adesso si paga su due schede su sei invece che
   sempre - ma non e' stato misurato. Si misura con `dumpsys gfxinfo ... reset`,
   quattro secondi fermi sulla seconda scheda, e si legge quanti fotogrammi
   sono usciti. Se pesa, la strada e' spegnere i cicli della scultura sul
   `feelsIt` che gia' riceve, non aggiungere un secondo flag che dice quasi la
   stessa cosa.
6-bis. **Niente di questa passata e' stato provato in mano, e non poteva
   esserlo**: da questo container `dl.google.com` non si raggiunge (403 al
   CONNECT del proxy), quindi non ci sono ne' l'AGP ne' l'SDK - `lintDebug` e
   `assembleDebug` non partono affatto. Compilano e girano solo i test del
   pacchetto `data`, che non hanno niente di Android (fatto a mano col
   compilatore Kotlin preso da Maven Central: 21 test verdi, compresi i quattro
   nuovi di `AlertBadgeTest`). **Tutto cio' che e' Compose l'ha compilato la
   CI, non questa sessione.** Da guardare per primi, che sono movimenti e
   misure e in uno scatto non si giudicano: se la striscia in fondo si trascina
   davvero col pollice invece di essere solo un disegno; quanti punti toglie
   davvero alla scultura su uno schermo corto; se le sei parole ci stanno a
   10sp sotto i 360 punti di larghezza o se la soglia dei 340 va alzata; e se
   la striscia dei giorni, cambiando giorno da una colonna della settimana,
   arriva centrata invece che di scatto.

6-ter. **Il job `screenshots` cade da solo, e non da adesso.** L'emulatore della
   CI muore a meta' giro: `screencap: error: closed`, poi `device offline`, poi
   `device 'emulator-5554' not found`, e alla fine `adb emu kill` risponde
   `could not connect to TCP port 5554`. E' la morte gia' descritta nella
   sezione 8 - sparisce la macchina virtuale, non l'app - e il logcat lo
   conferma: l'ultima riga e' un normale `previsione pronta: 24 ore`, nessuna
   eccezione, nessun ANR, nessun OOM. Poi il file finisce.

   Misurato, due giri per parte sullo stesso pomeriggio:

   | | dove muore | scatti |
   |---|---|---|
   | `main` `e0f6613`, giro 1 | sul `d12` | 20 ok, 18 mancati |
   | `main` `e0f6613`, giro 2 | sul `d12` | 20 ok, 18 mancati |
   | ramo del foglio, giro 1 | sul `d9-aria` | 19 ok, 3 mancati |
   | ramo del foglio, giro 2 | sul `d9-aria` | 19 ok, 3 mancati |

   Due cose da leggere insieme. La prima: **il job e' rosso su `main` da almeno
   sei merge**, e nessuno dei due punti di morte si sposta fra un giro e
   l'altro - quindi non e' un guasto occasionale, e' riproducibile. La seconda:
   **il ramo che porta il foglio unico ci arriva quattro scatti prima**, e
   questo e' il suo, non della base.

   **E adesso si sa qual era.** Il passaggio al feed ha tolto di mezzo i gesti
   dalla coda degli scatti: prima erano un tocco piu' cinque trascinate
   orizzontali dentro il foglio, adesso ogni scheda si mette in scena con
   `--ei sezione`, cioe' con un avvio. **Il giro e' arrivato in fondo, verde,
   in sei minuti**, con tutti e cinquantasette i file al loro posto - il primo
   da almeno sei merge.

   Quindi non era la coda di riavvii, che e' l'ipotesi scritta qui sotto e che
   era la piu' plausibile: **erano le trascinate**. Chi tornera' a metterne una
   in `capture.sh` sappia che paga questo prezzo, e che il prezzo e' l'intero
   giro di verifica. Le due strade che restavano da provare - riavviare
   l'emulatore fra una fase e l'altra, o spezzare il giro in job piu' corti -
   **non servono piu'**, e non vanno intraprese per abitudine.

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
9. **`PredictiveBackHandler`** al posto di `BackHandler`, per il ritorno con
   animazione di Android 14+. Gli strati adesso sono due - allerte e
   impostazioni - piu' l'indietro del feed, che dalla scheda in cui si e'
   riporta alla prima invece di chiudere l'app.
3. **La qualita' dell'aria non ha una previsione**, solo l'ora corrente: e'
   quello che l'endpoint da'. La pagina lo dichiara invece di disegnare una
   curva piatta.
4. **Le schede in orizzontale** non sono state pensate: in landscape una
   schermata piena e' larga e bassa, e cifra, segnaposto e numeri restano
   impilati dove ci starebbero affiancati. `MeteoLayout.landscape` c'e' gia' e
   nessuno glielo chiede.
10. **Quattro schede su sei sono ancora segnaposto**, ed e' voluto: cosa ospita
   ciascuna si decide una sezione alla volta. `FeedSection.stage` porta la
   consegna scritta accanto al riquadro vuoto - dall'erba che si piega dalla
   parte da cui tira all'arco della giornata da percorrere col dito. La pioggia
   e' uscita dal segnaposto (sezione 8-quinquies); restano aria, vento, sole e luna.

---

## 8-bis. Il feed verticale, e le regole sul colore che restano

`ui/feed/`, `ui/theme/Contrast.kt`, `ui/theme/MeteoColorScheme.kt`,
`ui/common/`

**Le sei grandezze sono la navigazione.** Si scorre dal basso verso l'alto e si
passa dalla temperatura alla pioggia, dalla pioggia all'aria: una schermata
piena ciascuna, sullo stesso cielo. Prima vivevano dentro un foglio che saliva
dal basso, in un carosello orizzontale - due gesti di profondita' sotto la
schermata che si apre per prima - e per farlo scoprire c'era voluta una striscia
in fondo alla principale che ne disegnasse il bordo. Adesso la profondita' e'
zero: la pioggia sta uno scorrimento sotto la temperatura, non dentro qualcosa
che va aperto.

**Il gesto verticale era libero, e non per caso.** La regola di questa app
divide gli assi da sempre (trappola #5): orizzontale gira la scena, verticale
apre. Il feed prende l'asse che il foglio aveva; la rotazione della cifra, la
barra delle ore e la scelta del giorno restano orizzontali e non si contendono
niente. E' la stessa spartizione, con un contenuto diverso dentro.

**Le schede sono ancora segnaposto**, tranne le prime due - la temperatura e la
pioggia, che ha la sua sezione qui sotto. Ognuna ha titolo, la
cifra girabile, un riquadro tratteggiato che dichiara cosa ospitera'
(`FeedSection.stage`) e due o tre numeri che l'app sa gia' dire. Cosa metterci
davvero si decide una sezione alla volta - e' il modo di lavorare di questo
progetto - e un riquadro che dice cosa manca e' un lavoro in corso, mentre uno
vuoto e muto e' un difetto.

**La prima scheda e' la schermata di sempre, intatta.** E' la parte piu' provata
dell'app, e un cambio di navigazione non e' una ragione per rimetterla in
discussione. Cambia ai bordi: niente piu' tocco sulla cifra che apriva il foglio
(e con lui e' uscito `detectTapOrRotate`, che esisteva solo per distinguerlo dal
giro), niente piu' striscia delle grandezze in fondo, e il margine destro lascia
il posto alla colonna di icone.

**Niente scorrimento dentro una scheda.** Una scheda sta in una schermata e
basta. Le vecchie pagine erano colonne che scorrevano, ed e' per quello che
esisteva `SheetNestedScroll`: arbitrare fra lo scorrimento interno e il foglio
che lo conteneva. Tolto il foglio e' tolta la contesa, e va tenuta tolta - una
colonna che scorre dentro una pagina che scorre e' quella contesa che ritorna.

**Il tiro per ricaricare si prende il dito prima del carosello**, in
`onPreScroll`, e non sull'avanzo. La strada dell'avanzo - lasciar scorrere e
raccogliere cio' che resta - dipende da cosa l'effetto di sovrascorrimento
decide di trattenere per la sua stiratura: sarebbe un comportamento ereditato
invece che deciso. Sulla prima scheda sopra non c'e' niente da mostrare, quindi
prenderselo non toglie niente a nessuno. **Resta la guardia sul
`NestedScrollSource`** (trappola #35): un avanzo di slancio non e' un dito, e
un'app non chiede dati alla rete perche' una molla ha finito di tornare a posto.

**Le schede si centrano sul centro dello schermo, non su quel che avanza.** Il
margine che riservava la colonna era asimmetrico e spostava tutto ventidue punti
a sinistra: e' lo stesso difetto gia' corretto sulla prima scheda, che le altre
cinque si erano tenute. **Un margine simmetrico non sposta il centro: costa solo
larghezza**, quindi la domanda per ogni riga e' una sola - passa davanti alla
colonna? Titolo e numeri la scavalcano finche' la scheda e' alta piu' di
`RAIL_SPAN + 2 * endsBlock`, cioe' 394 punti: **ogni telefono in verticale passa,
l'orizzontale no** (914x411 lascia 363 punti e li' la colonna copre anche i
numeri), e sotto la soglia l'inserto va sull'intera colonna. `endsBlock` segue la
scala del carattere di sistema, se no il caso che il controllo evita rientra
proprio su chi ha il carattere grande.

Titolo, sottotitolo e numeri ci guadagnano quarantaquattro punti; la luna li
perde, e `MOON_RADIUS` e' passato da 0,32 a 0,35 - un compromesso, non un
ripristino.

**Toccare un giorno adesso fa qualcosa.** Il tocco era gia' collegato e scriveva
`selectedDay`; il difetto era che `HomeScreen` leggeva `state.hours` e
`state.hour`, cioe' **le ore di oggi e basta**, quindi scultura, cifra,
condizione e barra raccontavano oggi qualunque giorno si scegliesse. E la
striscia non segnava la colonna scelta di proposito: il suo commento diceva che
aprire un giorno "porta via da questa schermata", che era vero **col foglio del
dettaglio**. Il foglio non c'e' piu' e la giustificazione se n'e' andata con lui,
lasciando in piedi la conseguenza.

Adesso la prima scheda legge il giorno scelto (`shownHours` sale in `UiState`
accanto a `detailHour` e `detailDay`), la colonna si segna, e tre cose vanno con
lei o il giorno diverso diventa una bugia: il segno dell'ora vera **sparisce**
sugli altri giorni, alba e tramonto si prendono dal giorno mostrato, e il tasto
diventa **TORNA A OGGI** riportando indietro tutti e due gli assi. Il commutatore
ore/settimana passa da un `rememberSaveable` locale a `UiState.weekMode`, che
era gia' scritto e non lo usava nessuno.

Di contorno: il caso "un'altra giornata" diventa raggiungibile col dito. Ci si
arrivava solo con `--ei giorno`, perche' la trascinata che ci vorrebbe fa morire
l'emulatore.

**La colonna di icone sta sopra il carosello, non dentro.** E' l'unica cosa in
scena che non scorre, ed e' cio' che la rende un punto di riferimento invece di
un settimo contenuto: uno scorrimento profondo sei senza una mappa e' un pozzo.
Le caselle hanno misura fissa e l'accensione si legge **dentro il disegno**, da
una lambda sulla posizione del carosello - cosi' la tinta si travasa da un'icona
alla successiva senza che nessun fotogramma ricomponga o rifaccia il layout. I
sei glifi sono disegnati a mano, come tutte le icone di questa app.

**Le tinte della colonna e delle schede vengono da `skyAccents()`**, non da
`LocalMeteoAccents`. Le seconde sono tarate sull'antracite dei pannelli, e le
schede del feed non hanno superfici: vivono direttamente sul cielo, che a meta'
mattina e' grigio chiaro, e li' il giallo del sole sparirebbe. Vale la regola
qui sotto, ed e' il motivo per cui una scheda non usa `MaterialTheme.colorScheme`
per i suoi testi.

**Una sola sorgente di verita': il carosello.** `state.section` e' dove si
riparte, non una seconda copia di "su quale scheda sono". Tre regole da non
sciogliere, che erano gia' costate un giro sul carosello orizzontale:

- si scrive nello stato **solo su `settledPage`** - un trascinamento annullato
  non e' una scelta e non deve lasciare traccia;
- si legge `currentPage` per **cio' che si vede**: la scheda posata cambia
  troppo tardi, e la colonna resterebbe indietro per tutto il gesto;
- la colonna anima il pager **direttamente**, non passando dal ViewModel. La
  versione che passava dallo stato si cancellava l'animazione da sola, perche'
  l'effetto era chiavato su cio' che essa stessa cambiava.

**Cio' che si muove col dito passa per lambda, non per valore.**
`currentPageOffsetFraction` cambia a ogni fotogramma: letto nel corpo di un
composable ricompone l'intera schermata sessanta volte al secondo per travasare
una tinta. Letto **dentro** `graphicsLayer` o dentro una tela si ferma alla fase
di disegno.

**Il giorno e' un asse, non una schermata**, e adesso lo e' fino in fondo: lo
leggono tutte le schede (`pageDay`, `pageHours`, `detailHour`) e a sceglierlo c'e'
una colonna della striscia della settimana sulla prima. C'era una
`DayDetailScreen` che entrava da destra con un carosello suo; poi era diventata
una striscia in cima al foglio; adesso non serve nessuna delle due.

**Un'ora precisa sopra un totale del giorno e' una bugia.** La sezione del sole
diceva "OGGI · 15:00" sopra tredici *ore di sole*, che sono quelle di tutta la
giornata. `FeedSection.isDailyTotal` distingue i due casi, e il sottotitolo
della scheda glielo chiede.

**La luna non chiede niente alla rete**: la fase la calcola `MoonPhase` in
locale (Open-Meteo non la fornisce), quindi e' l'unica scheda che ha ancora
qualcosa da mostrare quando la previsione non arriva. Per questo il suo ramo sta
**prima** del controllo sulla cifra, che spegne tutte le altre. Il corpo e'
quello della scultura e del widget - stessa sfera, stessa luce, stessi mari - e
gira con il `rotatesScene` che la scheda ha gia'. Vedi la trappola #36 per cosa
gira e cosa no.

**Ogni scheda ha la sua `SceneRotation`.** Girare la cifra della pioggia non
deve girare la luna: sono oggetti diversi visti da punti diversi, non lo stesso
oggetto in due posti.

**Il carosello tiene composta anche la scheda accanto**, per averla pronta a
meta' trascinamento. Da qui il flag `alive` sulla prima: senza, il telefono
continuerebbe a vibrare di pioggia mentre si guarda la luna, e un tocco che non
corrisponde a niente di visibile non e' un riscontro. Resta invece **da
misurare** quanto costa il respiro della scultura mentre la prima scheda e'
composta ma fuori vista - vedi la sezione 8, "non fatto".

Le tre regole sul colore che seguono sono costate una passata intera e valgono
identiche sul feed.

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

**`@ReadOnlyComposable` e `remember` non convivono.** La prima dichiara che la
funzione non scrive nella composizione, la seconda ci scrive.

**Cosa e' uscito di scena con il foglio, e sta nella cronologia.**
`TemperatureDetailScreen`, `PanelPicker`, `DayStrip`, `DailyForecastCard`,
`MeteoChart` e le sei pagine di `ui/temperature/pages/`. Con loro se ne sono
andate le lezioni che ci vivevano dentro - le etichette con la pillola sotto
perche' un grigio su un arancione non si legge, `ChartBounds` perche' la scala
di un grafico non inventa valori, il `clamp` al posto di `coerceIn` perche' su
schermo stretto il minimo puo' superare il massimo. **Non sono sbagliate: sono
senza chiamante.** Chi rimettera' un grafico dentro una scheda le rilegga da
`git show`, invece di ripagarle.

`DetailChrome.kt` e' passato in `ui/common/MeteoGeometry.kt` e ha perso meta' di
se': il sole in miniatura sopra il grafico orario, l'area sotto la curva e il
nastro fra massime e minime vivevano per i grafici. Quel che resta -
l'illustrazione del tempo, la scala di colore dei gradi, la spline - lo leggono
la barra delle ventiquattro ore, la striscia della settimana e le schede, e non
ha piu' niente a che vedere con la temperatura in particolare.

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
altezza - che e' precisamente cio' che si cerca chiudendo la fascia. Il **disco
e' da 36dp e il segno da 18** (erano 30 e 15): a crescere e' il disegno, il
bersaglio resta quello del pulsante, quindi l'invariante regge. Sopra i 40 il
disco arriva a filo del bersaglio e si legge come un pulsante pieno. Nel
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

**La riga della fascia non scrive "ALLERTA".** Il segno tinto accanto dice gia'
che e' un avviso e di che gravita': legge `ARANCIONE  ·  TEMPORALI`. Non e' una
scorciatoia estetica - vedi qui sotto.

**Un avviso calcolato non si chiama "allerta gialla".** Giallo, arancione e
rosso non sono tre aggettivi: sono i gradini del sistema di allertamento
nazionale (D.lgs. 1/2018, Direttiva PCM 27/02/2004, colori nelle *Indicazioni
operative* del 2016), e a diramarli sono il Dipartimento e i centri funzionali
regionali. Un confronto fra una raffica e una costante scritta in
`DerivedAlerts.kt` che se li prendesse direbbe a chi legge che a pronunciarsi e'
stato l'ente. La distinzione c'era - `WeatherAlert.official` - e viveva **solo**
in fondo alla scheda del bollettino: fascia e pallino non la leggevano mai.

Adesso un derivato si annuncia `SOGLIA` nella fascia e `SOGLIA SUPERATA` nel
bollettino (`WeatherAlert.badgeLabel` e `shortBadge`, nei dati e non
nell'interfaccia, per la stessa ragione di `alertsAreDismissed`), prende il
colore del testo del contenitore invece di uno dei tre (`alertTint`), e porta un
**cerchio** al posto del triangolo (`NoticeCircle`). Due forme e non due tinte
soltanto: il colore da solo lascerebbe fuori chi non lo distingue.

Il rischio che questo chiude non e' "calcolare", e' "sembrare": presentare
un'informazione come se venisse da un'altra fonte e' cio' che gli artt. 21-22
del Codice del consumo (D.lgs. 206/2005, direttiva 2005/29/CE) chiamano
ingannevole. E il caso pericoloso e' quello **al contrario** - chi non vede
nessuna gialla da' per buono che l'ente non abbia niente da dire. Restano da
decidere, da parte di chi pubblica: una riga nelle impostazioni che dichiari le
due fonti, e se il titolo di `AlertsSheet` debba continuare a scrivere `ALLERTE`
anche quando in scena ci sono solo avvisi calcolati.

L'aggancio `--ei allerta 0` impone un avviso calcolato, cosi' la differenza si
fotografa invece di aspettare un giorno di vento fuori dalla copertura di
MeteoAlarm.

Tre cose imparate scrivendolo:

- **Aggiungere un comando a una riga sola fa troncare qualcos'altro, e i pesi
  decidono cosa.** Con la croce che si prende i suoi 48dp, la fascia usciva
  `ALLERTA ARANCI...`: a cedere era il **colore**, cioe' il pezzo per cui la
  fascia esiste, perche' il titolo aveva peso 1.4 contro 1 e il secondario
  mangiava il principale. Adesso il livello non ha peso - prende lo spazio che
  gli serve, per primo - e il titolo cede, che e' giusto: per esteso sta nel
  bollettino, a un tocco. Gli otto caratteri di `"ALLERTA "` erano il resto del
  margine. **Questo difetto lo ha trovato uno scatto, non un ragionamento.**
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

## 8-quinquies. La scheda della pioggia

`ui/feed/RainWindow.kt`, `ui/feed/WindowGlass.kt`, `ui/feed/Walkers.kt`,
`ui/feed/RainHours.kt`, `ui/feed/RainStory.kt`, `ui/render3d/Facets.kt`,
`ui/render3d/Precip.kt`

**E' la seconda scheda uscita dal segnaposto**, e il modello per le quattro che
restano. E' anche l'unica che e' stata rifatta due volte: prima una vasca
graduata, poi una finestra. Le due parti che seguono - com'era, e perche' non lo
e' piu' - sono la parte che vale.

### La vasca, e perche' non c'e' piu'

La vasca metteva l'acqua del giorno dentro un recipiente graduato, e la ragione
era giusta: **un numero senza scala non dice niente**, quattro e quaranta sono
due cifre e non due quantita'. Le sue scale venivano dalle soglie dell'app -
colmo a quaranta millimetri perche' e' li' che `DerivedAlerts` alza una gialla -
e sopra il colmo traboccava invece di ritararsi, perche' una vasca che si riscala
da sola non e' graduata.

E' caduta per le **misure**, non per il gusto: il riquadro dell'eroe e' una
feritoia di 283 punti per 600, e la vasca - che si dimensionava sul lato corto,
come ogni altro corpo di questa app - ne usava centotrentadue in altezza. Due
terzi del riquadro erano vuoti, e si vedeva. **Il lato corto e' l'unita' giusta
per una sfera o per una cifra, che sono larghe quanto alte; non per un riquadro
alto piu' del doppio della sua larghezza.** Una finestra verticale invece ci sta
dentro tutta.

**Il costo dichiarato**: con la vasca se ne sono andati i millimetri incisi
accanto al pelo dell'acqua, cioe' l'unico posto in cui il numero aveva una
scala addosso. Restano fra i tre numeri in fondo alla scheda, dove sono un
numero come gli altri. E' una perdita vera, ed e' stata scelta sapendola.

### La finestra

**Un blocco fresato con un foro rettangolare**, della stessa plastica bianca
opaca della cifra (`numberFace`, `numberSideNear`, `numberSideFar`,
`numberChamfer`), e dentro il foro **il cielo dell'ora scelta**. Scorrendo la
fascia delle ventiquattro ore qui sotto passa la giornata: l'alba, il
mezzogiorno, il temporale, la notte.

**Lo strombo e' la parallasse.** Girando il blocco col dito si scopre la parete
di dentro del foro, esattamente come si guarda di lato attraverso una finestra:
un montante copre una parte di cielo e lo scopre dall'altra. Non c'e' niente da
simulare, lo fa la geometria del muro spesso. Ed e' quello che da' finalmente un
mestiere alla rotazione, che sulla vasca era un giro attorno a un barattolo.

**Va detto perche' e' controintuitivo**: qui la camera **ruota l'oggetto**, non
sposta la testa di chi guarda. I due movimenti non sono lo stesso, e chi si
aspetta il secondo scrive il segno sbagliato.

**Il cielo dell'ora si calcola, non si prende dal tema.** `LocalMeteoColors`
porta il cielo di **adesso**, che e' quello giusto per la scheda attorno; qui
dentro serve l'ora scelta, e le tinte si passano **come valore**. Rifornire il
local sarebbe la strada corta e la trappola lunga: e' `staticCompositionLocalOf`,
e ogni fotogramma dello scorrimento invaliderebbe tutto il sotto-albero.

**Da una finestra si vede la fetta alta di cielo.** L'altezza del sole muove
l'astro **dentro la meta' superiore** dell'apertura, con un pavimento sotto cui
non scende mai. Prima comandava tutta l'apertura, ed era corretto e sbagliato
insieme: con l'astro basso - la notte, l'alba, il tramonto - la luna finiva in
mezzo alla gente che passa per strada. L'informazione resta (piu' alto il sole,
piu' in alto sta), il conflitto no. E' anche come sono fatte le finestre:
l'orizzonte lo copre il muro di fronte.

### La via e la gente

Sotto la finestra passa gente **tagliata al busto** dal davanzale, con gli
ombrelli.

**Non e' il personaggio gia' provato e bocciato** (sezione 10). Quello era un
esploratore *articolato*, una gerarchia di sfere con le braccia a collana, e il
verdetto fu *"se il risultato e' questo, lasciamo perdere"*. Qui non c'e' rig,
non c'e' articolazione, non c'e' faccia: sono contorni chiusi visti di profilo,
tabelle di coordinate come i profili delle coste del globo, e **il taglio al
busto toglie proprio le gambe**, cioe' la parte che rende difficile far
camminare qualcuno. Il rischio che resta e' estetico, e resta: quattro sagome
che si ripetono in una via si notano.

**Dicono qualcosa, non sono un salvaschermo**: chi ha l'ombrello aperto dice se
in quell'ora piove, quanto vanno curvi dice l'intensita'.

**Se piove, l'ombrello e' aperto.** Prima lo decideva la sola probabilita'
prevista, ed era la domanda sbagliata: la probabilita' dice se *piovera'*, ma la
scena mostra **un'ora**, e in quell'ora o piove o non piove. Con il codice
imposto degli scatti - pioggia certa, previsione a zero - si vedeva la scena
piovere con quattro persone a spasso senza niente in mano. La probabilita' resta
a decidere **quanti** si coprono quando l'acqua non e' ancora arrivata.

**Le sagome sono scure, e allora e' la via a portare la luce.** Una silhouette e'
un buco nella luce, non una figura grigia; ma quattro buchi neri su un cielo
notturno si fondono in una macchia sola. La risposta non e' schiarire la gente -
**e' accendere la strada**, che e' anche come stanno le cose davvero: di notte
una via e' illuminata, ed e' per questo che chi ci passa ci si staglia contro.
Quindi la via si ricava **dalle sagome**, con `readableOn` e la soglia da segno
grande, cioe' con lo stesso attrezzo con cui questo progetto sceglie il colore di
ogni segno disegnato sul cielo. E la fascia dell'asfalto e' alta abbastanza da
prendere i busti: al cielo restano solo le teste.

**Non e' roba da fotogramma**: `readableOn` costa una manciata di elevamenti a
potenza per passo, e sta dentro il `remember` che gia' calcola il cielo di
quell'ora.

**E l'asfalto ha un tono suo, che non prende in prestito dal cielo.** Di giorno
e' **piu' scuro** del cielo, perche' e' il cielo a illuminarlo; di notte e' **piu'
chiaro**, perche' a illuminarlo sono i lampioni. Una regola sola che dia tutte e
due le cose non esiste, e il primo tentativo - ricavare la via dal solo contrasto
con le sagome - dava una strada **piu' chiara del cielo anche a mezzogiorno**:
ghiaccio, non asfalto. Misurato sullo scatto: via (200,221,236) su cielo
(176,204,227). Quindi due passi, ciascuno con un mestiere solo: prima si scurisce
come fa l'asfalto, che di giorno basta; poi si tira su **solo se** e' rimasto
tanto buio che le sagome ci sparirebbero dentro - e quella e' la notte, e sono i
lampioni.

**La soglia pero' non e' quella del testo.** Tre a uno e' quanto serve a una
scritta per essere letta; applicato qui spinge l'asfalto a un grigio medio,
cioe' **trasforma la notte in pieno giorno** pur di far risaltare quattro
sagome - visto in uno scatto, e corretto a una soglia da scena. Una sagoma alta
sessanta pixel non e' una didascalia: le basta staccare. E la fascia non e' di
un tono solo: va dal cielo in fondo all'asfalto sotto, se no si legge come una
parete e non come una superficie vista di taglio.

**La via ha profondita', e la profondita' e' in altezza.** Avevano tutti i piedi
sulla stessa riga, quindi due che si avvicinavano si accavallavano e basta: un
mucchio, non una via. Chi cammina in fondo sta **piu' in alto sulla fascia** - la
sua testa arriva appena all'orizzonte, quella di chi passa sotto casa lo supera -
ed e' cosi' che una strada si guarda dall'alto. E' anche quello che rende
naturale un incrocio invece che una collisione: chi si sovrappone lo fa a
distanze diverse.

**Erano sei e alte un terzo dell'apertura**, ed erano una folla schiacciata
contro il vetro invece di gente in una via. Quattro, alte un ottavo, con corsie,
fasi e distanze diverse. E' l'unica cosa di questa scheda che non si deduce: si
guarda.

### Gli orologi sono due

**Ce n'era uno solo, ed era il difetto peggiore della scheda.** Un dente di sega
da 0 a 1 ogni secondo e mezzo, che per la pioggia va benissimo perche' la pioggia
**si ripete**: una goccia esce dal fondo e ne rinasce una in cima, e il ritorno a
zero non si vede.

Ma **la gente che cammina non si ripete, avanza**. Leggendo quel ciclo come se
fosse il tempo, ogni passante faceva un decimo di traversata e poi tornava
indietro insieme a tutti gli altri, ogni secondo e mezzo: non una via, un
tremolio sul posto.

Il secondo orologio gira in venti secondi, e **il suo ritorno a zero e'
invisibile per costruzione**: ogni passante compie un numero **intero** di
traversate per giro, quindi quando l'orologio cala di uno la sua posizione cala
di un intero, e il resto su uno non se ne accorge. Vale anche per il passo, e per
la stessa ragione i passi per traversata sono interi. **La regola generale: un
ciclo si chiude scegliendo periodi commensurabili, non tarando a occhio dove
mettere il salto.** E perche' il rientro non si veda, il margine fuori
dall'apertura si **ricava dalla mezza larghezza dell'ombrello** invece di essere
scritto a mano - con il margine a occhio l'ombrello era piu' largo del margine, e
spuntava.

Due numeri scritti nello stesso `withFrameNanos`, letti solo dentro il disegno:
il costo del secondo orologio e' zero.

### L'eccezione dichiarata alla regola dei zero fotogrammi

Prima la pioggia batteva **solo mentre pioveva**. Da quando c'e' la gente,
**questa scheda si muove sempre mentre la si guarda** - anche col sereno, dove
camminano con l'ombrello chiuso - perche' spegnere la via nelle giornate belle
vorrebbe dire una strada di manichini fermi, che e' peggio di una strada vuota.

E' un'eccezione alla regola per cui da fermo l'app disegna zero fotogrammi
(trappola #8), ed e' dichiarata, non dimenticata. Il suo limite e' `alive`: il
carosello tiene composta anche la scheda accanto, e `withFrameNanos` dentro una
finestra visibile continua a battere anche per una pagina fuori vista.

**Va misurata, non dedotta - e adesso la misura la prende la CI.** In coda al
giro degli scatti, `dumpsys gfxinfo` conta i fotogrammi di quattro secondi due
volte: con la pioggia in scena, e con la scheda dell'aria in scena, cioe' con la
pioggia **composta ma fuori vista** - che e' esattamente il caso per cui la
guardia esiste. Misurato: **55 contro 1** al primo giro, **60 contro 0** al
secondo - e i due giri insieme dicono piu' di ciascuno da solo.

**E l'uno non era una perdita.** La soglia chiedeva lo zero esatto e il primo
giro ha stampato un avviso; ma un **orologio** che gira produce un flusso di
fotogrammi, non un fotogramma solo. Quell'uno era una ricomposizione di
passaggio - sulla scheda dell'aria si spiega da se', perche' la qualita' dell'aria
arriva da una richiesta a parte e quando arriva ridisegna una volta - e il giro
dopo, senza toccare l'app, ha dato zero. **Un valore che oscilla fra zero e uno e'
rumore**, ed e' esattamente per questo che pretendere lo zero esatto avrebbe reso
la prova capricciosa: avrebbe suonato l'allarme a caso, che e' il modo piu'
sicuro perche' nessuno le creda piu'. Adesso chiede quello che vuole davvero
sapere: che di la' un orologio giri, e che di qua non ne giri nessuno.

I due numeri finiscono anche in `misure.txt`, accanto agli scatti: un numero che
si legge solo scorrendo diecimila righe di registro e' un numero che nessuno
rilegge.

Il numero di sinistra dice anche un'altra cosa: sessanta fotogrammi in quattro
secondi sono **quindici al secondo**, non sessanta. E' l'emulatore, che rende via
software - ed e' la prova, in cifre, che di li' non esce nessun giudizio sul
costo per fotogramma.

E' l'unica meta' della verifica che un emulatore possa dare, ed e' anche quella
che conta: il costo per fotogramma li' non dice niente (la resa e' software), ma
"i fotogrammi si fermano oppure no" e' una domanda binaria e la risposta e' la
stessa dappertutto. **Il costo vero resta da prendere in mano.**

Se il costo per fotogramma non regge i 16 ms, **cedono gli strati e non il
movimento**: prima le persone, poi le gocce sul vetro.

### Il vetro

Dietro il vetro cade la pioggia, e **sul** vetro restano le gocce, con la loro
scia. Sono due strati e non uno perche' e' la differenza fra guardare la pioggia
e guardarla da dentro. **Nessun velo traslucido sopra tutto**: e' la trappola #18
- una sola forma grande e traslucida ha portato un fotogramma da 18 a 36 ms con
il settanta per cento di ritardi.

### La fascia delle ventiquattro ore

Risponde a quello che la cifra non poteva: venti millimetri distribuiti su tutto
il giorno e venti caduti in due ore sono due giornate diverse con lo stesso
numero. Una tela sola per colonne, curva della probabilita', cursore ed
etichette, perche' la larghezza di un'ora dev'essere la stessa per tutti e tre.
L'altezza e' **in punti e non in frazione**: con lo 0,62 del vecchio palco le
ventiquattro colonne diventavano ventiquattro pali alti mezzo schermo.

- il soffitto ha **tre gradini dichiarati e scritti** (4, 10, 30 mm/h). Adattivo
  direbbe sempre la stessa cosa - la colonna piu' alta tocca il bordo tutti i
  giorni; fisso a un valore solo, la pioviggine sparirebbe;
- un'ora bagnata a **zero millimetri** e' un contorno e non un pieno, cosi'
  "piove e il modello non dice quanto" si distingue da "sono caduti due decimi".
  E' la trappola #14 disegnata invece che solo rispettata;
- una colonna tagliata dal soffitto lo **dichiara** con una tacca.

**Il non-obiettivo "nessun gesto sulla fascia" e' caduto, e con una ragione.**
Era scritto per non avere due scrittori sullo stesso stato: l'ora si sceglie
sulla prima scheda. Ma tornare su per scorrere le ore e poi riscendere per
vederne l'effetto e' scomodo, e la scomodita' era vera. Il gesto adesso c'e', e
non ha aggiunto un secondo stato: **la fascia manda l'ora del giorno allo stesso
`selectHour`** che usa la prima scheda - le ore di un giorno partono da mezzanotte
e sono ventiquattro, quindi l'indice della colonna **e'** l'ora, e non serve una
seconda verita'.

Il riconoscitore e' uno solo e **non consuma la discesa**: aspetta lo scarto,
lascia perdere se il dito va piu' in verticale che in orizzontale - il carosello
se lo prende - e reclama solo quando va per il lungo. E' la trappola #5 (l'asse
decide) applicata dentro una pagina invece che fra le pagine.

### La lezione dell'avvolgimento, in `ui/render3d/Facets.kt`

Il fondo della vasca non si riempiva. Il motivo: **le facce proiettate di un
prisma non si avvolgono tutte nello stesso verso**, e con la regola `NonZero` due
quadrilateri avvolti al contrario si cancellano. Misurato: sei quadrilateri, tre
per verso, il cinquantotto per cento della colonna spariva.

Le due correzioni ovvie non funzionano, ed e' il pezzo che vale:

- `EvenOdd` non la aggiusta - due sovrapposizioni sono parita' pari, cioe' lo
  stesso buco;
- disegnare ogni faccia per conto suo con un'opacita' non la aggiusta - le
  sovrapposizioni si compongono, e da 0,42 si arriva a 0,66: tre bande scure che
  si muovono.

La correzione e' **normalizzare il verso**, e sta in `addFacet`: si calcola
l'area con segno del quadrilatero e, se e' negativa, lo si percorre al contrario.
Il file resta anche se la vasca non c'e' piu', perche' la lezione vale per
qualunque solido proiettato - e infatti la usa la faccia davanti della finestra.

Sotto il buco c'era **un secondo difetto**, che il buco nascondeva: l'acqua era
dipinta *dopo* le pareti vicine traslucide, quindi una volta riempita si sarebbe
letta spalmata fuori dalla vasca. Le pareti sono state divise in due passate con
l'acqua in mezzo.

### Il resto, che vale ancora

**La frase e' in italiano vero, non in maiuscolo spaziato**: "una frase in
maiuscolo spaziato si compita invece di leggersi" era gia' scritto, e vale qui.
Il temporale non prende aggettivo - "temporale moderata" non e' italiano, e gli
aggettivi di intensita' concordano al femminile con pioggia e neve. Due tratti
separati da una sola ora asciutta si fondono, se no una normale giornata di
fronte si spezza in cinque e la frase diventa un inventario. Su oggi si guarda
avanti dall'ora corrente: alle diciotto, "asciutto fino alle sedici" e' una
previsione del passato. **A picco zero non c'e' nessun aggettivo**: dire "debole"
partendo da un'assenza e' inventare un valore.

Le soglie di intensita' sono la scala d'uso comune dei servizi regionali italiani
(2 / 6 / 10 mm/h) e non quella americana, tarata sull'intensita' istantanea di un
pluviometro mentre una casella oraria e' gia' una media su un'ora.

**Tutto quel che si puo' provare senza emulatore sta in `RainStory.kt`**, senza
una riga di Compose: la frase, i tratti, le soglie, la tipologia. E' la stessa
scelta di `isWet()`, e per la stessa ragione. `Facets.kt` e' l'altro pezzo cosi':
la convessa e l'avvolgimento si provano con JUnit, senza uno schermo.

**`PrecipKind` e' stata completata, non solo chiamata.** `Wmo.precipKind` non
restituisce mai `MISTA` - nessun suo ramo la produce - perche' un codice
giornaliero dice il fenomeno prevalente e non i due insieme. `precipKindOf` la
ricava dalle due somme, **e il codice imposto vince su quello vero**: senza,
negli scatti la finestra pioveva mentre TIPOLOGIA diceva "--", che e' la scheda
che si contraddice da sola.

**`--ei giro` non arrivava al feed.** `rotation.pin` era chiamata in un posto
solo, in `HomeScreen`: nessuna scheda del feed si poteva fotografare girata, la
luna compresa. Adesso arriva, ed e' quello che rende verificabile lo strombo -
senza, l'unica cosa per cui il gesto esiste sarebbe infotografabile.

**Gli scatti nuovi sono due, in coda** (trappola #38): la finestra girata e la
finestra sotto la pioggia. **Il giro degli orologi pero' non si scatta**: che il
ritorno a zero non si veda e' una cosa che un fotogramma non puo' dire, e va
guardata in mano.

---

## 8-quater. I test

`app/src/test/`

Il progetto non ne aveva **nessuno**. Sotto c'era solo `probe-api`, che verifica
i contratti delle API ma non una riga di logica.

Novantatre prove, tutte su funzioni pure, nessun emulatore, un job `test` a se'
stante. **Erano quarantasei quando questa riga e' stata scritta, e la riga non
e' piu' cambiata mentre il numero raddoppiava** - un conto scritto a mano in un
documento e' esatto il giorno in cui lo si scrive e sbagliato tutti gli altri.
Adesso il numero vero lo stampa la CI a ogni giro, nel passo "Quante prove sono
girate", che fallisce anche se ne trova zero: quello e' il posto in cui
guardarlo. Sono scelte per cio' che coprono, non per fare numero — e cinque di loro
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
| `hasFiniteCoordinates` | La trappola #41: `NaN` non fa cadere niente e arriva fino alla rete travestito da errore di formato. Il test prova anche che `WeatherRepository.load()` si fermi **senza aprire una connessione**. |
| `failureMessage` | La trappola #42, e non prova il testo esatto: prova che cio' che si mostra **non sia** il messaggio dell'eccezione, che stia su una riga e che sia corto. Sono i tre modi in cui quel dump era arrivato sullo schermo. |

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
- **I commit non portano firme di strumenti.** Niente trailer di paternita'
  automatica, niente link a sessioni: nessun riferimento all'assistente, ne' nei
  messaggi ne' fra gli autori. Il lavoro e' firmato
  `NoximilienCoxen <313902161+NoximilienCoxen@users.noreply.github.com>`, che e'
  l'identita' da usare per `user.name` e `user.email`.

  Questa regola e' stata data piu' volte e altrettante volte disattesa, perche'
  la firma automatica viene reinserita a ogni sessione nuova: **e' scritta qui
  apposta**, ed e' la prima cosa da impostare prima di committare.

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

---

## 11. Comandi universali — lavorare in due sullo stesso progetto

Questa sezione e' per chi arriva adesso, con una sessione di Claude propria, e
vuole contribuire senza rompere niente. Vale anche per chi il progetto lo
conosce: sono gli stessi comandi, sempre gli stessi, e l'ordine conta.

L'obiettivo di tutto quello che segue e' uno solo: **`main` sempre aggiornato, e
la release `apk-latest` sempre allineata a `main`**, cosi' chi installa l'APK dal
telefono si ritrova davvero l'ultimo lavoro di tutti e due e non la build di
ieri di uno solo.

### 11.1 Come comportarsi, prima ancora dei comandi

- **Leggi questo documento dall'inizio.** La sezione 7 e' un elenco di trappole
  gia' pagate: ripeterle costa le stesse ore una seconda volta. La sezione 9 e'
  cosa vuole l'utente, detto da lui.
- **Misura invece di dedurre.** Compilare e installare dura una manciata di
  secondi (sezione 1); un dubbio su come si vede una cosa si chiude con uno
  screenshot, non con un ragionamento.
- **Aggiorna CONTESTO.md nello stesso commit del cambiamento.** Questo file e'
  la memoria condivisa fra due persone che non si vedono lavorare: una cosa
  scoperta e non scritta qui verra' riscoperta dall'altro, pagandola di nuovo.
- **Una cosa per branch.** Branch corti e a tema unico si uniscono senza
  conflitti; un branch che tocca mezza app resta aperto per giorni e litiga con
  tutto.
- **Avvisa prima dei limiti, non dopo.** Se una richiesta non si puo' fare come
  e' stata chiesta, si dice subito.

### 11.2 Chi firma i commit — da impostare **prima** di committare

Ogni sessione nuova di Claude riparte con la propria identita' come autore, e
quella identita' qui non e' voluta (sezione 9). Due comandi, prima di qualsiasi
altra cosa:

```bash
git config user.name  "NoximilienCoxen"
git config user.email "313902161+NoximilienCoxen@users.noreply.github.com"
```

Nel messaggio di commit **niente trailer di paternita' automatica, niente link
a sessioni, nessun riferimento all'assistente**. Se una firma e' scappata
comunque, si toglie dalla storia con `bash scripts/spolvera_firme.sh <branch>` —
che e' una riscrittura, quindi si fa prima che il branch sia stato unito, e si
guarda il risultato prima di spingerlo con `--force-with-lease`.

Verifica veloce, prima di spingere:

```bash
git log -3 --format='%an <%ae>%n%B'
```

### 11.3 Il giro completo, dal primo comando all'APK sul telefono

```bash
# 1. si parte sempre da un main aggiornato
git checkout main && git pull --ff-only origin main

# 2. un branch a tema, dentro claude/
git checkout -b claude/<argomento>-<sigla>

# 3. si lavora, si prova sul telefono
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/*.apk

# 4. si committa (identita' gia' impostata, vedi 11.2)
git add -A && git commit -m "Cosa cambia, in una riga"

# 5. si pubblica il branch
git push -u origin claude/<argomento>-<sigla>
```

Poi si apre una pull request verso `main`, si aspetta che la CI sia verde, e si
unisce. **Il passo 5 non basta**: finche' il lavoro sta su un branch, sul
telefono non arriva niente.

`git push` che fallisce per rete si ritenta, non si aggira: quattro tentativi
con attese di 2, 4, 8, 16 secondi.

### 11.4 Perche' bisogna passare da `main` (e non e' una formalita')

In `.github/workflows/build.yml` il job che pubblica ha questa condizione:

```yaml
  rilascio:
    needs: build
    if: github.ref == 'refs/heads/main'
```

Cioe':

- un push su `claude/**` **compila, prova e fotografa**, ma non pubblica niente;
- solo un `main` che si muove, e con `build` verde, riscrive l'allegato
  `weather.apk` sul tag fisso `apk-latest`.

La condizione c'e' apposta e non va tolta: prima che ci fosse, ogni branch in
corso poteva spedire sul telefono una build a meta', e con due persone che
lavorano in parallelo succederebbe di continuo — l'ultimo che spinge vince, e
l'altro si ritrova l'app senza il proprio lavoro dentro.

Ne segue la regola pratica: **il telefono vede solo cio' che e' stato unito a
`main`.** Se l'APK non ha la tua modifica, quasi sempre non e' un difetto:
e' un merge che non e' stato fatto, o una CI rossa.

### 11.5 Dopo il merge: controllare che l'APK sia davvero cambiato

Un `main` verde e una release aggiornata non sono la stessa cosa: se `build`
fallisce su `main`, il job `rilascio` non parte e l'allegato resta quello di
prima, **senza dirlo a nessuno**. Va guardato ogni volta.

```bash
git checkout main && git pull --ff-only origin main
git rev-parse main          # e' questo lo SHA che ci si aspetta nella release
```

Il corpo della release riporta la riga `Commit: <sha>`: se coincide con quello
qui sopra, l'APK in linea e' l'ultimo. La pagina e' sempre la stessa,
<https://github.com/NoximilienCoxen/test-weather/releases/tag/apk-latest>.

Con `gh` installato (macchina locale) lo si chiede senza aprire il browser:

```bash
gh run list  --branch main --limit 3 --repo NoximilienCoxen/test-weather
gh release view apk-latest --repo NoximilienCoxen/test-weather --json publishedAt,body
```

Dentro Claude Code sul web `gh` non c'e': la stessa cosa si chiede con gli
strumenti GitHub dell'agente (elenco dei run, ultima release).

Se il run su `main` e' rosso, **il lavoro non e' finito**: si corregge e si
spinge su `main` finche' torna verde, perche' fino ad allora chiunque scarichi
l'APK installa l'app di prima.

### 11.6 Tenere il proprio branch al passo con l'altro

Con due branch aperti insieme, chi unisce per secondo trova `main` cambiato
sotto i piedi. Si riallinea **prima** di aprire la pull request:

```bash
git fetch origin main
git merge origin/main        # merge, non rebase: il branch e' gia' pubblicato
```

Rebase e `--force-push` su un branch che l'altro potrebbe aver gia' preso
invalidano il suo clone: non si fanno.

Il file che litiga piu' spesso e' proprio `CONTESTO.md`, perche' lo scrivono
tutti e due. Risolvendo il conflitto **si tengono entrambe le versioni**: si
accodano le righe nuove, non si riscrive la sezione dell'altro. E' un diario, e
in un diario non si cancella.

### 11.7 Cosa non fare mai

- **Non caricare l'APK a mano** sulla release: lo produce e lo firma la CI. Un
  allegato messo a mano con un nome diverso resta li' accanto a `weather.apk` e
  chi arriva sulla pagina scarica quello sbagliato.
- **Non spostare ne' ricreare il tag `apk-latest`**: e' fisso apposta, cosi' il
  link si puo' tenere fra i preferiti.
- **Non togliere la condizione `if: github.ref == 'refs/heads/main'`** dal job
  `rilascio` (vedi 11.4).
- **Non riscrivere la storia di `main`** senza che sia una decisione presa
  insieme: i link ai commit vecchi — il corpo della release, gli `INFO.txt` su
  `ci-artifacts` — puntano nel vuoto.
- **Non spegnere ne' saltare i test per far passare la CI.** Un test rosso e'
  una notizia, non un ostacolo.
- **Non firmare i commit con lo strumento** (11.2).

### 11.8 Quando la CI e' rossa: cosa guardare, in ordine

Gli stessi controlli girano in locale, e li' rispondono in secondi:

```bash
./gradlew assembleDebug      # errori Kotlin, righe che cominciano con "e: "
./gradlew testDebugUnitTest  # i test
./gradlew lintDebug          # il referto finisce nel log, non solo in un HTML
./gradlew assembleRelease    # R8: una regola di keep mancante si vede solo qui
```

Niente `--stacktrace`: seppellisce gli errori veri sotto trecento righe di
stack Gradle (trappola #1).

Cio' che la CI ha visto davvero — risposte delle API, screenshot
dall'emulatore — sta sul branch `ci-artifacts`, che non si controlla:

```bash
git fetch origin ci-artifacts
git show origin/ci-artifacts:api/hourly.json > /tmp/hourly.json
git show origin/ci-artifacts:screenshots/scuro-1-temp.png > /tmp/x.png
```

### 11.9 L'inizio di ogni sessione, gia' automatico

In `.claude/settings.json` c'e' un hook che a ogni avvio di sessione fa:

```
git checkout main && git pull --ff-only origin main
```

Cioe' ogni sessione nuova parte da un `main` aggiornato senza doverlo ricordare.
Se il comando fallisce, quasi sempre e' perche' ci sono modifiche non
committate: si guardano con `git status` e si decide, non si forza.

---

## 12. Sala: la galleria che ha sostituito il feed

`ui/sala/` (nuovo), al posto di `ui/home/` (tranne `MoonPhase.kt`, che resta),
`ui/feed/`, `ui/alerts/`, `ui/settings/SettingsScreen.kt`, e la meta' di
`ui/render3d/` che serviva solo alla cifra prismatica (`TextPrism.kt`,
`PrismRenderer.kt`, `Facets.kt`, `Precip.kt`, `Skyline.kt`) e a `ui/render/`
(`TemperatureRenderer.kt`, `ExtrudedText.kt`). `ui/motion/SceneRotation.kt`,
`WeatherHaptics.kt` e `PhysicalNumber.kt` sono usciti con lei: servivano solo
al trascinamento della cifra. `ui/render3d/Camera.kt` e `Bodies.kt` **restano**
- li usano ancora i widget, il mappamondo del benvenuto e adesso anche la
luna di Sala IV - e cosi' `ui/motion/DeviceTilt.kt`, che serve al benvenuto.

**Perche' un cambio cosi' grande.** Non e' un restyling sopra il feed: e' la
sostituzione decisa nella chat di Claude Design "Mobile app design watercolor"
(`chats/` nel bundle di handoff) - la direzione **1a - Sala**, scelta esplicita
dell'utente contro **1b - Catalogo**. Sette sale sfogliabili in un carosello
verticale al posto delle sei schede del feed, un indicatore di percorso al
posto della colonna di icone, un cartellino da museo (titolo, dati, didascalia)
sotto un lavaggio ad acquerello che cambia con l'ora e col tempo. La scultura
3D in plastica bianca fresata e il fondo a cielo sfumato - il cuore tecnico del
feed, sezioni 4 e 8-bis - non fanno piu' parte della schermata principale.
Restano nella cronologia (`git show`), e nei widget dove la sfera e il sole
prospettici vivono ancora.

### La tavolozza: `ui/sala/SalaTheme.kt`

Il fondo non e' piu' un cielo continuo (`skyColors`, che resta per il
benvenuto e i widget): e' una **carta** che scurisce a scatti con l'ora, sopra
la quale galleggiano tre macchie d'acquerello il cui colore dipende dalla fase
del giorno (`SalaPhase`: alba/giorno/tramonto/notte) e dal tempo
(`SalaCondition`: sereno/nuvoloso/pioggia/grandine/temporale/temporale con
grandine). E' la tabella colori del prototipo (`PHASE_WASH`, `COND_WASH`,
`PHASE_TINT`) portata 1:1 in `washColors()`, coi token del sistema Broadsheet
(`SalaTokens`) copiati dal suo `styles.css` - e' una tavolozza tutta nuova,
ciano/magenta/giallo di quadricromia, non piu' l'azzurro del cielo.

**`paperDarkness()` non legge l'orologio come il prototipo**: legge
`SkyState.dayness`, che e' gia' la transizione morbida (reale, per la
localita' mostrata) fra notte e giorno pieno che governa comparsa di sole e
luna altrove in app. Stessa forma della curva del prototipo - **un salto solo,
che scavalca la fascia grigia di mezzo dove nessun colore di testo reggerebbe**
- ma guidata dai dati veri invece che da un orologio finto tarato su Forli'
d'agosto.

**La fase (`salaPhaseOf`)** si legge dall'altezza reale del sole
(`sky.altitude`), non da un intervallo di ore fisso: sopra 0,22 e' giorno,
sotto -0,42 e' notte, in mezzo alba o tramonto secondo `sky.evening`. Sono le
stesse soglie che governa `SkyState.of` per il cielo del feed.

**Semplificazioni dichiarate, non dimenticanze:**

- **Niente distorsione a turbolenza.** Il filtro SVG (`feTurbulence` +
  `feDisplacementMap`) che nel prototipo rende il bordo delle macchie
  irregolare, "acquerellato", non e' stato riprodotto: le macchie sono cerchi
  puliti che sfumano a trasparente (`Brush.radialGradient`), che e' gia' il
  grosso dell'effetto. Chi vuole il bordo vero puo' provare un `RenderEffect`
  (API 31+).
- **Niente respiro (`animation:breathe`).** Il prototipo fa pulsare piano le
  macchie su quasi tutte le sale. E' stato tolto apposta: e' un'animazione
  sempre accesa, ed e' esattamente quello che la trappola #8 vieta - da fermo
  l'app deve disegnare zero fotogrammi. Un respiro sempre attivo su sette
  schermate e' un costo di batteria per un tocco decorativo.
- **Font Source Serif 4, ma statico.** Il prototipo lo carica variabile da
  Google Fonts (`ital,opsz,wght@0,8..60,300..700`). Qui sono stati scaricati
  quattro pesi statici (300/400/600/700, `res/font/source_serif_*.ttf`) invece
  del file variabile vero: costruire l'XML del font variabile e verificarne
  gli assi senza poter compilare qui sembrava piu' rischio che valore. Chi
  vuole il file variabile vero lo trova sulla stessa CSS2 API con uno
  user-agent che dichiari supporto alle variazioni **e non** al WOFF2 - non
  banale da ottenere in un colpo solo.

### Cosa e' cambiato nei dati, non solo nel disegno

- **`AirQualityRepository`** adesso chiede anche `nitrogen_dioxide` e `ozone`
  a Open-Meteo (erano gia' nel suo endpoint, non si chiedevano). Servono a
  Sala V, che mostra quattro inquinanti veri (PM2,5, PM10, NO₂, O₃) e non i
  cinque del prototipo - i pollini non sono nell'endpoint base e non sono
  stati aggiunti.
- **Sala III (pioggia) e Sala VII (UV) scrivono sullo stesso `selectHour`**
  della prima sala, come gia' faceva la vecchia scheda della pioggia del feed
  (sezione 8-quinquies): il giorno resta un asse solo per tutta la galleria.
  Sala VII nel prototipo aveva un cursore *suo*, separato, sulle sole ore
  6-21; qui legge le ventiquattro ore vere e lo stesso asse di tutti.
- **Gli avvisi di Sala I si filtrano sull'ora scelta** (`activeAt`, in
  `SalaOggi.kt`) confrontando `onset`/`expires` veri con l'ora scorsa sulla
  barra - non piu' un intervallo scritto a mano come nel prototipo.
- **La luna di Sala IV e' la sfera vera** (`ui/render3d/Bodies.kt::moon`,
  la stessa dei widget), non un disco piatto SVG: fase e illuminazione da
  `MoonPhase` (gia' in app, epoca sinodica nota), non dal calcolo del
  prototipo. Il trascinamento orizzontale sposta solo quale giorno del mese si
  guarda (`-15..15`); il prototipo aveva anche un'inclinazione verticale che
  nell'ultima versione veniva comunque azzerata a ogni trascinata, quindi non
  e' stata portata.
- **Niente rotazione verticale sulla scultura di Sala I.** Il prototipo la
  aveva (`scTilt`), ma verticale e' l'asse con cui il carosello cambia sala:
  tenerla avrebbe riacceso la trappola #5 (i due gesti si sarebbero contesi il
  dito) su un tocco decorativo. Resta solo la rotazione orizzontale.
- **"Le localita'" chiede il meteo vero per ogni preferita** aprendo il
  pannello (`WeatherViewModel.loadFavoritesWeather`), una richiesta completa
  per localita' invece di un'iconcina inventata - il prototipo mostrava un
  meteo demo per ogni citta' del suo elenco fisso. E' la stessa chiamata della
  previsione principale, presa per intero e tenuto solo `current`: costa piu'
  di un endpoint dedicato, ma non ne aggiunge uno nuovo da scrivere e provare.
  Finche' non arriva, quella riga non mostra un'iconcina inventata.
- **`SalaImpostazioniScreen`** aggiunge preferenze nuove a `SettingsPrefs`:
  `CardTheme` (Auto/Chiaro/Scuro, l'interruttore di Sala), `SalaWindUnit`
  (km/h, m/s, nodi), `CaptionStyle` (Brevi/Complete - "Brevi" e' gia' cablato
  a nascondere il solo corpo del testo, non ancora ripreso da nessuna sala:
  serve una passata perche' ogni sala guardi questa preferenza) e
  `AlertToggles` per i quattro avvisi calcolati. Sono preferenze nuove, non
  ancora lette da nessun produttore di notifiche vere - oggi decidono solo
  cosa l'interruttore mostra acceso.

### Cosa e' rimasto fuori, in ordine di probabile utilita'

1. **Il tiro per ricaricare.** Il feed lo aveva (`PullToRefresh` in
   `FeedScreen.kt`, cancellata insieme al resto); Sala per ora ricarica solo
   all'avvio, al cambio di localita' o tornando in primo piano dopo venti
   minuti (`refreshIfStale`). La classe andrebbe estratta in un file suo
   invece di essere ricopiata.
2. **Il bollettino allerta per esteso.** `ui/alerts/AlertsSheet.kt` e'
   cancellato: Sala I mostra titolo e riga breve di ogni avviso attivo, ma non
   c'e' piu' un posto dove leggere descrizione e istruzioni per intero.
   `WeatherViewModel.openAlerts/closeAlerts` esistono ancora (senza
   chiamante): risistemare un bollettino - magari toccando la riga
   dell'avviso in Sala I - e' la strada piu' diretta per riprenderselo.
3. **Il tema Scuro forzato non e' mai stato visto su un fondo scuro vero.**
   `CardTheme.SCURO` esiste e `paperDarkness` gli risponde, ma nessuna sala e'
   stata fotografata cosi': va provato che macchie e inchiostro restino
   leggibili sulla carta scurita quanto lo sono su quella chiara.
4. **Le didascalie "Brevi"** sono un'opzione salvata ma senza lettore: nessuna
   sala controlla ancora `state.captionStyle`.
5. **`WeatherViewModel.forceAlert`/`collapseAlerts`** (gli agganci di verifica
   automatica su un'allerta finta) restano validi, ma senza un bollettino da
   aprire il gesto "tocca il pallino per riaprire tutto" del feed non ha piu'
   un posto dove atterrare.

### Gli agganci della cattura non hanno mai funzionato, e Sala l'ha fatto vedere

**Il guasto non e' di Sala: e' di `BuildConfig.DEBUG`, e c'era da prima.**
`applyExtras` in `MainActivity` era protetta da `if (!BuildConfig.DEBUG) return`,
e il commento accanto spiegava che `DEBUG` non va confuso con `isDebuggable` -
messo a `false` apposta, perche' la fluidita' si misura sulla build vera - e che
`DEBUG` "resta comunque vero". **Non e' cosi'**: AGP genera `BuildConfig.DEBUG`
da `isDebuggable`, non dal nome del tipo di build. Spegnendo uno si era spento
l'altro, e la funzione usciva alla seconda riga in **ogni** build: `--ei ora`,
`--ei sezione`, `--ei allerta`, `--ei meteo` non hanno mai fatto niente.

Adesso la guardia e' `BuildConfig.AGGANCI_CATTURA`, un `buildConfigField` acceso
sul tipo `debug` e spento sulla `release`: fa quello che si credeva facesse
`DEBUG`, e non dipende da `isDebuggable`.

**Perche' nessuno se n'era accorto.** La prima scheda del feed era animata -
scultura, stelle cadenti, uccelli: sei scatti della **stessa** scheda uscivano
comunque diversi fra loro, e passavano per sei schede diverse. Sala da ferma
disegna zero fotogrammi (trappola #8), e i sei scatti byte per byte identici
hanno reso visibile un guasto che era li' da prima. **Un referto che varia da
solo non prova niente**: e' lo stesso insegnamento della guardia "zero prove
eseguite" nel job `test`.

**Come si e' arrivati alla causa, che e' la parte che conta.** Dai pixel si sono
dedotte due cause, **tutte e due sbagliate**: prima un `ripple` senza precedenti,
poi una gara sul campo `room`. Ognuna e' costata un giro di CI da nove minuti e
una correzione che non correggeva il sintomo. La risposta e' arrivata quando si e'
smesso di dedurre e si e' messa **una riga di log** all'inizio di `applyExtras`:
nel logcat della cattura non e' mai comparsa, e li' la domanda era chiusa. Quella
riga e' rimasta, per la stessa ragione per cui esiste `previsione pronta`.

Nota su questo container: i log grezzi dei giri di GitHub non sono raggiungibili
da qui (l'archivio che li ospita e' negato dalla policy di rete della sessione).
Cio' che la cattura salva da se' - `logcat-streaming.txt` fra gli artefatti -
e' quindi l'unico strumento di misura disponibile, e va usato **prima** di
guardare i pixel, non dopo.

### Una gara latente trovata per strada: la richiesta di una sala

Cercando la causa sbagliata si e' trovato un difetto vero, che pero' **non era
quello che si vedeva**: `requestRoom` scriveva la sala chiesta in `state.room`,
lo stesso campo che il carosello riscrive da se' a ogni pagina posata
(`showRoom(settledPage)`). Due scrittori sullo stesso campo sono una gara, non
uno stato, e all'avvio si sarebbe potuta perdere.

`roomRequest` e' quindi diventato `SalaRoom?` invece di un contatore: porta la
sala chiesta, il carosello non la tocca, e chi la esaudisce la spegne
(`roomRequestHonoured`). Cosi' la stessa sala chiesta due volte di fila riparte
davvero - l'unica ragione per cui prima serviva un contatore. Correzione tenuta
perche' giusta, non perche' risolvesse il sintomo: quello era altrove.

`scripts/capture.sh` e' stato allineato nello stesso passaggio: le sale sono
sette e in un ordine loro, mentre il giro fotografava ancora le sei schede del
feed coi loro nomi. Gli scatti della "finestra" della pioggia hanno perso quel
nome insieme alla lastra di vetro, cancellata con `ui/feed/`.

### Due test sono usciti insieme al codice che provavano

`FacetsTest.kt` provava `convexHull`/`facetTwiceArea` di `Facets.kt` - la
matematica della vasca della vecchia scheda della pioggia, cancellata con
lei. `RainStoryTest.kt` provava `RainStory.kt`, in `ui/feed/`, uscito con
tutto il resto del feed. Cancellati insieme ai file che provavano: un test
verde su codice che non esiste piu' non prova niente, e uno che non compila
piu' avrebbe rotto il job `test` per intero.

### 12-bis. L'Ingresso, e le altre cose chieste dopo il primo giro

**Il mappamondo e' uscito.** Girava cercando casa, si fermava sulla longitudine
giusta e ci piantava uno spillo: era il pezzo piu' ingegnoso della vecchia
schermata di benvenuto, ed e' uscito lo stesso perche' raccontava la cosa
sbagliata. Una galleria non si apre con un globo che cerca - si apre con una
parete, un cartellino e una frase. Con lui e' uscito l'ultimo lettore
dell'accelerometro: `rememberDeviceTilt` non lo chiama piu' nessuno, mentre
`findLifecycleOwner`, che sta nello stesso file, serve ancora a `MeteoApp`.

**La frase e' di un artista vero, e ogni frase porta la sua fonte** (vedi
`ui/welcome/Citazioni.kt`). Le tre in elenco sono state verificate una per una
contro la fonte primaria: Constable nella lettera a John Fisher del 23 ottobre
1821, Van Gogh nelle lettere a Theo dell'8 settembre e del 9-10 luglio 1888.
Un paio attribuite a Monet e il "The Sun is God" di Turner sono state
**escluse apposta**: circolano dappertutto ma non si e' risaliti a un
originale, e circolare molto non e' una fonte.

E' la stessa regola della nota sugli agganci in `MainActivity`, applicata
altrove: un'app che mette in bocca a un artista vero una frase mai detta sta
prestando la propria faccia a qualcosa che non viene da dove sembra. Una
citazione inventata e' anche **piu' difficile** da smentire di un'allerta
finta, non piu' facile. La traduzione italiana e' nostra, quindi l'originale
resta scritto accanto: e' il punto in cui una citazione verificata puo'
comunque tradire.

**"Guardando il cielo…" respira, e non viola niente.** Due trappole la
riguardano e vanno lette per intero. La #8 vieta di disegnare fotogrammi da
fermi: qui fermi non si e', e un'attesa in cui niente si muove e'
indistinguibile da un'app bloccata. La #17 dice che `rememberInfiniteTransition`
"qui non anima": non animava dove il valore si leggeva **solo dentro il
disegno** - erano le gocce di pioggia - mentre qui finisce nel colore di un
`Text`, cioe' in composizione, che e' il caso in cui la comodita' funziona.
L'animazione muore con l'attesa.

**Il giro della scultura e' completo e torna a casa da solo.** Era bloccato a
settanta gradi e ci restava: voleva dire che la scultura aveva un rovescio che
nessuno poteva vedere.

> **Questo paragrafo diceva una cosa sbagliata, e la cosa sbagliata e' tornata
> in codice.** Diceva: *"adesso il giro e' un `Animatable` - `snapTo` sotto il
> dito, molla al rilascio"*. E' esattamente la costruzione che
> `ui/motion/SceneRotation.kt` aveva **gia' abbandonato**, col perche' scritto
> nel suo commento: ogni delta apre una coroutine per il proprio `snapTo`, il
> dispatcher consegna al fotogramma e non subito, gli ultimi `snapTo` di un
> gesto veloce arrivano *dopo* l'avvio della molla, e un `Animatable` che
> riceve uno `snapTo` **annulla l'animazione in corso**. L'oggetto parte e si
> pianta a meta' giro, tanto piu' spesso quanto piu' deciso e' stato il gesto.
> La documentazione ha fatto da ponte per riportare dentro una trappola gia'
> pagata: **una nota sbagliata e' peggio di una nota assente**, perche' chi la
> legge smette di cercare. Vedi `ui/sala/SalaGiro.kt`, sezione 12-ter.

**Le impostazioni hanno un comando loro.** Tre linee disegnate a mano - non
c'e' una libreria di icone e non vale aprirne una per tre segmenti - e il nome
della citta' smette di essere la porta di servizio: torna a dire dove sei, e
toccandolo si cambia posto, che e' quello che chiunque si aspetta.

**"Torna ad adesso" compare solo quando si e' lontani dal presente.** Un
comando che non fa niente insegna a non fidarsi anche degli altri.

**Le citta' consigliate sono uscite da "Le localita'".** Erano nomi decisi da
noi, senza rapporto con chi guarda: una scorciatoia verso posti che nessuno
aveva chiesto, mentre chi voleva la propria citta' doveva comunque cercarla.
Resta un modo solo, la ricerca, che era gia' l'unico buono per tutti. La citta'
che si sta guardando ha un segno accanto al nome, **fermo**: un pallino che
pulsa sarebbe la scelta ovvia e sarebbe la trappola #8 riaperta per dire "sei
qui".

**La luna e' una sfera.** Usava gia' il corpo vero, coi mari al posto giusto,
ma lo riempiva di tinta piatta: al novilunio si vedeva un disco grigio, non un
corpo. Adesso il pigmento si addensa verso il lembo, col centro del degrade'
spostato verso la luce - ombreggiatura, non vignettatura. Il trascinamento
orizzontale li' resta del mese: darglielo anche alla rotazione avrebbe
riaperto la trappola #5 su due gesti che si contendono lo stesso dito.

### Niente di questo e' stato provato in mano, e non poteva esserlo

Stessa nota di ogni passata scritta da questo container (sezione 8, punto
6-bis): **niente SDK Android qui**, quindi ne' `lintDebug` ne' `assembleDebug`
sono partiti da questa sessione. Tutto cio' che e' Compose - le sette sale, le
due schermate di servizio, il carosello, i gesti - l'ha compilato **solo** la
CI, non questa sessione. Il font scaricato (`source_serif_*.ttf`) e' stato
verificato per firma SFNT (`\x00\x01\x00\x00`), non per rendering.

Da guardare per primi, in mano, appena la CI e' verde:

- che il trascinamento orizzontale sulla scultura di Sala I giri la scena
  senza che il carosello verticale rubi il gesto (trappola #5, di nuovo);
- che la barra dell'ora di Sala I, la fascia di Sala III e la curva di Sala
  VII si scorrano senza far scattare il cambio sala - lo stesso punto esposto
  che il feed aveva gia' pagato;
- se le macchie d'acquerello, senza turbolenza, si leggono ancora come tali o
  se servono comunque un bordo mosso;
- il costo per fotogramma della sfera lunare di Sala IV mentre la sala
  accanto e' in vista nel carosello (stessa domanda mai chiusa della sezione
  8, "non fatto", per la scultura della vecchia prima scheda).

### 12-ter. Da stati a passaggi, e le quattro misure che mentivano

Sesto giro su Sala. Il punto di partenza e' una misura, non un'impressione:
cercando `spring(`, `tween(`, `animate*AsState`, `AnimatedVisibility` e
`Crossfade` in tutto `ui/sala` si trovavano **tre** animazioni in totale - i due
pannelli di servizio e la molla del giro. Tutto il resto scattava. E' questo che
si sentiva come "lenta e meccanica": non la velocita', l'**assenza di passaggi**.

#### L'aggancio del giro era morto, e sei scatti mentivano da mesi

`grep -rn forcedYawDeg app/src/` dava tre occorrenze, **tutte dentro
`WeatherViewModel.kt`**: il commento, il campo, il setter. Nessuno lo leggeva.
`MainActivity` instradava l'extra, `capture.sh` mandava ancora `--ei giro 90` e
`135`, e sei scatti - 90°, 135°, 180°, la luna a 155°, la pioggia a 45° e 60° -
ritraevano la scena **ferma nella posa di riposo**. Il lettore era
`SceneRotation.pin()`, uscito col vecchio feed; il resto della catena e' rimasto
in piedi e ha continuato a sembrare che funzionasse.

E' la terza volta che questo progetto scopre che **il banco di prova non
fallisce, dice di si'**: prima gli agganci muti sotto `BuildConfig.DEBUG`, poi la
galleria che ritraeva l'Ingresso in ogni scatto, adesso il giro. La lezione non
e' "controllare meglio": e' che uno scatto va confrontato con **cosa dovrebbe
mostrare di diverso**, e una scultura girata di novanta gradi somiglia comunque a
una scultura - che e' precisamente perche' e' passata.

Gli scatti girati ora cambiano davvero, per la prima volta. Non e' una
regressione, ed e' scritto anche dentro `capture.sh` accanto al comando.

Uscito anche il `--ei giro 60` su Sala III: quella sala non ha un oggetto che
gira, quindi era un comando che non fa niente in uno scatto che prometteva di
mostrarlo.

#### Il giro: una molla sola, e il conto dei secondi

`SalaGiro.kt` faceva **due animazioni in fila** - `animateDecay` con attrito 1,1,
e solo dopo `animateTo(0f)` a rigidita' 45. Il conto: costante di tempo
`1/(0,80·√45)` = 186 ms, e portare 360° sotto la soglia implicita di 0,01° vuole
`ln(36000)·0,186` ≈ 1,95 s, **dopo** circa un secondo e mezzo di decadimento.
Tre secondi e mezzo per tornare a casa.

Adesso: nessun decadimento separato, l'energia del lancio entra come velocita'
iniziale di **una** molla che punta gia' al giro intero piu' vicino;
`dampingRatio = 0.82f`, `stiffness = 90f`, `visibilityThreshold = 0.05f`. Un
ritorno da quaranta gradi in ~0,86 s, un giro intero in ~1,14 s. La
sovraelongazione e' l'uno per cento, cioe' nessun rimbalzo percepibile: si
assesta prima **senza** diventare scattante, perche' e' salita solo la rigidita'
e non lo smorzamento.

`onDragStarted` e' il pezzo che `SalaGiro` non aveva mai avuto, ed e' meta' della
cura: prima un tocco a meta' del ritorno non fermava la molla, ci **correva
contro**, due scrittori sullo stesso valore da coroutine diverse.

La soglia dichiarata non e' solo velocita': senza, la molla resta viva a limare
centesimi di grado, e ogni fotogramma li' e' un ridisegno intero della scultura a
schermo fermo - la trappola #8 rientrata dalla porta di servizio.

#### Fase e tempo: due interpolazioni diverse, e il perche'

> **Fase → interpolazione geometrica. Tempo → interpolazione temporale.**

La fase e' un continuo vero che il codice buttava via: `salaPhaseOf` sceglieva
una delle quattro caselle con soglie nette. Alle cinque e mezza il cielo **e'**
mezza alba, e deve sembrarlo **anche stando fermi li'** - col cursore delle ore
ci si puo' parcheggiare. Una molla non basterebbe: mostrerebbe i valori di mezzo
solo mentre passa. Quindi `faseContinua(sky)` restituisce le due fasi adiacenti e
quanto si e' fra loro, e `washColors` mescola i **risultati** delle due tabelle -
che restano intatte, sono il porting uno a uno del prototipo tarato a mano.

Il tempo non ha un continuo: non esiste un codice WMO "sessanta per cento
piovoso". L'unica cosa che deve essere morbida e' il **cambio**, ed e' una molla
sul risultato.

**L'avvolgimento non ha cuciture, e non e' un'affermazione.** Le giunture sono
due. A mezzanotte entrambe le fasi sono NOTTE con avanzamento zero. Al
mezzogiorno solare il crepuscolo nominato passa da ALBA a TRAMONTO perche'
`evening` scavalca la meta' - ma `SunClock.eveningness` e'
`smoothstep(0,42 → 0,58)` sulla frazione di giornata e attraversa la meta' a
frazione 0,5, cioe' al mezzogiorno solare, dove l'altezza del sole vale 1,0. Li'
entrambi gli smoothstep sono saturi, l'avanzamento vale 1, e la miscela e' cento
per cento GIORNO **qualunque** crepuscolo sia nominato.

#### `paperDarkness` non si tocca, e il suo scalino nemmeno

Il salto da 0,21 a 0,62 non e' una scelta di resa: e' una **garanzia
sull'insieme degli stati raggiungibili**. Nessun valore di `dayness` porta la
carta nella fascia in cui ne' il nero del testo ne' il bianco reggono il fondo.
Appianarlo per renderlo continuo butterebbe via la garanzia - e con una barra
delle ore su cui si puo' **parcheggiare** alle cinque e mezza, "brevemente
illeggibile" diventerebbe "illeggibile finche' non ci si sposta".

La cura non e' appianare lo scalino, e' **attraversarlo nel tempo**: il bersaglio
della molla sta sempre fuori dalla fascia, quindi la fascia si attraversa e non
si abita mai. Criticamente smorzata, perche' un rimbalzo oltre 1 darebbe una
carta piu' scura di `paperDark`, che non e' un colore che esiste.

`SalaPalette.dark: Boolean` e' diventato `buio: Float` (con un `dark` derivato
per lo stile delle barre di sistema, che e' o chiaro o scuro e non ha vie di
mezzo da dichiarare). `darkVeil` e `lightVeil` sono una funzione sola: i due
pennelli differivano in tinta, **posizione delle fermate** e opacita', e tutte e
tre si interpolano pulite. `salaPalette` ha perso `sky`, `phase` e `theme` dalla
firma ed e' un assemblatore puro di numeri gia' animati - ed e' questo che rende
tutto il resto possibile senza toccare le sale.

#### La scultura ha smesso di ramificare

Sette scalari 0..1 - sole, copertura, tempesta, bagnato, ghiaccio, neve, notte -
al posto di `condition`, `nevica` e `notte`. Due arrivano gratis:
`SkyState.sunPresence` e `moonPresence` erano gia' continui, gia' smorzati a
monte in `MeteoApp`, e sono **gli stessi** che usano i widget. E la copertura
viene dal dato vero (`HourForecast.cloudCover`): il codice WMO da cui l'enum
nasce e' esso stesso derivato da quello, quindi leggerlo direttamente non e' una
scorciatoia, e' togliere un passaggio che buttava via precisione.

Tre cose vanno lette prima di toccare quel file.

**Come compare una massa di nuvola senza schioccare.** Nessuna delle due
risposte ovvie basta da sola: con la sola opacita' compare un fantasma **a
grandezza piena** al cinque per cento, che l'occhio legge come un errore di resa
e non come una cosa che arriva; con la sola scala compare un punto **pienamente
opaco** che si gonfia, cioe' uno schiocco con una rampa incollata davanti.
Servono entrambe, con l'alfa **al quadrato** perche' la massa resti tenue finche'
e' piccola. E l'ordinamento in profondita' si calcola su tutte e sette **sempre**:
se dipendesse dalla presenza, le masse si riordinerebbero mentre una entra.

**Pioggia, neve e grandine non si mescolano: si sovrappongono.** Sono tre segni
genuinamente diversi, e interpolare fra un tratto e un fiocco non da' niente.
Due cadute che condividono corsie e orologio e sfumano l'una nell'altra sono
esattamente com'e' fatto il nevischio, e non costano una riga di geometria nuova.

**Il sole e la luna sono lo stesso timbro con pesi complementari.** Non e' solo
piu' morbido del salto: e' un'immagine migliore. Il disco si **raffredda** dal
giallo al grigio mentre il terminatore lo morde, che e' quello che fa il
crepuscolo. Un `if (notte)` non puo' dirlo.

#### Una collisione sottile, fra i valori animati e l'orologio

`rememberTempoScena` riparte da zero a ogni riaccensione. Se la condizione di
accensione si ricalcolasse dai valori **animati**, si ribalterebbe a meta'
transizione: gli uccelli si congelerebbero a mezz'aria prima di svanire, e la
pioggia risalirebbe in cima mentre sfuma. La regola, scritta anche nel codice:
**i valori animati possono solo allungare l'orologio, mai accorciarlo**. Il
bersaglio dice se ci sara' qualcosa da muovere; i valori animati lo tengono
acceso finche' un passaggio e' in volo. E gia' che e' diventato portante,
l'orologio adesso **accumula** invece di azzerarsi.

#### La luna: prendeva in prestito l'inchiostro del testo

Due difetti distinti, stessa causa. In Sala IV si passava `light = palette.ink`,
che in tema chiaro e' `#201E1D`: la parte **illuminata** veniva dipinta quasi
nera. E `dark = lerp(ink, ground, 0.65)` disegnato a un quarto di opacita' era un
disco in ombra invisibile sulla carta `#F3F2F2` - cio' che si vedeva nello scatto
non era la luna, era la sola vignettatura del lembo, sotto una scritta che
diceva "Novilunio · 0 % illuminata". In Sala I `tintaSole(notte = true)` valeva
`neutral200 #EAE7E7`, due per cento di stacco dalla carta.

In tema scuro funzionava **per combinazione**, perche' li' l'inchiostro e' quasi
bianco e i ruoli tornavano da soli. E' il modo piu' insidioso in cui una cosa
puo' sembrare giusta.

Adesso `SalaTokens.lunaLuce` (avorio caldo) e `lunaOmbra` (ardesia), **fisse nei
due temi**, piu' un filo di contorno perche' la sfera si stacchi dalla carta
chiara: un corpo celeste non ha il colore dell'inchiostro della pagina che lo
mostra.

#### La barra delle ore era un grafico travestito da comando

Riconosceva **solo** `detectHorizontalDragGestures`: un tocco secco non faceva
niente. Chi ci provava non otteneva risposta, e da un comando che non risponde si
impara che non e' un comando. L'ora scelta non era scritta sulla barra, e il
colore era lo stesso grigio dal primo minuto all'ultimo.

`SalaBarraOre.kt`: binario colorato **ora per ora** col tempo di quell'ora - la
forma della giornata si legge senza toccare niente - maniglia visibile, ora
scritta **sopra la maniglia** e che viaggia con lei, tocco secco oltre al
trascinamento, colpetto di vibrazione a ogni ora attraversata. La curva della
temperatura resta dietro, in sordina: era l'unica cosa buona di prima.

Ogni valore letto dentro i riconoscitori passa da `rememberUpdatedState`. E' la
trappola #7, e il progetto l'ha gia' pagata **su questa stessa barra**.

#### Le vibrazioni, e perche' hanno un tetto

Il permesso `VIBRATE` stava nel manifesto da sempre, col commento "la pioggia si
sente in mano", e non lo usava nessuno da quando `WeatherHaptics.kt` e' uscito
col feed.

Gli istanti d'impatto **non li decide** `VibrazioniMeteo.kt`: li calcola
`Corsie`, la stessa che disegna le gocce, con la stessa formula - una corsia
tocca quando la sua corsa scavalca un intero. Un contatore suo andrebbe in fase
per un po' e poi scivolerebbe, e una vibrazione fuori tempo rispetto a cio' che
si vede e' peggio di nessuna vibrazione. Non si vibra dal disegno: un `DrawScope`
puo' essere invocato piu' volte per fotogramma, o nessuna.

**Il tetto sugli impatti al secondo non tradisce la richiesta.** Con la pioggia
fitta le corsie toccano decine di volte al secondo: farle sentire tutte non da'
"la pioggia in mano", da' un ronzio continuo, che in mano si legge come un guasto
del telefono. Il tetto si allenta col crescere della pioggia - due colpi al
secondo per una pioviggine, sei per un rovescio - cosi' e' il **ritmo** a dire
quanto piove.

#### L'eccezione dichiarata, e la sua via d'uscita

Sala I adesso si muove **sempre** mentre la si guarda: stelle di notte (anche
coperta - le nuvole non spengono le stelle, le coprono), uccelli e pulviscolo di
giorno, cio' che cade quando cade. E' un'eccezione alla regola dei zero
fotogrammi a schermo fermo, ed e' la seconda che il progetto si concede dopo la
via della vecchia scheda della pioggia.

Dichiararla senza lasciare una via d'uscita sarebbe stato dichiararla a meta':
`SettingsPrefs.animazioniRidotte` la spegne, e con lei le vibrazioni. L'orologio
resta legato a `inVista`, quindi sotto le altre sei sale non gira comunque.

#### `animazioniIstantanee`: perche' gli scatti non possono animare

Gli extra `--ei` si applicano in `onCreate`, prima della composizione, quindi non
animano. **Ma la previsione arriva dopo il primo fotogramma** e muove altezza del
sole, nuvolosita' e condizione. Finche' Sala quantizzava tutto in enum questo era
innocuo: erano gia' definitivi allo scatto. Con le molle sarebbero **in volo**,
con un solo secondo di margine nello script - la carta si assesta in circa sei
decimi, i colori in quattro, la scena in cinque. Dentro il secondo, ma non di
molto, e "non di molto" e' esattamente come gli ultimi due guasti della cattura
sono rimasti invisibili per due giri interi.

Quindi `MainActivity` dichiara la cattura e le molle diventano `snap()`. Un campo,
scatti riproducibili byte per byte, e la galleria smette di dipendere da un
`sleep`.

#### `scripts/import_audit.py`

Qui non c'e' l'SDK: la CI e' il compilatore, e due giri rossi di questo progetto
sono stati import morti e import mancanti - cose che **non si vedono
rileggendo**. Lo script ha trovato tre funzioni Compose senza import in un file
appena scritto, che sarebbero state tre minuti di CI ciascuna.

Due note su come e' fatto, perche' il primo tentativo era sbagliato in un modo
istruttivo. Le stringhe si scansionano **a mano e non con una espressione
regolare**: in Kotlin dentro `${...}` ci sta del codice, e dentro quello
un'altra stringa; una regolare si ferma alla prima virgoletta, e il primo
tentativo dichiarava morto un `roundToInt` che il file chiamava due volte -
cioe' il controllo produceva proprio il guasto che esiste per evitare. E i tipi
non bastano: un tipo mancante si nota rileggendo, una `animateFloatAsState`
senza import no, perche' somiglia a tutto il resto del file.

Resta un buco noto: le estensioni di `Modifier` si chiamano con un punto davanti
e passano per membri, quindi un `Modifier.offset` senza import non viene
segnalato.

#### Cosa non e' stato provato

Come ogni passata scritta da questo container: **niente SDK Android qui**, quindi
ne' `lintDebug` ne' `assembleDebug` sono partiti. Le molle, il tatto del giro, il
ritmo delle vibrazioni e il costo per fotogramma vanno presi in mano - e
l'emulatore della CI rende via software, quindi di li' non esce nessun giudizio
sul costo per fotogramma. Cio' che la CI puo' dire resta binario e resta utile:
se compila, se i fotogrammi si fermano, e cosa si vede negli scatti.

### 12-quater. I comandi che non comandavano niente

Passata di igiene dopo il merge in `main`. Il filo comune: **cinque comandi
nelle impostazioni che si accendevano, si spegnevano, si ricordavano fra un
avvio e l'altro, e non erano letti da nessuno.**

Non e' una svista da poco. Un interruttore che non comanda niente e' peggio di
un interruttore assente: chi lo prova e non vede cambiare nulla impara che i
comandi di quella schermata non contano, e da li' in poi non si fida nemmeno di
quelli veri, che stanno tutti nella stessa lista.

**Le quattro allerte.** «Pioggia intensa», «Temporali», «Raggi UV sopra 6» e
«Vento forte» adesso filtrano davvero — in `WeatherViewModel.permessa`, sulle
allerte **calcolate** e non su quelle ufficiali: un avviso della Protezione
Civile non lo si nasconde perche' un interruttore e' giu'.

Per farlo ho dovuto aggiungere l'avviso che mancava: `AlertKind.UV` non
esisteva, quindi l'interruttore dei raggi UV non aveva niente dietro da
accendere. La soglia e' quella scritta sull'interruttore, sei, che e' il punto
in cui la scala mondiale passa da moderato ad alto; `uvMax` era gia' fra i dati
chiesti a Open-Meteo.

**Le didascalie.** «Brevi» e «Complete» adesso decidono qualcosa: il **corpo**
del testo sparisce, titolo e riga dei dati restano. Chi chiede didascalie brevi
vuole meno parole, non meno informazione.

Passa da `LocalDidascalie` e non da sei parametri: il corpo lo scrivono sei sale
diverse, e infilare la preferenza in sei firme avrebbe voluto dire toccarle
tutte a ogni ripensamento. `compositionLocalOf` e **non**
`staticCompositionLocalOf` come per `LocalAcquerello`, perche' questo valore
cambia mentre l'app e' aperta, ed e' esattamente il caso per cui i due si
distinguono.

**Una nota su come non farlo.** La sostituzione delle sei didascalie l'ho
tentata con una espressione regolare su piu' righe, e ha agganciato un `Text(`
che stava **prima** di quello giusto, inghiottendo il blocco in mezzo. Quattro
file corrotti in silenzio, visti solo rileggendo. E' la seconda volta in questo
progetto che una sostituzione automatica su codice fa danni (la prima fu uno
script di rientro su `MeteoApp.kt`): su blocchi multilinea si risale
dall'ancora **verso l'alto** fino all'apertura, oppure si fa a mano.

**Codice morto tolto**: `fioccoDiNeve`, che nessuno chiamava dal giorno in cui e'
nato, e `Scena.Ferma`.

**`scripts/import_audit_baseline.txt` ritarato** su `main` verde: la taratura
vale solo finche' l'albero di riferimento compila, e va rifatta dopo ogni merge.
Nel giro di oggi lo script ha preso sei import mancanti di `Didascalia` prima
che partisse la CI — che e' precisamente il lavoro per cui esiste.

---

## 13. Organic: il cielo al posto della carta

Tredicesimo giro, e il piu' largo dopo Sala. Viene da un secondo passaggio di
Claude Design — chat *"App meteo a tema organico"*, direzione **Oggi-1c**
scelta dall'utente — consegnato come bundle di handoff con `Caelum.dc.html`,
i transcript e il design system **Organic** (`_ds/organic-*`).

**Non e' un restyling sopra Sala: e' il suo contrario.** Sala aveva sostituito
il cielo con una **carta** che scuriva a scatti, con tre macchie d'acquerello
sopra: il fondo era una pagina, e una pagina appartiene a chi la scrive, quindi
ogni sala si disegnava la propria. Qui il fondo **e' il tempo** — sfumatura del
cielo, arco del sole, nuvole, colline, cio' che cade — ed e' uno solo per tutte
e sette: scorrendo il carosello non ricomincia, **resta**, e i pannelli gli
passano davanti. Da questa sola frase discende tutto il resto della passata.

### Cosa e' salito nella Shell

`SalaShell.kt` non e' piu' solo un carosello. Dentro ci sono adesso il cielo
(`SalaCielo.kt`), l'intestazione con gli avvisi, la colonna delle scorciatoie,
la barra delle ore e i sette trattini del percorso. Le sale sono diventate
**pannelli**: ricevono una tavolozza gia' animata e disegnano il proprio
contenuto, senza sapere che esista una molla.

Ne segue la regola che vale da qui in avanti: **le animazioni vivono in un
posto solo**. Cambiare il modo in cui un colore passa non apre sette file.

### La tavolozza: `SalaTheme.kt`

I token Broadsheet — ciano, magenta, giallo di quadricromia — sono usciti
interi, sostituiti da quelli di Organic copiati dal suo `styles.css`: fondo
sabbia, inchiostro `#2e2b25`, accento **terracotta** `#b2622d` (pesca `#ffc6a5`
sul tema scuro), verde salvia come secondo accento. Il blu di pioggia, chicchi
e fiocchi **non e' un token di Organic**: e' un'aggiunta dichiarata, tenuta in
`SalaTokens` con un commento che dice che lo e', perche' non lo si scambi per
sistema.

I caratteri sono due, come nel sistema: **Caprasimo** per titoli e numeri
grandi, **Figtree** per tutto il resto — quattro pesi statici scaricati dalla
CSS2 API e verificati per firma SFNT (`\x00\x01\x00\x00`), non per resa. Source
Serif 4 e' uscito con la carta.

Le tabelle del cielo (`CieloNotte/Alba/Giorno/Tramonto`, cinque gradi di
chiusura per quattro fasi, piu' `CieloNeve`) sono il **porting uno a uno** di
`SKY` nel prototipo: tarate a mano colonna per colonna, e non vanno
"semplificate" in una formula.

**Due interpolazioni diverse, come prima e per lo stesso motivo.** La fase resta
continua (`faseContinua`): alle cinque e mezza il cielo *e'* mezza alba, e con
un cursore su cui ci si puo' parcheggiare deve sembrarlo anche stando fermi li'.
La chiusura del cielo invece **e' diventata continua adesso**: il prototipo ha
nove voci con `liv` intero, noi abbiamo la nuvolosita' oraria vera, e
`livelloCielo` la porta sulla stessa scala 0..4 senza buttarla in cinque
caselle. Il fronte del temporale resta uno scalino dichiarato: un temporale e'
un fronte, non una nuvolosita' piu' alta.

#### `temaScuro` ha cambiato guidatore, e **non** forma

Questo e' il punto da leggere prima di toccare quel file.

Lo scalino (da 0,21 a 0,62) **non si tocca**: non e' una scelta di resa, e' una
garanzia sull'insieme degli stati raggiungibili — nessun cielo porta
l'interfaccia nella fascia in cui ne' l'inchiostro scuro ne' quello chiaro
reggono il fondo. La cura non e' appianarlo, e' **attraversarlo nel tempo**: il
bersaglio della molla sta sempre fuori dalla fascia.

Cio' che e' cambiato e' **cosa lo guida**. Prima era `dayness`, che si accende
molto prima che il sole spunti: sulla carta andava bene. Il cielo dell'alba del
prototipo pero' e' un viola scuro con una fascia arancione, e li' l'inchiostro
scuro sparisce. Adesso guida quanto e' **giorno pieno** — lo stesso smoothstep
che decide la fase GIORNO — cosi' alba e tramonto stanno col tema scuro, come
dice `chiaroScuro` nel prototipo.

Accanto c'e' una seconda porta allo scuro: `temaScuroPerTempesta`. Sotto un
fronte il cielo e' plumbeo a ogni ora. **E' una soglia netta e non una rampa,
apposta**: un bersaglio a meta' strada cadrebbe dentro la fascia illeggibile che
lo scalino esiste per saltare.

### Il cielo: `SalaCielo.kt`

Tutto misurato nel sistema del prototipo, 411 x 914. Le **posizioni** si
riscalano sui due assi separatamente; i **diametri** seguono la sola larghezza,
perche' un disco riscalato su due assi diversi non e' piu' un disco.

- **Il sole cambia colore con l'altezza**, ora per ora: arancione bruciato
  all'orizzonte, giallo acceso allo zenit, con l'alone che passa da pesca a
  giallo chiaro. Era la prima delle richieste, ed e' la cosa che un `if (alba)`
  non sa dire.
- **Le nuvole sono rifatte da zero**: corpo a pillola col fondo piatto, tre
  gonfiori, un tocco di luce in alto, un'ombra sotto. Niente sfocatura. Le
  cinque masse entrano **in fila** al crescere della copertura, con scala **e**
  alfa al quadrato: con la sola opacita' compare un fantasma a grandezza piena,
  con la sola scala un punto pienamente opaco che si gonfia.
- **La luna e' la fase vera di stanotte**, la stessa di Sala IV e dei widget: il
  prototipo aveva una gibbosa al 74% scritta a mano. Le sue tinte sono fisse nei
  due temi — lezione gia' pagata: un corpo celeste non ha il colore
  dell'inchiostro della pagina che lo mostra.
- **Pioggia, grandine e neve non si mescolano, si sovrappongono**, e usano le
  corsie di `Corsie` — **le stesse che decidono le vibrazioni**. Un contatore
  suo andrebbe in fase per un po' e poi scivolerebbe.
- Le colline e i tre alberi sono la sola cosa ferma: senza, il sole e la pioggia
  galleggiano in un fondale.

### L'eccezione dei zero fotogrammi si e' allargata, ed e' dichiarata

Prima si muoveva la sola Sala I. Adesso il cielo sta dietro tutte e sette,
quindi quando si muove si muove **sempre**. E' il prezzo della direzione scelta:
un cielo fermo non e' un cielo, e' uno sfondo. `SettingsPrefs.animazioniRidotte`
la spegne, e con lei le vibrazioni; `animazioniIstantanee` la congela per gli
scatti della CI, che restano riproducibili byte per byte.

### Cosa il prototipo dice e l'app non ripete

Tre bugie del disegno non sono state portate, e vanno lasciate fuori:

1. **Sorge e cala della luna** ("17:12", "04:38"): Open-Meteo, nei dati che
   questa applicazione chiede, non li porta. Al loro posto tre numeri veri —
   illuminata, eta', prossima piena.
2. **I pollini** di Sala V: non stanno nell'endpoint base. Restano i quattro
   inquinanti veri.
3. **La frase che riassume la settimana** ("un fronte da ovest da mercoledi'"):
   nel prototipo e' scritta a mano e descrive una settimana inventata. Qui e'
   calcolata dai giorni veri — quanti bagnati, quanti millimetri, che escursione.

Stessa regola delle citazioni verificate: **una cosa inventata che sembra un
dato e' peggio di un dato mancante**, perche' e' piu' difficile da smentire.

### Le parole, e i commenti di Kris

"Sala" resta il nome **nel codice** e nel numero romano che serve alla galleria
della CI. **Non si scrive piu' a schermo**: chi guarda non chiama queste
schermate "sale", e non deve impararlo per usarle. Quindi l'intestazione non
dice piu' "SALA I / VII", il collegamento di Sala I dice "apri la settimana", e
il benvenuto si intitola "Il cielo, ora per ora" con il pulsante "Trovami".

Al posto della fase del giorno — "GIORNO", "NOTTE", cioe' l'unica cosa che
chiunque sa gia' guardando fuori — l'intestazione mostra gli **avvisi**, filtrati
sull'ora scorsa sulla barra. "Allerta" resta una parola che spetta a un ente: un
avviso calcolato sulle soglie dice "avviso".

Le **localita'** sono uscite dalla colonna sul fianco e sono entrate nelle
impostazioni: cambiare citta' non e' spostarsi fra le schermate del tempo, e una
fila di sette icone piu' due intruse non e' piu' una fila.

### Codice uscito, e dove ritrovarlo

Cancellati perche' il cielo nuovo li sostituisce per intero — restano nella
cronologia (`git show`), come il feed prima di loro:

| File | Cosa era |
| --- | --- |
| `ui/sala/SalaScaffold.kt` | cornice, intestazione "SALA N / VII", indicatore |
| `ui/sala/SalaBackground.kt` | carta, macchie d'acquerello, velo |
| `ui/sala/SalaAcquerello.kt` | grana della carta, timbri, **la scultura** |
| `ui/sala/SalaGlyph.kt` | i glifi del tempo della vecchia striscia |
| `ui/sala/SalaGiro.kt` | la molla del giro: girava la scultura, che non c'e' piu' |
| `ui/welcome/Citazioni.kt` | le tre frasi d'artista verificate una per una |

Da `SalaVita.kt` sono usciti `caduta`, `uccelli`, `pulviscolo`, `riverbero` e
`fulmine`: li chiamava solo la scultura. Restano — e sono il pezzo che il cielo
nuovo riusa — `rememberTempoScena`, `cieloStellato`, `Corsie`, `Caduta`,
`forzaLampo` e `inMezzo`. `Scena` e `scenaBersaglio` sono passati in
`SalaScena.kt`, che e' cio' che erano gia': un modello, non un disegno.

**`Citazioni.kt` merita una riga a parte.** Le tre frasi erano verificate contro
la fonte primaria, una per una, e due attribuzioni diffuse erano state escluse
apposta perche' non si risaliva a un originale. E' uscito lo stesso: una
citazione in apertura chiede di **leggere** prima di guardare, e quella
schermata esiste per il contrario. Chi volesse rimetterla altrove trova il
lavoro gia' fatto in `git show`.

### Cosa non e' stato provato, e non poteva esserlo

Stessa nota di ogni passata scritta da questo container: **niente SDK Android
qui**, quindi ne' `lintDebug` ne' `assembleDebug` sono partiti. Tutto cio' che e'
Compose lo compila **solo** la CI. `scripts/import_audit.py` e' passato pulito
sull'intero albero (restano quattro segnalazioni che sono il buco noto dello
script: chiamate a membri che sembrano estensioni).

Da guardare per primi, in mano, appena la CI e' verde:

- che le colline e i tre alberi cadano dove devono su un telefono che non sia
  411 x 914 — le posizioni si riscalano sui due assi, e li' e' dove si vedra';
- il costo per fotogramma del cielo **mentre si scorre il carosello**: adesso
  disegna sempre, sotto tutte e sette le schermate;
- che la barra delle ore non rubi il gesto al carosello verticale (trappola #5,
  di nuovo: e' lo stesso punto esposto che il feed aveva gia' pagato);
- che il tema scuro col nuovo guidatore regga davvero all'alba e al tramonto,
  che sono le due ore in cui e' cambiato;
- se le due icone PNG dell'utente (aria, vento) si leggono a 19-20 punti una
  volta tinte dal tema.

### Gli errori di compilazione della CI si leggono, e non dai log

La sezione 12 diceva che i log grezzi dei giri di GitHub non sono raggiungibili
da questo container, ed e' vero: li ospita `productionresultssa*.blob.core.windows.net`,
che la policy di rete della sessione nega al CONNECT. Se ne era dedotto che da
qui non si potesse sapere **perche'** un giro e' rosso, e per un giro intero e'
costato aspettare senza poter fare niente.

Non e' cosi'. Le **annotazioni** del check stanno sull'API di GitHub, che invece
risponde, e contengono le righe `e:` del compilatore Kotlin per intero - file,
riga, colonna e messaggio:

```bash
curl -s "https://api.github.com/repos/NoximilienCoxen/test-weather/actions/runs/<RUN>/jobs"
curl -s "https://api.github.com/repos/NoximilienCoxen/test-weather/check-runs/<JOB_ID>/annotations"
```

C'e' anche una via piu' corta, e la CI la scriveva gia' senza che nessuno la
leggesse: il job salva le righe `e:` su `ci-artifacts`, in
`compilazione/errori.txt`, **tutte** e non solo le ultime.

```bash
git fetch origin ci-artifacts
git show FETCH_HEAD:compilazione/errori.txt
git show FETCH_HEAD:compilazione/INFO.txt   # a quale commit e run appartengono
```

`INFO.txt` va guardato sempre: quel file resta fermo all'ultimo giro che ha
**fallito**, quindi dopo un giro verde e' vecchio, e preso per buono manda a
correggere errori gia' corretti.

Un avvertimento sulla forma delle annotazioni: **sono poche e sono le
ultime**. Un giro con venti errori ne mostra sei, quindi un secondo giro rosso
subito dopo il primo non vuol dire che la correzione non e' servita - vuol dire
che sotto ce n'erano altri. Conviene quindi **non** limitarsi a correggere cio'
che l'annotazione dice: il primo giro di questa passata ha segnalato solo
`AlertToggles.uv`/`.vento` (i campi veri sono `uvAlto` e `ventoForte`), e a
trovare il resto ci sono voluti due controlli scritti per l'occasione - i nomi
dei membri contro le dichiarazioni vere, e i nomi dei parametri contro le firme
vere. Senza SDK, quella e' la compilazione che ci si puo' permettere qui.

### 13-bis. Due difetti che solo il telefono poteva mostrare

Il ridisegno era verde in CI, passava i controlli offline e gli scatti erano
belli. Provato in mano, non si muoveva niente e un comando non portava da
nessuna parte. Vale la pena scrivere **perche'** nessuno dei tre banchi di prova
poteva dirlo.

#### `scattoFermo()` era incondizionato, e teneva ferma l'app intera

`applyExtras` in `MainActivity` chiamava `viewModel.scattoFermo()` senza
guardare se un aggancio fosse davvero arrivato. `AGGANCI_CATTURA` e' acceso su
ogni build di **debug**, e un avvio dall'icona porta un Intent non nullo con zero
extra: le due uscite anticipate passavano, il flag si accendeva, e non esiste il
setter inverso. Da li' restavano fermi il cielo, le vibrazioni e - per via di
`SalaMolle.ferma` - **ogni** molla dell'app.

Il difetto c'era da quando il flag e' nato, e il commento di `scattoFermo`
dichiarava gia' il contratto giusto: *"quando un qualsiasi aggancio e' stato
applicato"*. **Una nota che descrive cio' che il codice dovrebbe fare non prova
che lo faccia**, ed e' la seconda volta che questo progetto ci inciampa (la
prima e' nella sezione 12-bis, il paragrafo sul giro che "era un `Animatable`").

Non si vedeva perche' l'orologio della vecchia Sala I non era legato a quel
flag. Col cielo nuovo dietro tutte e sette le schermate, lo stesso difetto e'
diventato l'app intera - e **gli scatti non potevano mostrarlo**, perche' uno
scatto e' fermo per definizione. E' il complemento della lezione della sezione
12: li' un referto che varia da solo non prova niente; qui un referto **fermo**
non distingue un'app ferma da un'app che sta posando.

La cattura adesso **si dichiara**, con `--ez cattura true`. Dedurla dalla
presenza di un altro aggancio non bastava: `capture.sh` avvia l'app anche senza
alcun extra, e quello scatto sarebbe tornato a dipendere da una `sleep`. In
`capture.sh` tutti e tredici gli avvii passano dall'helper `avvia`, che
l'aggancio lo mette da se': **l'helper esiste perche' non si possa
dimenticare**, non per accorciare le righe.

E la riga di log degli agganci adesso stampa anche `cattura=`. E' l'unico modo
di sapere, dal `logcat-streaming.txt` che finisce fra gli artefatti, se un avvio
ha ricevuto il suo aggancio: uno saltato non si vedrebbe altrimenti - lo scatto
uscirebbe soltanto *un po'* diverso, ed e' esattamente cosi' che gli ultimi due
guasti della cattura sono rimasti invisibili per due giri interi.

```bash
git show FETCH_HEAD:screenshots/logcat-streaming.txt | grep agganci:   # tutti cattura=true
```

**Da non provare a verificare con gli scatti.** Confrontare l'impronta di uno
scatto a ora e meteo imposti fra due giri sembra la prova che la cattura e'
ancora congelata, e non lo e': la previsione e' vera e cambia fra un giro e
l'altro, quindi quelle immagini differiscono comunque. La prova sta nel log.

#### "Le località" si apriva sotto le impostazioni

I due pannelli a tutto schermo sono fratelli nello stesso `Box` di `SalaShell`,
e le impostazioni erano composte per ultime: stavano sopra e si prendevano i
tocchi. Toccando la riga, la lista si apriva **sotto** un pannello opaco e non
cambiava niente a schermo; il primo indietro chiudeva le impostazioni e scopriva
la lista che era li' da prima.

Adesso le localita' sono composte **dopo**. Con quello solo il giro diventa
quello giusto - Impostazioni → Le località → indietro → Impostazioni → indietro →
il cielo - senza toccare lo stato.

**Va fatto con l'ordine e non con uno `zIndex`**, ed e' il punto da non perdere:
`BackHandler` da' la precedenza all'**ultimo registrato**, cioe' all'ordine di
composizione, non all'impilamento. Con `zIndex` l'aspetto sarebbe giusto e
l'indietro chiuderebbe ancora le impostazioni per prime, lasciando la lista
orfana a schermo. Chi riordina quei due blocchi per pulizia riapre il difetto:
il commento accanto lo dice.

Nello stesso giro, `choosePlace` chiude la lista - sceglierla e restarci era un
comando che sembrava non aver fatto niente - e `closeLocations` si porta via la
ricerca come fa gia' `closeSettings`.

#### E una terza, che il primo giro verde ha fatto uscire

Sistemato l'ordine dei pannelli, lo scatto `00-impostazioni.png` ha cominciato a
ritrarre **le localita'**. Non era una conseguenza della correzione: lo faceva
gia' da tre giri. Quel tocco segue il rimando in fondo al benvenuto, che quando
la riga fu scritta apriva le impostazioni e col redisegno e' passato ad aprire
la lista delle citta' (`MeteoApp.onChooseByHand`). Il nome del file e' rimasto
indietro, e la galleria ha avuto due schermate di servizio fotografandone una
sola - dicendo di averle tutte e due.

E' la **terza** volta che questo progetto trova il banco di prova che dice di
si': prima gli agganci muti sotto `BuildConfig.DEBUG`, poi la galleria che
ritraeva l'Ingresso in ogni scatto, adesso un nome di file che prometteva una
schermata mai presa. La lezione e' sempre la stessa e conviene riscriverla:
**uno scatto va confrontato con cio' che dovrebbe mostrare di diverso**, e un
nome di file non e' una prova di niente.

Adesso `00-localita` si chiama come cio' che mostra, e le impostazioni hanno uno
scatto loro, preso dal comando a due cursori in alto a sinistra.

#### Cosa resta vero

La vibrazione si sente **solo quando cade qualcosa** (`scena.bagnato`): col
sereno il cielo si muove e non c'e' niente da sentire. E l'interruttore
"Animazioni ridotte" spegne di proposito tutte e due.


### 13-ter. Il giorno scelto si fermava a Sala I

Toccando giovedi' nella striscia, il cielo e la prima schermata passavano a
giovedi' e **tutto il resto restava a oggi**: la pioggia, il vento, i raggi UV e
la luna. `UiState` aveva gia' la risposta - `detailHour`, `detailDay` e
`shownHours`, quest'ultima con scritto accanto "le ore del giorno mostrato:
quelle vere, non quelle di oggi" - e le tre sale orarie leggevano `hours`, che
e' oggi. Un dato vero, messo dove non e' vero.

Adesso leggono `shownHours`, e la fase lunare si calcola **una volta sola nella
Shell** dal giorno mostrato: la usano il cielo, Sala IV, la cella di Sala I e il
riquadro di Sala II. Quattro letture della stessa data divergono al primo che ne
aggiusta una; una sola non puo'.

**Il caso vuoto si dichiara.** `shownHours` torna vuota oltre le ~72 ore: i
modelli a corto raggio danno i totali del giorno e non le sue ore. Le tre sale
scrivono una riga ("per questo giorno la previsione da' i totali, non le ore")
al posto del grafico, e la barra delle ore mostra il binario spento e non si
lascia trascinare - **non poter scorrere le ore di un giorno che non ha ore e'
la risposta giusta**. Qui c'era un `ifEmpty { state.hours }`, scritto da me col
ridisegno, che dipingeva oggi sotto l'intestazione di un altro giorno: e' la
stessa bugia dello scatto chiamato `00-impostazioni` che ritraeva le localita',
in un altro punto.


### 13-quater. Un cielo che si muove, e che risponde

"L'app sembra morta." Detto col telefono in mano, subito dopo che
`animazioniIstantanee` ha smesso di tenerla congelata: appena il cielo ha
cominciato a muoversi si e' visto **quanto poco** si muoveva. Sole e luna erano
due dischi con un alone, le stelle puntini da un pixel e mezzo, una cadente ogni
dodici secondi, e niente rispondeva al dito.

**Il sole.** Tre strati di bagliore invece di uno - uno solo finisce di colpo e
si legge come un bollo con un contorno sfocato - e una **corona di sedici raggi
che gira**, novanta secondi per tornare al punto di partenza. E' lei a dare il
movimento continuo: il respiro da solo e' una pulsazione, si nota per un minuto
e poi non piu'. I raggi sono alternati lunghi e corti e ognuno palpita per conto
suo, se no la corona si legge come un ingranaggio. Disco e alone respirano
**sfasati**: all'unisono sembrerebbero un oggetto solo che cambia taglia.

**La luna** aveva un difetto vero: la parte in ombra era quasi nera, e al
novilunio spariva come se qualcuno l'avesse spenta. Adesso c'e' la **luce
cinerea** - quel disco fantasma dentro la falce, che e' Terra che la illumina e a
occhio nudo si vede eccome - piu' un alone a due strati e tre scintille lente che
le girano attorno.

**Le stelle** sono passate da 72 a 160, e una decina sono **luminose**: raggio
doppio, un bagliore attorno e una croce di scintillio che pulsa. Un cielo di
puntini tutti uguali e' una trama; sono le poche grandi a dare la scala a tutte
le altre. Il tremolio adesso ha **un periodo per stella** invece di uno solo: con
un periodo comune il cielo lampeggia, e un lampeggio sincronizzato si legge come
un difetto dello schermo.

**Le cadenti** sono due tracce con cadenze prime fra loro, 4,3 e 6,7 secondi:
non tornano mai in fase, quindi a volte se ne vedono due insieme e a volte
nessuna - che e' come cadono davvero. La scia sfuma invece di essere una riga
piena, che era un graffio sul vetro.

**E la vita che avevo tolto e' tornata.** `uccelli` e `pulviscolo` erano usciti
col vecchio feed perche' li chiamava solo la scultura; il cielo nuovo aveva il
difetto opposto - di giorno, sereno, non si muoveva niente. Ripresi da
`git show 8fff850f^` **identici**, perche' funzionavano: riusare batte
riscrivere, e le note che portano dietro erano gia' state pagate.

#### Le tre interazioni, e perche' sono tutte tocchi

Il verticale e' del carosello e l'orizzontale della barra delle ore: la
trappola #5 nasce da due gesti che si contendono il dito, e un trascinamento sul
cielo l'avrebbe riaperta. Quindi **solo tocchi**, che non contendono niente.

1. **Inclinare** - parallasse. `rememberDeviceTilt` era in `ui/motion/` e non lo
   chiamava piu' nessuno dal giorno in cui il mappamondo del benvenuto e'
   uscito: torna da -1 a 1 per asse, gia' smorzato, con la linea di base che
   insegue la posa (quindi non deriva). Ogni piano ha il suo fattore - stelle
   0,12, sole 0,34, uccelli 0,52, nuvole 0,78, **colline zero** - perche' e' la
   differenza fra i piani a dire che c'e' spazio in mezzo. Le colline non si
   muovono: sono terra, e il mondo non si stacca dai piedi.
2. **Toccare il cielo** - un'increspatura che si allarga e svanisce in poco piu'
   di un secondo. Due anelli, uno largo e tenue e uno netto: un cerchio solo che
   cresce si legge come un bersaglio, due come un'onda. Ne vivono al massimo
   quattro, e le spente si potano **prima** di aggiungerne una - una lista che
   cresce a ogni tocco e non cala e' una perdita lenta.
3. **Toccare il disco** - divampa, e si sente (`vibrazioni.scatto()`, la stessa
   della barra). Il bersaglio e' il cerchio del sole allargato di tre quinti:
   stretto quanto il disegno sarebbe un tiro al bersaglio.

Tutto questo vive **dentro `SalaCielo`** e non nella Shell: sono cose del cielo,
nessun'altra schermata le usa, e tenerle li' vuol dire che la Shell non sa
nemmeno che esistano. `interattivo` le spegne tutte - con le animazioni ridotte
un accelerometro acceso e' un costo che chi ha chiesto meno movimento non si
aspetta, e la cattura vuole scatti ripetibili.

#### Un controllo in piu', dopo un errore che nessun controllo prendeva

Rimettendo gli uccelli, `private class Uccello` e' finita **dichiarata due
volte**: la potatura di allora aveva tolto la funzione e la lista ma non la
classe, e il mio controllo delle graffe non se ne accorge - un doppione e'
perfettamente bilanciato. Ai controlli offline si aggiunge quindi il conto delle
dichiarazioni per file: se un nome compare due volte a livello di file, lo dice.
Senza SDK questa e' la compilazione che ci si puo' permettere, e va allargata
ogni volta che lascia passare qualcosa.


### 13-quinquies. Il radar: per ora si chiede, non si scrive

Il radar e' stato chiesto nominando la fonte: **Radar-DPC del Dipartimento
della Protezione Civile**, dati in licenza CC BY-SA 4.0. Da questo container
`radar-api.protezionecivile.it` non si raggiunge - il proxy della sessione nega
il CONNECT, come per `dl.google.com` - ma la CI si', ed e' li' che si chiede.

Il passo **"Si puo' avere il radar delle precipitazioni"** esisteva gia' e aveva
gia' provato tre strade: il nowcast `minutely_15` di Open-Meteo (globale, senza
mappa), l'indice di **RainViewer** con una tessera vera scaricata, e il fondo
cartografico di OpenStreetMap. Le risposte stanno su `ci-artifacts` in
`api/radar.txt`. Adesso c'e' anche la sezione DPC, che prova i candidati e
scrive cio' che rispondono - **404 compresi**, perche' un 404 registrato e' un
indirizzo escluso e un indirizzo escluso e' informazione.

La sonda chiede due cose oltre al "risponde":

- **che forma ha il prodotto** - una data? un PNG? un indirizzo di tessere?
  quali estremi geografici? - perche' da quella dipende se il radar e' una
  tessera su una mappa scorrevole o un'immagine sola da posare, che sono due
  lavori di taglia molto diversa;
- **se vuole un'intestazione** `Origin`/`Referer`: un servizio nato per il
  proprio sito puo' rifiutare chi non si dichiara, e provare con e senza lo dice
  in una riga.

Due cose da tenere presenti quando si scrivera' il codice, e sono scritte qui
perche' decidono la forma della schermata:

1. **Il DPC copre l'Italia.** Questa app apre Tokyo e Nairobi - la sonda le
   interroga apposta. Fuori copertura il radar non e' vuoto: e' **assente**, e
   sono due cose diverse. L'app ha gia' questa distinzione per MeteoAlarm
   (`alertsOutOfCoverage`), col suo commento: *"un silenzio non e' una risposta
   rassicurante: e' un silenzio"*. Se serva un ripiego fuori Italia - RainViewer
   e' gia' sondato - si decide coi dati in mano, non adesso.
2. **CC BY-SA 4.0 vuole l'attribuzione a schermo**, sotto la mappa, non in un
   elenco di licenze che nessuno apre: "Dati radar: Dipartimento della
   Protezione Civile".

Quando si fara', il radar va **dentro Sala III "La pioggia"** - mappa sopra,
barre delle dodici ore sotto - cosi' la colonna resta di sette icone e non
nasce una schermata per un dato che parla della stessa cosa.


### 13-sexies. Un giro rosso che non era del codice

Il 17 settembre 2026 `test` e `build` sono caduti insieme, e la diagnosi e'
valsa piu' della correzione: **`compilazione/errori.txt` era vuoto, le
annotazioni non avevano nessuna riga `failure`, e non esisteva nessun
`/tmp/build.log`**. Tre assenze che insieme dicono una cosa sola - non si e'
arrivati a compilare.

L'elenco dei passi lo conferma in un colpo d'occhio, e si chiede all'API senza
toccare i log grezzi (che restano negati da qui):

```bash
curl -s ".../actions/runs/<RUN>/jobs" | python3 -c "import json,sys;
[print(j['name'], s['number'], s['name'], s['conclusion'])
 for j in json.load(sys.stdin)['jobs'] for s in j['steps']
 if s['conclusion'] not in ('success','skipped')]"
```

Cadeva il passo 5, `android-actions/setup-android`, in tutti e due i job.

#### La prima diagnosi era plausibile ed era sbagliata

> Questo paragrafo diceva: *"GitHub ha cominciato a forzare su Node 24 le action
> che dichiarano Node 20, e `setup-android@v3` dichiara `using: node20`; la v4
> dichiara `node24`"*. Il ragionamento tornava, l'avviso di deprecazione c'era
> davvero nelle annotazioni, e la v4 e' stata spinta con quella spiegazione
> scritta accanto. **E' caduta identica, allo stesso passo.**

L'avviso su Node 20 stava li' **per caso**: compare in ogni giro, riguarda
quattro action su cinque e non c'entrava niente. Averlo preso per la causa e' lo
stesso errore di metodo gia' pagato due volte deducendo dai pixel - due
correzioni che non correggevano il sintomo, e la risposta arrivata solo quando
si e' smesso di dedurre. Qui non si e' potuto nemmeno smettere: **il motivo vero
di quel fallimento non e' mai stato leggibile da qui**, perche' l'unico posto in
cui e' scritto e' il log grezzo, e quell'host la politica di uscita lo blocca.

#### Cosa si e' fatto invece

Di fronte a una dipendenza che fallisce **e non sa dire perche'**, cambiarle
versione una terza volta sarebbe stata la terza ipotesi non verificata di fila.
E' uscita: l'immagine `ubuntu-latest` porta gia' l'SDK Android, e adesso il
passo lo dichiara, lo mette nel PATH e - se non lo trovasse - lo dice con un
`::error::`, che si legge dall'API anche quando i log non si leggono.

La regola che resta, ed e' la piu' cara di questo file: **un giro rosso subito
dopo il proprio push si legge come "ho rotto qualcosa", ed e' la lettura
sbagliata piu' facile da fare.** Si guarda **quale passo** e' caduto prima di
guardare cosa si e' scritto. Un errore di compilazione lascia righe `e:`; quando
non ce n'e' nemmeno una, il guasto sta altrove - e se la causa non e' leggibile,
si toglie di mezzo cio' che non sa spiegarsi invece di tirare a indovinare.


### 13-septies. Il DPC risponde 403 a tutto

La sonda ha chiesto, e la risposta e' netta: **nove indirizzi su nove,
`HTTP 403 Access Denied`**, in HTML e non in JSON. Con e senza
`Origin`/`Referer`, su tutti i tipi di prodotto (VMI, SRI, SRT1, SRT3), sulla
radice del servizio e sulla catena `getProduct`/`existsProduct`.

Non e' un indirizzo sbagliato: un indirizzo sbagliato risponde 404. Un 403 su
tutto, radice compresa, e' un servizio che **rifiuta il chiamante**, non la
richiesta - un runner di GitHub Actions e' un indirizzo di datacentro, e
quel servizio e' nato per il proprio sito.

Nota su come leggere quel file: la riga `data dell'ultimo prodotto: 1789624371`
non e' un dato vero. E' il `grep` della sonda che ha pescato una cifra dentro la
pagina d'errore: quando il corpo non e' quello che ci si aspetta, anche
l'estrazione che ne segue non lo e'. Il `getProduct` che viene dopo infatti
risponde 403 come tutti gli altri.

**Cosa resta possibile**, e sta gia' scritto nello stesso file due sezioni piu'
su: RainViewer risponde `HTTP 200`, pubblica un indice di tredici fotogrammi e
una tessera vera si scarica (2424 byte, `image/png`); il nowcast `minutely_15`
di Open-Meteo risponde per Forli', Tokyo e Nairobi. Sono due strade diverse -
una mappa vera, oppure la pioggia dei prossimi minuti senza mappa - e nessuna
delle due e' la fonte chiesta.

La decisione non e' tecnica e non spetta a questo file: **il radar ufficiale
italiano, da qui, non si puo' avere**. Chi lo vuole lo dica, e si scegliera' fra
il ripiego e il niente. Quello che non si fara' e' spacciare RainViewer per
Radar-DPC: sono due fonti con due licenze e due attribuzioni diverse, e
scriverne una col nome dell'altra e' la stessa bugia della citazione attribuita
a chi non l'ha detta.

### 13-octies. Il primo giro verde, e una didascalia smentita dal cielo

Run `35188522523`: **verde in tutto** - `test`, `probe-api`, `build`,
`screenshots` passati, `rilascio` saltato come deve su un ramo che non e'
`main`. E' la prima volta che il ridisegno Organic compila: i tre giri
precedenti erano caduti al passo dell'SDK, prima ancora di arrivare al
codice, e quindi non avevano mai detto niente sul codice.

Gli scatti confermano quello che gli scatti possono confermare:

- `cielo-mezzogiorno-sereno.png` - il sole ha la corona a raggi visibile e i
  tre strati di alone, l'arco tratteggiato passa dietro, colline e alberi
  stanno al loro posto, la temperatura in Caprasimo tiene la scala.
- `scuro-2-notte-luna.png` - la luna ha il bordo illuminato e il disco in
  luce cinerea, le stelle luminose hanno la croce di scintillio, le nuvole
  **coprono** la luna senza spegnerla.

Quello che gli scatti **non** possono confermare resta quello gia' scritto in
13-quater: in cattura il cielo e' fermo per costruzione, quindi corona che
gira, increspature al tocco, parallasse e fiammata si vedono solo col telefono
in mano.

**Il difetto che invece hanno mostrato** e' di parole, non di disegno. Sotto il
titolo *Notte coperta, poche stelle*, alle 02:00, il corpo diceva `Nubi medie
che coprono il sole a intervalli`. I titoli erano gia' per condizione **x
fase** - e c'e' un commento, qualche riga sopra, che spiega perche' il titolo
notturno era stato riscritto - ma i corpi erano per sola condizione, e nessuno
aveva riletto quello nuvoloso di notte.

La correzione non ribalta la tabella: i corpi si somigliano davvero fra le
fasi, ed elencarli tutti e quattro per sei condizioni avrebbe voluto dire
ventiquattro stringhe di cui venti identiche. Resta la tabella per condizione,
e accanto `SalaBodiesPerFase` elenca le poche caselle in cui la fase cambia le
parole - oggi una sola. `salaBody` prende ora anche la fase e consulta prima
l'eccezione.

## 14. Il radar, scritto senza aver mai visto una risposta

Il radar chiesto e' quello ufficiale italiano: **Dipartimento della Protezione
Civile**, `radar-api.protezionecivile.it`, dati in **CC BY-SA 4.0**. La sonda
gli ha chiesto nove indirizzi e ne ha avuti nove `403 Access Denied`
(13-septies): non e' l'indirizzo sbagliato, e' il servizio che rifiuta un
chiamante di datacentro. Anche il proxy di questa postazione lo nega a monte,
per politica di rete, quindi la risposta non si e' potuta leggere **da nessuna
parte**.

La decisione, presa da chi il progetto ce l'ha in mano, e' stata: si scrive per
il DPC lo stesso. Da un telefono italiano quel servizio, con ogni probabilita',
risponde.

### Quello che cambia quando non si puo' leggere la risposta

`WeatherAlertsRepository` porta gia' la cicatrice di un file scritto su campi
**dedotti** invece che letti: `awareness_level` e `awareness_type`, zero
occorrenze su trentacinquemila byte di feed vero, e ogni allerta mostrata gialla
generica senza che nessuno se ne accorgesse. Qui non si poteva leggere, e
ripetere quell'errore sarebbe stato peggio - perche' stavolta si sapeva.

Percio' `RadarDpcRepository` **non conosce nessun nome di campo**. Nessun
`@SerialName`, nessuna data class del prodotto. Si scorre il JSON e si cerca per
forma, in `RadarForma`:

| cosa | come la si riconosce |
| --- | --- |
| l'istante | un intero nella finestra plausibile di un'epoca, in secondi o millesimi; fra piu' d'uno, il piu' recente |
| l'immagine | una stringa che, decodificata da base64, comincia con gli otto byte di firma di un PNG |
| il riquadro | un array di quattro numeri, o di due coppie, i cui valori **stanno dove starebbero delle coordinate** |

Dedurre un nome e' tirare a indovinare; riconoscere una forma no. Un intero da
milletrecento miliardi dentro la risposta di un radar **e'** un istante,
comunque si chiami il campo che lo porta.

Il caso che conta davvero e' il terzo, ed e' il motivo per cui il riconoscimento
del riquadro e' severo: un array di quattro numeri e' anche un elenco di soglie,
di componenti di un colore, di dimensioni in pixel. Prendere uno di quelli per
riquadro sposterebbe la pioggia di centinaia di chilometri **senza che nessuno
se ne accorga**. Quindi i quattro valori devono cadere dove cadono delle
coordinate e coprire almeno tre gradi per lato, e `RadarFormaTest` prova proprio
i casi che devono essere rifiutati.

### Il riquadro non si inventa

Se il prodotto non dichiara il proprio riquadro - ne in `getProduct`, ne in
`findAvailableProductsByType` - **l'immagine non si disegna**. Posarla su un
rettangolo scelto da noi vorrebbe dire mostrare la pioggia dove non e', e una
carta che sbaglia di cinquanta chilometri e' peggio di nessuna carta: chi la
guarda non ha modo di accorgersene.

### L'indizio che finisce sullo schermo

Quando il lettore non riconosce niente, `StatoRadar.NonDisponibile` porta con
se' i primi duecento caratteri della risposta e l'elenco delle chiavi di primo
livello, e **quella riga si scrive sotto la carta**. E' brutta da leggere e sta
li' apposta: la sonda della CI al DPC non ci arriva, un telefono italiano si', e
quella riga e' l'unico modo che il progetto ha di sapere com'e' fatta davvero
una risposta di quel servizio. Chi la vede la riporti: il giorno in cui arriva,
`RadarForma` puo' smettere di indovinare la forma e leggere i nomi veri.

### Dov'e', e perche' li'

Dentro **"La pioggia"**, sotto le dodici colonne, non in una sala sua. Le
colonne dicono *quando*, la carta dice *dove*: e' la stessa domanda per due vie,
e leggerle vicine vale piu' che separarle con uno scorrimento.

La carta si disegna **sempre**, anche senza fotogramma: costa, confine, anelli
della distanza e il puntino del posto scelto. Una schermata che sparisce quando
il dato manca lascia chi guarda senza sapere se e' l'app a essere rotta o il
cielo a essere sereno.

Quattro stati e non tre, come gia' per le allerte: **fuori copertura non e' un
guasto**. A Tokyo il radar italiano non e' vuoto, e' assente, e dirlo come se
fosse un errore insegna a ignorare l'avviso quando invece e' vero.

### Le coste: `RadarCoste.kt`

`WORLD_COASTS` esiste ed e' fatta per un disco di duecento pixel - lo stivale ci
sta in dodici vertici. Sotto una macchia di pioggia dodici vertici rispondono
"forse". Da Natural Earth 1:50m, che e' di **dominio pubblico**, ridotte con
Douglas-Peucker da `scripts/coste_italia.py` che resta nel repository: quattro
anelli chiusi per l'Italia, che si riempiono, e trentanove tratti aperti per i
vicini tagliati al bordo, che si disegnano solo di linea. Senza i vicini
l'Adriatico sembrerebbe oceano aperto.

### La finestra: centrata su di te, non sull'Italia

Un grado e mezzo sopra e sotto il posto scelto, cioe' centosessanta chilometri
per lato: la distanza a cui un temporale che si vede e' ancora un temporale che
ti riguarda. Sull'Italia intera la stessa macchia sarebbe larga tre pixel, e la
carta risponderebbe a una domanda che nessuno fa. La finestra si **trasla** per
non uscire dal dominio del prodotto, e non si restringe: cosi' la scala resta
quella dichiarata dai due anelli, cinquanta e cento chilometri, che sono
misurati e per questo portano un numero. Il battito del puntino non ne porta
nessuno, perche' non misura niente.

### Il punto debole, dichiarato

Latitudine e longitudine si posano sul rettangolo **in modo lineare**
(equirettangolare), con la sola correzione del coseno della latitudine media. Se
il prodotto del DPC fosse invece in **Mercatore** - e non si e' potuto
verificare - la pioggia risulterebbe spostata in verticale rispetto alla costa,
di piu' verso i bordi.

Sarebbe un errore **visibile**: la macchia non seguirebbe il profilo della
penisola, e chi guarda se ne accorgerebbe. E' voluto che lo sia. Scegliere la
proiezione "giusta" tirando a indovinare produrrebbe lo stesso errore in
silenzio, e questo file e' pieno di prove che gli errori silenziosi costano di
piu'.

### Cosa non e' stato fatto, e perche'

- **Nessun ripiego travestito.** RainViewer risponde `200` e le sue tessere si
  scaricano, ma e' un'altra fonte con un'altra licenza e un'altra attribuzione.
  Scriverne una col nome dell'altra sarebbe la stessa bugia di una citazione
  attribuita a chi non l'ha detta.
- **Nessuna sequenza di fotogrammi**, nessun cursore del tempo: un solo
  fotogramma, l'ultimo. Prima si vede se il servizio risponde.
- **Niente e' stato provato con un servizio vero**, ne in CI ne qui. Quello che
  la CI prova e' che il livello compila, che la carta muta si disegna, e che
  `RadarForma` riconosce le forme che gli si danno in mano.

### 14-bis. I commenti di Kotlin si annidano

Il primo giro del radar e' caduto su una riga di **documentazione**. Dentro un
KDoc c'era scritto che il corpo poteva arrivare con un tipo MIME `image` seguito
da un asterisco. In Java quella sequenza dentro un commento non vuol dire
niente; in Kotlin **apre un commento dentro il commento**, e da li' in poi serve
una chiusura in piu'.

Il compilatore ha detto:

```
e: RadarDpcRepository.kt:367:1 Syntax error: Unclosed comment.
```

Il file e' lungo trecentosessantasei righe. Cioe' l'errore veniva segnalato
**alla riga dopo l'ultima**, che e' il posto in cui l'errore non e': il parser
aveva letto fino in fondo cercando una chiusura che non arrivava mai. A
cascata, tutto cio' che quel file dichiara e' risultato irrisolvibile altrove -
sette `Unresolved reference` in `WeatherViewModel.kt`, che con il ViewModel non
c'entravano niente.

E' lo stesso genere di diagnosi ingannevole di 13-sexies: **il punto in cui la
CI si lamenta non e' il punto in cui il guasto sta**. Li' era un'azione di terze
parti accusata al posto dell'ambiente, qui una riga di codice accusata al posto
di un commento.

`scripts/commenti_kotlin.py` conta aperture e chiusure file per file, tolte
prima le stringhe. Non dimostra che il codice compila - non compila niente - ma
questo errore lo trova in un secondo invece che in otto minuti di runner, e
soprattutto lo indica **dove sta**.

### 14-ter. L'indizio occupava mezza sala

Il primo scatto verde del radar ha mostrato una cosa che nessuna prova offline
avrebbe mostrato: l'indizio del guasto, riversato sotto la carta, era la pagina
d'errore del DPC **per intero**. Dieci righe di markup - `<HTML><HEAD>
<TITLE>Access Denied</TITLE> </HEAD><BODY> <H1>Access Denied</H1> You don't` -
che spingevano le dodici colonne della pioggia fuori dalla vista, per dire due
parole.

Un indizio che non si legge non e' un indizio. `riassunto` adesso distingue tre
forme: di un JSON tiene le **chiavi di primo livello**, che sono esattamente
cio' che servira' per scrivere il lettore vero; di una pagina HTML tiene il
`title` o il primo `h1`, dove le pagine d'errore mettono la loro unica frase
utile; di tutto il resto la testa, senza a capo. Sotto la carta, tre righe al
massimo.

E' la seconda volta in questo progetto che un difetto di impaginazione lo trova
**uno scatto e non un test** - la prima erano le etichette di Sala II che si
troncavano (sezione 13). I test dicono se il codice fa quello che dice; gli
scatti dicono se cio' che fa ci sta nello schermo.

## 15. Quattro difetti trovati fuori, e uno di lingua

Questa sezione non nasce da un test ne' da uno scatto della CI. Nasce da
qualcuno che ha tenuto l'app in mano, in strada, con una mano sola e il sole di
taglio. Sono le condizioni in cui questa app si usa davvero, e sono esattamente
quelle che ne' un emulatore ne' un'immagine PNG sanno riprodurre.

### 15.1 Il giorno mostrato si vedeva in due sale su sette

Scorrendo a "La pioggia" o a "Il vento" non c'era modo di sapere se quei numeri
erano di oggi o di giovedi'. Il giorno lo dicevano la striscia di Sala I e la
scheda di Sala II, e per leggerlo bisognava **risalire di due schermate** e poi
tornare indietro.

Non e' un difetto di quelle cinque sale: e' un difetto della cornice. La
sezione 13-ter aveva gia' fatto scendere il giorno scelto **nei dati** di tutte
le sale - `shownHours`, `detailHour` - ma non lo aveva mai **scritto** da
nessuna parte. Un dato che arriva ovunque e si legge in due posti e' mezzo
lavoro.

Adesso sta nella barra delle ore, che e' l'unico pezzo di cornice sotto ogni
sala: giorno e ora sulla stessa riga, in fondo a destra, "OGGI 13:00" o
"GIO 18 13:00". Corto e non per esteso, e su una riga sola e non due: tutte e
due le scelte sono state pagate e sono spiegate in 15.4.

### 15.2 Al sole i micro-testi sparivano

Le righe minute fuori dal pannello - "TRASCINA PER CAMBIARE ORA", l'ora sopra
la barra - non stanno su un pannello: stanno sulle **colline**, cioe' su un
fondo che cambia con l'ora e che a volte e' quasi del loro stesso colore. Erano
`inkFaint`, una trasparenza al sessanta per cento scelta a occhio.

`Contrast.kt` esisteva da prima, con la sua formula WCAG e il suo test, e serviva
un caso solo. Adesso serve la palette intera:

| inchiostro | fondo su cui cade | soglia |
| --- | --- | --- |
| `inkSoft`, `inkFaint` | il pannello, senza trasparenza | AA (4.5) |
| `inkSuCielo`, `accentSuCielo` | la collina piu' avanti | AA grande (3.0) |

Due soglie e non una: le righe sulle colline sono maiuscoletti in grassetto e
cifre grandi, cioe' proprio cio' che la norma chiama testo grande, e chiedere
4.5 le spingerebbe al bianco pieno anche a mezzogiorno.

I corpi restano smorzati, ma **fino alla soglia e non oltre**: `readableOn`
parte dalla trasparenza di prima e schiarisce o scurisce solo quanto serve.

**I corpi sono cresciuti dove potevano, e dove non potevano si e' visto.**
`microLabel` da nove a dieci punti - e' lui che numera le ore sotto le colonne
della pioggia, cioe' proprio la "scala delle ore" che non si leggeva - e le
cifre della striscia dei giorni di un punto ciascuna.

`sectionLabel` invece e' stato portato a undici ed e' **tornato a dieci**.
Undici lo troncava: nello scatto "PROBABILITÀ" diventava "PROBABIL" e
"INTENSITÀ" diventava "INTENSIT", perche' quelle etichette stanno in celle da
un terzo di pannello. Una parola tagliata si legge peggio di una parola piccola.

Guardando meglio lo scatto e' saltato fuori che **"PROBABILITÀ" non ci stava
per intero nemmeno prima**: da sempre si leggeva "PROBABILI", e nessuno l'aveva
notato perche' una parola troncata sembra un'abbreviazione voluta.

Per farcela stare sono serviti due giri: il margine interno delle celle da
tredici punti a dieci, e poi - perche' col solo margine restava "PROBABILIT",
senza l'accento - la spaziatura fra le lettere da un decimo di em a
sessantacinque millesimi. Undici caratteri in un terzo di pannello e' il caso
limite di tutta l'app, e adesso ci sta. Chi aggiungera' una cella con
un'etichetta piu' lunga la trovera' troncata: li' la strada sara' accorciare la
parola, perche' da stringere non c'e' rimasto niente.

Il margine era la cosa da stringere, non la parola da accorciare - ma "una
parola tagliata sembra un'abbreviazione voluta" vale anche al contrario: per
due giri ho creduto fosse a posto perche' **quasi** ci stava.

`PaletteLeggibileTest` prova tutta la traversata dal tema chiaro a quello scuro,
in tutti e due i crepuscoli e col cielo aperto e chiuso. **Esclude il guado in
mezzo**, ed e' dichiarato perche': fra il quaranta e il sessanta per cento di
`dk` inchiostro e accento si incrociano, e quella fascia `temaScuro` la
attraversa di scatto apposta (12-ter). Chiedere la soglia anche li' vorrebbe
dire un lampo bianco a meta' traversata per evitare un difetto che non si vede.

### 15.2-bis Il 403 del radar arrivava a schermo vestito da HTML

Nello stesso scatto, sotto la carta del radar: `Il radar non ha risposto come
atteso: SRI, HTTP 403 da Radar-DPC (SRI): <HTML><HEAD> <TITLE>Access
Denied</TITLE>...`.

La sezione 14-ter aveva gia' affrontato questo, e non era bastata: aveva
insegnato a `riassunto` a spogliare una pagina HTML, ma quel testo non passa da
`riassunto`. Passa dal messaggio d'errore di `httpGet`, che al codice HTTP
attacca **anche il corpo della risposta** - ed e' giusto che lo faccia, perche'
Open-Meteo scrive li' dentro il motivo del rifiuto.

`riassuntoGuasto` tiene la testa - `HTTP 403 da Radar-DPC (SRI)`, che e'
l'informazione - e passa il resto per `riassunto`. Quaranta caratteri invece di
duecento.

E' la stessa lezione di 13-sexies in un'altra forma: si era corretto **un
percorso** e si era creduto di aver corretto **il problema**. I percorsi erano
due.

### 15.2-ter Tre etichette tagliate, e nessuna sembrava tagliata

Cercando dove finiva "PROBABILITÀ" ne sono uscite altre due, e tutte e tre per
lo stesso motivo: `maxLines = 1` **senza** `overflow`, che in Compose vuol dire
tagliare netto.

| dove | diceva | doveva dire |
| --- | --- | --- |
| Sala III, prima cella | `PROBABILI` | PROBABILITÀ |
| Sala VII, seconda cella | `ESPOSIZION` | ESPOSIZIONE |
| Sala VII, scala delle ore | `0 0 0 0 0 10 11 12 ... 19 2` | 05 06 07 ... 19 20 |

La terza e' la peggiore, ed e' quella nominata nella segnalazione. Sedici
colonne in duecento punti fanno dieci punti a colonna, e "05" ne vuole dodici:
le prime cinque ore erano tagliate al primo carattere e l'ultima pure. Non e'
un problema di contrasto - e' una scala oraria che non c'era, sotto un grafico
che si tocca per scegliere l'ora.

**Il motivo per cui erano li' da mesi e' il taglio netto.** Una parola troncata
senza puntini non sembra rotta, sembra un'abbreviazione voluta: "PROBABILI" si
legge come una scelta di chi ha disegnato, e nessuno va a controllare le scelte
altrui. Con i puntini sarebbe stato ovvio al primo scatto.

Quindi tre cose, in ordine di durata:

1. `CellaValore` mette `TextOverflow.Ellipsis` sull'etichetta. **E' questa la
   correzione vera**: da adesso in poi un'etichetta che non ci sta si vede.
2. Le due etichette lunghe scendono sotto gli otto caratteri della regola gia'
   scritta nella sezione 13: `PROBABILITÀ` diventa `PROBAB.` - un'abbreviazione
   col punto, che si legge come voluta perche' lo e' - ed `ESPOSIZIONE` diventa
   `AL SOLE`, che accanto a "~37 min" dice la stessa cosa in meno spazio.
3. La scala di Sala VII numera **un'ora su due**. Le colonne restano sedici,
   perche' sono i dati; a sparire sono le tacche dispari, che una scala non ha
   bisogno di numerare tutte. L'ora scelta fa eccezione sempre: quella non e'
   una tacca, e' la risposta a "dove sono".

**E dimezzarle non e' bastato.** Nello scatto dopo, la scala diceva
`… … 10 12 13 14 16 18 …`: 10, 12, 14, 16, 18 c'erano, 06, 08 e 20 no. La
colonna resta larga poco piu' di dieci punti, e li' dentro "12" ci sta mentre
"06" no - **la cifra uno e' piu' stretta delle altre**, e basta quello perche'
meta' di una scala si legga e meta' no.

Che si sia visto e' merito dei puntini messi un commit prima: senza, sarebbero
state altre tre "0" in fila, indistinguibili da una scelta.

La correzione e' `wrapContentWidth(unbounded = true)`: l'etichetta misura la
propria larghezza vera e sborda dalla colonna, centrata. Puo' farlo **perche'
le colonne dispari un'etichetta non ce l'hanno**, quindi lo spazio in cui
sborda e' vuoto per costruzione - non e' un trucco che regge da solo, regge
insieme alla decisione di numerare un'ora su due.

**E poi tutte le altre.** Trovate tre cosi', la domanda giusta non era "dove
sono le altre due" ma "quante `Text` hanno `maxLines` senza `overflow`". La
risposta era dodici, sparse in nove file - le temperature della striscia, i
millimetri, le ore di Sala III, le pastiglie, il nome della fase lunare, le
voci dell'Ingresso. Nessuna di queste si taglia **oggi**, con i dati di oggi e
su questo schermo: si taglierebbero con un numero a tre cifre, una lingua piu'
lunga, un corpo piu' grande nelle impostazioni di sistema. E si taglierebbero
in silenzio, come queste tre.

Adesso hanno tutte i puntini. Non e' una correzione di difetti: e' togliere di
mezzo il modo in cui questi difetti restano nascosti.

### 15.3 La colonna di destra si sbagliava col pollice

Sette dischi da trentaquattro punti, sei di distanza: **quaranta punti di
passo**, contro i quarantotto che le linee guida chiedono come minimo. E sul
bordo destro del vetro, dove arriva il pollice di chi tiene il telefono con una
mano sola.

Il disco cresce di quattro punti, il bersaglio di quattordici, e il bersaglio
cresce **verso l'interno** oltre che in altezza: il dito che arriva da destra
trova l'area prima del bordo, non dopo. L'arrangiamento non aggiunge piu'
spazio fra le voci - lo fa il bersaglio - perche' sette bersagli da quarantotto
piu' sei di distanza non ci starebbero su uno schermo corto.

Il margine dal bordo scende da dieci a sei punti e **i dischi non si spostano**:
il bersaglio e' cresciuto di cinque punti per lato, e quel margine glieli
restituisce.

### 15.4 Lo spazio verticale: il ragionamento era giusto e la sala sbagliata

L'osservazione era: la scheda occupa circa meta' schermo, le sale ricche - "La
settimana" su tutte - si stringono, e il cielo sfocato sopra resta
inutilizzato.

Il primo tentativo l'ha presa alla lettera: quattro punti di respiro in piu' nel
pannello, le spaziature di Sala II allargate, la striscia dei giorni piu' alta.
Il ragionamento sembrava solido - il pannello e' ancorato in basso e si
dimensiona sul contenuto, quindi allargandolo sale nel cielo.

**Lo scatto della CI ha detto di no.** In `chiaro-d6-settimana.png` l'ultima
riga della striscia - i millimetri di ogni giorno - era tagliata a meta' dal
bordo inferiore del pannello.

Il motivo e' che la premessa vale per le sale corte e non per quella. "La
settimana" **cielo sopra non ne ha**: e' gia' alta quanto lo schermo glielo
concede. Il pannello e' un `Column` ritagliato, il suo genitore gli passa
un'altezza massima, e quando il contenuto la supera i figli in eccesso -
insieme al margine inferiore - finiscono fuori dal ritaglio. Non sale: si fa
tagliare in fondo.

A peggiorarla c'era un difetto tutto mio, di 15.1: il giorno e l'ora **in
colonna** sopra la barra costavano venticinque punti di altezza a **tutte e
sette** le sale, perche' quella barra sta sotto ognuna. Affiancati non costano
niente, e la riga era gia' alta quanto l'ora.

Quindi: giorno e ora sulla stessa riga, e le spaziature tornate dov'erano.
Restano i corpi cresciuti di un punto (15.2), che sono sei punti di altezza
contro i ventotto di margine che Sala II aveva prima.

**Cosa resta vero dell'osservazione.** Il cielo vuoto sopra il pannello nelle
sale corte - "Oggi" quando non piove, "La luna" - non e' spazio sprecato: e' il
cielo, che in questa app e' il dato principale e non lo sfondo. Il pannello che
si ferma a meta' schermo li' e' la scelta, non il difetto. Dove il difetto
c'era davvero - Sala II - lo spazio non c'era da prendere, e prenderlo lo stesso
tagliava una riga.

**La lezione, che e' la terza volta.** Sezione 13 le etichette troncate, 14-ter
l'indizio del radar lungo dieci righe, e adesso questa: ogni volta un problema
di **quanto ci sta** e ogni volta l'ha trovato uno scatto. I test dicono se il
codice fa quello che dice; gli scatti dicono se cio' che fa ci sta nello
schermo. Di una modifica alle spaziature non si scrive "fatto" prima di aver
guardato.

### 15.5 Gli accenti scritti con l'apostrofo

Quattordici stringhe a schermo dicevano "L'esposizione e' sicura" e "Meta'
disco" invece di "è" e "Metà".

**Nei commenti l'apostrofo resta**, ed e' una scelta di questo progetto che non
cambia: i commenti li legge chi scrive il codice, spesso su terminali e diff che
con gli accenti fanno brutti scherzi. Ma cio' che va a schermo lo legge chi
usa l'app, e li' "e'" non e' una convenzione: e' un refuso.

Dove si guarda, se dovesse ricapitare: dentro una stringa, un apostrofo che
**non e' seguito da una lettera** e' quasi sempre un accento scritto male -
`e'`, `meta'`, `piu'` - mentre uno seguito da lettera e' un'elisione legittima:
`l'aria`, `dell'ombra`. L'unica eccezione vera in tutto il progetto e'
`"'wght' $weight"`, che e' la sintassi delle variazioni di un font e non
italiano.

### 14-quater. Prima di dire "rifiuta i datacentro", si esclude l'agente

La sezione 13-septies conclude che il DPC rifiuta **il chiamante**, non la
richiesta, e che un runner di GitHub e' un indirizzo di datacentro. E'
plausibile. Non e' dimostrato.

Perche' la sonda ha chiesto con un agente solo: `caelum-probe
(github.com/NoximilienCoxen/test-weather)`. Davanti a quel servizio c'e' una
CDN, e **filtrare sull'agente e' la prima cosa che una CDN fa** - piu' comune,
in rete, del filtro per intervallo di indirizzi. Nove 403 su nove indirizzi con
un agente solo non distinguono le due ipotesi: le conferma tutte e due.

E' esattamente l'errore di 13-sexies in una forma nuova. Li' si era accusata
un'azione di terze parti al posto dell'ambiente, e la prova che smontava
l'accusa - cambiare versione e fallire identico - era a portata di mano e non
era stata fatta. Qui la prova a portata di mano e' cambiare intestazione.

La sezione 6 della sonda chiede **lo stesso indirizzo** cinque volte, cambiando
una variabile sola: curl com'e', un agente da browser Android, lo stesso con
`Referer` e `Origin` del sito vero, lo stesso con `Accept: application/json`, e
l'agente di okhttp - cioe' come lo manderebbe un'app Android qualsiasi.

- Se una risponde **200**, il 403 non e' una condanna: e' un'intestazione
  mancante, e `httpGet` gliela puo' mettere. Il radar funzionerebbe anche in
  CI, e si potrebbe finalmente **leggere** la forma di una risposta invece di
  riconoscerla.
- Se rispondono **tutte 403**, l'ipotesi del filtro per indirizzo resta in
  piedi da sola, e resta scritto che l'altra e' stata esclusa invece di essere
  stata ignorata.

Le due risposte valgono uguale. La sonda serve a non dover scegliere a mente
fra due spiegazioni che sembrano tutte e due ragionevoli.

#### La risposta: e' l'indirizzo

Cinque prove, una variabile sola, **cinque volte `HTTP 403`** - e
quattrocentotrentaquattro byte identici tutte e cinque, cioe' la stessa identica
pagina:

| chi chiede | risposta |
| --- | --- |
| curl com'e' | 403 |
| agente da browser Android | 403 |
| agente da browser + `Referer` e `Origin` del sito vero | 403 |
| agente da browser + `Accept: application/json` | 403 |
| agente di okhttp, come un'app Android | 403 |

L'ipotesi dell'agente e' **esclusa**, e non ignorata. Resta quella di 13-septies:
il servizio rifiuta il chiamante per il posto da cui chiama, e un runner di
GitHub Actions e' un indirizzo di datacentro.

**Cosa cambia per l'app: niente, ed e' una buona notizia.** Nessuna intestazione
da aggiungere, nessun codice da correggere. La conseguenza pratica e' che il
radar **non si potra' mai provare da qui** - ne dalla CI ne da questa postazione,
il cui proxy quel dominio lo nega a monte. L'unico posto in cui quella richiesta
puo' riuscire e' un telefono su una rete italiana normale, ed e' esattamente il
motivo per cui `StatoRadar.NonDisponibile` porta a schermo cio' che ha visto.

Vale la pena aver speso un giro di CI per un risultato negativo: senza, in
questo file sarebbe rimasta una spiegazione plausibile spacciata per accertata,
che e' il difetto che 13-sexies ha gia' fatto pagare una volta.

## 16. Quello che il telefono ha visto e la CI no

Due scatti da Fontevivo, presi in mano. Tre difetti su quattro li avevo
introdotti io nel giro precedente, e due di quei tre erano **invisibili negli
scatti della CI** perche' dipendono dai dati: con altri numeri, la stessa
schermata sembrava a posto.

### 16.1 Il grafico UV a quote alterne

Le sedici colonne dell'istogramma stavano a altezze scoordinate, alternate, e
il grafico non diceva piu' niente sui propri valori.

La causa e' la correzione della sezione 15.2-ter. Per far stare le etichette
ne avevo dimezzate le occorrenze, con un `if` attorno al `Text`:

```kotlin
if (ora % 2 == 0 || indice == scelta) { Text(...) }
```

Solo che la `Row` che tiene le colonne le allinea **in basso**, e una colonna
senza etichetta e' piu' corta di una con etichetta di tutta l'altezza di una
riga di testo. Allineate in basso, le barre con l'etichetta salivano di
quell'altezza. Un grafico che sposta le proprie barre a seconda di quale
etichetta gli tocca.

**Perche' la CI non l'ha visto e il telefono si'.** Lo ha visto, e l'ho
guardato: nello scatto `chiaro-d11-uv.png` la scala diceva
`06 08 10 12 13 14 16 18 20` - era quello che stavo controllando - e le barre
erano gia' sbagliate. A Forli' alle 13:00 le colonne alte stavano tutte al
centro, dove le etichette si alternano fitte, e l'alternanza si leggeva come la
forma della curva. A Fontevivo con l'indice a zero il disegno era sparso, e lo
sbaglio saltava fuori.

Non e' che lo scatto non bastasse: **e' che avevo guardato la riga che avevo
appena corretto** invece della figura sopra.

La correzione e' che l'etichetta c'e' sempre, e quando non si deve leggere e'
stringa vuota. Un `Text` vuoto occupa comunque la propria interlinea, quindi
tutte le colonne restano alte uguale. Una casella d'altezza fissa avrebbe fatto
lo stesso, al prezzo di scrivere l'interlinea a mano in un secondo posto, dove
sarebbe divergata al primo che tocca il corpo.

### 16.2 Il giorno galleggiava sopra l'ora

`OGGI` e `13:00`, accanto sopra la barra, erano allineati **in basso** con due
punti di margine messi a occhio. Allineare in basso due riquadri di corpi
diversi non allinea le lettere: allinea i fondi delle caselle, che sotto le
lettere scendono di quanto vuole ciascun font - e i due font qui sono lo stesso
a due corpi e due pesi.

`alignByBaseline` allinea quello che l'occhio guarda: la riga su cui le lettere
poggiano. Nessun margine da tarare, e regge se un domani i corpi cambiano.

### 16.3 Due navigazioni per un carosello solo, e una diceva il verso sbagliato

Sotto le schede c'erano **sette trattini orizzontali**, uno per sala: dicevano
dove sei e ci si saltava sopra. Esattamente quello che fa la colonna sul fianco
destro. Due comandi identici a due bordi opposti dello schermo, e chi li ha
usati l'ha notato al primo giro.

Peggio della ridondanza c'e' che erano **orizzontali**. Sette tacche in fila
orizzontale sono il segno universale di "si sfoglia di lato", e qui si sfoglia
in su e in giu'. Un indicatore che mente sul verso del gesto e' peggio di un
indicatore assente: chi lo legge prova il gesto sbagliato e conclude che l'app
non risponde.

Sono spariti. Sopravvive la colonna, che era gia' verticale come il carosello,
dice quale sala e' quale, e ci porta con un tocco. Le due cose che i trattini
facevano meglio ha preso anche quelle:

- **segue il dito in continuo.** L'accento non scatta al momento
  dell'aggancio: scorre fra un'icona e l'altra, con la stessa formula che
  avevano i trattini - la frazione di pagina di `pagerState`, letta **dentro
  il disegno** e non in composizione, come gia' faceva `PuntiSala`. Per questo
  il fondo del disco si disegna con `drawBehind` e non con `background`.
  L'inchiostro dell'icona no: quello lo deve sapere un composable, e passarglielo
  a ogni fotogramma costerebbe la ricomposizione che si sta evitando. Scatta, con
  una molla corta a coprirlo.
- **c'e' un filo dietro.** Una linea verticale tenue da centro a centro del
  primo e dell'ultimo bersaglio. Sette dischi sparsi sono sette bottoni; sette
  dischi su una linea sono un **asse**, e un asse verticale dice da se' in che
  verso si sfoglia. Va da centro a centro e non da bordo a bordo: un filo che
  spunta sopra la prima icona sembrerebbe tagliato, non finito.

E i sessanta punti che i trattini occupavano - quarantotto di bersaglio piu'
dodici di margine - sono andati alle schede.

### 16.4 La scheda scorre quando non ci sta

I sessanta punti aiutano e non risolvono: "La settimana" e "La pioggia"
riempiono lo schermo comunque, e su un telefono piu' corto del mio, o con il
corpo di sistema ingrandito, il taglio in fondo torna. La sezione 15.4 aveva
descritto il meccanismo e si era fermata li'.

La pagina del carosello adesso e':

```kotlin
Box(Modifier.fillMaxSize().padding(...)) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .verticalScroll(scorrimento),
    ) { ... }
}
```

- contenuto **piu' corto** dell'altezza: il `Column` si misura sul contenuto e
  resta ancorato in basso. **Le schede che ci stanno non cambiano di un punto**
  - ed e' la maggioranza;
- contenuto **piu' alto**: il `Column` si ferma all'altezza disponibile e
  scorre, invece di far uscire dal ritaglio i figli in eccesso.

Lo stato sta dentro `key(page)` perche' ogni sala ricordi il proprio:
condiviso, aprendo una sala corta dopo una lunga la si troverebbe scorrevole
senza niente da scorrere.

**Il prezzo va detto perche' e' un gesto che cambia**: su una scheda lunga il
dito scorre prima la scheda, e passa alla sala successiva quando e' arrivato in
fondo. E' il nested scroll di Compose - nessun codice nostro - ed e' quello che
fa qualsiasi pagina lunga dentro un carosello. Se sull'uso quotidiano risultera'
scomodo, la via d'uscita e' alleggerire le due sale dense, non togliere lo
scorrimento: senza, il taglio torna e basta.

### 16.5 Il radar: un ultimo tentativo, e cosa non si fara'

Il DPC risponde 403 **anche dal telefono**. La sezione 14-quater aveva escluso
l'ipotesi dell'agente, e va riletta con attenzione: quelle cinque prove
partivano da un indirizzo **gia' rifiutato**, quindi non potevano distinguere
"rifiuta questo indirizzo" da "rifiuta questo agente" - il primo filtro scatta
prima e maschera il secondo. Dal telefono, che un indirizzo buono ce l'ha, e'
stata provata **una combinazione sola**: l'agente predefinito di Android,
`Dalvik/2.1.0 (...)`, che e' fra i primi che una rete di distribuzione scarta.

Resta un tentativo, e costa una riga: `Http.kt` adesso manda
`Caelum/1.0 (+https://github.com/NoximilienCoxen/test-weather)`.

**Non e' un travestimento**, ed e' il punto della sezione. L'agente dice il
nome dell'app e dove sta il codice - se un giorno una fonte volesse chiedere di
smettere, da li' sa a chi scrivere. Non finge di essere Chrome, e non manda un
`Referer` del sito di qualcun altro.

Quella seconda cosa e' la riga da non superare, e sta scritta perche' qualcuno
sara' tentato: **se un servizio pubblico risponde solo a chi si spaccia per il
suo sito, quel servizio non e' aperto a terzi.** Aggirarlo e' la stessa
famiglia di gesti dello spacciare RainViewer per Radar-DPC, che questo progetto
ha gia' rifiutato: in un caso si mente sulla fonte dei dati, nell'altro sul
proprio nome.

Vale per tutte le fonti e non solo per il radar: e' cortesia verso ognuna, e la
policy delle tessere di OpenStreetMap un agente identificabile lo **chiede**.

**Il segnale, e cosa succede dopo.** La riga sotto la carta, sul telefono. Se
dice ancora `HTTP 403 ... Access Denied`, il DPC non serve terze parti e si
passa a RainViewer - deciso in anticipo, cosi' non serve tornare a chiedere.
Quel passaggio non e' uno scambio di indirizzo:

- le tessere di RainViewer sono in **Mercatore**, la carta e' equirettangolare.
  Va cambiata la proiezione in `RadarMappa.punto()` - una formula, un punto solo
  del codice, ed e' li' che il commento sulla proiezione gia' avvisa che
  succedera';
- vanno scaricate e composte le tessere `z/x/y` che coprono la finestra;
- **l'attribuzione a schermo cambia**: RainViewer e OpenStreetMap, non
  Protezione Civile.

L'ultima e' gia' pronta: `RadarProdotto` porta un campo `attribuzione`, e
`RadarMappa` scrive quello invece di una riga scritta a mano. Un'attribuzione
scritta dove si disegna resterebbe quella di prima il giorno in cui la fonte
cambia, e **il nome sbagliato sopra i dati di un altro** e' esattamente cio'
che si era rifiutato di fare. Chi porta i dati porta anche il proprio nome.

## 17. Il cielo che respira, e una dipendenza che non si e' aggiunta

Richiesta: traiettoria solare e cambio del gradiente (c'erano gia'), deriva
lenta delle nuvole, evaporazione al picco di calore, un riflesso sulla scheda
UV, un rimbalzo sull'icona toccata, e il rispetto del "meno movimento".

### 17.1 Le nuvole scorrono invece di oscillare

C'era gia' un movimento, ed era **sbagliato di genere**: un seno, trenta punti
avanti e indietro con un periodo di venti-quaranta secondi. Fino a nove punti
al secondo nel mezzo della corsa, e sempre di ritorno al punto di partenza. Un
cielo sereno che respirava come un fondale di teatro.

Adesso scorrono, e basta: **tre decimi di punto al secondo** col cielo aperto -
trenta volte meno di prima. Una nuvola attraversa lo schermo in una ventina di
minuti: nessuno la vede muoversi, e chi riapre l'app dopo mezz'ora la trova
altrove. E' il modo in cui un disegno dice *non c'e' vento* senza scriverlo, e
al contrario di una scritta non puo' contraddire i dati, perche' non afferma
niente di preciso.

Col cielo chiuso la velocita' sale a sei volte tanto: un fronte **si muove**, e
muoverlo come un cumulo di bel tempo lo farebbe sembrare lo stesso cielo con un
altro colore. Ogni massa ha il proprio passo fra il settanta e il centotrenta
per cento, perche' cinque nuvole alla stessa identica velocita' sono un
fondale, non cinque nuvole. E un `mod` riporta dentro chi esce: senza, dopo
un'ora di app aperta il cielo sarebbe vuoto.

### 17.2 L'evaporazione delle ore calde

Quando il sole e' alto sopra un cielo aperto, i cumuli si sfilacciano:
l'opacita' scende dall'intero a quattro quinti e la massa si allarga del tre
per cento.

**Non e' agganciata all'orologio.** La richiesta diceva "attorno alle 14:00 -
15:00", ed e' vero in pianura padana a luglio; non lo e' a dicembre, ne' a
Nairobi, ne' a Bergen - e questa app apre tutte e tre, e la sezione 12-ter porta
gia' la cicatrice di una scala europea applicata a Tokyo. E' agganciata
all'**altezza del sole**, che il picco ce l'ha per costruzione dovunque e in
qualsiasi stagione, e d'inverno non arriva mai alla soglia: il cielo non
evapora, che e' esattamente giusto.

Solo a cielo aperto: sotto un fronte non evapora niente, e vederlo schiarire a
mezzogiorno sarebbe il disegno che smentisce il dato.

### 17.3 Il riflesso sulla scheda UV, e il rimbalzo dell'icona

Sopra l'indice **cinque** - dove la scala dell'OMS passa da "moderato" ad
"alto", cioe' dove la protezione smette di essere consigliata e diventa
necessaria - una banda chiara attraversa la scheda in poco piu' di un secondo,
poi non succede niente per cinque. **La pausa e' la parte importante**: un
riflesso continuo diventa fondo, e un fondo non avvisa di niente.

Si accende sul valore **mostrato** e non sul picco del giorno: scorrendo la
barra fino alle tre di notte si spegne, perche' li' l'indice e' zero e un
avviso di sole alle tre di notte insegna a ignorare gli avvisi.

L'icona toccata nella colonna scatta in fuori di un quinto e torna con una
molla poco smorzata - cioe' oltrepassando e rientrando, che e' quello che fa
una cosa elastica - e si lascia dietro un anello caldo che si allarga oltre il
bersaglio e svanisce. Vale per tutte e sette e non per il solo sole: un
linguaggio di risposta al tocco o e' uno solo, o e' un'eccezione da spiegare.

Il rimbalzo si legge nel **livello** (`graphicsLayer` con blocco) e il fondo
nel **disegno** (`drawBehind`), non in composizione: sono sessanta ricomposizioni
al secondo risparmiate per travasare due numeri, e la stessa ragione per cui i
vecchi trattini leggevano la posizione dentro il `Canvas`.

### 17.4 Due interruttori per "meno movimento" erano uno di troppo

Caelum aveva il suo, nelle impostazioni, e faceva il suo mestiere. Ma leggeva
**solo** quello. Chi spegne le animazioni nel telefono - per vertigini, per mal
d'auto, o perche' un telefono lento va meglio cosi' - apriva Caelum e trovava
un cielo che si muoveva comunque.

Su una pagina web quell'impostazione si chiama `prefers-reduced-motion`; su
Android e' `Settings.Global.ANIMATOR_DURATION_SCALE` a zero. Adesso i due si
**sommano**: chi ha spento nel sistema non deve spegnere anche qui, e chi vuole
meno movimento solo in quest'app puo' continuare a chiederlo qui.

Con "meno movimento" si ferma l'orologio della scena, e con lui sole, nuvole,
pulviscolo, uccelli, vibrazioni, il riflesso della scheda UV e il rimbalzo
dell'icona. **Il sole resta fermo dov'e' l'ora scelta**, perche' la sua
posizione non e' un'animazione: e' un dato.

### 17.5 Rive: perche' no, e cosa si guadagnerebbe davvero

La richiesta chiedeva di realizzare l'animazione del sole con una **macchina a
stati di Rive**, "cosi' il movimento viene calcolato dalla GPU senza consumare
batteria". La seconda meta' non e' esatta, e la prima ha un costo che vale la
pena scrivere prima di pagarlo.

**Sul funzionamento.** Una macchina a stati di Rive avanza sulla CPU a ogni
fotogramma e poi disegna, come fa un `Canvas` di Compose: in tutti e due i casi
il lavoro per fotogramma e' una manciata di forme, e il disegno finisce
comunque sulla GPU. Quello che consuma batteria non e' *chi* calcola: e'
**avere un'animazione accesa a sessanta fotogrammi al secondo**, e quel costo e'
identico con Rive e senza. Il risparmio vero e' quello di 17.4 - spegnerla
quando chi guarda ha chiesto che sia spenta.

**Sul costo.** Questo progetto non ha **nessuna** libreria di terze parti:
niente rete, niente immagini, niente animazione. Rive porterebbe un runtime
nativo per due architetture e un file `.riv` prodotto da un editor esterno -
cioe' l'aspetto del sole uscirebbe dal repository, non si leggerebbe in un
diff, non si spiegherebbe in un commento, e chi volesse cambiare il raggio
dell'alone dovrebbe aprire un altro programma. Il sole di oggi e' una dozzina
di `drawCircle` documentati riga per riga.

Il sole pulsa gia', l'alone respira, la corona gira. Se dopo averlo visto in
mano il movimento risultasse povero, la strada e' aggiungere forme qui - dove
si vedono e si discutono - prima di aggiungere un motore. **Se la scelta e'
comunque Rive, si fa**: e' una decisione di chi il progetto ce l'ha in mano, e
questa sezione serve a prenderla sapendo cosa si compra.

### 17.6 Nota operativa: `ci-artifacts` riempie il disco

Chi lavora da un contenitore effimero e guarda gli scatti della CI a ogni giro
si trovera' il disco pieno, e il messaggio che arriva non parla di git.

Il motivo: `git fetch origin ci-artifacts` senza refspec scrive in `FETCH_HEAD`,
che **non e' un ref persistente**. Finito il comando gli oggetti scaricati non
sono piu' raggiungibili, quindi al giro dopo git non puo' offrirli nella
negoziazione e **riscarica tutto lo storico da capo** - e quello storico sono
gli scatti e i log di ogni giro di CI mai fatto. Undici `fetch` in una mattina
hanno prodotto undici pacchetti da due giga: trenta giga in `.git`, su un
repository il cui codice sta in meno di due mega.

Il rimedio, in due mosse:

```bash
# una volta, per ripulire: gli oggetti irraggiungibili se ne vanno
git reflog expire --expire=now --all && git gc --prune=now

# e da qui in avanti, sempre cosi'
git fetch --depth=1 origin "+refs/heads/ci-artifacts:refs/remotes/origin/ci-artifacts"
git show origin/ci-artifacts:screenshots/<nome>.png > /tmp/<nome>.png
```

`--depth=1` scarica il **solo** commit di punta - una ventina di mega, gli
scatti dell'ultimo giro - invece dello storico intero. Il `+` davanti alla
refspec serve perche' quel ramo la CI lo riscrive, e senza il `+` il fetch
viene respinto con `non-fast-forward` **lasciando in piedi il ref vecchio**:
si finisce a guardare gli scatti del giro precedente credendoli quelli nuovi,
che e' il modo peggiore di sbagliare una verifica.

## 18. Il radar passa a RainViewer, e cosa e' costato non averlo provato prima

Il Dipartimento della Protezione Civile risponde `403 Access Denied` **anche
dal telefono**, su rete italiana, con un agente che dichiara nome e indirizzo
del progetto. Quella e' la risposta, e l'ha data lo schermo di chi usa l'app.

### 18.1 Tre prove, e le prime due spiegavano male

| # | prova | risposta | conclusione |
| --- | --- | --- | --- |
| 1 | nove indirizzi dalla CI | nove 403 | rifiuta i datacentro |
| 2 | cinque intestazioni dalla CI | cinque 403 identici | non e' l'agente |
| 3 | dal telefono, agente dichiarato | **403** | non serve terze parti |

La seconda riga era **inutile e sembrava utile**: quelle cinque prove partivano
da un indirizzo gia' rifiutato, quindi il primo filtro scattava prima e
mascherava tutti gli altri. Cinque risposte identiche non distinguevano niente.
E' il difetto di 13-sexies in una terza forma: non "la diagnosi e' sbagliata",
ma **"l'esperimento non poteva rispondere alla domanda"**. Costa un giro di CI
e lascia in mano una certezza falsa, che e' peggio di un dubbio.

La regola che ne esce: prima di fidarsi di un esperimento, chiedersi **cosa
vedrei se l'ipotesi fosse falsa**. Se la risposta e' "la stessa cosa",
l'esperimento non serve.

### 18.2 Si e' scritto un lettore intero per un servizio che non ha mai risposto

`RadarDpcRepository` e `RadarForma` erano codice buono: non indovinavano
nessun nome di campo, riconoscevano l'istante dalla taglia, l'immagine dalla
firma, il riquadro dai valori; `RadarFormaTest` li provava su tre dialetti
inventati. Duecento righe, un test da tredici casi, tre giri di CI.

Non sono serviti a niente, e adesso sono cancellati.

**Nessuna eleganza compensa il non avere una risposta da leggere.** La sezione
14 diceva "questo file e' scritto senza aver mai visto una risposta" e lo
presentava come una difficolta' affrontata bene. Era invece il momento in cui
fermarsi: la sonda aveva gia' detto che il servizio non rispondeva, e l'unica
cosa ragionevole era non scrivere il lettore finche' non rispondeva.

Cio' che si e' salvato dal giro precedente non e' il codice: e' **la carta**.
Coste, anelli della distanza, puntino, ritaglio, riga di attribuzione - tutto
scritto per il DPC, tutto valido con qualunque fonte. Quello e' il pezzo che
andava fatto per primo.

### 18.3 RainViewer: nomi veri, letti da una risposta vera

Questo servizio ha risposto **subito e sempre**, e la sua risposta sta in
`ci-artifacts/api/radar.txt` da settimane. Quindi qui non c'e' nessun
riconoscimento per forma: ci sono i nomi dei campi.

```
{"host":"https://tilecache.rainviewer.com",
 "radar":{"past":[{"time":1789639800,"path":"/v2/radar/057a891b2bed"}, ...]}}
```

e l'indirizzo di una tessera, anche questo **provato** dalla sonda:

```
{host}{path}/{lato}/{zoom}/{colonna}/{riga}/{colore}/{morbido}_{neve}.png
```

Si prende l'ultimo fotogramma **misurato**. Il `nowcast` - la previsione a
brevissimo - c'e' nel formato ma nella cattura era vuoto, e mescolarlo al
misurato senza dire quale sia quale sarebbe la bugia di un'allerta calcolata
spacciata per ufficiale.

### 18.4 La proiezione era il debito annunciato, ed e' stato pagato

La carta era **equirettangolare**, con un commento che diceva: se un giorno
arriva una fonte a tessere, il punto da cambiare e' questo. E' arrivata.

Le tessere di RainViewer - come quelle di chiunque serva tessere - sono in
**Mercatore**: la longitudine e' lineare, la latitudine no. Posarle su una
carta lineare le stira, e di piu' ai bordi. Su tre gradi di finestra la
differenza e' di pochi pixel e nessuno se ne accorgerebbe; si e' cambiata lo
stesso, perche' "pochi pixel" e' una proprieta' di **questa** finestra, e il
giorno in cui qualcuno la allarga l'errore diventa visibile senza che nessuno
sappia da dove viene.

Costa e pioggia usano adesso la stessa proiezione. Il che vuol dire che se una
macchia non segue il profilo della penisola, non e' la proiezione: sono i dati,
ed e' una cosa che si puo' riportare.

`RadarTessere` e' aritmetica pura - griglia, Mercatore, quali tessere coprono
una finestra - ed e' l'unica parte che si possa provare senza rete.
`RadarTessereTest` la prova su Forli', Tokyo, Nairobi, Reykjavik, Bergen e
Aoraki, e verifica la cosa che conta: che **i quattro angoli della finestra
cadano dentro le tessere chieste**. Se ne mancasse una ci sarebbe un quadrante
senza pioggia, e sembrerebbe sereno.

Il tetto e' ventiquattro tessere e non sedici: le finestre vere ne chiedono
fino a sedici - a Bergen e a Reykjavik esattamente sedici - e un tetto che il
caso normale tocca non e' un tetto, e' un ritaglio silenzioso.

### 18.5 Cosa questo radar non sa dire, e come lo si dice

Il DPC copriva l'Italia: "fuori copertura" era una domanda con una risposta, e
`StatoRadar` aveva un caso apposta. RainViewer raccoglie radar da mezzo mondo e
**non dichiara dove arrivano**: dove non c'e' un radar le tessere sono
trasparenti, cioe' identiche a un cielo senza pioggia.

Quel caso e' stato tolto invece di essere riempito a occhio con un rettangolo
disegnato a mano. Al suo posto c'e' una frase, sotto la carta, sempre:

> Radar: RainViewer. Dove non arriva un radar la carta resta vuota: non vuol
> dire che non piove.

Non e' una soluzione, e' una dichiarazione - ed e' meglio di un rettangolo che
finge di sapere.

**E la sonda ha trovato qualcosa.** Dei tre indirizzi provati, due rispondono
404 e il terzo no:

```
https://tilecache.rainviewer.com/v2/coverage/0/256/{z}/{x}/{y}/0/0_0.png
  HTTP 200  byte=3012  image/png
```

Una mappa di copertura **esiste**, servita a tessere come il radar. Con quella,
"fuori copertura" torna a essere una domanda con una risposta: si guarda il
pixel della propria localita' nella tessera di copertura, e se e' trasparente
li' non guarda nessuno.

Non e' stata scritta subito, ed e' il punto di tutta questa sezione. Quella
prova dice che il livello esiste **al livello 2**, che e' quello che si e'
chiesto. Non dice se esista al livello 7, quello che l'app userebbe - i livelli
di copertura spesso si fermano molto prima - ne' se una tessera dove nessun
radar guarda sia davvero **diversa** da una dove guarda. Se fossero uguali, il
livello non distinguerebbe niente.

Quindi la sonda ha chiesto anche quelle due cose, e la risposta e' arrivata **al
contrario di come la si aspettava**:

| dove | alfa del pixel |
| --- | --- |
| Forli', che il radar ce l'ha | **0** |
| Kansas, la rete radar piu' fitta del mondo | **0** |
| Nairobi | 255 |
| in mezzo al Pacifico | 255 |
| in mezzo al Sahara | 255 |

Quel livello non disegna il coperto: disegna il **non** coperto. E'
l'ombreggiatura che la mappa di RainViewer stende sulle zone dove nessuno
guarda, e la si usa **invertita** - trasparente vuol dire che un radar c'e'.

Cinque punti su cinque, e due sono al di la' di ogni dubbio in tutte e due le
direzioni. Il primo giro si era fermato a due campioni e li aveva letti al
rovescio; se ci si fosse fidati, la sala avrebbe scritto "fuori copertura"
sopra Forli' mentre disegnava la pioggia che cade su Forli'.

Con questa risposta lo stato `FuoriCopertura` puo' tornare, e tornare
**sapendo**: si scarica la tessera di copertura della propria localita', si
guarda il pixel, e se e' opaco li' non guarda nessuno.

## 19. Il radar segue l'ora, o dice che non puo'

Il radar di RainViewer funziona: dal telefono si vede la pioggia vera, con le
coste allineate e il puntino al posto giusto. E chi l'ha guardato ha visto
subito il difetto che c'era sotto.

> È piuttosto inutile questo radar in questo modo. È una fotografia fissa che
> non cambia. L'orario è impostato sulle 22 ma l'orario fotografato è l'attuale.

Aveva ragione due volte.

### 19.1 Il fotogramma piu' recente sotto qualunque ora

La carta mostrava **sempre** l'ultimo fotogramma. La barra diceva le ventidue e
la carta era delle quindici e quaranta - ferma, uguale a se stessa a ogni ora
della giornata. Da fuori sembra una fotografia appesa; da dentro e' peggio,
perche' invita a leggere quella pioggia come se fosse delle ventidue.

E' lo stesso difetto di 13-ter - il giorno scelto che si fermava a Sala I - e
della galleria che ritraeva l'Ingresso in ogni scatto: **un dato vero, messo
dove non e' vero**.

Adesso `RadarIndice.vicinoA` cerca il fotogramma piu' vicino all'ora scelta e
lo restituisce **solo se e' abbastanza vicino**. Il "se" e' tutta la funzione:
senza, il piu' vicino e' sempre il piu' recente.

### 19.2 Il limite che nessuna interfaccia puo' aggirare

**Un radar misura, e quello che non ha misurato non lo sa.** RainViewer tiene
circa due ore di storico - dodici fotogrammi a dieci minuti l'uno dall'altro -
contro una barra che offre ventiquattro ore, spesso di un giorno futuro.

Quindi per quasi tutte le ore della giornata la risposta onesta e' "non c'e'
una fotografia di quel momento", e c'e' uno stato apposta, `FuoriOrario`, che
sotto la carta scrive:

> Il radar misura, non prevede: l'ultima fotografia è delle 22:30.

Non "non lo so": **"si sa fino a quest'ora"**, che e' un'informazione.

In pratica la carta risponde per l'ora corrente e per quella prima, e a
scorrere si vedono due o tre fotogrammi diversi. Non e' l'animazione che un
radar meriterebbe - per quella servirebbe una scala a dieci minuti, che questa
barra non ha - ma e' tutto quello che i dati permettono di dire senza mentire.

La tolleranza e' mezz'ora: i fotogrammi distano dieci minuti e la barra sceglie
ore intere, quindi mezz'ora copre l'ora corrente e quella prima. Allargarla
vorrebbe dire far passare per "le venti" una pioggia delle ventuno e mezza.

### 19.3 Come si scarica, scorrendo

Tre regole, e tutte e tre esistono per lo stesso motivo - chi scorre la barra
passa per **ogni** ora in mezzo:

1. **L'indice si chiede una volta per localita'.** Settecento byte che
   descrivono le ultime due ore: rifarlo a ogni scatto del dito sarebbe una
   richiesta per niente.
2. **Il fotogramma in volo si annulla** quando se ne chiede un altro. Senza,
   partirebbe una decina di scaricamenti di cui interessa solo l'ultimo, e
   arriverebbero in ordine sparso facendo lampeggiare la carta con fotogrammi
   gia' superati.
3. **Quattro fotogrammi restano in tasca.** Chi scorre torna indietro di un'ora
   o due, non di dodici; sono i byte compressi delle tessere, un centinaio di
   kilobyte l'uno.

### 19.4 "Fuori copertura" e' tornato, e adesso sa quello che dice

Con la maschera di copertura letta **invertita** (18.5), la domanda "qui guarda
qualcuno?" ha di nuovo una risposta. Si scarica la tessera di copertura della
localita', si legge il pixel di casa, e se e' opaco li' non guarda nessun radar.

Due cose che sembrano dettagli e non lo sono:

- si guarda il **pixel**, non la tessera: una tessera copre duecento
  chilometri, e la domanda che interessa e' quella del proprio paese;
- `null` non e' `false`. "Non si e' potuto guardare" - rete giu', tessera
  illeggibile - non e' "non c'e' copertura": col dubbio si prova a scaricare
  lo stesso, perche' dichiarare una copertura assente per colpa di una
  richiesta caduta e' peggio di una carta vuota.

E la frase sull'attribuzione si e' accorciata. Prima portava dietro sempre
l'avvertenza che una carta vuota non vuol dire che non piove; adesso
quell'avvertenza si accende **solo quando e' vera**. Un avviso che compare
sempre non e' un avviso, e' una cornice.

## 20. Tre ore su ventiquattro non sono una mappa

La 19 aveva tolto la bugia - la carta non mostra piu' la pioggia di un'ora
sotto l'etichetta di un'altra - e chi usa l'app l'ha riprovata:

> It still doesn't work. L'unico orario che registra ed è consultabile è
> l'orario con cui apri l'app, mentre le successive e le precedenti no.

**Non era un difetto, ed e' peggio che se lo fosse stato.** Il codice faceva
esattamente quello che doveva: RainViewer tiene due ore di storico, quindi alle
17:06 risponde dalle 15:10 alle 17:00. Tre ore su ventiquattro. Per le altre
ventuno la carta diceva "niente per quest'ora" e restava vuota.

Onesta, e inutile. **Un limite dichiarato bene resta un limite**, e la 19 si era
fermata a dichiararlo credendo di aver finito il lavoro.

### 20.1 Il modello risponde dove il radar non puo'

Un radar **misura**, e quello che non ha misurato non lo sa: quello non si
aggira, e la sala continua a dirlo. Ma il modello una risposta per ogni ora ce
l'ha - ed e' lo stesso Open-Meteo che riempie le dodici colonne **sopra** la
carta. Era sempre stato li'.

Chiesto su una griglia di punti invece che su uno solo, disegna la stessa cosa
che le colonne dicono in numeri. La sonda ha verificato prima di scrivere una
riga (e la 18 sta li' a ricordare cosa costa non farlo):

| griglia | punti | risposta |
| --- | --- | --- |
| 2×2 | 4 | 5,7 KB |
| 7×7 | 49 | 70 KB |
| 10×10 | 100 | 143 KB |
| 12×12 | 144 | 206 KB |

Una lista di risposte, quarantotto ore ciascuna, **in una richiesta sola**. La
carta ne chiede centodiciassette - tredici colonne per nove righe, la forma del
riquadro - e le tiene: scorrere la barra non costa niente, al contrario del
radar che ha un'immagine per fotogramma e le prende una alla volta.

### 20.2 Il dettaglio che si sarebbe sbagliato in silenzio

Open-Meteo non risponde sui punti che gli si chiedono: risponde su quelli della
**sua** griglia, il piu' vicino a ciascuno. Si chiede 42.9226 e torna 42.9375.

Sono venti chilometri. Usando le coordinate chieste invece di quelle tornate, la
pioggia finirebbe spostata di quel tanto: troppo poco per accorgersene,
abbastanza per mettere un rovescio sul paese sbagliato. La risposta le porta
entrambe, e si usano quelle tornate.

### 20.3 Come si tengono separate una misura e una previsione

**Chi guarda una carta radar le crede.** Crederebbe a una previsione a sedici
ore come se qualcuno l'avesse vista, ed e' per questo che mescolarle sarebbe
peggio che non avere la seconda. Tre differenze, e nessuna e' decorativa:

1. **Macchie morbide invece di pixel.** Il radar disegna quello che ha
   misurato e i suoi bordi sono bordi veri; il modello da' un numero ogni
   quaranta chilometri, e a quadretti netti avrebbe una precisione che non ha.
   Sfumate e sovrapposte dicono "da queste parti, all'incirca" - che e'
   esattamente quello che il dato dice.
2. **Una tavolozza diversa**, non quella di RainViewer: due carte che
   raccontano cose diverse non devono somigliarsi tanto da confondersi.
   L'opacita' si ferma a poco piu' di meta', perche' sotto ci sono le coste e
   gli anelli e una previsione che li copre si comporta come se contasse piu'
   di loro.
3. **L'etichetta dice "previsione"** invece di un'ora di scatto, in inchiostro
   tenue e non nel colore d'accento - il colore forte e' un'affermazione, e qui
   c'e' meno da affermare - e la riga sotto lo scrive: *Previsione del modello,
   non radar: la pioggia misurata c'è solo per le ultime due ore.*

Sotto il decimo di millimetro non si disegna niente: e' la pioggia che il
modello semina dappertutto e che nessuno sente cadere, e riempirebbe la carta di
velo azzurro facendo sembrare bagnato un giorno sereno.

### 20.4 Cosa risponde adesso, ora per ora

| ora scelta | cosa si vede |
| --- | --- |
| le ultime due ore | **radar**, con l'ora dello scatto |
| tutte le altre ore dei tre giorni | **previsione**, dichiarata tale |
| oltre i tre giorni | "il radar misura, non prevede: l'ultima fotografia è delle …" |
| dove nessun radar guarda | "su <posto> non guarda nessun radar" |

`FuoriOrario` non e' sparito: e' sceso a fare quello per cui serviva, il caso in
cui **nessuna** delle due fonti ha qualcosa da dire.

### 20.5 Una prova scritta sbagliata, e perche' la nota resta

Il primo giro di CI e' caduto su un test mio, non sul codice. Diceva:

> mezz'ora di scarto passa, un'ora no

copiato dal test del radar senza accorgersi che i due casi **non si
somigliano**. I fotogrammi del radar distano dieci minuti e finiscono: dopo
l'ultimo non c'e' piu' niente, e la tolleranza morde. Le ore del modello
distano un'ora e si toccano: dentro la finestra qualunque istante ha un'ora a
meno di trenta minuti, **sempre**. Li' la tolleranza non rifiuta niente, e non
deve.

Un test che verifica un comportamento impossibile non e' un test che fallisce:
e' un test che chiede di storpiare il codice per accontentarlo. Il bordo vero -
oltre l'ultima ora - c'e' adesso, ed e' li' che la tolleranza serve.

## 21. Una scheda che dice zero sopra una carta piena di colore

La 20 ha messo la previsione sulla carta, e chi usa l'app l'ha guardata:

> Non va bene

Due schermate, e nella prima la contraddizione era in faccia: **0,0 mm**,
**probabilita' 0%**, "nessuna precipitazione attesa nelle prossime dodici ore" -
e sotto una carta lavata di verde, azzurro e arancio.

Due errori, tutti e due nel disegno, e tutti e due della stessa famiglia: il
dato era giusto, la carta lo tradiva.

### 21.1 Le trasparenze si sommavano

Ogni punto era un cerchio sfumato largo un terzo piu' del passo della griglia,
perche' si toccassero senza lasciare buchi. Solo che cosi' ogni cerchio copriva
i nove o dodici vicini, e **dodici veli al ventisei per cento non fanno un
velo: fanno una vernice**.

Un decimo di millimetro sparso dappertutto - la pioggia che il modello semina e
che nessuno sente cadere - diventava una tinta piena. La soglia era troppo
bassa e l'opacita' troppo alta, ma il guaio vero era la somma: nessun valore di
soglia sistema un disegno che si accumula con se' stesso.

### 21.2 E la pioggia colava da quaranta chilometri

Il centro della carta e' il paese di chi guarda, e il valore li' deve essere
**il suo**. Invece ci arrivava sopra la coda sfumata dei vicini: la scheda
leggeva lo zero del punto giusto, la carta dipingeva la media di mezza
provincia, e le due cose non potevano che contraddirsi.

E' la stessa forma dei difetti di 13-ter e 19.1 - un dato vero messo dove non e'
vero - solo che qui il dato veniva spalmato invece che spostato.

### 21.3 Un campo, non un mucchio di macchie

Adesso si compone una figura piccola quanto la griglia - **tredici per nove
pixel, uno per punto** - e la si stira sul riquadro. L'ingrandimento la sfuma da
solo, con l'interpolazione bilineare che fa la GPU: ogni pixel dello schermo
riceve **un** colore, quello del valore interpolato, invece di una pila di veli.

Morbido come prima, e stavolta corrispondente al dato. Il pixel centrale della
figura e' esattamente il punto di casa - tredici colonne e nove righe, dispari
tutte e due, quindi il centro e' un punto vero e non un interstizio. **Se la
scheda dice zero, li' la carta e' pulita.**

Tre dettagli che non sono dettagli:

- **La figura si capovolge.** La griglia va da sud a nord, l'immagine dall'alto
  in basso: senza il capovolgimento la pioggia starebbe specchiata, e in una
  carta sfumata non se ne accorgerebbe nessuno finche' non piove davvero.
- **Mezza cella per lato.** I punti sono centri di cella, non angoli: senza,
  il campo risulterebbe rimpicciolito di una cella intera.
- **Un punto senza valore vale zero, non sparisce.** Toglierlo dalla lista -
  che era la scelta di prima, e aveva pure un test che la difendeva - sposta di
  uno tutti quelli dopo e manda la pioggia in un'altra riga della griglia. Zero
  e' una bugia piccola e locale; lo scorrimento e' una bugia grande e diffusa.

La soglia e' salita da un decimo a due decimi di millimetro, e i gradini sono
quelli con cui si parla di pioggia - pioviggine, pioggia, pioggia forte,
rovescio, nubifragio - invece di una scala continua: una scala continua su un
dato che ha un valore ogni quaranta chilometri promette sfumature che il dato
non contiene.

### 21.4 E il titolo diceva ancora "IL RADAR"

Nella stessa schermata, sopra la carta della previsione, l'intestazione diceva
**IL RADAR**. Un titolo che smentisce cio' che sta sotto e' l'ultima cosa che
dovrebbe fare un titolo, e "radar" e' esattamente la parola che fa credere a
una misura.

Adesso cambia col contenuto - "IL RADAR" per il misurato, "LA PIOGGIA PREVISTA"
per il modello - e la riga a destra porta l'**ora** invece della parola
"previsione", che ormai la dice il titolo: quello che serve sapere li' e' quale
ora si sta guardando.

## 22. La previsione sulla carta e' stata tolta, e il perche' vale piu' del codice

Due schermate a confronto, dal telefono di chi usa l'app:

> Nota la differenza tra le 8 e le 9. Le 8 sono definite e chiare, mentre le 9
> sono chiazze blurrate. Se non è possibile integrare il radar lascia perdere.

Alle 8 c'e' il radar: bordi netti, un temporale sul mare che si legge cella per
cella. Alle 9 c'e' la previsione: chiazze. Affiancate nella stessa schermata,
con lo stesso titolo e nello stesso riquadro, la seconda sembra una versione
rotta della prima.

**Non lo era, e non poteva diventare la prima.** La differenza non e' di
disegno, e nessun rendering la colma:

| | risoluzione |
| --- | --- |
| tessera radar di RainViewer | **~1 km** per pixel |
| griglia del modello, 117 punti | **~40 km** fra un valore e l'altro |

Quaranta volte piu' grossa. Le chiazze **erano** il dato: disegnarle nette
avrebbe solo spostato la bugia dal "sembra sfocato" al "sembra preciso", e la
seconda e' peggio - una carta che promette il chilometro quando ha la
provincia. La 21 aveva gia' tolto la vernice; sotto restava questo, e questo
non si toglie.

Si sarebbe potuto infittire la griglia: Open-Meteo accetta anche
centoquarantaquattro punti, e piu' richieste ne darebbero qualche centinaio. Ma
il modello sotto ha celle di qualche chilometro e la carta ne mostra
seicento per seicento: per avvicinarsi al radar servirebbero decine di migliaia
di punti, cioe' megabyte a ogni cambio di localita' per un disegno che
resterebbe comunque piu' grosso.

Quindi e' stata **tolta**: `PioggiaPrevistaRepository`, `MappaPrevista`,
`StatoRadar.Previsto`, il campo interpolato, la sonda della griglia e il test.
Restano il radar per le ore che ha - nitido, e quello funziona - e la frase per
le ore che non ha.

**Cio' che resta e' il metodo, non il codice.** Due giri di CI per scoprire che
una fonte a quaranta chilometri non puo' stare accanto a una da un chilometro:
si sarebbe potuto calcolarlo prima, dividendo la larghezza della finestra per il
numero di punti, in trenta secondi e senza scrivere niente. La sezione 18 diceva
"non scrivere un lettore per un servizio che non risponde"; questa aggiunge:
**guarda che risoluzione ha il dato prima di decidere che forma dargli.**

## 23. Le sale vanno di lato, e l'aria smette di essere un numero solo

Due richieste nello stesso messaggio, e la seconda spiega la prima:

> Lo scorrimento è un po' difficile, bisogna scorrere molto per passare tra un
> menù e l'altro. Dammi una soluzione per avere più spazio di lettura […]
> perché menù come la qualità dell'aria sono un po' spogli

### 23.1 Non era la soglia: era l'asse

Il carosello delle sale era un pager **verticale**, e dentro ogni pagina il
pannello scorreva **anch'esso in verticale**. Due cose che vogliono lo stesso
dito. Il nested scroll di Compose fa quello che deve: prima finisci il
contenuto, poi cambi pagina - e da fuori si vede come "bisogna scorrere molto".

Il commento nel codice quel prezzo lo dichiarava perfino, e lo chiamava
inevitabile: *"e' quello che fa qualsiasi pagina lunga dentro un carosello"*.
Era vero e irrilevante, perche' la domanda giusta non era "come riduco
l'attrito" ma **"perche' i due gesti sono sullo stesso asse"**.

Adesso le sale si cambiano di **lato**. I due gesti non si toccano: si legge in
giu' e si cambia sala di fianco, a qualunque altezza della scheda. E il pannello
puo' crescere quanto gli pare, perche' non ruba piu' niente a nessuno - che e'
anche la risposta alla richiesta di "piu' spazio di lettura": lo spazio non
andava aggiunto, andava **liberato**.

La colonna delle scorciatoie resta verticale a destra, e non e' un'incoerenza:
quella non si scorre, si **tocca**. E' un indice, e un indice sta in piedi di
lato.

La soglia e' scesa lo stesso da mezza pagina a un quinto: il gesto resta
deliberato, ma non e' piu' un trasloco.

### 23.2 "Spoglie" voleva dire senza contenuto, non senza spazio

Sala V mostrava un anello, una parola e quattro barrette: **27, discreta**.
Vero, e muto.

Il dato mancante **c'era gia' nell'endpoint**. Lo stesso indirizzo che da' il
valore di adesso da' anche le ventiquattro ore: si chiedeva `current` e basta.
E' la stessa forma del difetto della Norma - pagare una richiesta e buttarne
via meta' - solo al contrario: qui la richiesta era pagata e il campo non si
chiedeva nemmeno.

Tre aggiunte, e ognuna risponde a una domanda che il numero da solo non
copriva:

1. **L'andamento della giornata**, in colonne, con l'ora scelta in evidenza.
   Un indice che alle otto vale venti e alle quattordici sessanta racconta una
   giornata; detto una volta sola non dice **quando uscire**, che e' l'unica
   domanda che ci si fa guardando l'aria. Stessa forma del grafico dei raggi UV,
   e non per pigrizia: e' la stessa domanda, e due disegni diversi per la stessa
   domanda costringono a impararli tutti e due.
2. **La distanza dal limite, e chi comanda.** L'indice europeo e' il
   **peggiore** dei suoi componenti, non la loro media: dire "27, discreta"
   senza dire chi l'ha deciso lascia fuori la parte utile. Con l'ozono al
   settanta per cento del limite e le polveri al dieci, la giornata si comporta
   in un modo solo, e non e' quello delle polveri.
3. **Una riga che dice quando.** Fra le ore che restano, la migliore e la
   peggiore. Guarda **avanti** dall'ora scelta e non su tutta la giornata,
   perche' un consiglio che indica le sei del mattino alle sette di sera e' un
   consiglio per ieri.

### 23.3 I limiti sono quelli dell'OMS, non quelli di legge

Le barrette usavano le soglie europee: venticinque per il PM 2,5, cinquanta per
il PM 10. Sono il confine di cio' che e' **punibile**. L'OMS, dal 2021, mette
quindici e quarantacinque, ed e' il confine di cio' che **fa male**.

A chi sta decidendo se andare a correre serve il secondo metro. Il primo
risponde a una domanda che non ha fatto.

## 24. Il radar se n'e' andato, e al suo posto c'e' la domanda vera

> Il radar non funziona, ormai è appurato […] aggiungiamo inoltre che è limitato
> a solo l'Italia e non fa nemmeno un buon lavoro. Queste sono tot di fonti di
> dati che rainviewer utilizza, SE NON SI POSSONO USARE eliminare completamente
> questa cosa e facciamola finita.

Tre accuse, e sono vere tutte e tre.

### 24.1 "Limitato all'Italia" era letteralmente vero, ed era mio

Le coste di `RadarCoste.kt` andavano da **5° a 20,6° di longitudine** e fino a
**48,5° di latitudine**: Italia e vicini, milleun­centodue punti ritagliati da
Natural Earth per la finestra del radar *italiano* - quello del Dipartimento
della Protezione Civile, per cui la carta era nata.

Quando la fonte e' passata a RainViewer, che e' mondiale, **il fondo e'
rimasto quello**. A Forli' e a Noceto funzionava. A Bergen, a Tokyo, a Nairobi
la sala mostrava un rettangolo vuoto con due cerchi, un puntino e la pioggia
che galleggiava sul niente. Nessuno se n'e' accorto per giri e giri, perche'
tutte le prove si facevano su localita' italiane.

E' la stessa forma del difetto di 12-ter - una scala europea applicata a Tokyo -
e stavolta non era nemmeno una scala: era il disegno del mondo.

### 24.2 "Non funziona" era la conseguenza di un limite dichiarato

Due ore di storico contro una barra di ventiquattro, per sette giorni. Tre ore
su centosessantotto. La sezione 19 quel limite lo aveva dichiarato bene, la 20
aveva provato a riempirlo col modello, la 22 aveva tolto il ripiego perche'
quaranta chilometri non stanno accanto a un chilometro.

Tre sezioni di diario per arrivare dove si poteva arrivare subito: **una fonte
che risponde per il due per cento delle ore che l'interfaccia offre non e' una
funzione, e' una demo.**

### 24.3 Le fonti di RainViewer non si possono usare

L'elenco mostrato sono i servizi meteorologici nazionali che RainViewer
aggrega: una settantina, dal Met Office all'Agenzia giapponese, da Environment
Canada all'ARPA Veneto. Usarli direttamente vorrebbe dire scrivere e mantenere
settanta lettori, ognuno col proprio formato, proiezione, autenticazione e
licenza.

Ma non e' nemmeno quello il punto. Il punto e' che **in quell'elenco c'e'
"Protezione Civile — Italian Civil Protection Department"**, cioe' esattamente
la fonte su cui questo progetto ha gia' sbattuto tre volte: nove indirizzi
diversi, cinque intestazioni diverse, e infine dal telefono su rete italiana con
un agente che dichiarava nome e indirizzo del progetto. **403, sempre.**

RainViewer quei dati li ha per accordi che noi non abbiamo. Il suo elenco di
fonti non e' un elenco di porte aperte: e' l'elenco di cio' che lui puo' aprire
e noi no. Non c'era una scorciatoia da trovare, e cercarla avrebbe ripetuto
l'errore della sezione 18 con settanta servizi invece che con uno.

Quindi: **tolto tutto.** La carta, le coste, il repository, i quattro stati,
le due sonde di CI, i due file di test, lo script delle coste, e la seconda
porta di `Http.kt` che esisteva solo per scaricare immagini.

### 24.4 Al suo posto: quando piove

La domanda che uno si fa guardando la pioggia non e' "dove sta la macchia": e'
**devo uscire adesso, mi bagno?**. I dati quella risposta ce l'hanno sempre -
per tutte le ore e per tutti e sette i giorni - e non la si mostrava.

> Comincia fra 3 ore, verso le 14:00: circa 3 ore, 4,2 mm in tutto.

Guarda **avanti** dall'ora scelta e su tutte le ore che il modello ha, non sul
solo giorno mostrato: una pioggia che comincia a mezzanotte e mezza non e'
"domani", e' fra novanta minuti. Se sta gia' piovendo, dice **quando smette**,
che e' l'altra meta' della stessa domanda.

Sotto, i sette giorni in colonne di millimetri, **toccabili**: la barra in
fondo sceglie l'ora, ma il giorno si cambia solo da "La settimana" - vedere che
giovedi' piove e non poterci andare da qui vorrebbe dire uscire, scorrere,
tornare.

### 24.5 Cosa si porta via questa sezione

Tre giri di CI per il DPC, quattro per RainViewer, due per la previsione a
griglia. Tutto per una funzione cancellata.

Non e' stato inutile - la carta a tessere, la proiezione di Mercatore e la
lettura della maschera di copertura erano codice corretto e provato - ma la
domanda che avrebbe risparmiato tutto si poteva fare al primo giorno:

**Quante delle ore che l'interfaccia offre questa fonte sa coprire?**

Due su ventiquattro. La risposta era disponibile prima di scrivere una riga, e
nessuno l'ha chiesta.

---

## 25. La colonna diventa un cursore, e tre numeri diventano tre porte

Quattro rilievi arrivati guardando l'app in mano, non gli scatti: la colonna
delle scorciatoie si tocca e basta, il pannello ha una maniglia che promette un
gesto che non esiste, i riquadri di Sala I dicono senza portare, e la scala
oraria dei raggi UV, a un'ora dispari, non si legge.

Hanno una cosa in comune: **nessuno di questi e' un dato sbagliato.** Sono tutte
promesse - un segno che dice di poter fare una cosa, o che non dice di poterla
fare - e si vedono solo usando l'app, mai leggendo il codice.

### 25.1 Sette bottoni erano sette bottoni

La colonna sul fianco destro portava a una sala per tocco. Dalla settima alla
seconda sono due tocchi e due animazioni, e in mezzo non si vede niente di
quello che si sta saltando.

Adesso **si tiene premuto e si scorre**: la sala e' quella sotto il dito, senza
staccarlo, con un colpetto a ognuna che si attraversa. E' lo stesso gesto della
barra delle ore in fondo allo schermo, e questo e' meta' del punto: una cosa che
si trascina in meno di un'app non e' una cosa in meno da imparare, e' una cosa
in meno che risponde come dovrebbe.

Tre note di mestiere:

- **Il trascinamento sta sul contenitore, i tocchi sui dischi.** Compose li
  separa da se': il figlio riceve per primo, e finche' il dito non ha superato
  la soglia di slittamento resta un tocco; superata la soglia il rilevatore del
  genitore consuma il movimento e il tocco del figlio si annulla da solo.
  Consumare non e' un dettaglio: senza, alzando il dito partirebbe anche il
  salto animato della sala d'arrivo, sopra il carosello che ci sta gia'.
- **Trascinando non si anima.** Un tocco e' un salto e la molla lo racconta; un
  trascinamento il racconto ce l'ha gia' - e' il dito - e animare ogni sala
  attraversata vorrebbe dire inseguirlo con mezzo secondo di ritardo. Da qui
  `portaA` accanto a `vaiA` nella Shell: stessa destinazione, senza molla.
- **La chiave del `pointerInput` porta anche `movimento`.** Il blocco si ricorda
  com'era alla chiave; senza, chi spegne le animazioni ad app aperta
  continuerebbe a sentire i colpetti di un blocco scritto quando erano accese.

**Il gesto e' fotografato mentre e' in corso**, e non e' un vezzo: a dito
alzato il cartellino non c'e' piu', quindi uno scatto a gesto finito
proverebbe soltanto che si e' arrivati da qualche parte. `capture.sh` usa
`input motionevent`, che tiene premuto fra un comando e l'altro - la stessa
tecnica della rotazione della luna - e scatta a meta' del trascinamento:
`chiaro-d11b-colonna-trascinata.png`. I pixel dei bersagli vengono dalla
densita' vera, chiesta a `wm density`: dedurla dalla larghezza dello schermo e
dai 393 punti del Pixel 6 "da scheda tecnica" sbaglia del cinque per cento - il
profilo dell'emulatore e' 411 punti a 420 dpi - e su sette bersagli in colonna
sono quaranta pixel, cioe' il dito sul bersaglio sbagliato.

Insieme al gesto sono arrivate due cose che la colonna non aveva:

**Il cartellino col nome**, accanto alla sala sotto il dito e **solo mentre si
trascina**. Sette glifi da sedici punti sono riconoscibili quando si sa gia' cosa
sono; la prima volta no, e chi trascina sta appunto cercando. Fisso sarebbe
sette etichette perenni addosso al cielo, cioe' la cosa che questa colonna e'
nata per non essere. Sta dentro un riquadro `matchParentSize`, e non e' pulizia:
un `Box` si misura sul figlio piu' largo, quindi un nome lungo il triplo di un
bersaglio avrebbe allargato il riquadro e fatto scivolare la colonna verso il
centro dello schermo a ogni trascinamento.

**Il nome anche per chi non vede.** Le icone hanno la descrizione nulla - giusto,
sono decorazioni dentro un comando - ma il comando un nome non ce l'aveva, e
TalkBack leggeva sette "pulsante" in fila. Il nome della sala lo sapeva gia'
l'enum; il ruolo e' `Tab`, perche' sono pagine sorelle e non azioni.

### 25.2 Il disco cresce dove sei

I sette dischi erano larghi uguale e cambiavano solo tinta. Sette pastiglie
piene da trentotto punti in fila sono una barra bianca addosso al cielo, e la
sala corrente si riconosceva **solo** dal colore: al sole, o con un cielo
terracotta dietro, quella differenza si assottiglia.

Adesso il raggio scorre con la stessa frazione di pagina del colore - ventisei
punti dove non sei, trentotto dove sei - quindi la colonna a riposo e' una fila
di puntini con una pastiglia sola. Il limite in basso lo detta il glifo e non il
gusto: le icone restano larghe da sedici a venti punti a qualunque raggio, e
sotto i ventisei il disco smetterebbe di contenerle.

**Il bersaglio non si muove di un punto**: quarantotto per quarantotto, sempre.
Quello lo misura il polpastrello e non l'occhio, ed e' la correzione della
sezione 15.3 - che non si ripaga per fare ordine nel disegno.

### 25.3 La maniglia prometteva il gesto sbagliato

In cima a ogni pannello c'era una maniglia: cinquantadue punti per cinque, al
centro. Diceva "questa cosa sta sopra un'altra". Quello che **legge** chi la
vede e' un'altra cosa: una maniglia orizzontale al centro di una scheda e' il
segno con cui mezzo mondo apre un foglio a cassetto, cioe' si trascina in
verticale.

In verticale il pannello non si trascina. Scorre il suo contenuto quando non ci
sta (sezione 16.4) e per cambiare sala si va di lato (sezione 23.1). Un comando
che promette un gesto inesistente costa piu' di un comando assente, e questo lo
prometteva sette volte su sette.

E' sparita. I diciannove punti che occupava tornano al cielo: il pannello e'
ancorato in basso, quindi accorciarlo non sposta niente verso il basso, scopre
in alto. Il margine superiore passa da venti a ventiquattro perche' il titolo
non si appoggi al raggio dell'angolo, che qui e' largo.

### 25.4 Vento, umidita' e luna erano tre numeri muti

I tre riquadri di Sala I dicevano un valore e finivano li'. Sono pero' anche le
tre domande che quella schermata apre senza chiuderle - *undici chilometri
all'ora da dove?*, *e nelle prossime ore?* - e la risposta sta gia' nella
galleria, tre o quattro sale piu' in la'. L'unico modo di arrivarci era cercare
il glifo giusto in colonna, cioe' sapere gia' quale sala risponde a quale numero.

Adesso il riquadro **e'** il collegamento, esattamente come i sei di Sala II, e
con lo stesso segno: la freccetta. Non e' decorazione - e' l'unica cosa che
distingue una cella che porta da una che si limita a dire, e senza di lei la
scorciatoia si scoprirebbe toccando a caso tre riquadri che sembrano etichette.
Due segni diversi per la stessa promessa sarebbero due cose da imparare invece
di una.

Dove portano: vento a Sala VI, luna a Sala IV, e di giorno - dove al posto della
luna c'e' l'indice UV - a Sala VII. **L'umidita' porta a "La pioggia" e non a
"L'aria"**: l'acqua sospesa e l'acqua che cade sono la stessa storia a due
stadi, mentre Sala V parla di polveri e biossidi, che con la percentuale di
umidita' non c'entrano niente.

### 25.5 La scala UV si rompeva a un'ora dispari

> Nei raggi UV i numeri non si leggono.

E' la sezione 16.1 che torna, dalla parte che allora non si era guardata. La
correzione di 15.2-ter aveva dimezzato le etichette - le ore pari, piu' quella
scelta - e il seguito di 16.1 aveva sistemato l'allineamento delle barre.
Nessuno dei due ha guardato **cosa succede quando l'ora scelta e' dispari**: la
scala diventa `... 12 13 14 ...`, tre numeri in trenta punti, uno addosso
all'altro. Alle tredici, che e' l'ora in cui uno guarda i raggi UV. Ed e' l'ora
con cui la CI fotografa il tema chiaro: lo scatto `chiaro-d11-uv.png` ce l'ha
sempre avuto dentro.

Adesso le tacche sono una ogni tre ore - 06, 09, 12, 15, 18 - e ognuna ha tre
colonne per se'. L'ora scelta resta un'eccezione, perche' non e' una tacca della
scala: e' la risposta alla domanda "dove sono". Ma quando cade **accanto** a una
tacca, a spostarsi e' la tacca: la scala sa contare anche senza il 12, mentre
quella risposta non ha nessun altro posto in cui stare.

L'altra meta' del difetto era l'inchiostro. `inkFaint` e' il grigio delle
etichette dentro una cella, dove sopra c'e' sempre un valore nero a fare da
appiglio; sotto le colonne non c'e' nient'altro da leggere, e dieci punti di
corpo in grigio chiaro su carta chiara si guardano senza vederli. Adesso e'
`inkSoft`.

Resta - e va tenuta - la stringa vuota al posto del `Text` assente: e' la
correzione di 16.1, e un `if` attorno all'etichetta rimetterebbe le barre a
quote alterne.

---

## 26. Pioveva e non si vedeva, nevicava e cadeva grandine

Tre rilievi arrivati guardando l'app in mano: *l'animazione della pioggia non
succede quando dovrebbe*, *controlla neve e grandine*, *il fulmine va reso piu'
evidente*.

Il primo e' quello che insegna qualcosa: **la pioggia c'era, l'orologio girava,
le gocce scendevano - e chi guardava non le vedeva.** Un'animazione che nessuno
vede e un'animazione che non c'e' sono, da fuori, la stessa cosa; e siccome il
codice funzionava, nessun controllo automatico poteva accorgersene. Serviva uno
scatto guardato con l'occhio di chi non sa cosa dovrebbe esserci.

### 26.1 Sette tratti su uno schermo non sono una pioggia

Le corsie di caduta sono quattordici, con **una goccia ciascuna**, e la scheda
copre la meta' bassa dello schermo: restano sette segni visibili, sparsi su
milleduecento pixel d'altezza. Contati sullo scatto `scuro-3-pioggia.png`, i
pixel di colore acqua erano **millecento su un milione**: un decimo di punto
percentuale.

L'altra meta' del difetto era la tinta. `acqua` e' `#5B8AA5`, un azzurro medio
tarato sul cielo di giorno. Il cielo di notte sta fra `#0D1420` e `#2B2F3D`:
quell'azzurro ci finisce **dentro**, e una goccia con lo stesso valore di
luminanza del fondo non e' una goccia tenue, e' una goccia assente.

Due correzioni, nessuna delle quali tocca il battito che si sente in mano:

- **Ogni corsia porta una fila.** `Corsie.ripetizioni` da' quattro gocce alla
  pioggia, cinque alla neve, due alla grandine, sfalsate lungo la stessa
  discesa. La **corsa** resta una per corsia - e' lei che `impatti` conta - e la
  prima della fila ha scarto zero, quindi tocca terra esattamente quando il
  telefono batte. Le altre le stanno dietro a distanze appena irregolari: una
  fila spaziata a dovere si legge come una cucitura.
- **La tinta schiarisce col buio.** Si interpola verso il bianco ghiaccio con
  `notte` e, in parte, con la copertura. Non e' una licenza: di notte non si
  vede l'acqua, si vede la luce che ci rimbalza sopra.

Misurato sullo stesso scatto dopo: **3248** pixel d'acqua contro 1172, e a
occhio la differenza e' fra "graffi sul vetro" e "piove".

La fioritura a terra - la `FIORITURA` che stava in `Corsie` senza che la
leggesse nessuno - **non torna**. Il pannello arriva a meta' schermo e la riga
dove le gocce toccherebbero sta sotto di lui: una cosa dipinta dove nessuno la
vede costa e non si nota quando si rompe.

### 26.2 Lo stesso codice diceva neve a una strada e grandine all'altra

Qui il difetto non era di resa, era di **due verita' sullo stesso fatto**.

`salaConditionOf` metteva i codici 77, 85 e 86 - granuli e rovesci di **neve** -
fra la `GRANDINE`. `Wmo.family` metteva gli stessi tre fra la `NEVE`. La scena
chiedeva il ghiaccio alla prima e la neve alla seconda, e siccome tutte e due
rispondevano di si', per quei tre codici cadevano **chicchi e fiocchi insieme**:
due sostanze dalla stessa nuvola, nello stesso istante, per lo stesso codice.
Negli scatti non si notava - a quella taglia un chicco e un fiocco sono due
dischi chiari - e per mesi e' stato li'.

E la neve vera, 71, 73 e 75, cadeva nel ramo `code >= 51`, cioe' fra le piogge:
sopra una nevicata si leggeva **"Pioggia nella notte"**. Lo scatto
`scuro-5b-neve.png` lo diceva a lettere alte quindici punti.

Una strada sola, e passa da dove stanno i codici:

- `salaConditionOf` chiede la neve a `Wmo.family`, che e' l'elenco vero;
- `GRANDINE` esce dall'enum e al suo posto entra `NEVE`, con le sue quattro
  didascalie. La grandine non sparisce: **torna dov'e' davvero**, dentro
  `TEMPORALE_GRANDINE`, perche' i soli codici WMO che la nominano sono 96 e 99 e
  tutti e due dicono *temporale con grandine*. Una grandine senza temporale, in
  questi dati, non esiste;
- la neve smette di portare il tema scuro. Ci stava perche' era etichettata
  grandine, e una cella di grandine porta il buio del fronte che la fa; una
  nevicata e' il contrario, ed e' la giornata piu' chiara dell'anno.

`CadutaTest` prova la cosa nella forma in cui puo' rompersi di nuovo: non "il
codice 86 fa questo", ma **nessuno dei ventotto codici fa cadere due sostanze
insieme**, e la neve cade per tutti e soli i codici di famiglia neve. Chi
aggiunge una famiglia domani trova quella riga.

Lo scatto di conferma e' insolito: `scuro-5b-neve.png` e
`scuro-5b2-rovesci-di-neve.png` - codici 73 e 86 - sono **byte per byte lo
stesso file**. E' esattamente cio' che si voleva dimostrare: due codici della
stessa famiglia, adesso, dipingono lo stesso cielo.

### 26.3 Il fulmine non aveva una saetta

Era un alone tondo in alto a destra, sempre nello stesso punto, acceso e spento
da due rampe lineari. Diceva "temporale" con la coda dell'occhio, e va bene; ma
di un fulmine non aveva **niente**: nessun canale, nessuna biforcazione, nessuno
sfarfallio, e un centro fisso che dopo il secondo giro si legge come una macchia
dello schermo.

Adesso sono tre strati, in ordine di quanto sono larghi:

1. **Il velo su tutta la tela**, colline comprese: un fulmine illumina il
   paesaggio, non solo la nuvola che lo fa.
2. **L'alone** attorno al punto da cui scende il canale, che tiene insieme il
   velo e la saetta.
3. **La saetta**: dodici nodi, zigzag laterale, deriva che cresce col quadrato
   della discesa - un fulmine scende dritto e sbanda, non serpeggia - e due rami
   corti che se ne staccano. Quattro passate sullo stesso `Path`, dall'alone
   largo e tenue al nucleo bianco: e' cosi' che si dipinge una cosa che
   **emette** luce invece di rifletterla. Un `Path` per passata e non un tratto
   per segmento, se no a opacita' parziale ogni giunto diventa un puntino piu'
   chiaro e il canale sembra una collana di perle.

Il tempo e' cambiato quanto il disegno. Tre scariche per ciclo, ognuna che
**sale di colpo e si spegne per esponenziale**: una scarica arriva al massimo in
microsecondi - un fotogramma non la vede salire - e il canale caldo si raffredda
perdendo ogni volta una frazione di quel che resta. La vecchia rampa in salita
dava al fulmine il tempo di *arrivare*, e un fulmine che arriva non e' un
fulmine. Il canale e' lo stesso dentro un colpo e diverso a ogni colpo, che e'
come si comportano le riprese di una scarica vera.

**Il velo e' sceso dal novantacinque al diciotto per cento**, ed e' la parte
contro-intuitiva. Lo scatto del temporale, prima, era una macchia chiara in cui
non si distingueva ne' una nuvola ne' un chicco: il riverbero da solo cercava di
fare tutto il lavoro. Un fulmine vero stacca il paesaggio in controluce, non lo
cancella - e a bucare il cielo ci pensa la saetta, che e' stretta e puo'
permettersi il bianco pieno.

### 26.4 E lo scatto della neve era stato letto male una volta

Nel giro precedente `scuro-5b-neve.png` mostrava un cielo notturno di nevicata
**con la scheda in tema chiaro**: due cose che insieme non esistono. Per un
momento e' sembrato un difetto della tavolozza.

Non lo era. `restart_with` aspettava i dati e scattava subito dopo, mentre il
tema arriva con una molla partendo dal ripiego diurno - la stessa cosa che
`cielo()` aveva gia' imparato e per cui ha un `sleep 3`. La fotografia era
presa a meta' di una dissolvenza. Adesso `restart_with` aspetta come `cielo()`,
e gli scatti dicono la verita'.

**E' la lezione della sezione 16.1 in un'altra forma**: quando uno scatto mostra
una cosa impossibile, la prima domanda non e' "quale colore ho sbagliato" ma
"cosa stava succedendo mentre scattavo".

## 27. Snellire: cio' che si e' tolto, cio' che si e' scoperto, cio' che resta

Richiesta: *impieghiamo del tempo per snellire l'app per renderla piu'
ottimizzata a livello di scorrimento e utilizzo & pulizia delle cartelle,
sottocartelle e pulizia codice*.

Tre risposte hanno governato tutto il giro, e vale la pena tenerle scritte
perche' sono loro ad aver deciso cosa non fare: **l'unico scatto che si sente
davvero e' la barra delle ore** (il resto e' preventivo), **il cielo deve
restare identico** - non "quasi" - e **riorganizzare le cartelle e' permesso**,
non solo cancellare.

Risultato in numeri: `res/` da 1,95 MB a 956 KB, il Kotlin di `main` da 17.545
righe su 69 file a 16.452 su 65, le prove da 13 classi a 16 (87 in tutto), gli
scatti della galleria da 41 a 34 - sette di meno perche' sette non ritraevano
piu' niente.

### 27.1 Il guasto vero di questo giro non e' nel codice: e' nel come l'ho letto

Il commit che toglieva `forcedYawDeg` dal costruttore di `UiState` si e'
portato via, insieme a lui, **tutto il blocco che lo precedeva**: `forecast`,
`air`, `airUnavailable`, `alerts`, `forcedAlert`, `selectedDay`,
`selectedHour`, `forcedWeatherCode`. Cioe' il dato. L'app non compilava piu'.

Ed e' rimasta cosi' per **tre commit**.

Non perche' la CI non l'abbia detto: la CI non ha mai avuto occasione di dirlo.
I due giri in mezzo sono stati **annullati** dal push successivo - il workflow
ha una concorrenza che ferma il giro in corso quando ne arriva uno nuovo - e io
ho letto `cancelled` come "non ancora finito" invece di andare a vedere. Ho
continuato a spingere commit sopra un albero rotto, e ogni push cancellava il
giro che me l'avrebbe detto.

**La regola che ne esce e' secca: un giro annullato non e' un giro passato.**
Se si spinge piu' in fretta di quanto la CI compili, la CI non sta piu'
verificando niente, sta solo consumando minuti. Qui l'SDK Android non c'e' - la
rete non lo lascia scaricare - quindi la CI e' **l'unico compilatore**, e
aspettarla non e' pazienza, e' l'unico modo di sapere.

### 27.2 Il metodo di verifica che avevo proposto non reggeva, e l'ho scoperto misurandolo

L'idea era pulita: la cattura congela l'orologio della scena a `t = 0`, quindi
un rifacimento che non cambia i pixel deve produrre gli stessi PNG. Misurato
contro la base salvata prima di cominciare: **il 100% dei pixel diverso su
quasi tutti gli scatti**.

Non era una regressione. I due giri erano andati a **dodici ore di distanza**,
uno di notte e uno a mezzogiorno. E scavando, il difetto del metodo e' piu'
profondo del fuso orario:

- gli scatti che non impongono l'ora prendono quella vera del runner;
- **anche quelli che la impongono si muovono**, perche' la previsione e'
  **viva**: temperature, nuvolosita', UV e qualita' dell'aria cambiano fra un
  giro e l'altro. E la nuvolosita' oraria entra nel cielo anche con `--ei meteo`
  imposto: e' la trappola #14 che funziona come deve, il dato vero decide
  *quanto* dentro il possibile;
- restano stabili solo gli scatti che impongono **ora e codice insieme**:
  misurato, `cielo-mezzogiorno-sereno` 2,4% sul cielo e 0,3% sulle schede,
  `scuro-15-notte-coperta-stelle` 0,0% e 0,3%.

Quindi **la prova forte non e' il PNG, e' il test**. Dove si puo' affermare
l'identita' si afferma nel codice, e in questo giro si e' fatto quattro volte:
`MoonPhaseTest` confronta i bit del terminatore su mille fasi, `SparsoTest` i
bit di ogni tabella contro la funzione che rimpiazza, `OrePerGiornoTest` le ore
raccolte contro il setaccio che sostituiscono, `SchemaMaterialeTest` le venti
tinte ferme contro le loro formule. Un test copre anche `t > 0`, che la cattura
non vede. Gli scatti restano per cio' per cui sono nati: che l'app parta, che
nessuna sala sia vuota, che non manchi un pezzo.

Se un giorno servisse davvero un confronto a pixel, la strada e' una previsione
finta caricata da un aggancio di cattura - `--ez fixture` - cosi' i numeri
smettono di muoversi sotto gli scatti. E' una funzione a se', non parte di una
pulizia.

### 27.3 Un controllo che a mani vuote rispondeva "tutto bene"

Stessa famiglia del giro annullato, e scoperto per caso mentre rigeneravo la
taratura. `scripts/import_audit.py` guarda **i file che gli si passano**, e
chiamato senza argomenti non ne guarda nessuno: stampava `0 da guardare`, che
e' parola per parola la riga di un albero pulito. L'ho chiamato cosi' per tutto
il giro, e ogni volta mi sono detto che era a posto.

Peggio: `--baseline` senza file scriveva una taratura **vuota**, cancellando le
ottantanove righe note - cioe' i falsi allarmi gia' esaminati - e al giro dopo
sarebbero tornate fuori tutte come se fossero nuove.

C'e' anche un secondo inciampo, piu' piccolo: la taratura tiene i percorsi come
li scrive `git`, quindi `find . -name "*.kt"` (con il `./` davanti) non fa
combaciare **nessuna** riga nota. L'invocazione buona e'
`find app -name "*.kt" | xargs python3 scripts/import_audit.py`.

Adesso lo script rifiuta di partire a mani vuote e lo dice. Chiamato come si
deve, sull'albero di questo giro segnala quattro righe nuove rispetto a prima
della pulizia, tutte e quattro falsi allarmi noti al copione - due
`LazyThreadSafetyMode`, che sta nel pacchetto `kotlin` ed e' importato d'ufficio,
un parametro di lambda destrutturato chiamato come funzione, e una funzione di
file chiamata dal suo stesso pacchetto.

**La lezione e' la stessa del giro annullato**: un controllo va letto per cio'
che ha guardato, non per cio' che ha stampato.

### 27.4 Lo scatto della barra aveva una causa a catena, e il rimedio non era dove sembrava

Ogni scalino della barra emette uno stato nuovo che ri-punta **undici molle**
con circa due secondi di assestamento. Trascinando si ri-puntano piu' in fretta
di quanto si assestino: per tutta la durata del gesto piu' due secondi
**l'albero si ricompone a ogni fotogramma**. E ogni ricomposizione pagava una
dozzina di riscansioni delle centosessantotto ore, per i valori derivati di
`UiState` che erano `get()` senza memoria.

La correzione ovvia era memorizzarli, e si e' fatta. Ma **il guadagno piu'
grosso non e' il conto risparmiato: e' la stabilita' del riferimento.**
`hoursOf` rispondeva con una lista **nuova a ogni chiamata**, e questo rendeva
inutile il `remember` della barra: la chiave cambiava per riferimento anche a
contenuto identico, quindi il confronto ne scorreva ventiquattro elemento per
elemento solo per concludere di non dover ricalcolare niente. Un `remember` che
funziona e non serve a niente e' piu' difficile da vedere di un `remember` che
manca.

**`nowIndex` non si memoizza, ed e' l'unico.** E' il candidato piu' ovvio -
chiama `Instant.now()` a ogni lettura - e congelarlo per stato vuol dire che
scavalcando l'ora l'ora corrente non si sposta piu', e la pastiglia "torna a
ora" continua a offrire un'ora che e' gia' adesso. E' la famiglia della
trappola #7, gia' pagata **su questa stessa barra** (sezione 25).

### 27.5 La tabella e' un memo davanti alla funzione, non un rimpiazzo

`sparso(i, sale)` e' un seno e una parte frazionaria, dipende dal solo indice, e
girava circa millesettecento volte per fotogramma. Adesso le stelle, il
pulviscolo e le corsie leggono tabelle calcolate all'avvio.

Ma le stelle cadenti chiamano `sparso(quale, ...)` dove `quale` **cresce col
tempo e non ha un limite**. Una riscrittura a sola tabella sarebbe passata tutti
gli scatti della CI - che congelano l'orologio a zero, dove `quale` vale 0 o 101
- e sarebbe andata fuori indice sul telefono dopo pochi secondi. La cattura non
avrebbe potuto dirlo: e' esattamente il caso che non fotografa.

Due altri vincoli tenuti apposta: in tabella va il **risultato nudo** di
`sparso`, non il valore composto, cosi' l'aritmetica che lo usa resta la stessa
parola per parola; e le tabelle delle corsie stanno **dentro `Corsie`**, non nei
punti di disegno, perche' le stesse funzioni le legge chi fa vibrare il telefono
e due strade parallele mandano il colpetto fuori tempo rispetto alla goccia.

### 27.6 Un commento che diceva la verita' e che nessuno aveva letto fino in fondo

Dentro `toColorScheme` c'era scritto da sempre: *e' l'unica coppia dello schema
che si muove durante il giorno*. Vero. E intanto le altre venti tinte si
rifacevano insieme a lei, a ogni fotogramma in cui il cielo si muove, ognuna con
una ricerca di contrasto su colori che sono **costanti scritte tre schermate
piu' su, nello stesso file**.

Il piano prevedeva di spostare le due `Surface` a schermo pieno su
`palette.schermoPieno` per far uscire `MeteoTheme` dal sottoalbero animato.
Guardando il codice, la premessa non reggeva: `surface` nello schema **e' gia'
una costante**, quindi quei due lettori non erano loro a tenere in vita il
calcolo. Spostarli sarebbe stato un colore cambiato - invisibile, perche' i due
pannelli si dipingono gia' il proprio fondo da bordo a bordo - in cambio di
niente. Portare fuori le venti costanti costa meno e ottiene di piu'.

### 27.7 Le cartelle adesso dicono cosa contengono

| Da | A | Perche' |
|---|---|---|
| `ui/common/` | cancellata, `MinTouchTarget` in `ui/theme/Misure.kt` | Settecentosessanta righe, **una sola viva**. |
| `ui/render3d/` | `widget/paint/render3d/` | Stava sotto `ui/` e nessuna schermata la usava. |
| `ui/home/MoonPhase.kt` | `data/MoonPhase.kt` | Non esiste nessuna schermata "home": e' astronomia. |
| `docs/` | cancellata | Conteneva solo la guida all'acquerello, insieme ai timbri. |

Dopo: `ui/` e' solo l'interfaccia del telefono, `widget/` tutto cio' che disegna
i widget, `data/` i dati e i conti che non sanno di Compose.

E la CI adesso **stampa quanto pesa l'APK** a ogni giro, risorse e dex separati.
Il megabyte tolto in questo giro si e' dovuto dedurre scaricando due file e
confrontandoli a mano: la prossima crescita si vedra' il giorno in cui succede.

### 27.8 I difetti noti che restano, scritti perche' non si riscoprano da zero

- **Le icone delle barre di sistema si decidono sul cielo sbagliato.**
  `MeteoApp` chiama `SystemBarIcons` con `colors.skyZenith` e
  `colors.skyHorizon` - la sfumatura del **benvenuto** - mentre dietro le barre,
  da quando c'e' Sala, c'e' la carta di `SalaPalette`, che si dipinge da bordo a
  bordo con tinte sue. Il commento sopra quella chiamata lo dice gia' a meta':
  *qui restano solo i colori del benvenuto*. Ma la chiamata copre tutta l'app,
  non solo il benvenuto, quindi con la carta chiara sopra un cielo notturno le
  icone possono uscire chiare su chiaro.

  La scelta e' stata di **segnalarlo e non toccarlo**: correggerlo cambierebbe i
  pixel delle barre in molti scatti, cioe' proprio il segnale su cui questo giro
  si verifica. E non e' la riga sola che sembra: le fermate vere sono quelle di
  `SalaShell` (`cieloStops`), che a `MeteoApp` non arrivano. O si porta la
  chiamata dentro Sala - dove la tavolozza c'e' gia' - o si fa salire la
  tavolozza fin qui. La prima e' la strada breve, e lascia a `MeteoApp` solo il
  benvenuto, che e' quello che il commento diceva di gia'.
- **Le tre lune.** Tre disegni per tre usi - il cielo di Sala, la sala della
  luna, il widget - circa duecentotrenta righe. Unificarle cambia dei pixel.
- **Le due tavolozze del cielo.** Stessa ragione.
- **Il baseline profile** (avvio e primo scorrimento) vuole un modulo di
  benchmark e un giro di CI dedicato. Vale, ma e' un lavoro a se'.

## 28. Diceva nuvoloso, e fuori il cielo era aperto

Tre rilievi, arrivati guardando l'app in mano: *mancano delle info nelle
impostazioni essenziali*; *da calcolare meglio quanto coperto il cielo deve
essere, anche quando e' leggermente coperto il sole*; *vorrei che il sole e la
luna siano sempre illuminati - capita spesso che segna nuvoloso e nell'app il
sole non si vede quasi per niente, e nell'effettivo il cielo non e' cosi'
coperto come dice l'app*.

Il secondo e il terzo sono **lo stesso difetto visto da due lati**, e la causa
era una sola.

### 28.1 Un numero gonfiato, e tre sintomi

`scenaBersaglio` calcolava `copertura = maxOf(nuvolosita' vera, pavimento del
codice)`, e il pavimento del nuvoloso vale 0,45. Sopra, `salaConditionOf`
mandava in NUVOLOSO tutto quello che aveva `code >= 1` - e **il codice WMO 1
significa "prevalentemente sereno"**, una o due ottavi di cielo. Quindi con il
cinque per cento di nuvole vere la sala lavorava sul quarantacinque.

Da li' scendeva tutto il resto:

| Sintomo | Dove | Con copertura 0,45 |
|---|---|---|
| Il cielo troppo grigio | `livelloCielo = copertura * 3` | livello 1,35 su 4 |
| Le nuvole troppe e troppo piene | `presenza = (copertura - i*0,13)/0,24` | **tre masse su cinque**, alfa fino a 0,94 |
| Il sole che sparisce | `velo = 1 - copertura*0,92` | disco al **59 %** |

**Una causa, tre manopole, e nessuna delle tre era rotta.** E' la ragione per
cui non si e' toccata nessuna delle tre: corretto il numero in ingresso, il
cielo e le nuvole sono rientrate da sole. Se si fosse messo mano anche a quelle
sarebbe stato impossibile dire quale avesse fatto cosa.

### 28.2 La doppia verita' sul codice 1, che era gia' scritta

`Wmo.family` classifica lo zero **e l'uno** come `ASCIUTTO` da sempre.
`salaConditionOf` diceva il contrario, nello stesso progetto, sullo stesso
numero - e il KDoc di quella funzione, due righe sopra, racconta gia' al passato
la stessa storia a proposito della neve: *"Da due verita' sullo stesso codice
nasceva un cielo in cui cadevano chicchi e fiocchi insieme... Una sola verita',
e sta dove stanno i codici."*

Il disaccordo non era teorico: `glifoDi` - che legge la famiglia e la
nuvolosita' vera - disegnava il **sole** mentre la sala dietro dipingeva il
coperto. Lo stesso numero, due risposte, sulla stessa schermata.

### 28.3 Il pavimento difendeva qualcosa che li' non c'era

`coperturaMinima` esiste per la **trappola #14**: se il codice WMO dice che
piove deve piovere, e una pioggia che cade da un cielo vuoto e' lo stesso
errore delle gocce che non cadevano. Giusto - e si applicava anche alle due
condizioni **asciutte**, dove non c'e' nessuna precipitazione da difendere.

Adesso la regola ha due meta', e a separarle e' `cade`: dove cade qualcosa il
minimo resta un pavimento e il conto e' identico a prima, `null` compreso; dove
non cade niente il dato vero comanda da solo, e il minimo resta come **ripiego**
per quando la nuvolosita' oraria manca - dal quarto giorno in poi i modelli a
corto raggio danno i totali e non le ore.

La regola sta in un posto solo, `coperturaDi`, e si chiama da due. La barra
delle ore aveva gia' smesso di ricopiarsi la **tabella** dei minimi; la
**formula** intorno era rimasta ricopiata, e sarebbe stata la prossima a
divergere.

### 28.4 Il velo piu' severo dell'app stava sugli astri

`velo = 1 - copertura * 0,92`. Il confronto dice tutto: le stelle perdono al
massimo il 72 per cento, gli uccelli il 62, il pulviscolo il 55, **il sole e la
luna il 92**. Il disco finiva all'otto per cento sotto un cielo chiuso, e al
quattro sotto una nevicata - perche' li' si moltiplicava anche per il fattore
della neve, che era contarla due volte: la neve chiude gia' il cielo per la sua
strada, con un minimo di copertura a 0,85 e con la tavolozza `CieloNeve`.

Il commento che accompagnava quella riga - *dietro un fronte non si vede ne'
l'uno ne' l'altra* - **e' vero del fronte**, dove la copertura sta sopra 0,9.
Veniva applicato linearmente anche al poco nuvoloso, dove non lo e' per niente.
In CONTESTO non c'era nessuna sezione che motivasse quel numero: era stato
scritto una volta e non piu' guardato.

Adesso il coefficiente e' 0,45 con un minimo di 0,55. La curva tocca il minimo
**esattamente a copertura piena**, quindi non c'e' nessun gomito.

    copertura   0,00   0,20   0,45   0,60   0,80   1,00
    prima       100 %   82 %   59 %   45 %   26 %    8 %
    adesso      100 %   91 %   80 %   73 %   64 %   55 %

Sotto un temporale il sole resta un chiarore dietro le nuvole, che e' quello che
si vede davvero; a coprirlo ci pensano le masse, che gli passano davanti - si
disegnano dopo di lui, con alfa fino a 0,96.

E' sparito anche `if (velo <= 0.01f) return`: con un minimo a 0,55 non puo' piu'
scattare, e **un ramo irraggiungibile e' un ramo che mente**.

### 28.5 La prova ha corretto me

`cade solo dalle famiglie che nominano qualcosa che cade` era scritta, la prima
volta, come *"da un codice asciutto non cade niente, da tutti gli altri si'"*.
La CI l'ha bocciata: cosi' formulata pretendeva che cadesse roba anche dal
**nuvoloso**, che e' asciutto quanto il sereno.

E' la stessa confusione che aveva prodotto il difetto di tutto questo giro -
trattare *ci sono delle nuvole* come *sta succedendo qualcosa* - ed e' andata
bene che a scriverla in una prova l'abbia detta a voce alta, dove qualcuno la
controlla.

### 28.6 Gli scatti nuovi ritraevano lo stesso cielo quattro volte

Aggiunti i due scatti che mancavano - `mezzogiorno-quasi-sereno` e
`mezzogiorno-poco-nuvoloso` - li ho scaricati dal giro di CI per guardarli in
fila. Due dei quattro avevano **lo stesso md5**:

    ea3a76aa...  cielo-mezzogiorno-coperto.png
    ea3a76aa...  cielo-mezzogiorno-poco-nuvoloso.png

E gli altri due, misurata la luminanza della fascia di cielo, stavano a 159,5
contro 159,5.

**Non era un guasto del cielo: era una conseguenza diretta della correzione.**
Da quando la copertura, dove non cade niente, viene dalla nuvolosita' vera e
non piu' da un pavimento per condizione, imporre il **codice** non cambia piu'
quanto il cielo appare chiuso - decide le parole, non il grigio. E gli scatti
imponevano solo il codice: `--ei meteo 2` e `--ei meteo 3` alla stessa ora
fotografano la stessa nuvolosita' vera, quindi la stessa scena.

Era la galleria che ricominciava a mentire, **vista mentre succedeva** - la
stessa famiglia dei sette scatti tolti nella sezione 27, con la differenza che
questa volta li ho beccati prima di lasciarli li' per mesi.

La cura e' un aggancio in piu', simmetrico a quello del codice:
`--ei nuvolosita` impone la percentuale, `SalaShell` la legge prima del dato
vero come gia' fa col codice, e i quattro scatti di mezzogiorno impongono
adesso **ora, codice e nuvolosita'**: 5, 20, 55 e 95 per cento.

La lezione e' la stessa della sezione 2 di questo giro, letta al contrario:
**quando si toglie a un aggancio il potere di cambiare la scena, gli scatti che
lo usavano smettono di ritrarre qualcosa** - e non lo dicono, perche' un PNG
identico a un altro ha la stessa faccia di un PNG giusto.

### 28.7 Le impostazioni avevano solo comandi

Quattro selettori, cinque interruttori, una riga di navigazione: **zero
informazione**. La regola che governa quella schermata - *ogni interruttore qui
dentro comanda qualcosa* - e' giusta e non c'entra: vale per i comandi, e a
furia di applicarla era rimasta una pagina che sa solo ricevere ordini e non
risponde a una domanda.

Tutto quello che e' entrato **esisteva gia' nel codice** e non lo leggeva
nessuna schermata:

- la **versione**, motivata per iscritto in cima a `build.gradle.kts` e mai
  letta da una riga di Kotlin;
- l'**ultimo scarico**, che aveva accanto un commento il quale dichiarava *"la
  schermata delle impostazioni lo dichiara"*. Non lo dichiarava: era un residuo
  del vecchio `ui/settings/SettingsScreen.kt`. Adesso il commento e' vero;
- il **modello meteo attivo**, che cambia i numeri della previsione e di cui non
  si poteva sapere niente;
- le **fonti**, che erano la decisione lasciata in sospeso da 8-ter;
- la **localita' per esteso**, e soprattutto **chi l'ha scelta**;
- **Aggiorna adesso**, perche' `refresh()` era pubblico senza chiamanti
  d'interfaccia e `state.error` - un messaggio gia' scritto per chi guarda - non
  aveva **un solo lettore in tutta l'app**.

### 28.8 Le note legali, e il limite dichiarato

Una schermata a parte, aperta dalle impostazioni, che contiene **solo fatti
verificabili nel codice**: i quattro indirizzi interrogati, la differenza fra
ALLERTA e AVVISO, cosa esce dal telefono, cosa resta, i tre permessi uno per
uno.

**Non sono condizioni d'uso**, ed e' scritto dentro. Un contratto lo scrive chi
pubblica l'app e se ne assume la responsabilita'.

Il pannello va registrato **dopo** le impostazioni: `BackHandler` da' la
precedenza all'ultimo registrato, ed e' la stessa trappola gia' pagata con "Le
localita'".

**Resta un blocco da riempire alle fonti**: la frase di attribuzione che
Open-Meteo richiede e i termini di MeteoAlarm per il riuso dei feed vanno
copiati verbatim dalle loro pagine di licenza. Da questo ambiente la rete non ci
arriva, e scriverli a memoria in una pagina legale sarebbe esattamente il tipo
di errore che quella pagina esiste per evitare.

### 28.9 Cosa resta fuori

- **Le condizioni d'uso vere**, come sopra.
- **`skyCloudiness`** legge ancora **solo** il codice WMO e non `cloudCover`:
  e' la seconda verita' sulla nuvolosita' che resta in piedi. Alimenta pero'
  soltanto lo sfondo del benvenuto e le icone delle barre di sistema, cioe' il
  difetto gia' registrato in 27.8 - e si corregge insieme a quello.
- **Le soglie e l'opacita' delle nuvole.** Se dopo la prova in mano fossero
  ancora troppe, la riga e' quella di `presenza` in `SalaCielo`. Non si e'
  toccata apposta: vedi 28.1.
- **Il selettore del modello meteo.** Adesso si vede quale e' attivo; sceglierlo
  vuol dire rimettere `setModel`, una riga di scelta e la ricarica, ed e' una
  funzione, non un'informazione.

---

## 29. Il cielo prende la tinta della scena d'apertura

**Le colline nella nebbia sono uscite** dalle scene d'apertura: non piacevano.
Ne restano sette (sei fuori dal periodo natalizio).

**La domanda era: la scena d'apertura come sfondo fisso dell'app?** No, per tre
motivi: i testi sopra un quadretto pieno di chiari e scuri non hanno un fondo su
cui tarare il contrasto; la scena e' a caso e non segue il tempo, quindi
racconterebbe il sole mentre piove; e un disegno animato a tutto schermo sotto
ogni pannello e' esattamente il costo della trappola #18.

**Cosa si e' fatto invece: il cielo di Sala prende i colori della scena**
(`ui/scene/TintaCielo.kt`, `tingiCielo`). Le tre fermate della sfumatura si
avvicinano in CIELAB alla tinta del cielo della scena - cima con cima,
orizzonte con orizzonte - e **la luce non si muove**: la chiarezza si ritocca
finche' la luminanza relativa torna quella della tabella, entro l'1 %. Il
contrasto dipende solo dalla luminanza, quindi `temaScuro` e i test di
contrasto restano veri. `TintaCieloTest` lo verifica su tutte le tabelle, per
ogni scena, di giorno, di notte e a meta'.

Due freni, tutti e due voluti:

- **tinge cio' che il tempo lascia neutro.** La forza cala con la croma della
  fermata: un grigio coperto ne prende la meta', l'arancione del tramonto un
  ottavo. Senza, al tramonto il cielo diventava rosa pallido e non diceva piu'
  che ora fosse;
- **sotto un fronte sparisce** (`1 - tempesta`): un temporale e' plumbeo
  qualunque quadretto si sia visto aprendo.

I colori del cielo di ogni scena stanno ora in `cieloDi`, usati sia dal disegno
sia dalla tinta: cambiarne uno cambia tutti e due. Durante la cattura la tinta
e' sempre quella del mare, perche' gli scatti restino confrontabili.
