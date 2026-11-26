package com.caelumweather.ui.sala.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caelumweather.lingua.Lingue
import com.caelumweather.data.StatoDelMare
import com.caelumweather.lingua.tr
import com.caelumweather.ui.UiState
import com.caelumweather.ui.sala.PannelloSala
import com.caelumweather.ui.sala.SalaPalette
import com.caelumweather.ui.sala.SalaType
import com.caelumweather.ui.sala.Didascalia

/**
 * Sala VII - Il mare, **solo dove c'e'**.
 *
 * Stava in fondo alla scheda del vento, perche' il mare e' vento che ha avuto
 * tempo; ma sulla costa onde, acqua e maree allungavano quella scheda fino a
 * farla scorrere dentro di se'. Adesso ha una scheda sua, che compare solo
 * nelle localita' di mare (`UiState.mareMostrato`): altrove non esiste, e nei
 * puntini in basso non c'e'.
 */
@Composable
fun SalaMareScreen(
    state: UiState,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
    movimento: Boolean = true,
) {
    val ora = state.mareMostrato ?: return
    PannelloSala(palette = palette, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tr("Il mare", "The sea", "Das Meer", "La mer", "El mar"), style = SalaType.cardTitle, color = palette.ink)
                Didascalia(statoDelMare(ora.onda), palette, modifier = Modifier.padding(top = 6.dp))
            }
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = ora.onda?.let { String.format(Lingue.locale, "%.1f", it) } ?: "--",
                    style = SalaType.numeroSala,
                    color = palette.ink,
                )
                Text(
                    text = "m",
                    style = SalaType.hourLabel,
                    color = palette.accent,
                    modifier = Modifier.padding(start = 3.dp, top = 8.dp),
                )
            }
        }
        SezioneMare(
            ora = ora,
            unita = state.unit,
            palette = palette,
            movimento = movimento,
            mare = state.mare.takeIf { state.forcedMare == null },
            conEtichetta = false,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/**
 * Lo stato del mare a parole, **sulla stessa scala della riga sotto**
 * (`StatoDelMare`, Douglas): due scale diverse nella stessa scheda dicevano
 * "mosso" sopra e "poco mosso" sotto per la stessa onda.
 */
private fun statoDelMare(metri: Double?): String {
    val m = metri ?: return tr("Onde non disponibili.", "Waves unavailable.", "Wellen nicht verfügbar.", "Vagues indisponibles.", "Olas no disponibles.")
    val stato = StatoDelMare.di(m) ?: return ""
    val come = when (stato) {
        StatoDelMare.CALMO, StatoDelMare.QUASI_CALMO -> tr("acqua quasi ferma.", "the water is almost still.", "das Wasser steht fast still.", "l'eau est presque immobile.", "el agua casi quieta.")
        StatoDelMare.POCO_MOSSO -> tr("increspature e piccole onde.", "ripples and small waves.", "Kräuselungen und kleine Wellen.", "rides et petites vagues.", "rizos y olas pequeñas.")
        StatoDelMare.MOSSO -> tr("onde corte, qualche cresta bianca.", "short waves, a few white crests.", "kurze Wellen, einige Schaumkronen.", "vagues courtes, quelques moutons.", "olas cortas, algunas crestas blancas.")
        StatoDelMare.MOLTO_MOSSO -> tr("onde lunghe, creste bianche dappertutto.", "longer waves, white crests everywhere.", "längere Wellen, überall Schaumkronen.", "vagues longues, moutons partout.", "olas largas, crestas blancas por todas partes.")
        else -> tr("mare grosso, meglio restarne fuori.", "heavy sea, best stay out.", "schwere See, besser fernbleiben.", "grosse mer, mieux vaut rester à l'écart.", "mar gruesa, mejor mantenerse fuera.")
    }
    return stato.label.replaceFirstChar { it.uppercase() } + ": " + come
}
