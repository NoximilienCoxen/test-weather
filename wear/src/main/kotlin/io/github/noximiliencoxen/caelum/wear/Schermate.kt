package io.github.noximiliencoxen.caelum.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.timeTextCurvedText
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.lingua.tr
import io.github.noximiliencoxen.caelum.ui.sala.GlifoMeteo
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.disegnaGlifo
import io.github.noximiliencoxen.caelum.ui.sala.glifoDi
import java.time.LocalDateTime

/**
 * L'app: il cielo di Sala sul fondo, e sopra tre pagine da scorrere di lato,
 * Adesso, Prossime ore e Giorni.
 *
 * Il cielo e' **uno per tutte le pagine** e non scorre con loro, come sul
 * telefono: e' il tempo di adesso, e le pagine ci passano davanti.
 */
@Composable
fun WearApp(stato: WearViewModel.Stato) {
    val previsione = (stato as? WearViewModel.Stato.Pronta)?.previsione
    // L'ora del posto, non quella dell'orologio: la previsione e' per la' dove si trova.
    val adesso = previsione?.nowThere() ?: LocalDateTime.now()
    val cielo = remember(previsione, adesso.hour, adesso.minute / 15) { cieloOrologio(previsione, adesso) }

    MaterialTheme {
        AppScaffold(
            timeText = {
                // Senza la pillola scura di serie: l'ora sta sul cielo come il
                // resto, e il suo inchiostro e' gia' misurato sulla cima.
                val stile = TimeTextDefaults.timeTextStyle(color = cielo.ink)
                TimeText(backgroundColor = Color.Transparent) { ora -> timeTextCurvedText(ora, stile) }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) { disegnaCielo(cielo) }
                when (stato) {
                    WearViewModel.Stato.Caricamento -> Messaggio(tr("Carico il tempo…", "Loading weather…"), cielo)
                    WearViewModel.Stato.Errore -> Messaggio(tr("Nessun dato.\nServe la rete.", "No data.\nNetwork needed."), cielo)
                    is WearViewModel.Stato.Pronta -> Pagine(stato.previsione, adesso, stato.fahrenheit, cielo)
                }
            }
        }
    }
}

@Composable
private fun Pagine(previsione: Forecast, adesso: LocalDateTime, fahrenheit: Boolean, cielo: CieloOrologio) {
    val pagine = rememberPagerState { 3 }
    HorizontalPagerScaffold(
        pagerState = pagine,
        pageIndicator = {
            HorizontalPageIndicator(
                pagerState = pagine,
                selectedColor = cielo.ink,
                unselectedColor = cielo.ink.copy(alpha = 0.35f),
                backgroundColor = Color.Transparent,
            )
        },
    ) {
        HorizontalPager(state = pagine) { pagina ->
            AnimatedPage(pageIndex = pagina, pagerState = pagine) {
                when (pagina) {
                    0 -> ScreenScaffold { PaginaAdesso(previsione, adesso, fahrenheit, cielo) }
                    1 -> PaginaOre(previsione, adesso, fahrenheit, cielo)
                    else -> PaginaGiorni(previsione, fahrenheit, cielo)
                }
            }
        }
    }
}

@Composable
private fun Messaggio(testo: String, cielo: CieloOrologio) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(testo, style = SalaType.value, color = cielo.ink, textAlign = TextAlign.Center)
    }
}

// -- Adesso -------------------------------------------------------------------

/**
 * La pagina modello: il posto, la figurina accanto al numero grande, la
 * condizione, la giornata in una riga, e in fondo, appoggiata sulle colline,
 * l'unica domanda che si fa a un polso - piove?
 */
@Composable
private fun PaginaAdesso(previsione: Forecast, adesso: LocalDateTime, fahrenheit: Boolean, cielo: CieloOrologio) {
    val ora = previsione.current
    val oggi = previsione.dayOf(adesso)
    val glifo = glifoDi(ora.weatherCode, nuvolositaAdesso(previsione, adesso))
    val pioggia = remember(previsione, adesso.hour) { pioggiaInVista(oreDaAdesso(previsione, adesso)) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            previsione.place.name,
            style = SalaType.rowTitle.copy(fontSize = 14.sp),
            color = cielo.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconaGlifo(glifo, notte = !ora.isDay, dimensione = 46.dp)
            Spacer(Modifier.width(6.dp))
            Text(temperatura(ora.temperature, fahrenheit), style = SalaType.giant(60), color = cielo.ink, maxLines = 1)
        }
        Text(
            condizioneInFrase(ora.weatherCode),
            style = SalaType.value.copy(fontSize = 15.sp),
            color = cielo.ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            rigaGiornata(oggi, ora.apparent, fahrenheit),
            style = SalaType.rowNote.copy(fontSize = 12.sp),
            color = cielo.inkSoft,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        Pastiglia(testoPioggia(pioggia), cielo, bagnata = pioggia !is PioggiaInVista.Asciutto)
    }
}

/** "↑24° ↓15° · percepita 19°": la giornata intera, sotto l'istante. */
private fun rigaGiornata(oggi: DayForecast?, percepita: Double?, fahrenheit: Boolean): String {
    val pezzi = buildList {
        if (oggi?.tempMax != null && oggi.tempMin != null) {
            add("↑${temperatura(oggi.tempMax, fahrenheit)} ↓${temperatura(oggi.tempMin, fahrenheit)}")
        }
        if (percepita != null) add(tr("percepita ", "feels ") + temperatura(percepita, fahrenheit))
    }
    return pezzi.joinToString(" · ")
}

/**
 * La pastiglia della pioggia. Accento pieno quando c'e' qualcosa da dire,
 * pannello quieto quando e' asciutto: il colore e' gia' la risposta, prima
 * ancora di leggerla.
 */
@Composable
private fun Pastiglia(testo: String, cielo: CieloOrologio, bagnata: Boolean) {
    val fondo = if (bagnata) cielo.palette.accent else cielo.palette.panel
    val inchiostro = if (bagnata) cielo.inkAccento else cielo.inkPanel
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(fondo)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(testo, style = SalaType.pill.copy(fontSize = 12.sp), color = inchiostro, maxLines = 1)
    }
}

// -- Prossime ore -------------------------------------------------------------

@Composable
private fun PaginaOre(previsione: Forecast, adesso: LocalDateTime, fahrenheit: Boolean, cielo: CieloOrologio) {
    val ore = remember(previsione, adesso.hour) { oreDaAdesso(previsione, adesso) }
    val lista = rememberScalingLazyListState()
    ScreenScaffold(scrollState = lista) { margini ->
        ScalingLazyColumn(
            state = lista,
            contentPadding = margini,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Titolo(tr("Prossime ore", "Next hours"), cielo) }
            items(ore) { ora -> RigaOra(ora, ora == ore.first(), fahrenheit, cielo) }
        }
    }
}

@Composable
private fun RigaOra(ora: HourForecast, prima: Boolean, fahrenheit: Boolean, cielo: CieloOrologio) {
    Riga(cielo) {
        Text(
            if (prima) tr("Ora", "Now") else oraBreve(ora.time),
            style = SalaType.hourLabel.copy(fontSize = 13.sp),
            color = if (prima) cielo.palette.accent else cielo.inkPanel,
            modifier = Modifier.width(34.dp),
            maxLines = 1,
        )
        IconaGlifo(glifoDi(ora.weatherCode, ora.cloudCover), notte = !ora.isDay, dimensione = 24.dp)
        Text(
            temperatura(ora.temperature, fahrenheit),
            style = SalaType.value.copy(fontSize = 16.sp),
            color = cielo.inkPanel,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            maxLines = 1,
        )
        probabilitaPioggia(ora.precipProbability)?.let {
            Text(it, style = SalaType.pill.copy(fontSize = 12.sp), color = cielo.acqua, maxLines = 1)
        }
    }
}

// -- Giorni -------------------------------------------------------------------

@Composable
private fun PaginaGiorni(previsione: Forecast, fahrenheit: Boolean, cielo: CieloOrologio) {
    val scala = remember(previsione) { estremiSettimana(previsione.days) }
    val lista = rememberScalingLazyListState()
    ScreenScaffold(scrollState = lista) { margini ->
        ScalingLazyColumn(
            state = lista,
            contentPadding = margini,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Titolo(tr("Giorni", "Days"), cielo) }
            items(previsione.days) { giorno -> RigaGiorno(giorno, scala, fahrenheit, cielo) }
        }
    }
}

@Composable
private fun RigaGiorno(
    giorno: DayForecast,
    scala: ClosedFloatingPointRange<Double>?,
    fahrenheit: Boolean,
    cielo: CieloOrologio,
) {
    Riga(cielo) {
        Text(
            giorno.label,
            // "TODAY" e' la sigla piu' larga: a trentotto punti diventava "TODA".
            style = SalaType.hourLabel.copy(fontSize = 12.sp, letterSpacing = 0.02.em),
            color = cielo.inkPanel,
            modifier = Modifier.width(46.dp),
            maxLines = 1,
        )
        IconaGlifo(glifoDi(giorno.weatherCode, null), notte = false, dimensione = 22.dp)
        Text(
            temperatura(giorno.tempMin, fahrenheit),
            style = SalaType.giornoMin.copy(fontSize = 12.sp),
            color = cielo.inkPanelSoft,
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp),
            maxLines = 1,
        )
        BarraGiorno(giorno, scala, cielo, Modifier.weight(1f).height(6.dp).padding(horizontal = 4.dp))
        Text(
            temperatura(giorno.tempMax, fahrenheit),
            style = SalaType.giornoMax.copy(fontSize = 13.sp),
            color = cielo.inkPanel,
            modifier = Modifier.width(30.dp),
            maxLines = 1,
        )
    }
}

/**
 * Da minima a massima, sulla scala comune della settimana: la stessa lettura
 * della striscia dei giorni sul telefono, stretta in una riga.
 */
@Composable
private fun BarraGiorno(giorno: DayForecast, scala: ClosedFloatingPointRange<Double>?, cielo: CieloOrologio, modifier: Modifier) {
    val binario = cielo.palette.maniglia
    val tratto = cielo.palette.accent
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(binario, cornerRadius = r)
        val min = giorno.tempMin ?: return@Canvas
        val max = giorno.tempMax ?: return@Canvas
        if (scala == null) return@Canvas
        val ampiezza = scala.endInclusive - scala.start
        val da = ((min - scala.start) / ampiezza).toFloat().coerceIn(0f, 1f)
        val a = ((max - scala.start) / ampiezza).toFloat().coerceIn(da, 1f)
        val x = da * size.width
        // Un segmento mai piu' corto della sua altezza: una giornata senza
        // escursione resta un punto, non sparisce.
        val w = ((a - da) * size.width).coerceAtLeast(size.height)
        drawRoundRect(tratto, topLeft = Offset(x.coerceAtMost(size.width - w), 0f), size = Size(w, size.height), cornerRadius = r)
    }
}

// -- Pezzi comuni -------------------------------------------------------------

@Composable
private fun Titolo(testo: String, cielo: CieloOrologio) {
    Text(
        testo,
        style = SalaType.citta.copy(fontSize = 18.sp, lineHeight = 20.sp),
        color = cielo.ink,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

/** Una riga a pastiglia, translucida sul cielo come i pannelli del telefono. */
@Composable
private fun Riga(cielo: CieloOrologio, contenuto: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(cielo.palette.panel)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = contenuto,
    )
}

/** La figurina del tempo, la stessa del telefono: il disegno sta nel modulo `core`. */
@Composable
private fun IconaGlifo(glifo: GlifoMeteo, notte: Boolean, dimensione: Dp) {
    Canvas(Modifier.size(dimensione)) { disegnaGlifo(glifo, notte) }
}
