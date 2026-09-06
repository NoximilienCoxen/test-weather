# Il foglio del dettaglio come pila di schede — specifica di progetto

Data: 7 settembre 2026. Stato: approvata la forma, da implementare.

Il foglio del dettaglio smette di essere un carosello orizzontale di pagine e
diventa una **pila verticale di schede agganciate**: una per schermata, ma
contenuta, con la schermata principale sfumata tutt'intorno.

---

## 1. La forma, e le due frasi che sembravano contraddirsi

La richiesta conteneva due cose che paiono opposte: *"tipo TikTok"* (aggancio, un
elemento per volta) e *"non a tutto schermo, tutte visibili"*. Non erano in
conflitto: **l'aggancio riguarda lo scorrimento, il contenimento riguarda la
scheda.**

Una scheda per schermata di scorrimento, quindi uno swipe la porta al centro e ci
resta. Ma la scheda **non arriva ai bordi**: ha margini, e attraverso quei
margini si vede la schermata principale sfumata, piu' le schede vicine che
sbucano sopra e sotto. Da qui vengono tre cose insieme:

- si capisce che c'e' altro sopra e sotto, senza doverlo scorrere per scoprirlo;
- l'aggancio ha un ritmo, invece di essere uno scorrimento indistinto;
- **esiste un "fuori"**, ed e' li' che si tocca per chiudere (§4).

Tecnicamente: `VerticalPager` con `contentPadding` verticale. Il padding e' cio'
che produce **sia** il contenimento **sia** lo sbirciare delle vicine, e non e'
una decorazione: senza, l'aggancio pieno diventa a tutto schermo, cioe' proprio
la cosa che e' stata scartata.

Quanto padding e' la stessa domanda di quanto sia alta la scheda compatta, ed e'
una sola decisione presa da due lati: il padding e' cio' che avanza. Va misurata
sul telefono piu' piccolo che l'app supporta, con `MeteoLayout` che esiste gia'
per questo genere di conti (§7.3), e non fissata qui a occhio.

Lo sfondo sfumato **esiste gia'** e non va inventato: il KDoc di `MeteoApp`
dichiara che il dettaglio e' "un foglio che sale seguendo il dito. Dietro, la
principale arretra e si smorza".

---

## 2. Le schede

Le **stesse sei sezioni** di oggi: Temperatura, Sole, Precipitazioni, Vento,
Aria, Luna. Il contenuto e' gia' scritto e collaudato; cambia come lo si sfoglia
e cosa se ne vede da chiuso, non cosa dice.

Ogni scheda ha due stati.

**Compatta** — una cosa sola in evidenza, quella che risponde alla domanda per
cui si apre quella sezione, piu' un segno che dichiara che c'e' dell'altro. La
scelta di *quale* sia quella cosa e' il lavoro di contenuto di questa specifica,
ed e' una decisione per sezione (§7).

**Espansa** — tutto il contenuto della sezione: le pagine che oggi vivono in
`ui/temperature/pages/` diventano il corpo della scheda espansa, **senza essere
riscritte**. Sono gia' impaginate per uno spazio verticale che scorre, che e'
esattamente quello che serve.

---

## 3. Un solo scorrimento per volta

E' il punto in cui questa schermata puo' rompersi, e la difesa e' strutturale,
non una guardia da azzeccare.

| stato | chi scorre | chi sta fermo |
|---|---|---|
| scheda compatta | la pila (aggancio attivo) | il contenuto |
| scheda espansa | il contenuto | la pila (`userScrollEnabled = false`) |

Espandendo si spegne l'aggancio; richiudendo si riaccende. **Non esiste un
istante in cui due scorrimenti verticali si contendono lo stesso dito.**

Non e' prudenza teorica: e' la trappola **#35** di `CONTESTO`, "un
avanzo di scorrimento non e' un dito", dove `SheetNestedScroll` si assestava
sull'avanzo di chiunque e una guardia scritta come confronto esatto (`open >= 1f`)
su un numero che viene da una molla bloccava **del tutto** lo scorrimento del
contenuto. Quel difetto e' costato caro una volta; qui la struttura lo rende
impossibile invece di difendersene.

---

## 4. Come si chiude

**Toccando fuori dalla scheda**, cioe' sul velo dove si vede la principale
sfumata. E' possibile solo perche' le schede sono contenute: a tutto schermo un
fuori non ci sarebbe.

Resta anche il gesto di ritorno di sistema. **Non** si aggiunge un tasto di
chiusura: il velo e' un bersaglio grande quanto i margini di tutta la schermata,
ed e' piu' facile da colpire di qualunque pulsante.

Il tocco sul velo va distinto dal trascinamento: appoggiare il dito sul margine e
scorrere deve muovere la pila, non chiudere. Stessa distinzione che la schermata
principale fa gia' fra tocco e rotazione della scena (`detectTapOrRotate`), e va
risolta allo stesso modo - con una soglia di movimento, non con due gestori
separati.

---

## 5. Il vetro

**Si sfuma la principale una volta sola**, dietro tutto, non un backdrop per
scheda. Sfocare sei superfici a ogni fotogramma di scorrimento e' il modo piu'
rapido per far scendere i fotogrammi su un telefono medio, e il risultato
visibile sarebbe quasi identico.

Sopra, ogni scheda e' una superficie **traslucida**, con:

- angoli tondi;
- un bordo chiaro sottile **in gradiente**, piu' acceso in alto e spento in
  basso: e' quel bordo a dare il "lucido", non il fondo;
- nessuna ombra portata pesante. Il distacco lo da' gia' la sfocatura dietro.

I colori escono da `LocalMeteoColors`, che segue il cielo lungo la giornata:
**una scheda con una tinta fissa andrebbe bene a mezzogiorno e sarebbe sbagliata
all'alba.** E' l'errore gia' fatto una volta col selettore ore/settimana, dove
`colors.text` e `colors.label` si sono rivelati indistinguibili su cielo chiaro.

---

## 6. `minSdk` da 26 a 31

**Va fatto prima, su un branch suo, e misurato da solo.**

La sfocatura vera (`RenderEffect`, `Modifier.blur`) esiste **solo da Android 12**.
Sotto, non viene peggio: non viene affatto. Le alternative erano un ripiego per i
telefoni vecchi o un aspetto unico senza sfocatura; la scelta presa e' alzare il
minimo.

**Cosa si perde**: Android 8, 9, 10 e 11.

**Cosa si guadagna, oltre al vetro** - e non e' poco, e' codice che altrimenti ci
si porta dietro nella schermata nuova:

- `WeatherHaptics`: tre rami `SDK_INT >=` diventano morti, e con loro **i due
  `@SuppressLint("NewApi")`** e il KDoc che li giustifica. L'intera impalcatura
  di compatibilita' dell'aptica collassa in codice diretto.
- `DeviceLocation`: i tre override vuoti di `LocationListener` esistono solo
  perche' "su Android 8 le tre qui sotto sono ancora astratte" - lo dice il
  commento. Via.
- Lint: spariscono i warning su `targetCellWidth` e `targetCellHeight`, che sono
  attributi API 31 dichiarati con minimo 26.

La potatura va nello **stesso branch** dell'alzata: un `minSdk` alzato senza
raccogliere cio' che libera lascia in giro rami che nessuno rimuovera' piu'.

---

## 7. Cosa resta da decidere implementando

Sono decisioni di contenuto, non di struttura, e si prendono meglio guardando la
schermata vera che scrivendole qui.

1. **La cosa in evidenza, per ognuna delle sei.** Per Temperatura e' quasi
   certamente la massima e la minima; per Vento la raffica; per Aria l'indice.
   Per Sole e Luna e' meno ovvio - l'ora del tramonto? la fase? - e va guardato.
2. **La sorte di `PanelPicker`.** La fila orizzontale di linguette serviva a
   saltare fra pagine di un carosello. Con una pila verticale probabilmente non
   serve piu', ma toglierla significa che per arrivare alla Luna si scorre
   sempre. Va deciso guardando, non a tavolino.
3. **L'altezza della scheda compatta.** Deve stare comoda sul telefono piu'
   piccolo che l'app supporta, e `MeteoLayout` esiste gia' per questo genere di
   conti.

---

## 8. Verifica

**I test JVM esistenti non toccano questa schermata** e devono restare verdi:
sono 39 e riguardano dati, non impaginazione.

**Gli scatti della CI vanno rifatti**: `capture.sh` naviga il carosello con
agganci pensati per le pagine orizzontali, e una pila verticale li invalida.

**Ma quegli scatti non possono fare da giudice**, e per una ragione ormai
misurata: il job `screenshots` fallisce perche' **il processo dell'emulatore
muore**, e l'autopsia del giro `34002545333` mostra che non e' esaurimento -
undici giga di memoria libera sull'host, carico a 0.00, memoria del processo
piatta a 1.297 MB, nessun OOM killer - ma un crash dell'emulazione grafica
(`Failed to find ColorBuffer`). Finche' non e' sistemato, la verifica vera resta
il telefono.

---

## 9. Ordine di lavoro

1. `minSdk` a 31 e la potatura che libera — branch suo, verifica sua.
2. La pila: contenitore, schede, i due stati, chiusura sul velo, vetro.
3. Il radar dei fulmini, che a quel punto e' una **scheda** e non una pagina
   (vedi l'emendamento in `2026-09-07-radar-fulmini-design.md`).

Invertire i primi due significherebbe scrivere la schermata nuova sopra
un'impalcatura di compatibilita' che si e' gia' deciso di buttare.
