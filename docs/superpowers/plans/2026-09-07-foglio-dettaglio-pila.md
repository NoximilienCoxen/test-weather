# Foglio del dettaglio come pila di schede — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** trasformare il foglio del dettaglio da carosello orizzontale di pagine a pila verticale di schede agganciate, una per schermata ma contenuta, con la schermata principale sfumata tutt'intorno.

**Architecture:** un `VerticalPager` con `contentPadding` sostituisce l'`HorizontalPager` in `TemperatureDetailScreen`. Ogni pagina del pager e' una scheda di vetro (`GlassCard`) che in stato compatto mostra un solo sommario e in stato espanso ospita la pagina gia' esistente di `ui/temperature/pages/`. L'espansione spegne l'aggancio del pager, cosi' un solo scorrimento verticale e' attivo per volta.

**Tech Stack:** Kotlin, Jetpack Compose (Foundation `VerticalPager`, `Modifier.blur`), nessuna dipendenza nuova. `minSdk` alzato a 31 nel Task 1.

**Spec:** `docs/superpowers/specs/2026-09-07-foglio-dettaglio-pila-design.md`

## Global Constraints

- `minSdk = 31` dal Task 1 in poi. `compileSdk = 37`, `targetSdk = 36`, JDK 17.
- **Nessuna dipendenza nuova.** Il progetto ne ha 21 e le conta.
- **Lint deve restare a 0 errori** (18 warning sono lo stato attuale accettato). Comando: `.\gradlew.bat lintDebug`.
- **I 39 test JVM devono restare verdi.** Comando: `.\gradlew.bat testDebugUnitTest`.
- **Il progetto non ha test di interfaccia**: nessun `androidTest`, nessuna dipendenza `compose.ui.test`. Per i task di UI la verifica e' `assembleDebug` + installazione + screenshot sul telefono. Non inventare un'infrastruttura di test UI: sarebbe una dipendenza nuova, vietata sopra.
- **Il job `screenshots` della CI e' rotto per una causa esterna** (crash dell'emulazione grafica, misurato nel giro `34002545333`). Non usarlo come giudice: il giudice e' il telefono.
- **Ambiente**: PowerShell, `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` prima di ogni `.\gradlew.bat`. `adb` sta in `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` e va chiamato da PowerShell, non da Git Bash.
- **Commit**: identita' gia' configurata nel repo. Messaggio in italiano, una riga su cosa cambia, e chiudere con `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- **`CONTESTO.md` va aggiornato nello stesso commit del cambiamento** che lo merita (regola 11.1 del progetto).

---

### Task 1: `minSdk` a 31, e la potatura che libera

Deliverable indipendente: si puo' unire da solo, senza nulla della pila.

**Files:**
- Modify: `app/build.gradle.kts:13`
- Modify: `app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/motion/WeatherHaptics.kt`
- Modify: `app/src/main/kotlin/io/github/noximiliencoxen/caelum/data/DeviceLocation.kt`
- Modify: `CONTESTO.md`

**Interfaces:**
- Consumes: nulla.
- Produces: `Modifier.blur` e `RenderEffect` diventano utilizzabili senza guardia di versione. `WeatherHaptics.Mode` perde i rami morti ma **mantiene tutti e cinque i valori** (`PRIMITIVE`, `AMPLITUDE`, `PREDEFINED`, `BLUNT`, `NONE`): dipendono dal telefono, non dalla versione di Android.

- [ ] **Step 1: registrare lo stato di partenza di lint**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat lintDebug --console=plain 2>&1 | Select-String -Pattern "errors,"
```

Atteso: `0 errors, 18 warnings`. Annotare il numero: al passo 6 deve essere sceso.

- [ ] **Step 2: alzare `minSdk`**

In `app/build.gradle.kts`, riga 13:

```kotlin
        minSdk = 31
```

- [ ] **Step 3: potare `WeatherHaptics`**

Rimuovere i due `@SuppressLint("NewApi")` sopra `raindrop()` e `thunder()`, l'import `android.annotation.SuppressLint`, e il blocco di KDoc che comincia con `**Sui @SuppressLint("NewApi") qui sotto.**` fino alla fine di quel paragrafo. Poi sostituire le due funzioni di supporto:

```kotlin
@Composable
fun rememberWeatherHaptics(): WeatherHaptics {
    val context = LocalContext.current
    return remember(context) {
        val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
        WeatherHaptics(vibrator, modeOf(vibrator))
    }
}

/**
 * Quale scala di ripieghi puo' reggere questo telefono.
 *
 * **Non dipende piu' dalla versione di Android** - da `minSdk 31` primitive ed
 * effetti predefiniti ci sono sempre - ma dipende ancora dal **vibratore**, che
 * e' hardware: `areAllPrimitivesSupported` risponde di no su molti telefoni
 * recenti, e `hasAmplitudeControl` pure. I cinque modi restano tutti.
 */
private fun modeOf(vibrator: Vibrator?): WeatherHaptics.Mode = when {
    vibrator == null || !vibrator.hasVibrator() -> WeatherHaptics.Mode.NONE
    vibrator.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_CLICK,
    ) -> WeatherHaptics.Mode.PRIMITIVE
    vibrator.hasAmplitudeControl() -> WeatherHaptics.Mode.AMPLITUDE
    else -> WeatherHaptics.Mode.PREDEFINED
}
```

Rimuovere poi l'import `android.os.Build` se non piu' usato nel file.

**Attenzione**: `BLUNT` non e' piu' raggiungibile da `modeOf` (era il ramo sotto API 29) ma **il valore resta nell'enum e resta gestito** nei `when` di `raindrop` e `thunder`. Toglierlo significherebbe toccare due `when` esaustivi per guadagnare nulla.

- [ ] **Step 4: potare `DeviceLocation`**

In `awaitFix`, dentro `object : LocationListener`, rimuovere i tre override vuoti e il commento che li giustifica:

```kotlin
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }
                }
```

Il commento da rimuovere e' quello che comincia con `// Su Android 8 le tre qui sotto sono ancora astratte`. Rimuovere anche l'import `android.os.Bundle` se non piu' usato.

- [ ] **Step 5: compilare e provare**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug testDebugUnitTest --console=plain 2>&1 | Select-String -Pattern "BUILD|FAILED|^e: "
```

Atteso: `BUILD SUCCESSFUL`. Se compare `e: unresolved reference`, e' un import rimosto di troppo: rimetterlo.

- [ ] **Step 6: verificare che lint sia migliorato**

```powershell
.\gradlew.bat lintDebug --console=plain 2>&1 | Select-String -Pattern "errors,"
```

Atteso: `0 errors` e **meno di 18 warning** — spariscono quelli su `targetCellWidth` e `targetCellHeight`, che erano attributi API 31 dichiarati con minimo 26. Se gli errori non sono 0, fermarsi: qualcosa e' stato potato di troppo.

- [ ] **Step 7: provare l'aptica sul telefono**

L'aptica e' l'unica cosa che questo task cambia a runtime, e nessun test la copre.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "app\build\outputs\apk\debug\app-debug.apk"
& $adb shell am start -n io.github.noximiliencoxen.caelum/.MainActivity
```

Scorrere la barra delle ore fino a un'ora di pioggia: deve vibrare come prima. Nessuna vibrazione = `modeOf` sbagliato.

- [ ] **Step 8: aggiornare `CONTESTO.md`**

Nella sezione 2 (Toolchain), aggiornare `minSdk` da 26 a 31 e aggiungere il perche': la sfocatura del vetro esiste solo da Android 12, e alzare il minimo ha permesso di togliere l'impalcatura di compatibilita' dell'aptica e i tre override vuoti di `DeviceLocation`. Citare cosa si perde: Android 8, 9, 10, 11.

- [ ] **Step 9: commit**

```bash
git add -A
git commit -m "minSdk a 31, e via l'impalcatura che teneva in piedi

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: i sommari compatti

Prima la logica, che e' pura e quindi si testa davvero. La grafica viene dopo.

**Files:**
- Create: `app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/temperature/CardSummary.kt`
- Test: `app/src/test/kotlin/io/github/noximiliencoxen/caelum/ui/temperature/CardSummaryTest.kt`

**Interfaces:**
- Consumes: `DetailMode` (gia' esistente), `DayForecast`, `AirQuality`, `TempUnit`, `MoonPhase`, `MoonSegment`.
- Produces:
  - `data class CardSummary(val valore: String, val didascalia: String?)`
  - `fun summaryFor(mode: DetailMode, day: DayForecast?, air: AirQuality?, date: LocalDate, unit: TempUnit): CardSummary`

  Il Task 4 chiama **solo** `summaryFor` con questa firma esatta.

- [ ] **Step 1: scrivere i test che falliscono**

Creare `app/src/test/kotlin/io/github/noximiliencoxen/caelum/ui/temperature/CardSummaryTest.kt`:

```kotlin
package io.github.noximiliencoxen.caelum.ui.temperature

import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CardSummaryTest {

    private val giorno = DayForecast(
        date = LocalDate.of(2026, 9, 7),
        label = "OGGI",
        tempMax = 36.2,
        tempMin = 23.4,
        precipitationSum = 12.0,
        precipProbability = 80,
        gustMax = 14.5,
        sunrise = LocalDateTime.of(2026, 9, 7, 6, 45),
        sunset = LocalDateTime.of(2026, 9, 7, 19, 42),
    )

    @Test
    fun `la temperatura mostra massima e minima`() {
        val s = summaryFor(DetailMode.TEMPERATURA, giorno, null, giorno.date, TempUnit.CELSIUS)
        assertEquals("36° / 23°", s.valore)
    }

    @Test
    fun `il vento mostra la raffica massima`() {
        val s = summaryFor(DetailMode.VENTO, giorno, null, giorno.date, TempUnit.CELSIUS)
        assertEquals("15 m/s", s.valore)
    }

    @Test
    fun `il sole mostra il tramonto`() {
        val s = summaryFor(DetailMode.SOLE, giorno, null, giorno.date, TempUnit.CELSIUS)
        assertEquals("19:42", s.valore)
        assertEquals("TRAMONTO", s.didascalia)
    }

    @Test
    fun `senza dati il valore e' due trattini e non un'eccezione`() {
        val s = summaryFor(DetailMode.TEMPERATURA, null, null, giorno.date, TempUnit.CELSIUS)
        assertEquals("--", s.valore)
    }
}
```

**Se `DayForecast` non accetta questi parametri con questi nomi**, aprire `data/Model.kt` e usare i nomi veri: il modello e' la fonte, non questo piano.

- [ ] **Step 2: far fallire i test**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest --console=plain 2>&1 | Select-String -Pattern "CardSummary|FAILED|BUILD"
```

Atteso: fallimento di compilazione, `unresolved reference: summaryFor`. E' il fallimento giusto.

- [ ] **Step 3: scrivere l'implementazione minima**

Creare `CardSummary.kt`. Le scelte di *quale* sia la cosa importante per ogni sezione sono decisioni di questo piano, non del lettore:

```kotlin
package io.github.noximiliencoxen.caelum.ui.temperature

import io.github.noximiliencoxen.caelum.data.AirQuality
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.asPlainDegrees
import io.github.noximiliencoxen.caelum.ui.home.MoonPhase
import io.github.noximiliencoxen.caelum.ui.home.MoonSegment
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Cosa una scheda tiene in evidenza da chiusa: una cifra e, se serve, cosa e'. */
data class CardSummary(val valore: String, val didascalia: String?)

private val ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val VUOTO = "--"

/**
 * La cosa che risponde alla domanda per cui si apre quella sezione.
 *
 * Le scelte, perche' non sono ovvie tutte allo stesso modo:
 *
 * - **Temperatura**: massima e minima. Erano sotto la condizione nella
 *   schermata principale e sono state tolte di li' proprio per finire qui.
 * - **Sole**: il **tramonto**, non le ore di sole. La pagina ha "ORE DI SOLE"
 *   come unita', ma nessuno apre la sezione sole per sapere quante ore sono
 *   state: la si apre per sapere quando fa buio.
 * - **Precipitazioni**: i millimetri, con la probabilita' come didascalia. Il
 *   quanto forte prima del quanto probabile: e' la differenza fra prendere
 *   l'ombrello e non prenderlo.
 * - **Vento**: la **raffica**, non la media. La media non rovescia niente.
 * - **Aria**: l'indice europeo, che e' l'unica cifra della pagina.
 * - **Luna**: il nome della fase, con la percentuale illuminata sotto. Il nome
 *   e' cio' che si riconosce; la percentuale da sola non dice niente.
 */
fun summaryFor(
    mode: DetailMode,
    day: DayForecast?,
    air: AirQuality?,
    date: LocalDate,
    unit: TempUnit,
): CardSummary = when (mode) {
    DetailMode.TEMPERATURA -> {
        val max = day?.tempMax
        val min = day?.tempMin
        if (max == null || min == null) {
            CardSummary(VUOTO, null)
        } else {
            CardSummary("${max.asPlainDegrees(unit)} / ${min.asPlainDegrees(unit)}", null)
        }
    }

    DetailMode.SOLE -> CardSummary(
        valore = day?.sunset?.format(ORA) ?: VUOTO,
        didascalia = "TRAMONTO",
    )

    DetailMode.PRECIPITAZIONI -> CardSummary(
        valore = day?.precipitationSum?.let { "${it.roundToInt()} mm" } ?: VUOTO,
        didascalia = day?.precipProbability?.let { "$it% DI PROBABILITA'" },
    )

    DetailMode.VENTO -> CardSummary(
        valore = day?.gustMax?.let { "${it.roundToInt()} m/s" } ?: VUOTO,
        didascalia = "RAFFICA MASSIMA",
    )

    DetailMode.ARIA -> CardSummary(
        valore = air?.europeanAqi?.toString() ?: VUOTO,
        didascalia = "INDICE EUROPEO",
    )

    DetailMode.LUNA -> {
        val phase = MoonPhase.at(date)
        CardSummary(
            valore = MoonSegment.of(phase).label,
            didascalia = "${(MoonPhase.illumination(phase) * 100f).roundToInt()}% ILLUMINATA",
        )
    }
}
```

- [ ] **Step 4: far passare i test**

```powershell
.\gradlew.bat testDebugUnitTest --console=plain 2>&1 | Select-String -Pattern "FAILED|BUILD"
```

Atteso: `BUILD SUCCESSFUL`, e i test totali passano da 39 a 43.

- [ ] **Step 5: commit**

```bash
git add app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/temperature/CardSummary.kt app/src/test/kotlin/io/github/noximiliencoxen/caelum/ui/temperature/CardSummaryTest.kt
git commit -m "Cosa tiene in evidenza ogni scheda da chiusa

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: il guscio di vetro

**Files:**
- Create: `app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/common/GlassCard.kt`

**Interfaces:**
- Consumes: `LocalMeteoColors` (gia' esistente).
- Produces:
  ```kotlin
  @Composable
  fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)
  ```
  I Task 4, 5 e 6 avvolgono il contenuto delle schede in questa.

- [ ] **Step 1: scrivere il composable**

```kotlin
package io.github.noximiliencoxen.caelum.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors

/**
 * La superficie di vetro su cui vive una scheda del dettaglio.
 *
 * **Il lucido lo fa il bordo, non il fondo.** Un fondo piu' chiaro sembra
 * plastica; un filo di luce che scorre dall'alto in basso sembra il bordo di una
 * lastra. Il gradiente parte acceso in cima e si spegne verso il basso, come se
 * la luce venisse da sopra - che e' da dove viene in ogni schermata di questa
 * app, visto che il cielo sta in alto.
 *
 * **I colori escono da `LocalMeteoColors`** e non sono fissi. Il fondo di questa
 * app segue il cielo lungo tutta la giornata: una tinta scelta guardando
 * mezzogiorno sarebbe sbagliata all'alba. E' l'errore gia' fatto una volta col
 * selettore ore/settimana, dove `text` e `label` si sono rivelati
 * indistinguibili su cielo chiaro.
 *
 * Nessuna ombra portata: il distacco lo da' gia' la sfocatura dietro (Task 4).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalMeteoColors.current
    val forma = RoundedCornerShape(ANGOLO)

    Column(
        modifier = modifier
            .clip(forma)
            .background(colors.background.copy(alpha = FONDO))
            .border(
                width = BORDO,
                brush = Brush.verticalGradient(
                    listOf(
                        colors.text.copy(alpha = LUCE_ALTA),
                        colors.text.copy(alpha = LUCE_BASSA),
                    ),
                ),
                shape = forma,
            ),
        content = content,
    )
}

private val ANGOLO = 28.dp
private val BORDO = 1.dp

/** Quanto il vetro lascia passare. Sotto, la sfocatura si perde; sopra, sparisce. */
private const val FONDO = 0.42f
private const val LUCE_ALTA = 0.55f
private const val LUCE_BASSA = 0.08f
```

- [ ] **Step 2: compilare**

```powershell
.\gradlew.bat assembleDebug --console=plain 2>&1 | Select-String -Pattern "BUILD|^e: "
```

Atteso: `BUILD SUCCESSFUL`. Nessuna verifica visiva ancora: il guscio non e' in scena finche' il Task 5 non lo usa.

- [ ] **Step 3: commit**

```bash
git add app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/common/GlassCard.kt
git commit -m "Il guscio di vetro delle schede

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: la sfocatura della schermata principale

**Files:**
- Modify: `app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/MeteoApp.kt`

**Interfaces:**
- Consumes: la frazione di apertura del foglio, che `SheetGesture` gia' espone e che `MeteoApp` gia' usa per smorzare la principale.
- Produces: nulla di nuovo per gli altri task.

- [ ] **Step 1: trovare dove la principale viene gia' smorzata**

```bash
grep -n "graphicsLayer\|alpha =\|scaleX\|scaleY" app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/MeteoApp.kt
```

Il KDoc del file dichiara "dietro, la principale arretra e si smorza": esiste gia' un `graphicsLayer` sulla home legato all'apertura del foglio. **La sfocatura va aggiunta li', sulla stessa frazione**, non in un punto nuovo.

- [ ] **Step 2: aggiungere la sfocatura**

Sul modificatore della schermata principale, accanto a scala e opacita' esistenti:

```kotlin
    .blur(radius = (SFOCATURA_MAX * frazioneApertura).dp)
```

con, in fondo al file:

```kotlin
/**
 * Quanto si sfuma la principale col foglio aperto.
 *
 * **Una sola sfocatura, dietro tutto**, e non un fondo sfocato per ogni scheda:
 * sfocare sei superfici a ogni fotogramma di scorrimento fa scendere i
 * fotogrammi su un telefono medio, e sullo schermo la differenza non si vede.
 *
 * Da `minSdk 31` `Modifier.blur` c'e' sempre: e' esattamente la ragione per cui
 * il minimo e' stato alzato (Task 1).
 */
private const val SFOCATURA_MAX = 24
```

Import: `androidx.compose.ui.draw.blur`.

- [ ] **Step 3: provare sul telefono**

```powershell
.\gradlew.bat assembleDebug --console=plain 2>&1 | Select-String -Pattern "BUILD|^e: "
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "app\build\outputs\apk\debug\app-debug.apk"
& $adb shell am start -n io.github.noximiliencoxen.caelum/.MainActivity
```

Trascinare il foglio verso l'alto: la principale deve sfocarsi **progressivamente col dito**, non di scatto a foglio aperto. Se e' a scatto, la sfocatura e' legata a un booleano invece che alla frazione.

- [ ] **Step 4: commit**

```bash
git add app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/MeteoApp.kt
git commit -m "La principale si sfuma sotto il foglio

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: la pila, in stato compatto

Il cambio di contenitore. Alla fine di questo task le sei schede si sfogliano ma **non si espandono ancora**: l'espansione e' il Task 6.

**Files:**
- Modify: `app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/temperature/TemperatureDetailScreen.kt:262-284`

**Interfaces:**
- Consumes: `summaryFor` (Task 2), `GlassCard` (Task 3).
- Produces: `pagerState` verticale che il Task 6 spegne e riaccende.

- [ ] **Step 1: sostituire il pager**

Cambiare l'import `androidx.compose.foundation.pager.HorizontalPager` in `VerticalPager`, e sostituire il blocco alle righe 262-284:

```kotlin
            VerticalPager(
                state = pagerState,
                // Il padding e' cio' che rende la scheda **contenuta** invece che
                // a tutto schermo, e insieme fa sbucare le vicine sopra e sotto.
                // Senza, l'aggancio pieno diventa full screen, cioe' proprio la
                // forma che e' stata scartata.
                contentPadding = PaddingValues(vertical = SBIRCIA),
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                val mode = modes.getOrNull(page) ?: return@VerticalPager
                val giorno = state.forecast?.days?.firstOrNull()
                val sommario = summaryFor(
                    mode = mode,
                    day = giorno,
                    air = state.air,
                    date = giorno?.date ?: LocalDate.now(),
                    unit = state.unit,
                )
                GlassCard(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Text(
                        text = mode.title,
                        style = MeteoType.caption,
                        color = LocalMeteoColors.current.label,
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp),
                    )
                    Text(
                        text = sommario.valore,
                        style = MeteoType.title,
                        color = LocalMeteoColors.current.text,
                        modifier = Modifier.padding(start = 20.dp, top = 6.dp),
                    )
                    sommario.didascalia?.let { didascalia ->
                        Text(
                            text = didascalia,
                            style = MeteoType.caption,
                            color = LocalMeteoColors.current.label,
                            modifier = Modifier.padding(start = 20.dp, top = 4.dp),
                        )
                    }
                }
            }
```

E in fondo al file:

```kotlin
/**
 * Quanto delle schede vicine sbuca sopra e sotto quella al centro.
 *
 * **Non e' decorazione**: e' cio' che dice, senza scriverlo, che la pila
 * continua. Un aggancio pieno senza questo margine e' indistinguibile da una
 * schermata sola che cambia contenuto.
 *
 * Il valore va **misurato sul telefono piu' piccolo che l'app supporta**, non
 * scelto qui: e' la stessa decisione dell'altezza della scheda compatta, presa
 * dai due lati. `MeteoLayout` esiste gia' per questo genere di conti.
 */
private val SBIRCIA = 56.dp
```

Import da aggiungere: `androidx.compose.foundation.pager.VerticalPager`, `androidx.compose.foundation.layout.PaddingValues`, `java.time.LocalDate`.

- [ ] **Step 2: compilare**

```powershell
.\gradlew.bat assembleDebug --console=plain 2>&1 | Select-String -Pattern "BUILD|^e: "
```

Se `pagerState` da' errore di tipo: `rememberPagerState` e' condiviso fra i due pager e non cambia firma. Se `modes` non e' visibile, e' dichiarato alla riga 106.

- [ ] **Step 3: provare sul telefono e fotografare**

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "app\build\outputs\apk\debug\app-debug.apk"
& $adb shell am start -n io.github.noximiliencoxen.caelum/.MainActivity
```

Trascinare il foglio verso l'alto, poi scorrere in verticale. Verificare **tutte** queste cose, che sono i modi in cui questo task puo' sbagliare:

1. una scheda si aggancia al centro, non scorre libera;
2. si vedono i bordi di quella sopra e di quella sotto;
3. attorno alla scheda si vede la principale sfocata, non un fondo pieno;
4. i sei sommari mostrano cifre vere, non `--`;
5. il bordo lucido si vede sia di giorno sia di notte (scorrere la barra delle ore fino alle 03:00 e riaprire).

Il punto 5 e' quello che si dimentica, ed e' quello che ha gia' fatto sbagliare il selettore ore/settimana.

- [ ] **Step 4: togliere `PanelPicker`**

La fila orizzontale di linguette serviva a saltare fra le pagine di un carosello
orizzontale. Con una pila verticale **non ha piu' un carosello da comandare**:
lasciata in scena mostrerebbe i comandi di una navigazione che non esiste,
ed e' il genere di residuo che resta li' per mesi perche' "intanto non da'
fastidio".

Trovarne l'uso e rimuoverlo:

```bash
grep -n "PanelPicker" app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/temperature/TemperatureDetailScreen.kt
```

Rimuovere la chiamata e il suo import. **Non cancellare il file
`PanelPicker.kt`** in questo commit: se alla prova sul telefono la pila risulta
scomoda da percorrere — sei sezioni sono sei trascinate per arrivare alla Luna —
il modo piu' rapido di rimediare e' rimetterla. Si cancella nel commit
successivo, quando la pila ha dimostrato di reggere da sola.

Se dopo la prova la pila regge: cancellare `PanelPicker.kt` e togliere anche
`DetailMode.chipLabel`, che esisteva solo per quelle linguette.

- [ ] **Step 5: commit**

```bash
git add app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/temperature/TemperatureDetailScreen.kt
git commit -m "Il dettaglio diventa una pila verticale di schede

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: l'espansione, e lo spegnimento dell'aggancio

**Files:**
- Modify: `app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/temperature/TemperatureDetailScreen.kt`

**Interfaces:**
- Consumes: il `VerticalPager` del Task 5, le sei pagine di `ui/temperature/pages/` con le firme esatte gia' in uso alle righe 266-281.
- Produces: nulla per i task successivi.

- [ ] **Step 1: aggiungere lo stato di espansione**

Accanto a `pagerState` (riga 111 circa):

```kotlin
    // ── Un solo scorrimento attivo per volta ──────────────────────────────────
    //
    // Scheda chiusa: scorre la pila. Scheda aperta: scorre il contenuto, e la
    // pila si ferma. **Non e' prudenza teorica**: e' la trappola #35 di
    // CONTESTO, dove due scorrimenti verticali che si contendevano lo stesso
    // dito hanno prodotto un foglio che si assestava sull'avanzo di chiunque e
    // una guardia che bloccava del tutto lo scorrimento del contenuto. Qui il
    // groviglio non puo' presentarsi perche' non esiste l'istante in cui
    // entrambi sono attivi.
    var espansa by rememberSaveable { mutableStateOf(false) }
```

- [ ] **Step 2: spegnere l'aggancio quando e' espansa**

Sul `VerticalPager`:

```kotlin
                userScrollEnabled = !espansa,
```

- [ ] **Step 3: rendere la scheda toccabile e mostrare il contenuto**

Dentro `GlassCard`, dopo i testi del sommario:

```kotlin
                    if (espansa) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            when (mode) {
                                DetailMode.TEMPERATURA ->
                                    TemperaturePage(state, layout, viewModel::setWeekMode, week = week)
                                DetailMode.SOLE ->
                                    SunPage(state, layout, viewModel::setWeekMode, week = week)
                                DetailMode.PRECIPITAZIONI ->
                                    RainPage(state, layout, viewModel::setWeekMode, week = week)
                                DetailMode.VENTO ->
                                    WindPage(state, layout, viewModel::setWeekMode, week = week)
                                DetailMode.ARIA ->
                                    AirPage(state, layout, week = week)
                                DetailMode.LUNA ->
                                    MoonPage(state, layout, week = week)
                            }
                        }
                    }
```

e sul `GlassCard` stesso il tocco che apre e chiude:

```kotlin
                GlassCard(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = if (espansa) "chiudere la scheda" else "aprire la scheda",
                        ) { espansa = !espansa },
                ) {
```

Import da aggiungere: `androidx.compose.foundation.verticalScroll`, `androidx.compose.foundation.rememberScrollState`, `androidx.compose.foundation.clickable`, `androidx.compose.foundation.interaction.MutableInteractionSource`, `androidx.compose.runtime.saveable.rememberSaveable`, `androidx.compose.runtime.mutableStateOf`, `getValue`, `setValue`.

**Attenzione**: se una pagina contiene gia' un proprio `verticalScroll`, **toglierlo li'** invece di annidarlo qui. Due `verticalScroll` annidati sono lo stesso guaio che questo task esiste per evitare.

- [ ] **Step 4: provare sul telefono**

Installare e verificare, in quest'ordine:

1. scheda chiusa, scorrimento verticale → la pila si aggancia;
2. tocco sulla scheda → si apre e mostra il contenuto della sezione;
3. scorrimento con scheda aperta → scorre **solo** il contenuto, la pila resta ferma;
4. tocco di nuovo → si richiude e la pila torna a scorrere;
5. ripetere su tutte e sei.

Il punto 3 e' il cuore: se scorrendo il contenuto la pila cambia scheda, `userScrollEnabled` non e' legato allo stato giusto.

- [ ] **Step 5: commit**

```bash
git add app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/temperature/TemperatureDetailScreen.kt
git commit -m "Una scheda si apre, e la pila si ferma

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: la chiusura toccando fuori

**Files:**
- Modify: `app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/temperature/TemperatureDetailScreen.kt`

**Interfaces:**
- Consumes: la richiamata di chiusura del foglio che `MeteoApp` gia' passa (cercarla con `grep -n "onClose\|onDismiss" TemperatureDetailScreen.kt` — esiste gia' perche' il foglio si chiude gia' col gesto).
- Produces: nulla.

- [ ] **Step 1: rendere il velo toccabile**

Sul contenitore che sta **dietro** al pager, non sul pager:

```kotlin
        // Il velo chiude, ma solo se il dito **non si e' mosso**: appoggiarlo sul
        // margine e scorrere deve muovere la pila, non chiudere il foglio. E' la
        // stessa distinzione che la schermata principale fa gia' fra tocco e
        // rotazione della scena, e si risolve allo stesso modo - con una soglia
        // di movimento, non con due gestori separati che si rubano l'evento.
        .pointerInput(Unit) {
            detectTapGestures(onTap = { onClose() })
        }
```

Import: `androidx.compose.foundation.gestures.detectTapGestures`, `androidx.compose.ui.input.pointer.pointerInput`.

`detectTapGestures` ignora gia' i gesti che diventano trascinamenti: non serve una soglia scritta a mano.

- [ ] **Step 2: provare sul telefono**

1. toccare il margine attorno alla scheda → il foglio si chiude;
2. **appoggiare il dito sul margine e trascinare** → la pila scorre, il foglio **non** si chiude;
3. toccare dentro la scheda → si espande, non si chiude.

Il punto 2 e' quello che si rompe per primo.

- [ ] **Step 3: commit**

```bash
git add app/src/main/kotlin/io/github/noximiliencoxen/caelum/ui/temperature/TemperatureDetailScreen.kt
git commit -m "Si chiude toccando fuori dalle schede

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: rifare gli agganci di `capture.sh`

**Files:**
- Modify: `scripts/capture.sh`
- Modify: `CONTESTO.md`

**Interfaces:**
- Consumes: la pila dei Task 5-7.
- Produces: nulla.

- [ ] **Step 1: trovare cosa naviga il carosello**

```bash
grep -n "d5-temperatura\|d6-sole\|d7-pioggia\|d8-vento\|d9-aria\|d10-luna\|input swipe" scripts/capture.sh
```

Gli scatti `dN` sfogliavano il carosello **in orizzontale**. Con una pila verticale quei gesti non arrivano piu' da nessuna parte.

- [ ] **Step 2: sostituire i gesti**

Le trascinate orizzontali diventano verticali. Il gesto che passava alla sezione successiva era della forma `adbt shell input swipe $FROM_X $MID_Y $TO_X $MID_Y 300` — da destra a sinistra, `MID_Y` fisso — e diventa una trascinata dal basso verso l'alto a `x` fisso:

```bash
# La pila si aggancia: una trascinata = una scheda. I trecento millisecondi sono
# gia' tarati per il carosello e vanno bene anche qui.
prossima_scheda() {
  adbt shell input swipe "$(( W / 2 ))" "$(( H * 62 / 100 ))" \
                         "$(( W / 2 ))" "$(( H * 28 / 100 ))" 300
  sleep 1
}
```

E poiche' il contenuto delle sezioni ora vive nello stato **espanso**, ogni sezione vuole due scatti invece di uno:

```bash
scheda() {
  local nome="$1"
  shoot "${nome}-chiusa"
  # Il tocco al centro apre la scheda (Task 6). Toccare **fuori** dalla scheda
  # chiuderebbe il foglio: quello e' il velo, non un punto neutro.
  adbt shell input tap "$(( W / 2 ))" "$(( H / 2 ))"
  sleep 1
  shoot "${nome}-aperta"
  adbt shell input tap "$(( W / 2 ))" "$(( H / 2 ))"
  sleep 1
}
```

`W` e `H` sono gia' calcolati dallo script, alla riga con `wm size`.

- [ ] **Step 3: aggiornare `CONTESTO.md`**

Aggiungere alla sezione 7 una trappola nuova: gli agganci di `capture.sh` sono legati alla **forma** della navigazione, non solo ai nomi delle schermate, e un cambio di contenitore li invalida in silenzio — il job resta verde e fotografa la schermata sbagliata.

- [ ] **Step 4: NON usare la CI come giudice**

Il job `screenshots` fallisce per una causa esterna gia' misurata (crash dell'emulazione grafica, giro `34002545333`: emulatore morto con 11 GB liberi, carico 0.00, memoria del processo piatta). Verificare gli agganci **sul telefono**, con lo stesso `adb shell input` che usa lo script.

- [ ] **Step 5: commit**

```bash
git add scripts/capture.sh CONTESTO.md
git commit -m "Gli scatti seguono la pila, non piu' il carosello

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Dopo il piano

Il radar dei fulmini (`docs/superpowers/specs/2026-09-07-radar-fulmini-design.md`) diventa la **settima scheda** e va pianificato a parte, dopo che questa pila e' in piedi. Il suo emendamento del 7 settembre lascia due cose da decidere allora: cosa mostra da chiusa, e dove sta nella pila visto che tace per gran parte dell'anno.
