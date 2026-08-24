package com.forli.meteo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.DayForecast
import com.forli.meteo.data.Forecast
import com.forli.meteo.data.Wmo
import com.forli.meteo.prefs.ThemeMode
import com.forli.meteo.ui.components.ChartSeries
import com.forli.meteo.ui.components.DataTable
import com.forli.meteo.ui.components.DayStrip
import com.forli.meteo.ui.components.DayWeekToggle
import com.forli.meteo.ui.components.RainOverlay
import com.forli.meteo.ui.components.ScrubBar
import com.forli.meteo.ui.components.SplineChart
import com.forli.meteo.ui.components.TableRow
import com.forli.meteo.ui.components.ThemeSwitch
import com.forli.meteo.ui.render.ExtrudedText
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoTheme
import com.forli.meteo.ui.theme.MeteoType
import kotlin.math.roundToInt

private enum class MetricPage(val title: String) {
    TEMPERATURA("Temp."),
    PRECIPITAZIONI("Precip."),
    VENTO("Vento"),
}

@Composable
fun WeatherApp(viewModel: WeatherViewModel) {
    val state by viewModel.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.themeMode) {
        ThemeMode.AUTO -> systemDark
        ThemeMode.CHIARO -> false
        ThemeMode.SCURO -> true
    }
    MeteoTheme(dark = dark) {
        WeatherScreen(state = state, viewModel = viewModel)
    }
}

@Composable
private fun WeatherScreen(state: UiState, viewModel: WeatherViewModel) {
    val colors = LocalMeteoColors.current
    val pagerState = rememberPagerState(pageCount = { MetricPage.entries.size })

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                ThemeSwitch(mode = state.themeMode, onChange = viewModel::setThemeMode)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                pageSpacing = 20.dp,
            ) { index ->
                MetricPageContent(
                    page = MetricPage.entries[index],
                    state = state,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun MetricPageContent(
    page: MetricPage,
    state: UiState,
    viewModel: WeatherViewModel,
) {
    val colors = LocalMeteoColors.current
    val forecast = state.forecast
    val day = forecast?.days?.getOrNull(state.selectedDay)
    // "ORA" ha senso solo per oggi in modalita' giorno: negli altri casi il
    // dato mostrato e' una previsione, non una misura.
    val showingNow = !state.weekMode && state.selectedDay == 0
    val header = if (showingNow) "ORA" else day?.label ?: "--"
    val data = page.buildData(forecast, day, showingNow)
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
            fontSize = 44.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            ExtrudedText(
                text = data.bigNumber,
                fontSize = maxHeight * 0.64f,
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
): PageData {
    val colors = LocalMeteoColors.current
    val current = forecast?.current
    val days = forecast?.days.orEmpty()
    val strong = colors.numberFace
    val faint = colors.label

    return when (this) {
        MetricPage.TEMPERATURA -> {
            // Per un giorno futuro non esiste una "temperatura" istantanea:
            // uso il punto medio fra massima e minima, che resta distinto da
            // entrambe le righe sottostanti.
            val representative = if (showingNow) current?.temperature
            else listOfNotNull(day?.tempMax, day?.tempMin)
                .takeIf { it.size == 2 }?.average()
            PageData(
                rows = listOf(
                    TableRow("TEMPERATURA", representative.asDegrees()),
                    TableRow("MASSIMA", day?.tempMax.asDegrees()),
                    TableRow("MINIMA", day?.tempMin.asDegrees()),
                    TableRow(
                        "PERCEPITI",
                        (if (showingNow) current?.apparent else day?.apparentMax).asDegrees(),
                    ),
                    TableRow(
                        "UMIDITÀ",
                        (if (showingNow) current?.humidity else day?.humidityMean).asPercent(),
                    ),
                    TableRow(
                        "PUNTO DI RUGIADA",
                        (if (showingNow) current?.dewPoint else day?.dewPointMean).asDegrees(),
                    ),
                ),
                bigNumber = representative.asBigNumber(),
                series = listOf(
                    ChartSeries(days.map { it.tempMax }, strong, strokeWidthDp = 2.4f),
                    ChartSeries(days.map { it.tempMin }, faint, strokeWidthDp = 1.6f),
                ),
            )
        }

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
