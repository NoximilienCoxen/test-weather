package com.forli.meteo.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.DayForecast
import com.forli.meteo.data.Forecast
import com.forli.meteo.data.Wmo
import com.forli.meteo.ui.components.ChartSeries
import com.forli.meteo.ui.components.DataTable
import com.forli.meteo.ui.components.DayStrip
import com.forli.meteo.ui.components.DayWeekToggle
import com.forli.meteo.ui.components.RainOverlay
import com.forli.meteo.ui.components.ScrubBar
import com.forli.meteo.ui.components.SplineChart
import com.forli.meteo.ui.components.TableRow
import com.forli.meteo.prefs.TempUnit
import com.forli.meteo.ui.home.nearestHourIndex
import com.forli.meteo.ui.motion.PhysicalNumber
import com.forli.meteo.ui.motion.SceneRotation
import com.forli.meteo.ui.motion.rememberSceneRotation
import com.forli.meteo.ui.render.NumberMotion
import com.forli.meteo.ui.render.ExtrudedText
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType
import kotlin.math.roundToInt

private enum class MetricPage(val title: String) {
    TEMPERATURA("Temp."),
    PRECIPITAZIONI("Precip."),
    VENTO("Vento"),
}

/** La schermata di dettaglio: si raggiunge trascinando in alto la principale. */
@Composable
internal fun DetailScreen(state: UiState, viewModel: WeatherViewModel, tilt: State<Offset>) {
    val colors = LocalMeteoColors.current
    // La temperatura e' la prima pagina, non per convenzione dell'enum ma
    // perche' e' quella su cui si apre toccando la cifra in home: il foglio
    // deve alzarsi gia' li', non su qualunque scheda fosse rimasta aperta
    // l'ultima volta.
    val pagerState = rememberPagerState(
        initialPage = MetricPage.TEMPERATURA.ordinal,
        pageCount = { MetricPage.entries.size },
    )
    // Qui gli oggetti non si girano col dito: il gesto orizzontale appartiene
    // gia' al pager, e contendergli le pagine sarebbe peggio che rinunciare
    // alla rotazione. Resta il respiro dell'inclinazione.
    val rotation = rememberSceneRotation()

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
                state = pagerState,
                modifier = Modifier.weight(1f),
                pageSpacing = 20.dp,
            ) { index ->
                when (val page = MetricPage.entries[index]) {
                    MetricPage.TEMPERATURA -> TemperaturePageContent(
                        state = state,
                        viewModel = viewModel,
                        rotation = rotation,
                        tilt = tilt,
                    )
                    else -> MetricPageContent(
                        page = page,
                        state = state,
                        viewModel = viewModel,
                        rotation = rotation,
                        tilt = tilt,
                    )
                }
            }
        }
    }
}

/**
 * La scheda temperatura, per conto suo.
 *
 * Non c'e' piu' il titolo estruso "Temp.": la scheda si riconosce dal
 * contenuto, e sopra la cifra un secondo oggetto tridimensionale non
 * aggiungeva informazione, solo peso. Non c'e' nemmeno la barra di scrub:
 * seleziona lo stesso giorno di `DayStrip` appena sotto, ed avere due comandi
 * per la stessa scelta e' una domanda su quale dei due sia quello vero.
 *
 * Al posto della curva sui sette giorni, qui la curva racconta le ore: e' il
 * dato che manca altrove, e in cambio la cifra rinuncia a un po' della sua
 * altezza per lasciarle spazio.
 */
@Composable
private fun TemperaturePageContent(
    state: UiState,
    viewModel: WeatherViewModel,
    rotation: SceneRotation,
    tilt: State<Offset>,
) {
    val colors = LocalMeteoColors.current
    val forecast = state.forecast
    val day = forecast?.days?.getOrNull(state.selectedDay)
    val showingNow = !state.weekMode && state.selectedDay == 0
    val header = if (showingNow) "ORA" else day?.label ?: "--"
    val labels = forecast?.days?.map { it.label } ?: List(7) { "--" }
    val hours = forecast?.hours.orEmpty()
    val unit = state.unit
    val current = forecast?.current

    val representative = if (showingNow) current?.temperature
    else listOfNotNull(day?.tempMax, day?.tempMin).takeIf { it.size == 2 }?.average()

    // Il punto di rugiada di "adesso" viene dall'ora piu' vicina della serie
    // oraria, non dalla sola lettura istantanea: e' lo stesso dato che
    // popola il grafico, quindi i due non possono raccontare cose diverse.
    val dewPoint = if (showingNow) {
        forecast?.let { hours.getOrNull(nearestHourIndex(hours, it.nowThere()))?.dewPoint }
            ?: current?.dewPoint
    } else {
        day?.dewPointMean
    }
    val excursion = listOfNotNull(day?.tempMax, day?.tempMin)
        .takeIf { it.size == 2 }
        ?.let { (max, min) -> max - min }

    // Sul tema chiaro la faccia della cifra e' quasi bianca: come colore di
    // tracciato sparirebbe sul fondo. Le curve seguono il colore del testo.
    val strong = colors.text
    val faint = colors.label
    val reference = colors.text.copy(alpha = 0.30f)

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = header,
            style = MeteoType.caption,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.48f),
            contentAlignment = Alignment.Center,
        ) {
            PhysicalNumber(
                text = representative.asBigTemperature(unit),
                // Piu' piccola di quella delle altre schede: qui sotto c'e'
                // anche il grafico orario, e una cifra alta quanto la meta'
                // schermo lo spingerebbe fuori vista.
                fontSize = maxHeight * 0.58f,
                rotation = rotation,
                tilt = tilt,
                modifier = Modifier.fillMaxSize(),
            )
        }

        state.error?.let { message ->
            Text(
                text = message.uppercase(),
                style = MeteoType.caption,
                color = colors.label,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            )
        }

        SplineChart(
            series = listOf(
                ChartSeries(hours.map { it.temperature }, strong, strokeWidthDp = 2.4f),
                ChartSeries(hours.map { it.apparent }, faint, strokeWidthDp = 1.6f),
                ChartSeries(List(hours.size) { day?.tempMax }, reference, strokeWidthDp = 1.2f),
                ChartSeries(List(hours.size) { day?.tempMin }, reference, strokeWidthDp = 1.2f),
            ),
            tickSuffix = "°",
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        )

        TemperatureLegend(
            strong = strong,
            faint = faint,
            reference = reference,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )

        Spacer(Modifier.height(12.dp))

        DataTable(
            rows = listOf(
                TableRow("PUNTO DI RUGIADA", dewPoint.asDegrees(unit)),
                TableRow("ESCURSIONE TERMICA", excursion.asDegreeSpan(unit)),
            ),
        )

        Spacer(Modifier.height(14.dp))

        DayStrip(
            labels = labels,
            selected = state.selectedDay,
            onSelect = viewModel::selectDay,
        )

        DayWeekToggle(
            weekMode = state.weekMode,
            onChange = viewModel::setWeekMode,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
    }
}

/** Tre pallini colorati: dicono quale curva e' quale senza serve una tabella. */
@Composable
private fun TemperatureLegend(
    strong: Color,
    faint: Color,
    reference: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        LegendEntry(label = "ORARIA", color = strong)
        LegendEntry(label = "PERCEPITA", color = faint)
        LegendEntry(label = "MIN / MAX", color = reference)
    }
}

@Composable
private fun LegendEntry(label: String, color: Color) {
    val colors = LocalMeteoColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MeteoType.caption, color = colors.label)
    }
}

@Composable
private fun MetricPageContent(
    page: MetricPage,
    state: UiState,
    viewModel: WeatherViewModel,
    rotation: SceneRotation,
    tilt: State<Offset>,
) {
    val colors = LocalMeteoColors.current
    val forecast = state.forecast
    val day = forecast?.days?.getOrNull(state.selectedDay)
    // "ORA" ha senso solo per oggi in modalita' giorno: negli altri casi il
    // dato mostrato e' una previsione, non una misura.
    val showingNow = !state.weekMode && state.selectedDay == 0
    val header = if (showingNow) "ORA" else day?.label ?: "--"
    val data = page.buildData(forecast, day, showingNow, state.unit)
    val labels = forecast?.days?.map { it.label } ?: List(7) { "--" }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = header,
            style = MeteoType.caption,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        ExtrudedText(
            text = page.title,
            fontSize = 42.dp,
            // Estrusione piu' corta della cifra gigante: alla dimensione del
            // titolo una profondita' piena impasta le lettere.
            depth = 7.dp,
            // Il titolo ruota molto meno della cifra: sta su un piano piu'
            // lontano, e la differenza fra i due si legge come profondita'.
            motion = { NumberMotion(yawDeg = tilt.value.x * 5f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            PhysicalNumber(
                text = data.bigNumber,
                fontSize = maxHeight * 0.66f,
                rotation = rotation,
                tilt = tilt,
                modifier = Modifier.fillMaxSize(),
            )
            if (page == MetricPage.PRECIPITAZIONI) {
                RainOverlay(
                    probability = day?.precipProbability ?: 0,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        state.error?.let { message ->
            Text(
                text = message.uppercase(),
                style = MeteoType.caption,
                color = colors.label,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            )
        }

        DataTable(rows = data.rows)

        Spacer(Modifier.height(14.dp))

        DayStrip(
            labels = labels,
            selected = state.selectedDay,
            onSelect = viewModel::selectDay,
        )

        SplineChart(
            series = data.series,
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp),
        )

        ScrubBar(
            count = labels.size,
            selected = state.selectedDay,
            onSelect = viewModel::selectDay,
            modifier = Modifier.padding(top = 2.dp),
        )

        DayWeekToggle(
            weekMode = state.weekMode,
            onChange = viewModel::setWeekMode,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
    }
}

private data class PageData(
    val rows: List<TableRow>,
    val bigNumber: String,
    val series: List<ChartSeries>,
)

@Composable
private fun MetricPage.buildData(
    forecast: Forecast?,
    day: DayForecast?,
    showingNow: Boolean,
    unit: TempUnit,
): PageData {
    val colors = LocalMeteoColors.current
    val current = forecast?.current
    val days = forecast?.days.orEmpty()
    // Sul tema chiaro la faccia della cifra e' quasi bianca: come colore di
    // tracciato sparirebbe sul fondo. Le curve seguono il colore del testo.
    val strong = colors.text
    val faint = colors.label

    return when (this) {
        // La temperatura ha un suo layout dedicato (TemperaturePageContent):
        // qui non arriva mai, perche' il pager la intercetta prima.
        MetricPage.TEMPERATURA -> error("La scheda temperatura non passa da qui")

        MetricPage.PRECIPITAZIONI -> PageData(
            rows = listOf(
                TableRow("RAPPORTO", day?.precipitationSum.asMillimetresPerDay()),
                TableRow("PROBABILITÀ", day?.precipProbability.asPercent()),
                TableRow("TIPOLOGIA", Wmo.precipKind(day?.weatherCode).label),
                TableRow("ORE DI PIOGGIA", day?.precipHours.asHours()),
            ),
            bigNumber = day?.precipitationSum?.roundToInt()?.toString() ?: "--",
            series = listOf(
                ChartSeries(
                    values = days.map { it.precipitationSum },
                    color = RainBlue,
                    filled = true,
                    strokeWidthDp = 1.8f,
                ),
            ),
        )

        MetricPage.VENTO -> {
            val speed = if (showingNow) current?.windSpeed else day?.windMax
            PageData(
                rows = listOf(
                    TableRow("VELOCITÀ", speed.asMetresPerSecond()),
                    TableRow(
                        "MASSIMA",
                        (if (showingNow) current?.windGusts else day?.gustMax).asMetresPerSecond(),
                    ),
                    TableRow(
                        "DIREZIONE",
                        Wmo.windDirection(
                            if (showingNow) current?.windDirection else day?.windDirection,
                        ),
                    ),
                    TableRow("UV", day?.uvMax.asIndex()),
                ),
                bigNumber = speed.asBigNumber(),
                series = listOf(
                    ChartSeries(days.map { it.windMax }, strong, strokeWidthDp = 2.4f),
                    ChartSeries(days.map { it.gustMax }, faint, strokeWidthDp = 1.6f),
                ),
            )
        }
    }
}

private val RainBlue = Color(0xFF2C7BF2)
