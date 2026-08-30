package com.forli.meteo.ui.temperature

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.forli.meteo.data.DayForecast
import com.forli.meteo.data.Forecast
import com.forli.meteo.data.Wmo
import com.forli.meteo.prefs.TempUnit
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.WeatherViewModel
import com.forli.meteo.ui.asBigTemperature
import com.forli.meteo.ui.asDegrees
import com.forli.meteo.ui.asPercent
import com.forli.meteo.ui.motion.rememberDeviceTilt
import com.forli.meteo.ui.motion.rememberSceneRotation
import com.forli.meteo.ui.motion.rotatesScene
import com.forli.meteo.ui.render.ExtrudedText
import com.forli.meteo.ui.render.NumberMotion
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Palette fissa ad alto contrasto — indipendente dal tema dinamico sky/time
// ---------------------------------------------------------------------------
private val CardBackground  = Color(0xFF1A1A1A)
private val CardBorder      = Color(0xFF333333)
private val PillWhite       = Color(0xFFFFFFFF)
private val PillBlack       = Color(0xFF000000)
private val MetricLabel     = Color(0xFF888888)
private val MetricValue     = Color(0xFFFFFFFF)
private val SunAccent       = Color(0xFFFFDE59)
private val AirAccent       = Color(0xFF7EB8F7)
private val LineStrong      = Color(0xFFFFFFFF)
private val PageDotActive   = Color(0xFFFFFFFF)
private val PageDotInactive = Color(0xFF444444)

// ---------------------------------------------------------------------------
// Enum pagine
// ---------------------------------------------------------------------------
internal enum class TemperaturePage(val title: String) {
    TEMPERATURA("TEMPERATURA"),
    SOLE("SOLE"),
    PRECIPITAZIONI("PRECIPITAZIONI"),
    ARIA("ARIA"),
}

// ---------------------------------------------------------------------------
// Enum selettore grafico — usa .entries, non il deprecato .values()
// ---------------------------------------------------------------------------
enum class GraphView { GIORNO, SETTIMANA }

// ---------------------------------------------------------------------------
// Dati riga per la tabella metriche
// ---------------------------------------------------------------------------
internal data class MetricRow(
    val label:       String,
    val value:       String,
    val highlighted: Boolean = false,
)

// ---------------------------------------------------------------------------
// 1. TemperatureDetailScreen
//    Root composable: HorizontalPager con 4 pagine + Page Indicator a pallini
// ---------------------------------------------------------------------------
@Composable
fun TemperatureDetailScreen(
    state:     UiState,
    viewModel: WeatherViewModel,
) {
    val colors     = LocalMeteoColors.current
    val pagerState = rememberPagerState(pageCount = { TemperaturePage.entries.size })
    val tilt       = rememberDeviceTilt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            HorizontalPager(
                state       = pagerState,
                modifier    = Modifier.weight(1f),
                pageSpacing = 20.dp,
            ) { index ->
                TemperaturePage(
                    page      = TemperaturePage.entries[index],
                    state     = state,
                    viewModel = viewModel,
                    tilt      = tilt,
                )
            }

            // Indicatore a pallini sincronizzato con la pagina corrente
            PageIndicator(
                pageCount   = TemperaturePage.entries.size,
                currentPage = pagerState.currentPage,
                modifier    = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Indicatore a pallini
// ---------------------------------------------------------------------------
@Composable
private fun PageIndicator(
    pageCount:   Int,
    currentPage: Int,
    modifier:    Modifier = Modifier,
) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val fraction by animateFloatAsState(
                targetValue   = if (index == currentPage) 1f else 0f,
                animationSpec = spring(stiffness = 400f),
                label         = "dot$index",
            )
            val dotColor = lerp(PageDotInactive, PageDotActive, fraction)
            val dotSize  = lerp(6.dp, 8.dp, fraction)
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 2. TemperaturePage
//    Layout verticale di una singola pagina del carosello
// ---------------------------------------------------------------------------
@Composable
internal fun TemperaturePage(
    page:      TemperaturePage,
    state:     UiState,
    viewModel: WeatherViewModel,
    tilt:      State<Offset>,
) {
    val colors     = LocalMeteoColors.current
    val forecast   = state.forecast
    val day        = forecast?.days?.getOrNull(state.selectedDay)
    val showingNow = !state.weekMode && state.selectedDay == 0
    val unit       = state.unit

    val headerText       = buildHeaderText(state)
    val bigNumber        = page.bigNumberFor(forecast, day, showingNow, unit)
    val rows             = page.metricsFor(forecast, day, showingNow, unit)
    val graphDaySeries   = page.hourSeriesFor(forecast, unit)
    val graphWeekSeries  = page.weekSeriesFor(forecast, unit)
    val accentColor      = page.accent()

    // SceneRotation — gestisce il gesto orizzontale con molla al rilascio
    val rotation = rememberSceneRotation()

    Column(
        modifier            = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header orario ────────────────────────────────────────────────────
        Text(
            text      = headerText,
            style     = MeteoType.caption,
            color     = colors.text,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth(),
        )

        // ── Numero 3D interattivo ─────────────────────────────────────────────
        BoxWithConstraints(
            modifier         = Modifier
                .fillMaxWidth()
                .weight(1f)
                .rotatesScene(rotation),           // gesto orizzontale → yaw
            contentAlignment = Alignment.Center,
        ) {
            Interactive3DNumber(
                text        = bigNumber,
                fontSize    = maxHeight * 0.62f,
                tilt        = tilt,
                rotation    = rotation,
                accentColor = accentColor,
                modifier    = Modifier.fillMaxSize(),
            )
        }

        // ── Tabella metriche ─────────────────────────────────────────────────
        if (rows.isNotEmpty()) {
            TemperatureMetricsTable(
                rows     = rows,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Grafico + selettore GIORNO/SETTIMANA ─────────────────────────────
        TemperatureGraphSection(
            daySeries  = graphDaySeries,
            weekSeries = graphWeekSeries,
            weekMode   = state.weekMode,
            onToggle   = viewModel::setWeekMode,
            accent     = accentColor,
            modifier   = Modifier
                .fillMaxWidth()
                .height(130.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers — header
// ---------------------------------------------------------------------------
private fun buildHeaderText(state: UiState): String {
    val hour = state.hour ?: return "OGGI"
    val dayLabel = when (state.selectedDay) {
        0    -> "OGGI"
        1    -> "DOMANI"
        else -> state.forecast?.days?.getOrNull(state.selectedDay)?.label ?: "--"
    }
    val hourStr = try {
        hour.time.format(DateTimeFormatter.ofPattern("HH"))
    } catch (_: Exception) { "--" }
    return "$dayLabel ALLE $hourStr"
}

// ---------------------------------------------------------------------------
// Helpers — dati per pagina
// ---------------------------------------------------------------------------
private fun TemperaturePage.bigNumberFor(
    forecast:   Forecast?,
    day:        DayForecast?,
    showingNow: Boolean,
    unit:       TempUnit,
): String {
    val current = forecast?.current
    return when (this) {
        TemperaturePage.TEMPERATURA -> {
            val temp = if (showingNow) current?.temperature
            else listOfNotNull(day?.tempMax, day?.tempMin)
                .takeIf { it.size == 2 }?.average()
            temp.asBigTemperature(unit)
        }
        TemperaturePage.SOLE -> {
            val sr = day?.sunrise; val ss = day?.sunset
            if (sr != null && ss != null) {
                try {
                    val h = java.time.Duration.between(sr, ss).toMinutes() / 60.0
                    h.roundToInt().toString()
                } catch (_: Exception) { "--" }
            } else "--"
        }
        TemperaturePage.PRECIPITAZIONI ->
            day?.precipitationSum?.roundToInt()?.toString() ?: "--"
        TemperaturePage.ARIA -> {
            val speed = if (showingNow) current?.windSpeed else day?.windMax
            speed?.roundToInt()?.toString() ?: "--"
        }
    }
}

private fun TemperaturePage.metricsFor(
    forecast:   Forecast?,
    day:        DayForecast?,
    showingNow: Boolean,
    unit:       TempUnit,
): List<MetricRow> {
    val current = forecast?.current
    return when (this) {
        TemperaturePage.TEMPERATURA -> {
            val representative = if (showingNow) current?.temperature
            else listOfNotNull(day?.tempMax, day?.tempMin)
                .takeIf { it.size == 2 }?.average()
            listOf(
                MetricRow("TEMPERATURA",      representative.asDegrees(unit), highlighted = true),
                MetricRow("MASSIMA",          day?.tempMax.asDegrees(unit)),
                MetricRow("MINIMA",           day?.tempMin.asDegrees(unit)),
                MetricRow("PERCEPITI",        (if (showingNow) current?.apparent else day?.apparentMax).asDegrees(unit)),
                MetricRow("UMIDITÀ",          (if (showingNow) current?.humidity else day?.humidityMean).asPercent()),
                MetricRow("PUNTO DI RUGIADA", (if (showingNow) current?.dewPoint else day?.dewPointMean).asDegrees(unit)),
            )
        }
        TemperaturePage.SOLE -> {
            val sr = day?.sunrise; val ss = day?.sunset
            val lightHours = if (sr != null && ss != null) {
                try {
                    val h = java.time.Duration.between(sr, ss).toMinutes() / 60.0
                    "${h.roundToInt()} H"
                } catch (_: Exception) { "--" }
            } else "--"
            val srStr = sr?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--"
            val ssStr = ss?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--"
            listOf(
                MetricRow("LUCE SOLARE", lightHours, highlighted = true),
                MetricRow("ALBA",        srStr),
                MetricRow("TRAMONTO",    ssStr),
                MetricRow("UV MASSIMO",  day?.uvMax?.let { String.format("%.1f", it) } ?: "--"),
            )
        }
        TemperaturePage.PRECIPITAZIONI -> listOf(
            MetricRow("RAPPORTO",       day?.precipitationSum?.let { String.format("%.1f MM/G", it) } ?: "--", highlighted = true),
            MetricRow("PROBABILITÀ",    day?.precipProbability.asPercent()),
            MetricRow("ORE DI PIOGGIA", day?.precipHours?.let { "${it.roundToInt()} H" } ?: "--"),
        )
        TemperaturePage.ARIA -> {
            val speed = if (showingNow) current?.windSpeed else day?.windMax
            val gusts = if (showingNow) current?.windGusts else day?.gustMax
            listOf(
                MetricRow("VELOCITÀ",  speed?.let { String.format("%.1f M/S", it) } ?: "--", highlighted = true),
                MetricRow("RAFFICHE",  gusts?.let { String.format("%.1f M/S", it) } ?: "--"),
                MetricRow("DIREZIONE", Wmo.windDirection(if (showingNow) current?.windDirection else day?.windDirection)),
                MetricRow("UV",        day?.uvMax?.let { String.format("%.1f", it) } ?: "--"),
            )
        }
    }
}

private fun TemperaturePage.hourSeriesFor(forecast: Forecast?, unit: TempUnit): List<Float?> {
    val hours = forecast?.hours ?: return emptyList()
    return when (this) {
        TemperaturePage.TEMPERATURA    -> hours.map { it.temperature?.let { v -> unit.from(v).toFloat() } }
        TemperaturePage.SOLE           -> hours.map { if (it.isDay) 1f else 0f }
        TemperaturePage.PRECIPITAZIONI -> hours.map { it.precipProbability?.toFloat() }
        TemperaturePage.ARIA           -> emptyList()
    }
}

private fun TemperaturePage.weekSeriesFor(forecast: Forecast?, unit: TempUnit): List<Float?> {
    val days = forecast?.days ?: return emptyList()
    return when (this) {
        TemperaturePage.TEMPERATURA    -> days.map { it.tempMax?.let { v -> unit.from(v).toFloat() } }
        TemperaturePage.SOLE           -> days.map { d ->
            val sr = d.sunrise; val ss = d.sunset
            if (sr != null && ss != null) {
                try { (java.time.Duration.between(sr, ss).toMinutes() / 60.0).toFloat() }
                catch (_: Exception) { null }
            } else null
        }
        TemperaturePage.PRECIPITAZIONI -> days.map { it.precipitationSum?.toFloat() }
        TemperaturePage.ARIA           -> days.map { it.windMax?.toFloat() }
    }
}

private fun TemperaturePage.accent(): Color = when (this) {
    TemperaturePage.TEMPERATURA    -> LineStrong
    TemperaturePage.SOLE           -> SunAccent
    TemperaturePage.PRECIPITAZIONI -> Color(0xFF3C8DF5)
    TemperaturePage.ARIA           -> AirAccent
}

// ---------------------------------------------------------------------------
// 3. Interactive3DNumber
//    Numero ruotabile con drag (pointerInput + detectTransformGestures).
//    - Pan orizzontale  → SceneRotation.yawDeg (gestito da rotatesScene sul parent)
//    - Pan verticale    → pitchDeg locale (clamp ±45°), passato a NumberMotion
//    - Ombra ellittica  → Canvas proiettato dinamicamente sotto il numero
//    - Rendering 3D     → ExtrudedText (renderer proprietario PrismRenderer)
// ---------------------------------------------------------------------------
@Composable
fun Interactive3DNumber(
    text:        String,
    fontSize:    androidx.compose.ui.unit.Dp,
    tilt:        State<Offset>,
    rotation:    com.forli.meteo.ui.motion.SceneRotation,
    modifier:    Modifier = Modifier,
    accentColor: Color    = LineStrong,
) {
    var localPitchDeg by remember { mutableFloatStateOf(0f) }

    Box(
        modifier         = modifier
            .pointerInput(Unit) {
                // detectTransformGestures cattura sia pan che pinch.
                // panZoomLock = true: ignora lo zoom, ci interessa solo il pan.
                detectTransformGestures(panZoomLock = true) { _, pan, _, _ ->
                    // Il pan verticale aggiorna il pitch (clamp ±45°).
                    // Il pan orizzontale è già intercettato da rotatesScene
                    // sul Box parent, quindi non lo duplichiamo qui.
                    localPitchDeg = (localPitchDeg - pan.y * 0.25f).coerceIn(-45f, 45f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // ── Ombra circolare proiettata dinamicamente ──────────────────────
        ProjectedShadow(
            yawDeg   = rotation.yawDeg,
            pitchDeg = localPitchDeg + tilt.value.y * 5f,
            color    = accentColor.copy(alpha = 0.20f),
            modifier = Modifier.fillMaxSize(),
        )

        // ── Numero 3D — renderer proprietario (PrismRenderer) ────────────
        // graphicsLayer con rotationX/Y applicato ANCHE qui per aggiungere
        // un secondo livello di inclinazione visiva (complementare al renderer).
        // cameraDistance = 16f * density garantisce la prospettiva corretta.
        ExtrudedText(
            text     = text,
            fontSize = fontSize,
            modifier = Modifier.fillMaxSize(),
            motion   = {
                NumberMotion(
                    yawDeg   = rotation.yawDeg + tilt.value.x * 7f,
                    pitchDeg = localPitchDeg + tilt.value.y * 5f,
                )
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Ombra ellittica proiettata — si sposta e si schiaccia con la rotazione
// ---------------------------------------------------------------------------
@Composable
private fun ProjectedShadow(
    yawDeg:   Float,
    pitchDeg: Float,
    color:    Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val cx       = size.width  / 2f
        val cy       = size.height / 2f

        val yawRad   = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()

        // L'ombra si allontana nella direzione opposta alla rotazione,
        // come farebbe l'ombra proiettata da una luce fissa a sinistra-alto.
        val offsetX  = kotlin.math.sin(yawRad)   * size.width  * 0.12f
        val offsetY  = kotlin.math.sin(pitchRad) * size.height * 0.06f

        // Schiacciamento: l'ellisse si restringe quando la rotazione è a 90°
        val halfPi   = (Math.PI / 2f).toFloat()
        val scaleX   = (1f - abs(yawRad)   / halfPi * 0.5f).coerceAtLeast(0.2f)
        val scaleY   = (1f - abs(pitchRad) / halfPi * 0.5f).coerceAtLeast(0.2f)

        val rX       = size.width  * 0.24f * scaleX
        val rY       = size.height * 0.055f * scaleY

        // L'ombra è posizionata leggermente sotto il centro del numero
        val shadowCy = cy + size.height * 0.34f + offsetY

        drawOval(
            color   = color,
            topLeft = Offset(cx + offsetX - rX, shadowCy - rY),
            size    = Size(rX * 2f, rY * 2f),
        )
    }
}

// ---------------------------------------------------------------------------
// 4. TemperatureMetricsTable
//    Tabella metriche ad alto contrasto con card scura e pillola sulla prima riga
// ---------------------------------------------------------------------------
/**
 * Tabella metriche:
 * - Sfondo [CardBackground] `#1A1A1A`, bordo `0.5.dp` [CardBorder] `#333333`, corner `16.dp`
 * - Prima riga: pillola [PillWhite] piena, etichetta e valore in [PillBlack]
 * - Righe 2+: etichetta [MetricLabel] 12sp Monospace, valore [MetricValue] 16sp Bold Monospace
 */
@Composable
internal fun TemperatureMetricsTable(
    rows:     List<MetricRow>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(0.5.dp, CardBorder, RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(vertical = 4.dp),
    ) {
        rows.forEachIndexed { index, row ->
            MetricsTableRow(row = row, isFirst = index == 0)

            // Divisore sottilissimo tra le righe (non dopo l'ultima)
            if (index < rows.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .padding(horizontal = 16.dp)
                        .background(CardBorder),
                )
            }
        }
    }
}

@Composable
private fun MetricsTableRow(row: MetricRow, isFirst: Boolean) {
    if (isFirst) {
        // ── Prima riga: pillola bianca piena, testo nero ───────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(PillWhite)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = row.label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color      = PillBlack,
                ),
            )
            Text(
                text  = row.value,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = PillBlack,
                ),
            )
        }
    } else {
        // ── Righe secondarie: label grigio + valore bianco Bold ────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = row.label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 12.sp,
                    color      = MetricLabel,
                ),
            )
            Text(
                text  = row.value,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MetricValue,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 5. TemperatureGraphSection
//    Card scura con grafico a linee Catmull-Rom + selettore GIORNO/SETTIMANA.
//    - drawText + rememberTextMeasurer per le etichette min/max sull'asse Y
//    - GraphView.entries — nessun deprecato .values()
//    - Area semi-trasparente sotto la linea
//    - Punti evidenziati su ogni campione
// ---------------------------------------------------------------------------
@Composable
fun TemperatureGraphSection(
    daySeries:  List<Float?>,
    weekSeries: List<Float?>,
    weekMode:   Boolean,
    onToggle:   (Boolean) -> Unit,
    accent:     Color    = LineStrong,
    modifier:   Modifier = Modifier,
) {
    // TextMeasurer per drawText nelle etichette asse Y
    val measurer     = rememberTextMeasurer()
    val activeView   = if (weekMode) GraphView.SETTIMANA else GraphView.GIORNO
    val activeSeries = if (weekMode) weekSeries else daySeries

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(0.5.dp, CardBorder, RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // ── Riga intestazione: label + toggle pillola ─────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = "ANDAMENTO",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 10.sp,
                    color      = MetricLabel,
                ),
            )
            GraphViewToggle(
                current  = activeView,
                onSelect = { view -> onToggle(view == GraphView.SETTIMANA) },
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Grafico ───────────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (activeSeries.isEmpty()) return@Canvas
            val values = activeSeries.filterNotNull()
            if (values.isEmpty()) return@Canvas

            var lo = values.min()
            var hi = values.max()
            if (hi - lo < 0.5f) { hi += 0.5f; lo -= 0.5f }

            // Margini interni — spazio a destra per le etichette numeriche
            val labelGutter = 32.dp.toPx()
            val padTop      = size.height * 0.10f
            val padBottom   = size.height * 0.18f
            val padLeft     = 4.dp.toPx()
            val padRight    = labelGutter
            val plotW       = size.width  - padLeft - padRight
            val plotH       = size.height - padTop  - padBottom

            // ── Punti della serie ────────────────────────────────────────
            val pts: List<Offset?> = activeSeries.mapIndexed { i, v ->
                val x = padLeft + plotW * i /
                        (activeSeries.size - 1).coerceAtLeast(1).toFloat()
                val t = v?.let { (it - lo) / (hi - lo) }
                t?.let { Offset(x, padTop + plotH * (1f - it)) }
            }

            val validPts = pts.filterNotNull()
            if (validPts.size < 2) return@Canvas

            // ── Area semi-trasparente ────────────────────────────────────
            drawPath(buildAreaPath(pts, padTop + plotH), accent.copy(alpha = 0.14f))

            // ── Linea Catmull-Rom ────────────────────────────────────────
            drawPath(
                path  = buildLinePath(pts),
                color = accent,
                style = Stroke(width = 2.dp.toPx()),
            )

            // ── Punti campione (cerchio pieno + centro vuoto) ────────────
            validPts.forEach { pt ->
                drawCircle(color = accent,           radius = 3.dp.toPx(), center = pt)
                drawCircle(color = CardBackground,   radius = 1.5.dp.toPx(), center = pt)
            }

            // ── Etichette asse Y: valore massimo e minimo ────────────────
            // rememberTextMeasurer() + drawText(textLayoutResult, ...) come
            // richiesto — nessun metodo fittizio di drawText.
            val labelStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize   = 9.sp,
                color      = MetricLabel,
            )
            val hiLayout = measurer.measure("${hi.roundToInt()}", labelStyle)
            val loLayout = measurer.measure("${lo.roundToInt()}", labelStyle)

            drawText(
                textLayoutResult = hiLayout,
                topLeft          = Offset(
                    x = size.width - hiLayout.size.width,
                    y = padTop - hiLayout.size.height / 2f,
                ),
            )
            drawText(
                textLayoutResult = loLayout,
                topLeft          = Offset(
                    x = size.width - loLayout.size.width,
                    y = padTop + plotH - loLayout.size.height / 2f,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// GraphViewToggle — pillola con selezione GIORNO / SETTIMANA
// ---------------------------------------------------------------------------
@Composable
private fun GraphViewToggle(
    current:  GraphView,
    onSelect: (GraphView) -> Unit,
) {
    Row(
        modifier          = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0xFF2A2A2A))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Usa GraphView.entries — nessun deprecato .values()
        GraphView.entries.forEach { view ->
            val isActive     = view == current
            val interaction  = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (isActive) PillWhite else Color.Transparent)
                    .clickable(
                        interactionSource = interaction,
                        indication        = null,
                        onClick           = { onSelect(view) },
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = view.name,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 9.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color      = if (isActive) PillBlack else MetricLabel,
                    ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers spline — Catmull-Rom
// ---------------------------------------------------------------------------

private fun buildLinePath(points: List<Offset?>): Path {
    val result = Path()
    segmentRuns(points).forEach { run -> result.addPath(catmullRomPath(run)) }
    return result
}

private fun buildAreaPath(points: List<Offset?>, baseline: Float): Path {
    val result = Path()
    segmentRuns(points).forEach { run ->
        result.addPath(
            Path().apply {
                addPath(catmullRomPath(run))
                lineTo(run.last().x, baseline)
                lineTo(run.first().x, baseline)
                close()
            }
        )
    }
    return result
}

/** Divide la serie nullable in sotto-sequenze continue di punti validi. */
private fun segmentRuns(points: List<Offset?>): List<List<Offset>> {
    val runs = mutableListOf<List<Offset>>()
    var run  = mutableListOf<Offset>()
    points.forEach { pt ->
        if (pt == null) {
            if (run.size > 1) runs += run.toList()
            run = mutableListOf()
        } else {
            run += pt
        }
    }
    if (run.size > 1) runs += run.toList()
    return runs
}

/**
 * Catmull-Rom → cubiche Bezier.
 * Le tangenti in ogni punto derivano dai due adiacenti: la curva passa per
 * tutti i punti senza oscillare. Identica alla versione in SplineChart.kt.
 */
private fun catmullRomPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
    return path
}
