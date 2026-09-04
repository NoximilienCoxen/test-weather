package io.github.noximiliencoxen.caelum.ui.temperature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.WeatherViewModel
import io.github.noximiliencoxen.caelum.ui.asHoursMinutes
import io.github.noximiliencoxen.caelum.ui.asIndex
import io.github.noximiliencoxen.caelum.ui.asMetresPerSecond
import io.github.noximiliencoxen.caelum.ui.asMillimetres
import io.github.noximiliencoxen.caelum.ui.asPercent
import io.github.noximiliencoxen.caelum.ui.asPlainDegrees
import io.github.noximiliencoxen.caelum.ui.common.MeteoCard
import io.github.noximiliencoxen.caelum.ui.common.MeteoEmptyState
import io.github.noximiliencoxen.caelum.ui.common.MeteoLayout
import io.github.noximiliencoxen.caelum.ui.common.MeteoSplitPills
import io.github.noximiliencoxen.caelum.ui.common.MeteoStatData
import io.github.noximiliencoxen.caelum.ui.common.MeteoStatGrid
import io.github.noximiliencoxen.caelum.ui.common.MeteoTopBar
import io.github.noximiliencoxen.caelum.ui.common.centerOn
import kotlinx.coroutines.launch
import io.github.noximiliencoxen.caelum.ui.common.rememberMeteoLayout
import io.github.noximiliencoxen.caelum.ui.secondsAsHoursMinutes
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoAccents
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val FULL_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val MONTH_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)

/**
 * Il dossier di un giorno, raggiunto toccandolo nella settimana.
 *
 * Prima qui c'erano due temperature e una probabilita' di pioggia, e basta:
 * niente alba, niente tramonto, niente UV, niente vento, niente millimetri -
 * tutti dati che l'app aveva gia' in mano. Un dettaglio che dice meno della
 * schermata da cui si arriva non e' un dettaglio.
 *
 * Si esce con la freccia in alto a sinistra o col tasto indietro di sistema, e
 * si passa da un giorno all'altro **scorrendo**, non solo toccando le
 * linguette: la striscia dei giorni ne mostra tre per volta, e per raggiungere
 * sabato bisognava prima trovarlo.
 */
@Composable
fun DayDetailScreen(
    state: UiState,
    viewModel: WeatherViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = rememberMeteoLayout()
    val scope = rememberCoroutineScope()
    val days = state.forecast?.days.orEmpty()
    val index = state.selectedDay.coerceIn(0, maxOf(days.lastIndex, 0))

    // Il carosello si dichiara **prima della barra**, che adesso lo legge: il
    // titolo deve dire il giorno che si sta guardando, non quello posato. Con
    // la scrittura nello stato spostata a scorrimento finito, leggere
    // `state.selectedDay` qui avrebbe lasciato in cima il nome del giorno
    // precedente per tutta la durata del gesto.
    val pagerState = rememberPagerState(initialPage = index, pageCount = { days.size })
    val shownDay = days.getOrNull(pagerState.currentPage)

    Column(modifier = modifier.fillMaxSize()) {
        MeteoTopBar(
            title = shownDay?.date
                ?.format(FULL_DATE)
                ?.replaceFirstChar { it.uppercase() }
                ?: "GIORNO",
            subtitle = state.place.name.uppercase(),
            onBack = onBack,
            backLabel = "Torna al dettaglio",
            transition = { pagerState.currentPageOffsetFraction },
        )

        if (days.isEmpty()) {
            // Le linguette non restano in scena vuote sopra il messaggio: senza
            // giorni non c'e' niente da scegliere, e una striscia di caselle
            // vuote sembra un guasto piu' che un'attesa.
            MeteoEmptyState(
                title = if (state.loading) "IN ATTESA DEI DATI" else "NESSUNA PREVISIONE",
                message = state.error ?: "La previsione sta arrivando.",
            )
            return@Column
        }

        // Stesso schema del dettaglio delle grandezze, per la stessa ragione:
        // il carosello comanda e si scrive nello stato **solo a scorrimento
        // finito**. I due effetti che c'erano prima si rincorrevano - il
        // secondo era chiavato su cio' che il primo cambiava e cancellava la
        // propria animazione a meta' - e un trascinamento lasciato andare
        // cambiava comunque il giorno scelto.
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                if (page != state.selectedDay) viewModel.selectDay(page)
            }
        }

        DayTabs(
            days = days,
            selected = pagerState.currentPage,
            position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            width = layout.dayTabWidth,
            onSelect = { day -> scope.launch { pagerState.animateScrollToPage(day) } },
        )

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val day = days.getOrNull(page)
            if (day == null) {
                MeteoEmptyState(title = "GIORNO NON DISPONIBILE")
                return@HorizontalPager
            }
            DayBody(
                state = state,
                day = day,
                layout = layout,
                onFeelsLike = viewModel::setFeelsLike,
            )
        }
    }
}

@Composable
private fun DayBody(
    state: UiState,
    day: DayForecast,
    layout: MeteoLayout,
    onFeelsLike: (Boolean) -> Unit,
) {
    val accents = LocalMeteoAccents.current
    val hours = remember(state.forecast, day.date) {
        state.forecast?.hoursOf(day.date).orEmpty()
    }
    val unit = state.unit
    val feelsLike = state.feelsLike

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = layout.gutter),
        verticalArrangement = Arrangement.spacedBy(layout.gap),
    ) {
        HalfDayHeads(day = day, hours = hours, unit = unit, feelsLike = feelsLike)

        // Il selettore sta **sopra** cio' che comanda. Prima stava sotto il
        // grafico: si scopriva di poter cambiare curva dopo averla gia' letta.
        MeteoSplitPills(
            labels = listOf("EFFETTIVA", "PERCEPITA"),
            selectedIndex = if (feelsLike) 1 else 0,
            onSelect = { onFeelsLike(it == 1) },
        )
        Text(
            text = if (feelsLike) {
                "Quanto alta sembra la temperatura, tenendo conto di umidita', vento e sole."
            } else {
                "La temperatura misurata all'ombra."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val main = hours.map { (if (feelsLike) it.apparent else it.temperature)?.toFloat() }
        val ghost = hours.map { (if (feelsLike) it.temperature else it.apparent)?.toFloat() }

        // La rampa che il grafico usa davvero sull'intervallo mostrato: la
        // legenda deve dire il colore che si vede. La curva principale non ha
        // una tinta, ha la scala dei gradi, e un segno bianco accanto direbbe
        // il contrario di quello che c'e' sulla tela.
        val known = (main + ghost).filterNotNull()
        val tempRamp = if (known.size < 2) {
            listOf(MaterialTheme.colorScheme.onSurface)
        } else {
            temperatureRamp(known.min(), known.max())
        }

        ChartCard(
            title = "ANDAMENTO DELLA GIORNATA",
            legend = buildList {
                add(
                    tempRamp to
                        if (feelsLike) "Percepita" else "Temperatura misurata all'ombra"
                )
                add(
                    listOf(accents.ghost) to
                        if (feelsLike) "Effettiva, per confronto" else "Percepita, per confronto"
                )
                if (day.normTemp != null) {
                    add(listOf(accents.norm) to "Media degli ultimi dieci anni in questo mese")
                }
            },
        ) {
            MeteoChart(
                values = main,
                ghost = ghost,
                xLabels = hours.map { "%02d".format(it.time.hour) },
                daylight = hours.map { it.isDay },
                reference = day.normTemp?.let { ChartReference(it.toFloat(), "MEDIA") },
                useTemperatureRamp = true,
                formatValue = { it.toDouble().asPlainDegrees(unit) },
                description = "Temperatura del giorno",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(layout.tallChartHeight)
                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            )
        }

        // Tutto quello che questa schermata non diceva. I dati c'erano gia'
        // tutti: alcuni nella risposta giornaliera, gli altri nelle ore.
        MeteoStatGrid(
            stats = listOfNotNull(
                MeteoStatData("ALBA", day.sunrise?.format(CLOCK) ?: "--"),
                MeteoStatData("TRAMONTO", day.sunset?.format(CLOCK) ?: "--"),
                MeteoStatData(
                    "SOLE EFFETTIVO",
                    day.sunshineSeconds.secondsAsHoursMinutes(),
                    caption = "non ore di luce",
                    accent = accents.sun,
                ),
                MeteoStatData("UV MASSIMO", day.uvMax.asIndex(), accent = accents.sun),
                MeteoStatData(
                    "VENTO MASSIMO",
                    day.windMax.asMetresPerSecond(),
                    caption = "da ${Wmo.windDirection(day.windDirection)}",
                    accent = accents.wind,
                ),
                MeteoStatData("RAFFICHE", day.gustMax.asMetresPerSecond(), accent = accents.wind),
                MeteoStatData("UMIDITA' MEDIA", day.humidityMean.asPercent()),
                MeteoStatData(
                    "PIOGGIA ATTESA",
                    day.precipitationSum.asMillimetres(),
                    caption = day.precipHours?.let { "in ${it.asHoursMinutes()}" },
                    accent = accents.rain,
                ),
            ),
            columns = layout.statColumns,
            modifier = Modifier.padding(top = 4.dp),
        )

        RainOdds(hours = hours, fallback = day.precipProbability)

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ChartCard(
    title: String,
    /** Ogni voce porta i colori del suo tracciato: uno solo, o l'intera rampa. */
    legend: List<Pair<List<Color>, String>>,
    chart: @Composable () -> Unit,
) {
    MeteoCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        chart()
        // La legenda dice quale tracciato e' quale. Prima non c'era: tre curve
        // diverse sulla stessa tela, e la sola parola "Norma" scritta in nove
        // punti accanto a una tratteggiata.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            legend.forEach { (colors, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Spacer(
                        Modifier
                            .size(width = 16.dp, height = 4.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(
                                if (colors.size == 1) {
                                    SolidColor(colors.first())
                                } else {
                                    // Dal freddo al caldo: la rampa nasce per
                                    // un gradiente verticale, qui si legge da
                                    // sinistra a destra.
                                    Brush.horizontalGradient(colors.reversed())
                                },
                            ),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Le linguette dei giorni
// ---------------------------------------------------------------------------

/**
 * La striscia dei giorni, che si porta da sola sul giorno scelto.
 *
 * Con sette giorni larghi ottantaquattro punti ne stanno in scena tre e mezzo:
 * aprendo sabato la sua linguetta era fuori dallo schermo, e la schermata
 * mostrava il dettaglio di un giorno senza che si vedesse quale.
 */
@Composable
private fun DayTabs(
    days: List<DayForecast>,
    selected: Int,
    position: () -> Float,
    width: androidx.compose.ui.unit.Dp,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    // `animateScrollToItem` portava la linguetta al **bordo** d'ingresso, non
    // al centro: aprendo sabato ci si ritrovava la sua linguetta incollata a
    // sinistra, con i giorni prima invisibili e nessun senso di dove si fosse
    // nella settimana. `centerOn` misura da `layoutInfo` e la porta davvero in
    // mezzo, seguendo il carosello mentre il dito lo muove.
    // La posizione si legge **dentro** il blocco di `snapshotFlow`: e' li' che
    // Compose registra cosa osservare. Con un `Float` gia' calcolato fuori non
    // ci sarebbe niente da osservare e il flusso emetterebbe una volta sola.
    LaunchedEffect(listState, days.size) {
        snapshotFlow { position() }.collect { runCatching { listState.centerOn(it) } }
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        itemsIndexed(days, key = { _, day -> day.date.toString() }) { index, day ->
            val active = index == selected
            val color = if (active) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(
                modifier = Modifier
                    .width(width)
                    .clickable(
                        role = Role.Tab,
                        onClickLabel = "Mostra questo giorno",
                        onClick = { onSelect(index) },
                    )
                    .clearAndSetSemantics {
                        contentDescription = "${day.label} ${day.date.dayOfMonth}"
                    }
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(day.label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
                Text(
                    text = "%02d".format(day.date.dayOfMonth),
                    style = MaterialTheme.typography.headlineSmall,
                    color = color,
                    maxLines = 1,
                )
                Text(
                    text = day.date.format(MONTH_FORMAT).uppercase(Locale.ITALIAN),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                )
                Spacer(
                    Modifier
                        .padding(top = 8.dp)
                        .width(32.dp)
                        .height(2.dp)
                        .background(if (active) color else Color.Transparent),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Di giorno / di notte
// ---------------------------------------------------------------------------
@Composable
private fun HalfDayHeads(
    day: DayForecast,
    hours: List<HourForecast>,
    unit: TempUnit,
    feelsLike: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HalfDay(
            title = "DI GIORNO",
            value = if (feelsLike) day.apparentMax else day.tempMax,
            aside = if (feelsLike) day.tempMax else day.apparentMax,
            asideLabel = if (feelsLike) "EFFETTIVA" else "PERCEPITA",
            code = dominantCode(hours.filter { it.isDay }) ?: day.weatherCode,
            isDay = true,
            unit = unit,
            modifier = Modifier.weight(1f),
        )
        HalfDay(
            title = "DI NOTTE",
            value = if (feelsLike) day.apparentMin else day.tempMin,
            aside = if (feelsLike) day.tempMin else day.apparentMin,
            asideLabel = if (feelsLike) "EFFETTIVA" else "PERCEPITA",
            code = dominantCode(hours.filter { !it.isDay }) ?: day.weatherCode,
            isDay = false,
            unit = unit,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HalfDay(
    title: String,
    value: Double?,
    aside: Double?,
    asideLabel: String,
    code: Int?,
    isDay: Boolean,
    unit: TempUnit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.asPlainDegrees(unit),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            // La riga c'e' sempre, anche vuota: comparendo e sparendo
            // sposterebbe in su e in giu' tutto quello che ha sotto.
            text = aside?.let { "$asideLabel ${it.asPlainDegrees(unit)}" }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeatherGlyph(weatherCode = code, isDay = isDay, modifier = Modifier.size(32.dp))
            Text(
                text = Wmo.condition(code),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Probabilita' di pioggia, divisa fra giorno e notte
// ---------------------------------------------------------------------------

/**
 * I due picchi, dichiarati come picchi.
 *
 * Prima la schermata scriveva il massimo della mezza giornata sotto
 * l'etichetta "DI GIORNO", cioe' presentava un estremo come se fosse il
 * valore: un'ora al settanta per cento faceva sembrare piovosa un'intera
 * mattinata serena. Ora si dice che e' un picco, e a che ora cade.
 */
@Composable
private fun RainOdds(hours: List<HourForecast>, fallback: Int?) {
    val accents = LocalMeteoAccents.current
    val byDay = hours.filter { it.isDay }.maxByOrNull { it.precipProbability ?: 0 }
    val byNight = hours.filter { !it.isDay }.maxByOrNull { it.precipProbability ?: 0 }

    MeteoCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = "PROBABILITA' DI PRECIPITAZIONI",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Odds("PICCO DI GIORNO", byDay, fallback, accents.rain, Modifier.weight(1f))
                Odds("PICCO DI NOTTE", byNight, fallback, accents.rain, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Odds(
    title: String,
    peak: HourForecast?,
    fallback: Int?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val value = peak?.precipProbability ?: fallback
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.asPercent(),
            style = MaterialTheme.typography.displaySmall,
            color = if ((value ?: 0) > 0) accent else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = peak?.takeIf { (it.precipProbability ?: 0) > 0 }
                ?.let { "alle ${it.time.format(CLOCK)}" }
                .orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * Il tempo che ha caratterizzato mezza giornata.
 *
 * Il piu' frequente, e a parita' il piu' coperto: prendere semplicemente il
 * primo darebbe la condizione dell'alba a un pomeriggio di temporali, e
 * prendere il piu' severo trasformerebbe una giornata serena in un temporale
 * per via di un'ora sola.
 */
private fun dominantCode(hours: List<HourForecast>): Int? {
    if (hours.isEmpty()) return null
    return hours.mapNotNull { it.weatherCode }
        .groupingBy { it }
        .eachCount()
        .entries
        .maxWithOrNull(
            compareBy<Map.Entry<Int, Int>> { it.value }
                .thenBy { Wmo.cloudiness(it.key) },
        )
        ?.key
}
