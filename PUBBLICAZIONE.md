# Pubblicare Caelum Weather sul Play Store

Cosa c'è già nel repository, e cosa può fare solo chi possiede l'account.
Scritto il 10 novembre 2026; le regole dello store cambiano, quindi i punti
segnati **(verifica)** vanno riletti nella Play Console il giorno in cui si fa.

## 1. Cosa c'è già

| | dove |
|---|---|
| Bundle di rilascio (`.aab`) per telefono e orologio, a ogni giro di CI | job `build`, artifact `bundle` |
| Firma di rilascio letta da variabili, mai dal repository | `app/build.gradle.kts`, `wear/build.gradle.kts` (`segreto`) |
| `versionCode` che cresce da solo (telefono: numero di commit; orologio: un milione in più) | idem |
| R8 e riduzione delle risorse sulla release del telefono | `app/build.gradle.kts` |
| `targetSdk` 36, icona adattiva con livello monocromatico, regole di backup | manifest |
| Informativa sulla privacy, italiano e inglese | `docs/privacy.md` |
| Testi della scheda, italiano e inglese | `fastlane/metadata/android/` |
| Scatti dell'app nei due temi | ramo `ci-artifacts`, cartella `screenshots/` |

## 2. La chiave di caricamento (una volta sola)

Si genera sul proprio computer e **non si mette mai nel repository**:

```bash
keytool -genkeypair -v -keystore caelum-caricamento.jks -alias caelum -keyalg RSA -keysize 4096 -validity 10000
```

Va conservata in due posti sicuri insieme alle sue parole d'ordine. Con Play
App Signing (obbligatorio per le app nuove) Google tiene la chiave definitiva e
questa serve solo a caricare: se si perde, si può chiedere di sostituirla.

Poi quattro segreti nel repository (Settings → Secrets and variables → Actions):

| segreto | valore |
|---|---|
| `CAELUM_KEYSTORE_BASE64` | l'uscita di `base64 -w0 caelum-caricamento.jks` |
| `CAELUM_KEYSTORE_PASSWORD` | la parola d'ordine del file |
| `CAELUM_KEY_ALIAS` | `caelum` |
| `CAELUM_KEY_PASSWORD` | la parola d'ordine della chiave |

Dal giro successivo l'artifact `bundle` contiene `caelum-telefono.aab` e
`caelum-orologio.aab` firmati, e il passo lo dice con un avviso `notice`.
Senza segreti il passo resta verde e avvisa che i bundle non sono firmati.

In locale le stesse quattro cose si passano come variabili d'ambiente, con
`CAELUM_KEYSTORE` che punta al file:

```bash
CAELUM_KEYSTORE=/percorso/caelum-caricamento.jks CAELUM_KEYSTORE_PASSWORD=... CAELUM_KEY_ALIAS=caelum CAELUM_KEY_PASSWORD=... ./gradlew :app:bundleRelease :wear:bundleRelease
```

## 3. Prima di caricare: provare la release in mano

**La build che è sempre andata sul telefono è quella di debug, non minificata.**
La release passa da R8 e la CI la compila, ma nessuno l'ha mai aperta. Prima di
mandarla a qualcuno va installata sul telefono e girata tutta: previsione,
allerte, widget, notifiche, orologio. Dato che la firma cambia, la build di
`apk-latest` va disinstallata prima (le località salvate si perdono, a meno del
backup di Android).

```bash
CAELUM_KEYSTORE=... ./gradlew :app:assembleRelease
```

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

## 4. Nella Play Console

1. **Account sviluppatore** (25 USD una tantum, verifica dell'identità).
   Gli account personali nuovi devono fare un **test chiuso con almeno 12
   tester per 14 giorni** prima di poter pubblicare in produzione **(verifica)**.
2. **Crea l'app**: nome `Caelum Weather`, lingua predefinita italiano, app, gratuita.
3. **Scheda dello store**: i testi stanno in `fastlane/metadata/android/`
   (titolo, descrizione breve, descrizione completa) per `it-IT` e `en-US`.
   Servono inoltre, e si fanno a mano:
   - icona 512×512 PNG (dal vettoriale `ic_launcher_*`);
   - immagine in evidenza 1024×500;
   - almeno 2 scatti del telefono (quelli di `ci-artifacts` vanno bene) e, per
     l'orologio, almeno uno scatto Wear OS senza cornice.
4. **Norme sulla privacy**: l'indirizzo pubblico di `docs/privacy.md`. Il modo
   più semplice è accendere GitHub Pages sul ramo `main`, cartella `/docs`; il
   repository deve essere pubblico.
5. **Sicurezza dei dati** — le risposte che corrispondono al codice:
   - Raccoglie o condivide dati? **Sì**: *Posizione approssimativa*.
     Raccolta: sì (inviata ai servizi meteo per ottenere la previsione).
     Condivisa: sì, con i fornitori dei dati meteo. Scopo: *funzionalità
     dell'app*. Facoltativa. Non usata per pubblicità o profilazione.
     **(verifica)** se la Console considera "raccolta" l'invio di coordinate
     senza identificativo; nel dubbio dichiararla è la risposta prudente.
   - Dati cifrati in transito: **sì** (solo HTTPS).
   - Richiesta di cancellazione: non applicabile, nessun dato su server propri.
   - Nessun altro tipo di dato: niente identificativi, niente diagnostica.
6. **Classificazione dei contenuti**: questionario IARC, categoria *utilità*;
   nessun contenuto sensibile. **Pubblico di destinazione**: 13+ (o 18+ per
   evitare le regole delle app per famiglie).
7. **Annunci**: no. **Accesso all'app**: nessuna credenziale richiesta.
8. **Categoria**: Meteo.
9. **Wear OS**: in *Impostazioni avanzate → Fattori di forma* aggiungi Wear OS,
   poi carica `caelum-orologio.aab` nella stessa versione. Lo store fa una
   revisione a parte per l'orologio.
10. **Carica** `caelum-telefono.aab` nel test interno, poi chiuso, poi produzione.

## 5. Cose da sapere prima

- **Open-Meteo**: l'API gratuita è per uso **non commerciale** e con un tetto
  di richieste giornaliere. Un'app gratuita senza pubblicità ci rientra; se
  l'app diventasse a pagamento o con pubblicità, o se gli utenti crescessero
  molto, serve il loro piano a pagamento **(verifica** le condizioni attuali
  su open-meteo.com/en/terms**)**.
- **L'`applicationId` non si cambia più** dopo la prima pubblicazione:
  `io.github.noximiliencoxen.caelumweather` resta per sempre.
- **Chi ha l'app da `apk-latest`** dovrà disinstallarla per passare a quella
  dello store: la firma è diversa.
- **`targetSdk`**: lo store alza il minimo richiesto ogni anno, di solito a fine
  agosto. Oggi è 36.
- **Il nome e' "Caelum Weather"**, non piu' "Caelum" da solo. Sul Play Store
  esiste "Caelum - Local AI assistant" (altra categoria) e su iOS "iCaelum"
  (meteo, solo iPhone): nessuna si chiama esattamente cosi', ma in caso di
  contestazione sul nome conviene saperlo. Il pacchetto e'
  `io.github.noximiliencoxen.caelumweather`. Sotto l'icona compare solo
  "Caelum" (il nome intero verrebbe troncato); il titolo dello store porta una
  coda descrittiva, entro i 30 caratteri: "Caelum Weather: meteo, allerte".
