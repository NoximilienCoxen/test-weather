# Radar dei fulmini — specifica di progetto

Data: 7 settembre 2026. Stato: approvata la forma, da implementare.

Una schermata che mostra **dove stanno cadendo i fulmini attorno a te**, in
tempo quasi reale, disegnata come una scena e non come una mappa.

---

## 1. Cosa e' e cosa non e'

**E'** un radar personale: tu al centro, cento chilometri di raggio, i punti
dove le scariche stanno toccando. Ha una **scia**: le scariche raccolte nei giri
precedenti restano in scena sbiadendo con l'eta', cosi' si vede da dove arriva il
temporale e non solo dove si trova adesso.

**Non e'** una mappa. Niente tile, niente strade, niente libreria cartografica.
Il progetto ha ventuno dipendenze e le conta: `DeviceLocation` rifiuta
esplicitamente `play-services-location` perche' sarebbe "una dipendenza nuova su
un progetto che finora non ne ha aggiunte", e una mappa vera sarebbe la prima
dipendenza pesante della sua storia.

**Non e'** nemmeno uno strumento di sicurezza, e la fonte lo dice per prima: i
dati sono di rete commerciale ma il piano gratuito e' pensato per uso personale.
Chi deve decidere se mettersi al riparo guarda il cielo, non questa schermata.

### Perche' non si riusa il mappamondo che c'e' gia'

`render3d/WorldCoast.kt` contiene le coste del mondo ed e' stato il primo
candidato come sfondo. **Non serve**: il suo stesso commento dichiara "una decina
di gradi di fedelta', quel tanto che basta perche' l'Africa sia l'Africa", ed e'
tarato su un disco di duecento pixel. Su un cerchio di cento chilometri attorno a
una citta' non disegnerebbe nulla di riconoscibile.

Ne segue che **a questa scala non esiste alcun riferimento geografico**, e senza
mappa non ci sara'. Non e' una rinuncia: un radar vero e' fatto cosi' - centro,
anelli di distanza, punti cardinali - ed e' anche il linguaggio di questa app.

---

## 2. La fonte dei dati, e perche' questa

**Xweather (Vaisala)**, endpoint `lightning`. Piano gratuito: 15.000 chiamate al
mese, tutti gli endpoint, nessuna carta di credito, non scade.

**Blitzortung e' stata valutata e scartata**, ed e' bene che resti scritto perche'
e' la prima a cui si pensa. I suoi dati grezzi sono accessibili **solo a chi
gestisce una stazione ricevente** e continua a trasmettere: "the sferic positions
are free accessible in raw format for all users that transmit their data to our
servers", e l'accesso decade se la stazione smette. In piu' vieta l'uso
commerciale e impone alle app di terze parti di servire i dati **da un proprio
server** invece di collegare i client. Per un'app senza backend e senza stazione
VLF, e' chiusa.

### La chiamata

```
https://data.api.xweather.com/lightning/closest
  ?p=<lat>,<lon>
  &radius=100km
  &limit=1000
  &client_id=<id>&client_secret=<secret>
```

`closest` e non `within`: l'azione che accetta un'area circolare o poligonale
**richiede il piano Lightning Enterprise**, a pagamento. `closest` ordina per
distanza e sul gratuito basta.

Limiti del piano gratuito, da trattare come vincoli e non come impostazioni:
ultimi **5 minuti**, raggio massimo **100 km**, fino a **1000 scariche** per
chiamata.

### I campi che servono

| campo della risposta | dove finisce |
|---|---|
| `id` | chiave per non contare due volte la stessa scarica fra un giro e l'altro |
| `loc.lat`, `loc.long` | posizione |
| `ob.timestamp` | eta', quindi quanto sbiadire |
| `ob.pulse.type` | `CG` nube-suolo, `IC` intra-nube |
| `ob.pulse.peakamp` | intensita' e polarita' |

`type` non e' un dettaglio: **solo le `CG` toccano terra**. Le intra-nube sono
molte di piu' e riempirebbero la scena di punti che non cadono da nessuna parte.
Si disegnano entrambe ma diverse - le `CG` piene, le `IC` come aloni piu' tenui -
perche' entrambe dicono "c'e' un temporale li'", ma solo una dice "sta colpendo".

---

## 3. Componenti

### `data/LightningStrike.kt` — il modello

```kotlin
@Serializable
data class LightningStrike(
    val id: String,
    val quando: Long,          // epoch secondi, da ob.timestamp
    val latitudine: Double,
    val longitudine: Double,
    val nubeSuolo: Boolean,    // ob.pulse.type == "CG"
    val intensita: Double?,    // ob.pulse.peakamp, kA, con segno
)
```

Serializzabile perche' la scia si salva su disco (§5). `id` e' la chiave di
deduplicazione: due giri a un minuto di distanza rivedono le stesse scariche,
visto che la finestra dell'API e' di cinque minuti.

### `data/LightningRepository.kt` — la rete

Stessa forma di `WeatherRepository` e `AirQualityRepository`: `HttpURLConnection`,
`kotlinx.serialization`, ritorna `Result`. Nessuna libreria di rete nuova.

Espone uno stato a tre valori invece di un semplice successo/errore:

- `Configurato(scariche)` — e' andata
- `NonConfigurato` — mancano le credenziali (§7)
- `Fallito(causa)` — rete o API

### `data/LightningStore.kt` — la scia

Lista di `LightningStrike` in DataStore, serializzata in JSON. **Non Room**: e'
una lista effimera di qualche migliaio di punti, un database e' sovradimensionato
e sarebbe una seconda dipendenza.

**Scadenza a 60 minuti, applicata sia in scrittura sia in lettura.** In scrittura
per non far crescere il file; in lettura perche' un'app riaperta dopo una
settimana deve mostrare una scena vuota, non i fantasmi del temporale di allora.
Applicarla da un lato solo e' il difetto classico di queste cache.

### `work/LightningWorker.kt` — la raccolta in sottofondo

`CoroutineWorker` periodico, quindici minuti (il minimo che WorkManager concede).
Un temporale a quaranta all'ora percorre dieci chilometri in quel tempo: su cento
di raggio e' una grana piu' che sufficiente per una scia.

**Si accende solo quando serve.** Il `WeatherViewModel` guarda `lightning_potential`
e `cape` - dati che l'app **gia' scarica** da Open-Meteo, a costo zero - e
programma o cancella il lavoro. Niente temporale in vista, niente lavoro.

La condizione di accensione, esplicita perche' non resti a interpretazione di chi
implementa: **si raccoglie se nelle prossime sei ore almeno un'ora ha
`lightning_potential` sopra zero, oppure `cape` sopra 500 J/kg.** I due criteri
sono in `or` e non in `and`, ed e' voluto: `lightning_potential` non lo danno
tutti i modelli - ICON-2I si', altri no - e `cape` fa da rete di sicurezza dove
manca. La soglia di 500 e' il valore convenzionale sotto il quale l'atmosfera non
ha energia per un temporale; **e' un punto di partenza da ritarare** dopo la
prima stagione di uso vero, ed e' esattamente il genere di numero che va scritto
in un posto solo e con un nome.

Con questa condizione il budget smette di essere un problema: in Italia si
contano trenta-quaranta giorni di temporale l'anno, e raccogliendo sei ore in
ognuno si sta attorno alle **mille chiamate l'anno** su 15.000 al mese.

### `ui/temperature/pages/LightningPage.kt` — la scena

Settima voce di `DetailMode` (`FULMINI`, etichetta breve "Fulmini"), smistata in
`TemperatureDetailScreen` come le altre sei. Nessuna struttura di navigazione
nuova: il foglio del dettaglio sale gia' col dito e il carosello esiste.

> **Emendamento del 7 settembre 2026.** Il foglio del dettaglio sta per cambiare
> forma: da carosello orizzontale di pagine a **pila verticale di schede
> agganciate**, una per schermata ma contenuta con margini
> (`2026-09-07-foglio-dettaglio-pila-design.md`). Il radar diventa quindi la
> **settima scheda**, non la settima pagina.
>
> Per il contenuto non cambia nulla - la scena disegnata, i dati, la raccolta e
> la scia restano identici - ma cambiano due cose che vanno decise **quando si
> implementa, non adesso**:
>
> 1. **Cosa mostra la scheda da chiusa.** Ogni scheda tiene in evidenza una sola
>    cosa. Per il radar il candidato ovvio e' la distanza della scarica piu'
>    vicina ("12 km, a nord-ovest"), oppure il vuoto dichiarato quando non c'e'
>    niente. La scena col cerchio e gli anelli vive nello stato espanso.
> 2. **L'ordine nella pila.** Sei schede su sette parlano sempre; questa tace per
>    gran parte dell'anno. Metterla in mezzo significa un buco nello scorrimento
>    nei giorni sereni. Va valutato se debba stare in fondo, o comparire solo
>    quando ha qualcosa da dire - che e' la stessa domanda gia' posta, e allora
>    risolta scegliendo "schermata sempre raggiungibile".
>
> **Ordine di lavoro**: prima la pila, poi il radar. Al contrario si scriverebbe
> una pagina destinata a essere riscritta dopo pochi giorni.

---

## 4. La scena disegnata

Una `Canvas` sola, come ovunque in questa app.

- **Tu al centro.** Il centro e' la **localita' scelta**, non la posizione GPS:
  esiste sempre, quindi il radar funziona anche a permesso di posizione negato.
- **Anelli** a 25, 50, 75, 100 km, con la misura scritta piccola su uno solo di
  essi: quattro numeri sarebbero un righello.
- **N/E/S/O** sul bordo. Senza, un punto "in alto" non vuol dire niente.
- **Le scariche**: un punto per ognuna, che sbiadisce con l'eta'. E' la scia, ed
  e' cio' che rende leggibile la direzione del temporale.

  La dissolvenza e' **legata alla scadenza dello store, non a un numero suo**:
  opacita' `1 − eta / 60 minuti`, quindi una scarica appena nata e' piena e una
  che sta per essere scartata e' gia' trasparente. Due costanti indipendenti
  finirebbero prima o poi in disaccordo, e il sintomo sarebbe punti che spariscono
  di scatto mentre erano ancora ben visibili. Chi cambia i sessanta minuti cambia
  entrambe le cose insieme, perche' sono la stessa cosa.

  Le nube-suolo si disegnano piene, le intra-nube come aloni piu' tenui: entrambe
  dicono "c'e' un temporale li'", solo le prime dicono "sta colpendo".
- **Colore**: il viola del temporale che `HourBar.tintOf` usa gia' (`0xFF5B4BC4`),
  perche' il radar parli la stessa lingua della barra delle ore.

### La proiezione

Approssimazione locale piatta, che a cento chilometri e' piu' che sufficiente:

```
dx_km = (lon − lon0) · cos(lat0) · 111,32
dy_km = (lat − lat0) · 110,57
```

Nessuna libreria cartografica. L'errore rispetto a una proiezione vera, su questo
raggio e a latitudini italiane, e' sotto il mezzo chilometro: meno del raggio del
punto che si disegna.

### Accessibilita'

Una tela e' muta, ed e' un difetto che questo progetto ha gia' pagato due volte
(la barra delle ore, i glifi del meteo). La scena porta una descrizione che
riassume cio' che si vede: quante scariche, la piu' vicina a quanti chilometri e
in che direzione.

---

## 5. Il giro dei dati

```
Open-Meteo (lightning_potential, cape)
        │  gia' scaricati col meteo
        ▼
WeatherViewModel ──accende/spegne──▶ LightningWorker (15 min)
                                            │
                                            ▼
                                     LightningRepository
                                            │
                                            ▼
                                      LightningStore  ◀── scadenza 60 min
                                            │
                                            ▼
                                     LightningPage (scena)
                                            ▲
                                            └── in primo piano: un giro ogni 60 s
```

Sessanta secondi a pagina aperta e non trenta: sembra ugualmente vivo e consuma
la meta'.

---

## 6. Quando le cose vanno storte

| situazione | cosa si vede |
|---|---|
| credenziali assenti | "radar non configurato" — e nient'altro rotto |
| rete caduta | **la scia resta in scena**, una riga dice che e' vecchia |
| nessuna scarica | "nessuna scarica entro 100 km", con l'ora dell'ultimo controllo |
| permesso posizione negato | funziona uguale: il centro e' la localita' scelta |

Il secondo caso segue la filosofia gia' scritta nella schermata principale: il
dato vecchio resta, e a dire che e' vecchio ci pensa una riga. Cancellare la
scena perche' una chiamata e' fallita sarebbe perdere informazione buona per
segnalare un guasto passeggero.

Il terzo non e' un errore ed e' importante che non lo sembri: **per gran parte
dell'anno la risposta giusta e' "niente"**, e una schermata che dichiara il vuoto
con l'ora del controllo e' una schermata che sta funzionando.

---

## 7. Le credenziali

`local.properties` (gia' fuori da git) → `buildConfigField` → `BuildConfig`.

**La CI compila senza chiave**, e deve continuare a farlo: il job `build`
verifica `assembleDebug` e `assembleRelease`, e non ha segreti. Quindi l'assenza
delle credenziali e' uno **stato previsto**, non un errore: `NonConfigurato`.
Se la chiave mancante rompesse la compilazione o il test, ci si ritroverebbe con
due job rossi invece di uno.

Va detto chiaro: **una chiave dentro un APK e' estraibile.** Tenerla fuori dal
repository evita che finisca nella storia pubblica di git, non che qualcuno la
cavi dal pacchetto installato. Per un'app personale e' un rischio accettabile;
per una pubblicata non lo sarebbe, e la risposta in quel caso sarebbe un relay
proprio - cioe' un server, che questo progetto non ha.

---

## 8. Verifica

**Qui il test-driven development si applica davvero**, al contrario delle
modifiche di puro disegno: tre pezzi sono funzioni pure e si testano sulla JVM,
accanto ai 39 test esistenti.

1. **La proiezione** — coordinate note → scostamenti noti in chilometri, e il
   caso limite dell'antimeridiano.
2. **La scadenza** — una scia con voci di eta' mista, prima e dopo il filtro.
3. **La deduplicazione** — due risposte che si sovrappongono non devono
   raddoppiare le scariche.
4. **La lettura del JSON** — su una risposta vera di Xweather registrata come
   fixture, sullo stampo di `app/src/test/resources/meteoalarm-italia.xml` che
   il progetto usa gia' per le allerte.

### L'aggancio per la CI

I fulmini veri sono rari, quindi **lo scatto del radar in CI ritrarrebbe per
sempre una schermata vuota**. Serve un aggancio che inietti scariche sintetiche,
nello stile di `--ei meteo` e `--ei ora` che `capture.sh` gia' usa per il resto.
Senza, la galleria avrebbe una pagina che non mostra mai la funzione.

### Un rischio noto, da tenere d'occhio

Il carosello del dettaglio e' **il punto in cui l'emulatore della CI muore**
(trappola #38: "pagina aria due volte, dettaglio di un giorno, pagina vento"), e
questa aggiunge una settima pagina, cioe' uno scatto in piu' nella zona fragile.
Il branch `claude/emulatore-diagnostica-3k7p` sta misurando proprio quello.
**Conviene leggere il suo esito prima di unire questa specifica**, per non
attribuire a una funzione nuova un guasto che c'era gia'.

---

## 9. Cosa resta fuori, per scelta

- **Le notifiche** quando il temporale si avvicina. E' probabilmente la cosa piu'
  utile di tutte, ma e' un'altra funzione: vuole permesso di notifica, una
  politica su quando disturbare, e un lavoro in sottofondo che gira sempre invece
  che solo a temporale previsto.
- **La freccia della direzione** ("si avvicina da nord-ovest"). Si ricava dal
  movimento del centro delle scariche, ma e' una stima: una freccia sbagliata su
  un temporale e' peggio di nessuna freccia. La scia sbiadita dice la stessa cosa
  senza affermarla.
- **Lo zoom.** Il raggio e' cento chilometri perche' cosi' concede il piano
  gratuito. Uno zoom che non puo' andare oltre non e' uno zoom.
