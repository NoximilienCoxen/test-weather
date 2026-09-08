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

**Verificato sul telefono**: schermata principale, rotazione libera con
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
  quattro secondi sulla scheda, mediano e ritardi. Se non regge i 16 ms cedono
  gli strati e non il movimento - prima le persone, poi le gocce sul vetro
- **che la guardia `alive` spenga davvero la scena**: quattro secondi sulla
  scheda accanto, e i fotogrammi devono tornare a **zero**. E' l'unica cosa che
  tiene l'eccezione dentro un limite, ed e' dichiarata proprio per questo
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
finestra visibile continua a battere anche per una pagina fuori vista. **Va
misurata, non dedotta**: `dumpsys gfxinfo`, quattro secondi sulla scheda accanto,
e i fotogrammi devono tornare a zero.

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

Quarantasei prove, tutte su funzioni pure, nessun emulatore, un job `test` a se'
stante. Sono scelte per cio' che coprono, non per fare numero — e cinque di loro
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
