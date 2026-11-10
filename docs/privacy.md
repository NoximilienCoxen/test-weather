# Caelum — Informativa sulla privacy / Privacy policy

*Ultimo aggiornamento / Last updated: 10 novembre 2026*

## Italiano

Caelum è un'app di previsioni del tempo per telefono Android e orologio Wear OS.
Non ha account, non ha pubblicità, non contiene strumenti di statistica o di
tracciamento e non invia rapporti d'errore automatici.

### Cosa esce dal dispositivo

- **Le coordinate della località mostrata** (scelta a mano oppure, se lo chiedi
  tu, ricavata dalla posizione approssimativa del dispositivo). Servono a
  chiedere la previsione per quel punto e vengono inviate, su connessione
  cifrata, ai servizi elencati sotto. Non sono associate a un account né a un
  identificativo del dispositivo.
- **Il testo che scrivi nella ricerca delle località**, inviato al servizio di
  geocodifica di Open-Meteo.
- **Un'etichetta tecnica** (User-Agent) che dice quale programma sta chiedendo.

Come per ogni connessione a Internet, i servizi contattati vedono l'indirizzo
IP da cui arriva la richiesta.

### A chi vengono inviate

- Open-Meteo (`api.open-meteo.com`, `air-quality-api.open-meteo.com`,
  `geocoding-api.open-meteo.com`): previsioni, qualità dell'aria e polline,
  ricerca delle località.
- I servizi di allerta meteo ufficiali, a seconda del Paese della località:
  MeteoAlarm (`feeds.meteoalarm.org`), National Weather Service
  (`api.weather.gov`), Environment and Climate Change Canada
  (`api.weather.gc.ca`), Japan Meteorological Agency (`www.jma.go.jp`,
  `www.data.jma.go.jp`), IFRC Alert Hub (`alerthub-api.ifrc.org`).

Non ci sono altri destinatari. Nessun dato viene venduto o ceduto a terzi per
pubblicità o profilazione.

### Cosa resta sul dispositivo

La località scelta e quelle salvate, le unità di misura, la lingua, il tema, le
scelte sulle notifiche e la configurazione dei widget. Stanno nell'archivio
privato dell'app. Se il backup di Android è attivo sul tuo dispositivo, le
impostazioni (non la configurazione dei widget) sono incluse nel backup del tuo
account Google, gestito da Google. Se usi l'app per l'orologio, località, unità
e lingua passano dal telefono all'orologio attraverso il collegamento fra i due
dispositivi (Wear OS Data Layer).

### Permessi

- **Internet**: per chiedere previsioni e allerte.
- **Posizione approssimativa** (facoltativo): solo se chiedi all'app o a un
  widget di seguire la tua posizione. La posizione precisa non viene mai
  chiesta.
- **Notifiche** (facoltativo): pioggia o grandine in arrivo, allerte ufficiali,
  riepilogo del mattino. Ognuna si spegne dalle impostazioni.
- **Vibrazione**: il riscontro al tocco.

### Cancellare i dati

Non esistono dati conservati su server dell'autore. Per cancellare ciò che sta
sul dispositivo basta cancellare i dati dell'app dalle impostazioni di Android,
o disinstallarla.

### Minori

L'app non è rivolta specificamente ai minori e non raccoglie dati personali da
nessuno.

### Contatti

Per domande: <https://github.com/NoximilienCoxen/test-weather/issues>

---

## English

Caelum is a weather forecast app for Android phones and Wear OS watches. It has
no accounts, no ads, no analytics or tracking tools, and it sends no automatic
crash reports.

### What leaves the device

- **The coordinates of the place shown** (chosen by hand or, if you ask for it,
  derived from the device's approximate location). They are needed to request
  the forecast for that spot and are sent over an encrypted connection to the
  services listed below. They are not linked to an account or a device
  identifier.
- **The text you type in place search**, sent to the Open-Meteo geocoding
  service.
- **A technical label** (User-Agent) saying which program is asking.

As with any Internet connection, the services contacted see the IP address the
request comes from.

### Who receives it

- Open-Meteo (`api.open-meteo.com`, `air-quality-api.open-meteo.com`,
  `geocoding-api.open-meteo.com`): forecasts, air quality and pollen, place
  search.
- Official weather warning services, depending on the country of the place:
  MeteoAlarm (`feeds.meteoalarm.org`), National Weather Service
  (`api.weather.gov`), Environment and Climate Change Canada
  (`api.weather.gc.ca`), Japan Meteorological Agency (`www.jma.go.jp`,
  `www.data.jma.go.jp`), IFRC Alert Hub (`alerthub-api.ifrc.org`).

There are no other recipients. No data is sold or shared with third parties for
advertising or profiling.

### What stays on the device

The chosen and saved places, units, language, theme, notification choices and
widget configuration. They live in the app's private storage. If Android backup
is enabled on your device, the settings (not the widget configuration) are
included in your Google account backup, which is managed by Google. If you use
the watch app, place, units and language travel from the phone to the watch
over the link between the two devices (Wear OS Data Layer).

### Permissions

- **Internet**: to request forecasts and warnings.
- **Approximate location** (optional): only if you ask the app or a widget to
  follow your location. Precise location is never requested.
- **Notifications** (optional): rain or hail on the way, official warnings,
  morning summary. Each can be turned off in settings.
- **Vibration**: touch feedback.

### Deleting your data

No data is stored on servers run by the author. To delete what is on the
device, clear the app's data from Android settings, or uninstall it.

### Children

The app is not specifically directed at children and collects personal data
from no one.

### Contact

Questions: <https://github.com/NoximilienCoxen/test-weather/issues>
