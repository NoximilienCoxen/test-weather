package com.forli.meteo.ui.temperature.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.AirBand
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.asIndex
import com.forli.meteo.ui.common.MeteoCard
import com.forli.meteo.ui.common.MeteoEmptyState
import com.forli.meteo.ui.common.MeteoLayout
import com.forli.meteo.ui.common.MeteoMetric
import com.forli.meteo.ui.common.MeteoMetricCard

/**
 * La qualita' dell'aria.
 *
 * E' la pagina nuova, e non porta un dato nuovo: `AirQualityRepository` esiste
 * dal principio, ha il suo endpoint e il suo contratto verificato in CI, e
 * finora lo interrogava soltanto un widget. Dentro l'app quei numeri non si
 * vedevano da nessuna parte - e la pagina che si chiamava ARIA, in realta',
 * parlava di vento.
 *
 * L'indice europeo da solo non dice niente a nessuno: trenta e' buono o
 * cattivo? Quindi accanto al numero c'e' la banda, con la sua scala disegnata:
 * si vede dove cade fra le sei fasce dell'agenzia europea, non solo il valore.
 */
@Composable
internal fun AirPage(
    state: UiState,
    layout: MeteoLayout,
    modifier: Modifier = Modifier,
    /** La settimana, che chiude ogni pagina. */
    week: @Composable () -> Unit = {},
) {
    val air = state.air

    PageColumn(layout = layout, modifier = modifier, week = week) {
        if (air == null) {
            MeteoCard(modifier = Modifier.fillMaxWidth()) {
                MeteoEmptyState(
                    title = if (state.airUnavailable) {
                        "QUALITA' DELL'ARIA NON DISPONIBILE"
                    } else {
                        "IN ARRIVO"
                    },
                    message = if (state.airUnavailable) {
                        "La misura arriva da un servizio diverso da quello delle previsioni, " +
                            "e non ha risposto. Il resto della schermata non ne risente."
                    } else {
                        "La misura viaggia per conto suo e arriva subito dopo la previsione."
                    },
                )
            }
            return@PageColumn
        }

        MeteoCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(
                    text = air.band?.label ?: "--",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = air.band.advice(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(14.dp))
                BandScale(
                    aqi = air.europeanAqi,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf("0", "40", "80", "100+").forEach {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        MeteoMetricCard(
            rows = listOf(
                MeteoMetric(
                    "INDICE EUROPEO",
                    air.europeanAqi?.toString() ?: "--",
                    emphasis = true,
                ),
                MeteoMetric("BANDA", air.band?.label ?: "--"),
                // Le polveri portano l'unita' scritta: microgrammi per metro
                // cubo non e' una misura che si indovina dal contesto.
                MeteoMetric("PM 2.5", air.pm25.asIndex().withUnit("µg/m³")),
                MeteoMetric("PM 10", air.pm10.asIndex().withUnit("µg/m³")),
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "L'indice europeo riassume in un numero solo cinque inquinanti, e vale per " +
                "l'ora corrente: non ha una previsione come il resto della schermata.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun String.withUnit(unit: String): String = if (this == "--") this else "$this $unit"

/** Cosa comporta, in una riga. Un indice senza conseguenze non e' un'informazione. */
private fun AirBand?.advice(): String = when (this) {
    AirBand.BUONA -> "Aria pulita: nessuna precauzione."
    AirBand.DISCRETA -> "Va bene per tutti; chi e' molto sensibile puo' farci caso."
    AirBand.MEDIA -> "Chi ha problemi respiratori eviti gli sforzi prolungati all'aperto."
    AirBand.SCARSA -> "Meglio ridurre l'attivita' fisica all'aperto."
    AirBand.MOLTO_SCARSA -> "Sforzi all'aperto sconsigliati a tutti."
    AirBand.ESTREMAMENTE_SCARSA -> "Restare al chiuso quando si puo'."
    null -> "Misura non disponibile."
}

/** I colori delle sei fasce dell'agenzia europea, nell'ordine in cui salgono. */
private val BandColors = listOf(
    Color(0xFF4FBF7F),
    Color(0xFF9ACB48),
    Color(0xFFE7C93C),
    Color(0xFFED8A3A),
    Color(0xFFD9534F),
    Color(0xFF8E4B8E),
)

/** I confini delle fasce, in unita' dell'indice. L'ultima e' aperta verso l'alto. */
private val BandEdges = listOf(0f, 20f, 40f, 60f, 80f, 100f, 130f)

/**
 * La scala, coi suoi sei gradini e un segno dove cade il valore.
 *
 * A larghezze proporzionali all'ampiezza reale di ogni fascia, non a sei
 * segmenti uguali: le fasce sono tutte larghe venti tranne l'ultima, e
 * disegnarle uguali farebbe leggere "molto scarsa" come se cominciasse molto
 * piu' in la' di dove comincia.
 */
@Composable
private fun BandScale(aqi: Int?, modifier: Modifier = Modifier) {
    val marker = MaterialTheme.colorScheme.onSurface
    val spoken = aqi?.let { "Indice $it su una scala che arriva a 100 e oltre" }
        ?: "Indice non disponibile"
    Canvas(modifier.semantics { contentDescription = spoken }) {
        val total = BandEdges.last() - BandEdges.first()
        val radius = CornerRadius(size.height / 2f)
        BandColors.forEachIndexed { i, color ->
            val from = (BandEdges[i] - BandEdges.first()) / total * size.width
            val to = (BandEdges[i + 1] - BandEdges.first()) / total * size.width
            drawRoundRect(
                color = color,
                topLeft = Offset(from, 0f),
                size = Size((to - from - 2f).coerceAtLeast(1f), size.height),
                cornerRadius = radius,
            )
        }
        val value = aqi?.toFloat() ?: return@Canvas
        val x = (value.coerceIn(BandEdges.first(), BandEdges.last()) / total * size.width)
            .coerceIn(0f, size.width)
        // Il segno e' un bastoncino chiaro con un bordo scuro attorno: sulle
        // fasce chiare un segno bianco sparirebbe, sulle scure uno nero.
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.55f),
            topLeft = Offset(x - 4f, -3f),
            size = Size(8f, size.height + 6f),
            cornerRadius = CornerRadius(4f),
        )
        drawRoundRect(
            color = marker,
            topLeft = Offset(x - 2f, -1f),
            size = Size(4f, size.height + 2f),
            cornerRadius = CornerRadius(2f),
        )
    }
}
