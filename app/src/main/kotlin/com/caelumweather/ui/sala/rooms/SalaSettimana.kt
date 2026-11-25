package com.caelumweather.ui.sala.rooms

import com.caelumweather.lingua.inMezzoAllaFrase
import com.caelumweather.ui.sala.cedevole
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caelumweather.ui.sala.LocalMovimento
import com.caelumweather.ui.sala.rememberOndaScheda
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.caelumweather.ui.UiState
import com.caelumweather.ui.WeatherViewModel
import com.caelumweather.data.HourForecast
import com.caelumweather.data.MoonPhase
import com.caelumweather.ui.sala.GiornoSettimana
import com.caelumweather.ui.sala.PannelloSala
import com.caelumweather.ui.sala.CellaValore
import com.caelumweather.ui.sala.SalaPalette
import com.caelumweather.ui.sala.SalaRoom
import com.caelumweather.ui.sala.SalaTokens
import com.caelumweather.ui.sala.SalaType
import com.caelumweather.ui.sala.settimanaDi
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val OraMinuto: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * La settimana in una riga, **dai dati e non da una frase scritta a mano**.
 *
 * Il prototipo aveva una frase fissa ("un fronte da ovest da mercoledi'") che
 * descriveva una settimana inventata. Una frase del genere in un'app vera e'
 * una previsione senza fonte: qui si dice solo cio' che i numeri dicono.
 */
internal fun sommarioDella(settimana: List<GiornoSettimana>, state: UiState): String {
    if (settimana.isEmpty()) return tr("Previsione non ancora disponibile.", "Forecast not available yet.", "Vorhersage noch nicht verfügbar.", "Prévision pas encore disponible.", "Previsión aún no disponible.")
    val bagnati = settimana.count { (it.mm ?: 0.0) > 0.05 }
    val mm = settimana.sumOf { it.mm ?: 0.0 }
    val minime = settimana.mapNotNull { it.min }
    val massime = settimana.mapNotNull { it.max }
    val escursione = if (minime.isNotEmpty() && massime.isNotEmpty()) {
        val lo = state.unit.from(minime.min()).roundToInt()
        val hi = state.unit.from(massime.max()).roundToInt()
        tr("escursione fra $lo° e $hi°", "ranging from $lo° to $hi°", "Spanne von $lo° bis $hi°", "amplitude de $lo° à $hi°", "oscilación entre $lo° y $hi°")
    } else {
        null
    }
    val pioggia = when {
        bagnati == 0 -> tr("Sette giorni asciutti", "Seven dry days", "Sieben trockene Tage", "Sept jours secs", "Siete días secos")
        bagnati == 1 -> tr("Un giorno con precipitazioni, ${mm.virgola()} mm attesi", "One day with precipitation, ${mm.virgola()} mm expected", "Ein Tag mit Niederschlag, ${mm.virgola()} mm erwartet", "Un jour avec précipitations, ${mm.virgola()} mm attendus", "Un día con precipitaciones, ${mm.virgola()} mm previstos")
        else -> tr("$bagnati giorni con precipitazioni, ${mm.virgola()} mm attesi", "$bagnati days with precipitation, ${mm.virgola()} mm expected", "$bagnati Tage mit Niederschlag, ${mm.virgola()} mm erwartet", "$bagnati jours avec précipitations, ${mm.virgola()} mm attendus", "$bagnati días con precipitaciones, ${mm.virgola()} mm previstos")
    }
    return listOfNotNull(pioggia, escursione).joinToString(", ") + "."
}

private fun Double.virgola(): String = String.format(Lingue.locale, "%.1f", this)

/**
 * "6 h di sole", e sotto la mezz'ora "sole assente": "0 h di sole" si legge
 * come un dato che manca, non come un giorno coperto.
 */
internal fun oreDiSole(secondi: Double): String {
    val ore = (secondi / 3600.0).roundToInt()
    return if (ore == 0) tr("sole assente", "no sun", "keine Sonne", "pas de soleil", "sin sol") else tr("$ore h di sole", "$ore h of sun", "$ore Std. Sonne", "$ore h de soleil", "$ore h de sol")
}

