package com.forli.meteo.ui.temperature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forli.meteo.data.DayForecast
import com.forli.meteo.data.Forecast
import com.forli.meteo.data.HourForecast
import com.forli.meteo.data.Wmo
import com.forli.meteo.prefs.TempUnit
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.WeatherViewModel
import com.forli.meteo.ui.asBigDegrees
import com.forli.meteo.ui.asBigNumber
import com.forli.meteo.ui.asDegrees
import com.forli.meteo.ui.asPercent
import com.forli.meteo.ui.motion.PhysicalNumber
import com.forli.meteo.ui.motion.rememberSceneRotation
import com.forli.meteo.ui.motion.rotatesScene
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/** Dati riga per la tabella metriche. */
internal data class MetricRow(
    val label: String,
    val value: String,
    val highlighted: Boolean = false,
)

/**
 * La schermata di dettaglio: sale trascinando in alto la principale, oppure
 * toccando la cifra della temperatura.
 *
 * La cifra e' in cima e rimane fissa: il gesto orizzontale sulla cifra gira
 * la scena 3D esattamente come nella schermata principale.
 * Il contenuto sotto (metriche + grafico) e' un HorizontalPager: swipe
 * laterale su questa area cambia modalita' (Temp / Sole / Pioggia / Vento)
 * senza interferire con la rotazione della cifra.
 */
@Composable
fun TemperatureDetailScreen(
    state: UiState,
    viewModel: WeatherViewModel,
    tilt: State<Offset>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = DetailMode.entries
    val mode = state.detailMode
    val forecast = state.forecast
    val day = forecast?.days?.getOrNull(state.selectedDay)
    val hour = state.hour
    val accent = mode.accent()

    val rotation = rememberSceneRotation()

    // Sincronizzazione bidirezionale pager <-> viewModel.detailMode
    val pagerState = rememberPagerState(
        initialPage = modes.indexOf(mode).coerceAtLeast(0),
        pageCount = { modes.size },
    )

    // Pager -> ViewModel: quando l'utente swipa, aggiorna la modalita'
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val newMode = modes.getOrNull(page) ?: return@collect
            if (newMode != state.detailMode) viewModel.setDetailMode(newMode)
        }
    }

    // ViewModel -> Pager: quando si clicca un chip, anima il pager
    LaunchedEffect(mode) {
        val page = modes.indexOf(mode)
        if (page >= 0 && page != pagerState.currentPage) {
            pagerState.animateScrollToPage(page)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1D2026)),
    ) {
        // Barra in cima con titolo della modalita' corrente
        DetailTopBar(title = mode.title, onBack = onBack)

        // Chip di selezione modalita' (click + indicatori della pagina corrente)
        ModeChips(
            modes = modes,
            selected = mode,
            onSelect = viewModel::setDetailMode,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        // Intestazione ora/giorno
        Text(
            text = buildHeaderText(state),
            style = MeteoType.caption,
            color = MetricLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        // ── La cifra, girabile col dito ──────────────────────────────────────
        // Vive FUORI dal Pager: il gesto orizzontale sulla cifra e' la
        // rotazione 3D; il gesto orizzontale nel Pager sotto cambia pagina.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .rotatesScene(rotation),
            contentAlignment = Alignment.Center,
        ) {
            ProjectedShadow(
                yawDeg = rotation.yawDeg,
                pitchDeg = tilt.value.y * 5f,
                color = accent.copy(alpha = 0.20f),
                modifier = Modifier.fillMaxSize(),
            )
            PhysicalNumber(
                text = mode.bigNumber(forecast, day, hour, state.unit),
                smallTail = if (mode == DetailMode.TEMPERATURA) 1 else 0,
                fontSize = maxHeight * 0.74f,
                rotation = rotation,
                tilt = tilt,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── Pager: swipe laterale cambia modalita' ───────────────────────────
        // Ogni pagina ha il suo scroll verticale indipendente: cosi' si puo'
        // scendere a leggere le metriche di ogni modalita' senza perdere
        // la posizione delle altre.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val pageMode = modes.getOrNull(page) ?: return@HorizontalPager
            val pageAccent = pageMode.accent()
            val pageRows = pageMode.metrics(forecast, day, hour, state.unit)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Tabella metriche
                if (pageRows.isNotEmpty()) {
                    MetricsTable(
                        rows = pageRows,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }

                // La settimana solo in modalita' TEMPERATURA: nelle altre
                // e' ridondante (mostra sempre temp max/min, non sole o vento)
                if (pageMode == DetailMode.TEMPERATURA) {
                    DailyForecastCard(
                        days = forecast?.days.orEmpty(),
                        unit = state.unit,
                        onSelectDay = viewModel::openDayDetail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    )
                }

                // Grafico andamento interattivo
                GraphSection(
                    daySeries = pageMode.hourSeries(forecast, state.unit),
                    weekSeries = pageMode.weekSeries(forecast, state.unit),
                    dayLabels = forecast?.hours?.map {
                        "%02d".format(it.time.hour)
                    }.orEmpty(),
                    weekLabels = forecast?.days?.map { it.label }.orEmpty(),
                    weekMode = state.weekMode,
                    onToggle = viewModel::setWeekMode,
                    accent = pageAccent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(200.dp),
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Intestazione oraria
// ---------------------------------------------------------------------------
private fun buildHeaderText(state: UiState): String {
    val hour = state.hour ?: return "OGGI"
    val dayLabel = when (state.selectedDay) {
        0 -> "OGGI"
        1 -> "DOMANI"
        else -> state.forecast?.days?.getOrNull(state.selectedDay)?.label ?: "--"
    }
    val hourStr = try {
        hour.time.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        "--"
    }
    return "$dayLabel  \u00B7  $hourStr"
}

// ---------------------------------------------------------------------------
// Cifra grande per ogni modalita'
// ---------------------------------------------------------------------------
private fun DetailMode.bigNumber(
    forecast: Forecast?,
    day: DayForecast?,
    hour: HourForecast?,
    unit: TempUnit,
): String = when (this) {
    DetailMode.TEMPERATURA -> hour?.temperature.asBigDegrees(unit)
    DetailMode.SOLE        -> lightHours(day)?.roundToInt()?.toString() ?: "--"
    DetailMode.PRECIPITAZIONI -> day?.precipitationSum.asBigNumber()
    DetailMode.ARIA        -> (forecast?.current?.windSpeed ?: day?.windMax).asBigNumber()
}

private fun DetailMode.metrics(
    forecast: Forecast?,
    day: DayForecast?,
    hour: HourForecast?,
    unit: TempUnit,
): List<MetricRow> {
    val current = forecast?.current
    return when (this) {
        DetailMode.TEMPERATURA -> listOf(
            MetricRow("TEMPERATURA", hour?.temperature.asDegrees(unit), highlighted = true),
            MetricRow("MASSIMA", day?.tempMax.asDegrees(unit)),
            MetricRow("MINIMA", day?.tempMin.asDegrees(unit)),
            MetricRow("PERCEPITI", (hour?.apparent ?: day?.apparentMax).asDegrees(unit)),
            MetricRow("UMIDITA'", (current?.humidity ?: day?.humidityMean).asPercent()),
            MetricRow("PUNTO DI RUGIADA", (current?.dewPoint ?: day?.dewPointMean).asDegrees(unit)),
        )
        DetailMode.SOLE -> {
            val clock = DateTimeFormatter.ofPattern("HH:mm")
            listOf(
                MetricRow(
                    "LUCE SOLARE",
                    lightHours(day)?.let { "${it.roundToInt()} H" } ?: "--",
                    highlighted = true,
                ),
                MetricRow("ALBA", day?.sunrise?.format(clock) ?: "--"),
                MetricRow("TRAMONTO", day?.sunset?.format(clock) ?: "--"),
                MetricRow("UV MASSIMO", day?.uvMax?.let { String.format("%.1f", it) } ?: "--"),
            )
        }
        DetailMode.PRECIPITAZIONI -> listOf(
            MetricRow(
                "TOTALE GIORNO",
                day?.precipitationSum?.let { String.format("%.1f mm", it) } ?: "--",
                highlighted = true,
            ),
            MetricRow("PROBABILITA'", (hour?.precipProbability ?: day?.precipProbability).asPercent()),
            MetricRow("ORE DI PIOGGIA", day?.precipHours?.let { "${it.roundToInt()} H" } ?: "--"),
        )
        DetailMode.ARIA -> {
            val speed = current?.windSpeed ?: day?.windMax
            val gusts = current?.windGusts ?: day?.gustMax
            listOf(
                MetricRow(
                    "VELOCITA'",
                    speed?.let { String.format("%.1f m/s", it) } ?: "--",
                    highlighted = true,
                ),
                MetricRow("RAFFICHE", gusts?.let { String.format("%.1f m/s", it) } ?: "--"),
                MetricRow("DIREZIONE", Wmo.windDirection(current?.windDirection ?: day?.windDirection)),
                MetricRow("UV MAX", day?.uvMax?.let { String.format("%.1f", it) } ?: "--"),
            )
        }
    }
}

private fun lightHours(day: DayForecast?): Double? {
    val sunrise = day?.sunrise ?: return null
    val sunset = day.sunset ?: return null
    return runCatching {
        java.time.Duration.between(sunrise, sunset).toMinutes() / 60.0
    }.getOrNull()
}

private fun DetailMode.hourSeries(forecast: Forecast?, unit: TempUnit): List<Float?> {
    val hours = forecast?.hours ?: return emptyList()
    return when (this) {
        DetailMode.TEMPERATURA    -> hours.map { it.temperature?.let { v -> unit.from(v).toFloat() } }
        DetailMode.SOLE           -> hours.map { if (it.isDay) 1f else 0f }
        DetailMode.PRECIPITAZIONI -> hours.map { it.precipProbability?.toFloat() }
        DetailMode.ARIA           -> emptyList()
    }
}

private fun DetailMode.weekSeries(forecast: Forecast?, unit: TempUnit): List<Float?> {
    val days = forecast?.days ?: return emptyList()
    return when (this) {
        // tempMax ?: tempMin come fallback: se un modello non restituisce
        // la massima (es. ICON-2I oltre i 3 giorni), usa la minima piuttosto
        // che lasciare un null che spezza la curva.
        DetailMode.TEMPERATURA    -> days.map { (it.tempMax ?: it.tempMin)?.let { v -> unit.from(v).toFloat() } }
        DetailMode.SOLE           -> days.map { lightHours(it)?.toFloat() }
        DetailMode.PRECIPITAZIONI -> days.map { it.precipitationSum?.toFloat() }
        DetailMode.ARIA           -> days.map { it.windMax?.toFloat() }
    }
}

// ---------------------------------------------------------------------------
// Ombra ellittica proiettata
// ---------------------------------------------------------------------------
@Composable
private fun ProjectedShadow(
    yawDeg: Float,
    pitchDeg: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val yawRad   = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val offsetX = kotlin.math.sin(yawRad) * size.width * 0.12f
        val offsetY = kotlin.math.sin(pitchRad) * size.height * 0.06f
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

// ---------------------------------------------------------------------------
// Tabella metriche
// ---------------------------------------------------------------------------
@Composable
internal fun MetricsTable(
    rows: List<MetricRow>,
    modifier: Modifier = Modifier,
) {
    DetailCard(modifier = modifier) {
        Spacer(Modifier.height(4.dp))
        rows.forEachIndexed { index, row ->
            MetricsRow(row = row)
            if (index < rows.lastIndex) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .height(0.5.dp)
                        .background(CardBorder),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun MetricsRow(row: MetricRow) {
    if (row.highlighted) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(PillWhite)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = row.label, style = MeteoType.caption, color = PillBlack)
            Text(text = row.value, style = MeteoType.tabular, color = PillBlack)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = row.label, style = MeteoType.caption, color = MetricLabel)
            Text(text = row.value, style = MeteoType.tabular, color = MetricValue)
        }
    }
}

// ---------------------------------------------------------------------------
// Grafico andamento interattivo con crosshair
// ---------------------------------------------------------------------------
@Composable
private fun GraphSection(
    daySeries: List<Float?>,
    weekSeries: List<Float?>,
    dayLabels: List<String>,
    weekLabels: List<String>,
    weekMode: Boolean,
    onToggle: (Boolean) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val series = if (weekMode) weekSeries else daySeries
    val labels = if (weekMode) weekLabels else dayLabels

    // Posizione del tocco come frazione [0,1] della larghezza del plot.
    // -1f = nessun tocco attivo.
    var touchFraction by remember { mutableFloatStateOf(-1f) }

    DetailCard(modifier = modifier) {
        // Intestazione con toggle GIORNO / SETTIMANA
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "ANDAMENTO", style = MeteoType.caption, color = MetricLabel)
            Row {
                listOf(false to "GIORNO", true to "SETTIMANA").forEach { (week, label) ->
                    val active = week == weekMode
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (active) PillWhite else PillTrack)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = { onToggle(week) },
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = label,
                            style = MeteoType.caption,
                            color = if (active) PillBlack else MetricLabel,
                        )
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
                .pointerInput(series) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            touchFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            touchFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = { touchFraction = -1f },
                        onDragCancel = { touchFraction = -1f },
                    )
                },
        ) {
            val values = series.filterNotNull()
            if (values.size < 2) return@Canvas

            var lo = values.min()
            var hi = values.max()
            // Range minimo di 3 unita' per evitare che la curva collassi
            if (hi - lo < 3f) {
                val mid = (hi + lo) / 2f
                hi = mid + 1.5f
                lo = mid - 1.5f
            }

            val gutterRight = 32.dp.toPx()
            val gutterBottom = 18.dp.toPx()
            val padTop = 14.dp.toPx()
            val plotW = size.width - gutterRight
            val plotH = size.height - padTop - gutterBottom
            if (plotW <= 0f || plotH <= 0f) return@Canvas

            fun xOf(i: Int): Float =
                plotW * i / (series.size - 1).coerceAtLeast(1).toFloat()
            fun yOf(v: Float): Float =
                padTop + plotH * (1f - (v - lo) / (hi - lo))

            val pts: List<Offset?> = series.mapIndexed { i, v ->
                v?.let { Offset(xOf(i), yOf(it)) }
            }
            if (pts.filterNotNull().size < 2) return@Canvas

            // Area riempita
            drawPath(buildAreaPath(pts, padTop + plotH), accent.copy(alpha = 0.15f))
            // Linea principale
            drawPath(
                path = buildLinePath(pts),
                color = accent,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            // Etichette asse Y (hi e lo)
            val labelStyle = TextStyle(fontSize = 9.sp, color = MetricLabel)
            listOf(hi to padTop, lo to padTop + plotH).forEach { (value, y) ->
                val layout = measurer.measure("${value.roundToInt()}", labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = size.width - layout.size.width,
                        y = y - layout.size.height / 2f,
                    ),
                )
            }

            // Etichette asse X: mostra al massimo 4 etichette equidistanti
            val xLabelStep = when {
                labels.size <= 4  -> 1
                labels.size <= 8  -> 2
                labels.size <= 14 -> 3
                else              -> labels.size / 4
            }
            labels.forEachIndexed { i, lbl ->
                if (i % xLabelStep != 0) return@forEachIndexed
                val layout = measurer.measure(lbl, labelStyle)
                val x = xOf(i) - layout.size.width / 2f
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = x.coerceIn(0f, plotW - layout.size.width),
                        y = size.height - layout.size.height,
                    ),
                )
            }

            // ── Crosshair interattivo ────────────────────────────────────────
            if (touchFraction >= 0f) {
                val rawIdx = touchFraction * (series.size - 1)
                val idx = rawIdx.roundToInt().coerceIn(0, series.lastIndex)
                val value = series[idx] ?: return@Canvas
                val cx = xOf(idx)
                val cy = yOf(value)

                // Linea verticale tratteggiata
                drawLine(
                    color = MetricLabel,
                    start = Offset(cx, padTop),
                    end = Offset(cx, padTop + plotH),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                    ),
                )
                // Cerchio sul punto
                drawCircle(color = accent, radius = 4.dp.toPx(), center = Offset(cx, cy))
                drawCircle(
                    color = Color(0xFF1D2026),
                    radius = 2.5.dp.toPx(),
                    center = Offset(cx, cy),
                )

                // Etichetta valore
                val valLabel = measurer.measure(
                    "${value.roundToInt()}",
                    TextStyle(fontSize = 10.sp, color = MetricValue),
                )
                val lx = (cx - valLabel.size.width / 2f)
                    .coerceIn(0f, plotW - valLabel.size.width)
                val ly = (cy - valLabel.size.height - 5.dp.toPx())
                    .coerceAtLeast(0f)
                drawText(valLabel, topLeft = Offset(lx, ly))
            }
        }
    }
}
