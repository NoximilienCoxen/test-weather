package io.github.noximiliencoxen.caelum.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.asIndex
import io.github.noximiliencoxen.caelum.ui.asMetresPerSecond
import io.github.noximiliencoxen.caelum.ui.asMillimetres
import io.github.noximiliencoxen.caelum.ui.asPercent
import io.github.noximiliencoxen.caelum.ui.common.MeteoLayout
import io.github.noximiliencoxen.caelum.ui.home.MoonPhase
import io.github.noximiliencoxen.caelum.ui.home.MoonSegment
import io.github.noximiliencoxen.caelum.ui.motion.PhysicalNumber
import io.github.noximiliencoxen.caelum.ui.motion.SceneRotation
import io.github.noximiliencoxen.caelum.ui.motion.rememberSceneRotation
import io.github.noximiliencoxen.caelum.ui.motion.rotatesScene
import io.github.noximiliencoxen.caelum.ui.render3d.Camera
import io.github.noximiliencoxen.caelum.ui.render3d.MOON_SEAS
import io.github.noximiliencoxen.caelum.ui.render3d.glow
import io.github.noximiliencoxen.caelum.ui.render3d.moon
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.MeteoType
import androidx.compose.material3.Text
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Una scheda del feed, per ora **con il posto dell'oggetto ancora vuoto**.
 *
 * Lo scheletro e' lo stesso per tutte e cinque le sezioni che non sono la
 * temperatura: titolo, la cifra girabile col dito, il palco dell'oggetto che
 * verra', e sotto i due o tre numeri che l'app sa gia'. Si decide una sezione
 * alla volta cosa metterci - e' la regola di lavoro del progetto, una schermata
 * per volta e quella diventa il modello per le altre - e intanto il palco
 * dichiara cosa manca invece di essere un buco.
 *
 * **Niente scorrimento interno.** Una scheda sta in una schermata e basta: il
 * gesto verticale appartiene tutto al feed, e una colonna che scorre dentro una
 * pagina che scorre e' la contesa che `SheetNestedScroll` esisteva per
 * arbitrare. Tolto il foglio, e' tolta anche la contesa - e va tenuta tolta.
 *
 * **Niente animazioni a riposo**, per la trappola #8: da fermo l'app deve
 * disegnare zero fotogrammi, e con sei schede da comporre e' piu' facile
 * romperlo di prima. Quello che si muove qui e' la cifra, e si muove solo
 * mentre un dito la gira.
 *
 * I colori vengono da `LocalMeteoColors` e le tinte da [rememberSkyAccents], mai
 * da `MaterialTheme.colorScheme`: le schede vivono **sul cielo**, non su una
 * superficie antracite, e il grigio dei pannelli sopra un cielo di meta'
 * mattina e' il difetto a 1,01:1 della sezione 8-bis di CONTESTO.
 */
@Composable
fun SectionCard(
    section: FeedSection,
    state: UiState,
    tilt: State<Offset>,
    layout: MeteoLayout,
    /**
     * Falso quando la scheda e' composta ma non la guarda nessuno.
     *
     * Il carosello tiene composta anche la scheda accanto per averla pronta a
     * meta' trascinamento, e un `withFrameNanos` dentro una finestra visibile
     * continua a battere anche se la sua pagina e' fuori vista. Senza questa
     * guardia, stando sull'aria la pioggia continuerebbe a cadere in una vasca
     * che non si vede - e il telefono a vibrare per gocce che nessuno guarda.
     * E' la stessa ragione del flag della prima scheda (`FeedScreen.kt`), e la
     * stessa regola: **un tocco che non corrisponde a niente di visibile non e'
     * un riscontro.**
     */
    alive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val accents = rememberSkyAccents()
    val accent = section.accentOf(accents, colors.text)
    // Una scena per scheda, non una condivisa: girare la cifra della pioggia
    // non deve girare la luna. Sono oggetti diversi visti da punti diversi.
    val rotation: SceneRotation = rememberSceneRotation()

    // L'aggancio `--ei giro`, che finora arrivava alla sola prima schermata:
    // `rotation.pin` era chiamata in un posto solo, in `HomeScreen`. Senza,
    // **nessuna scheda del feed si puo' fotografare girata** - la luna
    // compresa - e siccome tutta la ragione per cui la vasca della pioggia si
    // gira e' che girandola si legge la scala incisa, quel giro sarebbe
    // infotografabile e quindi non verificabile.
    LaunchedEffect(state.forcedYawDeg) { rotation.pin(state.forcedYawDeg) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // ── Il centro della scheda e' il centro dello schermo ───────────────
        //
        // Non il centro di quel che avanza accanto alla colonna di icone. Il
        // margine asimmetrico che stava qui - `end = gutter + RAIL_WIDTH` -
        // spostava titolo, cifra, riquadro e numeri **ventidue punti a
        // sinistra**, ed e' lo stesso difetto gia' corretto sulla prima scheda:
        // il commento in `FeedScreen` racconta che li' il margine faceva
        // sembrare tutto spostato e che adesso la colonna galleggia nel margine
        // che c'e' gia'. Le altre cinque schede erano rimaste indietro.
        //
        // **Un margine simmetrico non sposta il centro: costa solo larghezza.**
        // Quindi la domanda per ogni riga e' una sola - passa davanti alla
        // colonna? La colonna e' alta [RAIL_SPAN] e sta a meta' altezza, quindi
        // le righe in cima e in fondo la scavalcano **se la scheda e' alta
        // abbastanza**. Sotto quella misura - cioe' in orizzontale, dove la
        // colonna occupa quasi tutta l'altezza - non la scavalca piu' nessuno,
        // e l'inserto va sull'intera colonna: resta centrata lo stesso, e non
        // collide.
        val railClearsEnds = maxHeight >= RAIL_SPAN + endsBlock() * 2
        val edgeInset = if (railClearsEnds) 0.dp else RAIL_WIDTH
        val bodyInset = if (railClearsEnds) RAIL_WIDTH else 0.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = layout.gutter + edgeInset),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = section.title,
                // `label` e non `caption`: e' la riga che dice di cosa parla la
                // scheda, e sulla prima lo stesso mestiere lo fa la condizione, che
                // usa questo. Il sottotitolo sotto resta una didascalia, e la
                // differenza fra le due si vede dalla misura oltre che dal colore.
                style = MeteoType.label,
                color = accent,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Text(
                text = subtitle(state, section),
                style = MeteoType.caption,
                color = colors.label,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            // Eroe e palco sono le due righe che passano davanti alla
            // colonna, e l'inserto che se ne tengono lontane e' **simmetrico**:
            // non le sposta, le stringe. Titolo, sottotitolo e numeri restano a
            // piena larghezza - la scavalcano - e ci guadagnano i
            // quarantaquattro punti che il vecchio margine si prendeva: su uno
            // schermo stretto una colonna dei numeri passa da novantasei a
            // centodieci punti, e PROBABILITA' smette di rischiare il taglio.
            SectionHero(
                section = section,
                state = state,
                rotation = rotation,
                tilt = tilt,
                accent = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(HERO_SHARE)
                    .padding(horizontal = bodyInset),
            )

            Stage(
                caption = section.stage,
                color = colors.line,
                label = colors.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(STAGE_SHARE)
                    .padding(horizontal = bodyInset, vertical = 10.dp),
            )

            SectionNumbers(
                section = section,
                state = state,
                accent = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }
    }
}

/**
 * La cifra della sezione, girabile col dito - o il corpo della luna, che una
 * cifra non ce l'ha.
 *
 * Il ramo della luna sta **prima** del controllo sul numero, e per due ragioni.
 * La prima e' che il suo eroe e' il corpo. La seconda e' che non ha bisogno
 * della previsione: la fase si calcola in locale, quindi e' l'unica sezione che
 * ha ancora qualcosa da mostrare quando la rete tace, e passare dal controllo la
 * spegnerebbe insieme alle altre.
 */
@Composable
private fun SectionHero(
    section: FeedSection,
    state: UiState,
    rotation: SceneRotation,
    tilt: State<Offset>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // **L'altezza si prende qui e si tiene in una variabile.** Piu' sotto la
        // cifra vive dentro un `Box` dentro una `Column`, e li' dentro
        // `maxHeight` non si risolve piu': i riceventi impliciti piu' vicini
        // sono `BoxScope` e `ColumnScope`, e Kotlin rifiuta di risalire al
        // `BoxWithConstraintsScope` che sta fuori - *cannot be called in this
        // context with an implicit receiver*. E' la stessa trappola di
        // `AlertPillSlot` sulla prima scheda, vista da un'altra angolazione.
        val available = maxHeight

        if (section == FeedSection.LUNA) {
            MoonBody(
                date = state.pageDay?.date ?: LocalDate.now(),
                rotation = rotation,
                tilt = tilt,
                light = accent,
                modifier = Modifier.fillMaxSize(),
            )
            return@BoxWithConstraints
        }

        val value = heroValue(section, state)
        if (value == null) {
            // Finche' non c'e' un numero non si disegna niente: un "--" alto
            // mezzo schermo, con tanto di spessore e di ombra, non dice "sto
            // aspettando", dice che l'app e' rotta. Si dice invece **perche'**
            // manca, che e' l'unica cosa utile in quel momento.
            val (title, message) = heroMissingReason(section, state)
            SkyMessage(title = title, message = message)
            return@BoxWithConstraints
        }

        val unit = section.unitLabel
        // La cifra sta dentro il riquadro che le tocca, e sotto di lei ci va
        // l'unita': con l'unita' in scena il corpo si riduce, se no il numero
        // sconfina di quel tanto che l'etichetta gli ha portato via.
        val body = available * if (unit.isBlank()) HERO_TYPE_SHARE else HERO_TYPE_SHARE_UNIT
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .rotatesScene(rotation),
                contentAlignment = Alignment.Center,
            ) {
                CastShadow(
                    yawDeg = { rotation.yawDeg },
                    pitchDeg = { tilt.value.y * SHADOW_PITCH },
                    color = accent.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxSize(),
                )
                PhysicalNumber(
                    text = value,
                    smallTail = heroSmallTail(section),
                    fontSize = body,
                    rotation = rotation,
                    tilt = tilt,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (unit.isNotBlank()) {
                Text(
                    text = unit,
                    style = MeteoType.caption,
                    color = LocalMeteoColors.current.label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * La luna, girabile col dito.
 *
 * E' lo stesso corpo della scultura della prima scheda e del widget - stessa
 * sfera, stessa luce, stessi mari - e sta nello stesso posto in cui le altre
 * schede mettono la cifra, dentro il `Box` che porta [rotatesScene]. Il gesto
 * quindi e' gia' quello di sempre, e non c'e' un secondo riconoscitore da
 * mettere d'accordo con nessuno.
 *
 * **I mari girano, la mediana no**, e non e' un difetto da correggere: i mari
 * stanno sulla sfera e passano dalla camera, quindi ruotando scivolano verso il
 * bordo e spariscono dietro; la mediana invece la disegna [moon] in coordinate
 * di schermo, perche' da che parte cada lo decide il Sole e non chi guarda. Una
 * falce che si raddrizza girando il telefono sarebbe una luna che cambia fase
 * perche' ci si e' spostati di venti centimetri.
 */
@Composable
private fun MoonBody(
    date: LocalDate,
    rotation: SceneRotation,
    tilt: State<Offset>,
    light: Color,
    modifier: Modifier = Modifier,
) {
    // Il tondo spento e i mari vogliono un grigio medio, non un grigio da
    // testo: la linea del cielo e' il tono che il tema tiene per i contorni.
    val dark = LocalMeteoColors.current.line
    val phase = remember(date) { MoonPhase.at(date) }
    val spoken = remember(phase) {
        val percent = (MoonPhase.illumination(phase) * 100f).roundToInt()
        "${MoonSegment.of(phase).label.lowercase()}, illuminata al $percent per cento"
    }
    Box(
        modifier = modifier.rotatesScene(rotation),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = spoken },
        ) {
            // Giro e inclinazione si leggono **qui dentro**, non in
            // composizione: girare deve ridipingere, non ricomporre.
            val unit = minOf(size.width, size.height)
            val camera = Camera(
                yawDeg = rotation.yawDeg,
                pitchDeg = tilt.value.y * SHADOW_PITCH,
                distance = unit * 2.7f,
                origin = Offset(size.width / 2f, size.height / 2f),
            )
            val radius = unit * MOON_RADIUS
            glow(camera, 0f, 0f, 0f, radius, light, 0.28f, spread = 2.0f)
            moon(
                camera = camera,
                x = 0f, y = 0f, z = 0f,
                radius = radius,
                phase = phase,
                light = light,
                dark = dark,
                alpha = 1f,
                marks = MOON_SEAS,
            )
        }
    }
}

/**
 * Il palco dell'oggetto che verra'.
 *
 * Un riquadro tratteggiato e una riga che dice cosa ci andra'. E' un segnaposto
 * dichiarato, e la differenza con uno spazio lasciato bianco e' tutta qui: chi
 * apre l'app vede un lavoro in corso invece di una schermata rotta, e chi ci
 * lavora legge la consegna dove serve.
 *
 * Il tratteggio si disegna in una tela sola: e' una figura, non un bordo di
 * layout, e passare da un `Modifier.border` vorrebbe dire una forma nuova solo
 * per farla tratteggiata.
 */
@Composable
private fun Stage(
    caption: String,
    color: Color,
    label: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF), 0f)
            val stroke = STROKE.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(CORNER.toPx()),
                style = Stroke(width = stroke, cap = StrokeCap.Round, pathEffect = dash),
            )
        }
        Text(
            text = caption,
            // `body` e non `caption`: e' una frase intera, e una frase in
            // maiuscolo spaziato si compita invece di leggersi. E' la stessa
            // ragione per cui `body` esiste.
            style = MeteoType.body,
            color = label,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
    }
}

/**
 * I due o tre numeri che la sezione sa gia' dire.
 *
 * Non sono un riempitivo del segnaposto: sono cio' che impedisce a una scheda
 * ancora da fare di essere una scheda vuota. Vengono tutti da dati che l'app ha
 * gia' in mano, quindi non costano una richiesta in piu' ne' un caso d'errore
 * nuovo.
 */
@Composable
private fun SectionNumbers(
    section: FeedSection,
    state: UiState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val day = state.pageDay
    val hour = state.pageHour
    val entries: List<Pair<String, String>> = when (section) {
        FeedSection.TEMPERATURA -> emptyList()

        FeedSection.PRECIPITAZIONI -> listOf(
            "PROBABILITA'" to hour?.precipProbability.asPercent(),
            "IN QUEST'ORA" to hour?.precipitation.asMillimetres(),
            "ORE DI PIOGGIA" to (day?.precipHours?.roundToInt()?.let { "$it H" } ?: MISSING),
        )

        FeedSection.ARIA -> listOf(
            "GIUDIZIO" to (state.air?.band?.label ?: MISSING),
            "PM 2.5" to (state.air?.pm25?.roundToInt()?.let { "$it" } ?: MISSING),
            "PM 10" to (state.air?.pm10?.roundToInt()?.let { "$it" } ?: MISSING),
        )

        FeedSection.VENTO -> listOf(
            "DIREZIONE" to Wmo.windDirection(hour?.windDirection),
            "RAFFICHE" to hour?.windGusts.asMetresPerSecond(),
            "MASSIMO OGGI" to day?.windMax.asMetresPerSecond(),
        )

        FeedSection.SOLE -> listOf(
            "ALBA" to (day?.sunrise?.toLocalTime()?.format(CLOCK) ?: MISSING),
            "TRAMONTO" to (day?.sunset?.toLocalTime()?.format(CLOCK) ?: MISSING),
            "UV MASSIMO" to day?.uvMax.asIndex(),
        )

        FeedSection.LUNA -> {
            val date = day?.date ?: LocalDate.now()
            val phase = MoonPhase.at(date)
            listOf(
                "FASE" to MoonSegment.of(phase).label,
                "ILLUMINATA" to "${(MoonPhase.illumination(phase) * 100f).roundToInt()}%",
                "ETA'" to "${MoonPhase.ageDays(phase).roundToInt()} GIORNI",
            )
        }
    }
    if (entries.isEmpty()) return

    val colors = LocalMeteoColors.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        entries.forEach { (label, value) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = MeteoType.caption,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = value,
                    // `metric` porta le cifre a larghezza fissa: tre valori
                    // affiancati che cambiano scorrendo le ore devono restare
                    // incolonnati invece di ballare.
                    style = MeteoType.metric,
                    color = accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Cosa manca, e perche', scritto **coi colori del cielo**.
 *
 * `MeteoEmptyState` faceva gia' questo mestiere e qui non andava bene: prende i
 * suoi toni da `MaterialTheme.colorScheme`, che e' tarato sull'antracite dei
 * pannelli. Sul cielo il messaggio secondario usciva marroncino su azzurro -
 * visto in uno scatto, illeggibile - ed e' esattamente il difetto della sezione
 * 8-bis di CONTESTO, reintrodotto dalla porta di servizio. Chi disegna sul cielo
 * prende le proprie tinte da `LocalMeteoColors`, dove sono gia' calcolate per
 * contrasto contro i due capi della sfumatura.
 */
@Composable
private fun SkyMessage(title: String, message: String?, modifier: Modifier = Modifier) {
    val colors = LocalMeteoColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MeteoType.label,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MeteoType.body,
                color = colors.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/**
 * L'ombra ellittica sotto la cifra.
 *
 * Non e' l'ombra proiettata della prima scheda - quella e' geometria vera dentro
 * il renderer - ma segue lo stesso giro e la stessa inclinazione, cosi' la cifra
 * qui non sembra appoggiata sul nulla.
 *
 * Giro e inclinazione arrivano per **lambda** e non per valore: cambiano a ogni
 * fotogramma del dito, e letti in composizione ricomporrebbero la scheda invece
 * di ridipingere una macchia.
 */
@Composable
private fun CastShadow(
    yawDeg: () -> Float,
    pitchDeg: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val yawRad = Math.toRadians(yawDeg().toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg().toDouble()).toFloat()
        val offsetX = sin(yawRad) * size.width * 0.12f
        val offsetY = sin(pitchRad) * size.height * 0.06f
        val halfPi = (Math.PI / 2f).toFloat()
        val scaleX = (1f - abs(yawRad) / halfPi * 0.5f).coerceAtLeast(0.2f)
        val scaleY = (1f - abs(pitchRad) / halfPi * 0.5f).coerceAtLeast(0.2f)
        val rX = size.width * 0.24f * scaleX
        val rY = size.height * 0.055f * scaleY
        val shadowCy = cy + size.height * 0.34f + offsetY
        drawOval(
            color = color,
            topLeft = Offset(cx + offsetX - rX, shadowCy - rY),
            size = Size(rX * 2f, rY * 2f),
        )
    }
}

/**
 * Di chi e di quando sono questi numeri.
 *
 * Sulla prima scheda la localita' sta scritta in cima; scendendo non ci sarebbe
 * piu', e i numeri di Forli' e quelli di Bergen si somigliano abbastanza da non
 * poterli distinguere a occhio.
 *
 * **Un'ora precisa sopra un totale del giorno e' una bugia**, e si vedeva: la
 * sezione del sole diceva "OGGI · 15:00" sopra tredici *ore di sole*, che non
 * sono le ore di sole delle quindici, sono quelle di tutta la giornata.
 */
private fun subtitle(state: UiState, section: FeedSection): String {
    val place = state.place.name.uppercase()
    val dayLabel = when (state.selectedDay) {
        0 -> "OGGI"
        1 -> "DOMANI"
        else -> state.forecast?.days?.getOrNull(state.selectedDay)?.label ?: MISSING
    }
    val moment = if (section.isDailyTotal) {
        "TUTTO IL GIORNO"
    } else {
        state.detailHour?.time?.let { runCatching { it.format(CLOCK) }.getOrNull() }
    }
    return listOfNotNull(place, dayLabel, moment).joinToString("  ·  ")
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private const val MISSING = "--"

/**
 * Quanta altezza prendono la cifra e il palco, dell'altezza che avanza.
 *
 * Il palco era otto decimi della cifra, e in uno scatto si vedeva perche' era
 * troppo: un riquadro vuoto alto mezza scheda non dice "qui arrivera'
 * qualcosa", dice che la scheda e' vuota. Ridotto, resta una fascia dichiarata
 * sotto l'oggetto invece di essere l'oggetto stesso.
 */
/**
 * Quanto prendono, in cima e in fondo, le righe che scavalcano la colonna.
 *
 * Titolo e sottotitolo da una parte, i tre numeri dall'altra. Cinquantasei
 * punti coprono il caso peggiore delle due.
 *
 * **Segue la scala del carattere di sistema**, e non e' un vezzo: a scala
 * doppia i tre numeri sono alti novanta punti e non quarantotto, e con una
 * soglia fissa il caso che questo controllo esiste per evitare rientrerebbe
 * dalla finestra proprio su chi ha il carattere grande - cioe' su chi ha piu'
 * bisogno che il conto torni.
 */
@Composable
private fun endsBlock(): Dp = 56.dp * LocalDensity.current.fontScale

private const val HERO_SHARE = 1f
private const val STAGE_SHARE = 0.62f

/**
 * Il corpo della cifra, in frazione dell'altezza che le tocca.
 *
 * Due valori e non uno: sotto la cifra ci va l'unita' quando c'e', e quella
 * riga si prende la sua altezza dalla stessa colonna. Con un valore solo la
 * cifra della pioggia - che l'unita' ce l'ha - sarebbe alta quanto quella della
 * temperatura, che non ce l'ha, e sconfinerebbe di quel tanto.
 */
private const val HERO_TYPE_SHARE = 0.74f
private const val HERO_TYPE_SHARE_UNIT = 0.66f

/** Quanto l'inclinazione del telefono piega l'ombra, in gradi. */
private const val SHADOW_PITCH = 5f

/**
 * Il raggio della luna, in frazione del lato corto del riquadro.
 *
 * Era quaranta centesimi, che e' il valore della vecchia pagina del dettaglio -
 * ma li' il riquadro dell'eroe era una frazione dell'altezza (`heroFraction`),
 * mentre qui prende tutto lo spazio che avanza. Visto in uno scatto, il corpo
 * riempiva la scheda da bordo a bordo e il segnaposto sotto sembrava
 * schiacciato: un oggetto che tocca i margini non si legge come un corpo nel
 * cielo, si legge come una macchia.
 *
 * **Da trentadue a trentacinque centesimi con la centratura.** Il raggio e'
 * frazione del lato corto del riquadro, che qui e' la larghezza, e l'inserto
 * simmetrico che tiene l'eroe lontano dalla colonna gliene toglie
 * quarantaquattro punti: a parita' di frazione la luna usciva **un sesto piu'
 * piccola** di prima, senza che niente lo dicesse. Trentacinque e' un
 * compromesso e non un ripristino - ci vorrebbe trentotto, cioe' quasi il
 * quaranta gia' scartato qui sopra - e va guardato in uno scatto, non dedotto.
 */
private const val MOON_RADIUS = 0.35f

private val CORNER = 18.dp
private val STROKE = 1.dp
private const val DASH_ON = 9f
private const val DASH_OFF = 9f
