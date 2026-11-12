package com.caelumweather.ui.sala.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.caelumweather.data.Forecast
import com.caelumweather.data.NeveDelGiorno
import com.caelumweather.data.PostoSottoLaNeve
import com.caelumweather.data.nevePrevista
import com.caelumweather.data.postoRispettoAllaNeve
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr
import com.caelumweather.prefs.TempUnit
import com.caelumweather.ui.sala.SalaPalette
import com.caelumweather.ui.sala.SalaType
import kotlin.math.roundToInt

/**
 * "La neve" in Sala III: centimetri attesi per giorno e fino a che quota.
 *
 * Compare solo dove serve ([com.caelumweather.data.serveLaNeve]): con neve in
 * arrivo, o su un posto sopra i mille metri, dove "oggi niente neve, lo zero
 * termico e' a 2900 m" e' comunque la risposta a quello che ci si chiede.
 *
 * **La quota e' una stima, e la sezione lo scrive sotto** (vedi
 * [com.caelumweather.data.SCARTO_QUOTA_NEVE_M]). I metri si danno in piedi per
 * chi ha scelto i gradi Fahrenheit, come le onde del mare: e' la stessa scelta.
 */
@Composable
internal fun SezioneNeve(
    previsione: Forecast,
    zeroTermicoOra: Double?,
    unita: TempUnit,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    val giorni = remember(previsione) { nevePrevista(previsione) }
    val totale = giorni.sumOf { it.cm }
    val quotaPiuBassa = giorni.mapNotNull { it.quota }.minOrNull()
    val posto = postoRispettoAllaNeve(previsione.elevation, quotaPiuBassa)
    val massimo = (giorni.maxOfOrNull { it.cm } ?: 1.0).coerceAtLeast(1.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = tr("LA NEVE", "SNOW", "SCHNEE", "LA NEIGE", "LA NIEVE"),
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        if (giorni.isEmpty()) {
            Text(
                text = tr("Nessuna neve attesa nei prossimi giorni.", "No snow expected in the coming days.", "In den nächsten Tagen ist kein Schnee zu erwarten.", "Pas de neige attendue dans les prochains jours.", "No se espera nieve en los próximos días."),
                style = SalaType.rowTitle,
                color = palette.ink,
            )
        } else {
            Text(
                text = tr(
                    "${cm(totale)} cm in ${giorni.size} ${if (giorni.size == 1) "giorno" else "giorni"}",
                    "${cm(totale)} cm over ${giorni.size} ${if (giorni.size == 1) "day" else "days"}",
                    "${cm(totale)} cm in ${giorni.size} ${if (giorni.size == 1) "Tag" else "Tagen"}",
                    "${cm(totale)} cm en ${giorni.size} ${if (giorni.size == 1) "jour" else "jours"}",
                    "${cm(totale)} cm en ${giorni.size} ${if (giorni.size == 1) "día" else "días"}",
                ),
                style = SalaType.rowTitle,
                color = palette.ink,
            )
            quotaPiuBassa?.let {
                Text(
                    text = tr(
                        "Nevica fino a circa ${altezza(it, unita)}.",
                        "Snow down to about ${altezza(it, unita)}.",
                        "Schnee bis etwa ${altezza(it, unita)}.",
                        "Neige jusqu'à environ ${altezza(it, unita)}.",
                        "Nieve hasta unos ${altezza(it, unita)}.",
                    ) + " " + (posto?.let(::frasePosto) ?: ""),
                    style = SalaType.rowNote,
                    color = palette.inkSoft,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            // **Una fila di colonnine, non una riga per giorno**: sette righe
            // in coda alla sala della pioggia la portavano oltre lo schermo.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // La quota sotto ogni giorno solo se cambia: quattro volte lo stesso
                // numero e' gia' nella riga qui sopra.
                val quoteDiverse = giorni.mapNotNull { it.quota }.distinct().size > 1
                giorni.take(GIORNI_DI_NEVE_IN_FILA).forEach { ColonnaNeve(it, massimo, unita, palette, quoteDiverse) }
                repeat(GIORNI_DI_NEVE_IN_FILA - giorni.size.coerceAtMost(GIORNI_DI_NEVE_IN_FILA)) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
        // Lo zero termico di adesso e l'avvertenza in un solo paragrafo breve.
        val zero = zeroTermicoOra?.let { z ->
            tr(
                "Zero termico ora a ${altezza(z.roundToInt(), unita)}. ",
                "Freezing level now at ${altezza(z.roundToInt(), unita)}. ",
                "Nullgradgrenze jetzt auf ${altezza(z.roundToInt(), unita)}. ",
                "Isotherme 0 °C maintenant à ${altezza(z.roundToInt(), unita)}. ",
                "Isoterma 0 °C ahora a ${altezza(z.roundToInt(), unita)}. ",
            )
        } ?: ""
        Text(
            text = zero + tr(
                "Quota neve stimata: zero termico meno circa 300 m. In montagna valgono i bollettini valanghe.",
                "Estimated snow line: freezing level minus about 300 m. In the mountains, avalanche bulletins come first.",
                "Geschätzte Schneefallgrenze: Nullgradgrenze minus etwa 300 m. In den Bergen gelten die Lawinenlageberichte.",
                "Limite de la neige estimée : isotherme 0 °C moins environ 300 m. En montagne, les bulletins d'avalanches font foi.",
                "Cota de nieve estimada: isoterma 0 °C menos unos 300 m. En montaña mandan los boletines de aludes.",
            ),
            style = SalaType.rowNote,
            color = palette.inkSoft,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private const val GIORNI_DI_NEVE_IN_FILA = 7

@Composable
private fun androidx.compose.foundation.layout.RowScope.ColonnaNeve(g: NeveDelGiorno, massimo: Double, unita: TempUnit, palette: SalaPalette, conQuota: Boolean) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height((6f + 34f * (g.cm / massimo).coerceIn(0.0, 1.0).toFloat()).dp)
                .clip(CircleShape)
                // Bianco-azzurro: la neve su una carta chiara deve avere un
                // contorno, e il blu dell'acqua le sta accanto senza
                // confondersi con la pioggia.
                .background(Color(0xFF9DC3E6)),
        )
        Text(text = g.etichetta, style = SalaType.microLabel, color = palette.inkFaint, maxLines = 1)
        Text(text = "${cm(g.cm)} cm", style = SalaType.microLabel, color = palette.ink, maxLines = 1)
        g.quota?.takeIf { conQuota }?.let { Text(text = altezza(it, unita), style = SalaType.microLabel, color = palette.inkSoft, maxLines = 1) }
    }
}

private fun cm(v: Double): String =
    if (v >= 10) v.roundToInt().toString() else String.format(Lingue.locale, "%.1f", v)

/** I metri di una quota, in piedi per chi legge i gradi Fahrenheit. */
internal fun altezza(metri: Int, unita: TempUnit): String =
    if (unita == TempUnit.FAHRENHEIT) "${(metri * 3.28084 / 10.0).roundToInt() * 10} ft" else "$metri m"

private fun frasePosto(p: PostoSottoLaNeve): String = when (p) {
    PostoSottoLaNeve.NEVICA_QUI -> tr("Da te nevica.", "It snows where you are.", "Bei dir schneit es.", "Chez vous, il neige.", "Donde estás, nieva.")
    PostoSottoLaNeve.AL_LIMITE -> tr("Da te è al limite: dipende dall'ora e dal versante.", "Where you are it's borderline: it depends on the hour and the slope.", "Bei dir ist es grenzwertig: es hängt von Uhrzeit und Hang ab.", "Chez vous, c'est limite : cela dépend de l'heure et du versant.", "Donde estás está en el límite: depende de la hora y la ladera.")
    PostoSottoLaNeve.PIU_IN_ALTO -> tr("Da te no: la neve resta più in alto.", "Not where you are: the snow stays higher up.", "Bei dir nicht: der Schnee bleibt weiter oben.", "Pas chez vous : la neige reste plus haut.", "Donde estás no: la nieve queda más arriba.")
}
