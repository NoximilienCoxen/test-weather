package io.github.noximiliencoxen.caelum.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.lingua.tr
import io.github.noximiliencoxen.caelum.ui.sala.GlifoMeteo
import io.github.noximiliencoxen.caelum.ui.sala.disegnaGlifo
import io.github.noximiliencoxen.caelum.ui.sala.glifoDi
import java.time.LocalDateTime
import java.time.ZoneOffset

/** L'app: tre pagine da scorrere di lato, Adesso, Prossime ore e Giorni. */
@Composable
fun WearApp(stato: WearViewModel.Stato) {
    MaterialTheme {
        AppScaffold {
            when (stato) {
                WearViewModel.Stato.Caricamento -> Messaggio(tr("Carico il tempo…", "Loading weather…"))
                WearViewModel.Stato.Errore -> Messaggio(tr("Nessun dato. Serve la rete.", "No data. Network needed."))
                is WearViewModel.Stato.Pronta -> Pagine(stato.previsione, stato.fahrenheit)
            }
        }
    }
}

@Composable
private fun Pagine(previsione: Forecast, fahrenheit: Boolean) {
    val pagine = rememberPagerState { 3 }
    // L'ora del posto, non quella dell'orologio: la previsione e' per la' dove si trova.
    val adesso = LocalDateTime.now(ZoneOffset.ofTotalSeconds(previsione.utcOffsetSeconds))
    HorizontalPager(state = pagine, modifier = Modifier.fillMaxSize().clipToBounds()) { pagina ->
        when (pagina) {
            0 -> PaginaAdesso(previsione, adesso, fahrenheit)
            1 -> PaginaOre(previsione, adesso, fahrenheit)
            else -> PaginaGiorni(previsione, fahrenheit)
        }
    }
}

@Composable
private fun Messaggio(testo: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(testo, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PaginaAdesso(previsione: Forecast, adesso: LocalDateTime, fahrenheit: Boolean) {
    val ora = previsione.current
    val glifo = glifoDi(ora.weatherCode, nuvolositaAdesso(previsione, adesso))
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(previsione.place.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        IconaGlifo(glifo, notte = !ora.isDay, dimensione = 56.dp)
        Text(temperatura(ora.temperature, fahrenheit), style = MaterialTheme.typography.displaySmall)
        Text(
            Wmo.condition(ora.weatherCode),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun PaginaOre(previsione: Forecast, adesso: LocalDateTime, fahrenheit: Boolean) {
    val ore = oreDaAdesso(previsione, adesso)
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text(tr("Prossime ore", "Next hours"), style = MaterialTheme.typography.labelMedium) }
        items(ore) { ora ->
            Riga(
                sinistra = oraBreve(ora.time),
                glifo = glifoDi(ora.weatherCode, ora.cloudCover),
                notte = !ora.isDay,
                centro = temperatura(ora.temperature, fahrenheit),
                destra = probabilitaPioggia(ora.precipProbability),
            )
        }
    }
}

@Composable
private fun PaginaGiorni(previsione: Forecast, fahrenheit: Boolean) {
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text(tr("Giorni", "Days"), style = MaterialTheme.typography.labelMedium) }
        items(previsione.days) { giorno ->
            Riga(
                sinistra = giorno.label,
                glifo = glifoDi(giorno.weatherCode, null),
                notte = false,
                centro = "${temperatura(giorno.tempMax, fahrenheit)} ${temperatura(giorno.tempMin, fahrenheit)}",
                destra = probabilitaPioggia(giorno.precipProbability),
            )
        }
    }
}

@Composable
private fun Riga(sinistra: String, glifo: GlifoMeteo, notte: Boolean, centro: String, destra: String?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(sinistra, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        IconaGlifo(glifo, notte, dimensione = 22.dp)
        Text(centro, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        if (destra != null) Text(destra, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** La figurina del tempo, la stessa del telefono: il disegno sta nel modulo `core`. */
@Composable
private fun IconaGlifo(glifo: GlifoMeteo, notte: Boolean, dimensione: Dp) {
    Canvas(Modifier.size(dimensione)) { disegnaGlifo(glifo, notte) }
}
