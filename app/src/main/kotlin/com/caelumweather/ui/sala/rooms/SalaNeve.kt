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
import com.caelumweather.data.neveAlSuolo
import com.caelumweather.data.nevePrevista
import com.caelumweather.data.postoRispettoAllaNeve
import com.caelumweather.data.quotaNeve
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr
import com.caelumweather.prefs.TempUnit
import com.caelumweather.ui.UiState
import com.caelumweather.ui.sala.CellaValore
import com.caelumweather.ui.sala.Didascalia
import com.caelumweather.ui.sala.PannelloSala
import com.caelumweather.ui.sala.SalaPalette
import com.caelumweather.ui.sala.SalaType
import com.caelumweather.ui.sala.crescitaAllArrivo
import com.caelumweather.ui.sala.misuraCheArriva
import kotlin.math.roundToInt

/**
 * La scheda della neve, **solo quando c'e'** ([com.caelumweather.data.serveLaNeve]): neve prevista
 * nei prossimi giorni o neve al suolo adesso.
 *
 * Era una sezione in fondo alla scheda della pioggia. A Sapporo, con quattro
 * giorni di neve a quote diverse, la portava a 689 dp contro un tetto di 462, e
 * la scheda scorreva dentro di se' (CONTESTO §85). Adesso ha una scheda sua,
 * subito dopo la pioggia; la pioggia ne tiene un rimando di una riga.
 *
 * In testa i centimetri attesi in tutta la previsione e fino a che quota; poi
 * una colonnina per giorno di neve; sotto le tre celle dell'ora scelta nella
 * barra (al suolo, zero termico, quota neve), come nelle altre schede.
 *
 * **La quota e' una stima, e la scheda lo scrive sotto** (vedi
 * [com.caelumweather.data.SCARTO_QUOTA_NEVE_M]). I metri si danno in piedi per
 * chi ha scelto i gradi Fahrenheit, come le onde del mare: e' la stessa scelta.
 */
@Composable
fun SalaNeveScreen(
    state: UiState,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    val previsione = state.forecast ?: return
    val giorni = remember(previsione) { nevePrevista(previsione) }
    val totale = giorni.sumOf { it.cm }
    val quotaPiuBassa = giorni.mapNotNull { it.quota }.minOrNull()
    val posto = postoRispettoAllaNeve(previsione.elevation, quotaPiuBassa)
    val massimo = (giorni.maxOfOrNull { it.cm } ?: 1.0).coerceAtLeast(1.0)
    val ora = state.detailHour
    val alSuolo = ora?.snowDepth ?: neveAlSuolo(previsione, state.nowIndex)
    val unita = state.unit

    PannelloSala(palette = palette, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tr("La neve", "Snow", "Schnee", "La neige", "La nieve"), style = SalaType.cardTitle, color = palette.ink)
                Didascalia(
                    if (giorni.isEmpty()) {
                        tr(
                            "Nessuna nevicata attesa nei prossimi giorni: è la neve rimasta al suolo.",
                            "No snowfall expected in the coming days: this is the snow left on the ground.",
                            "In den nächsten Tagen kein Schneefall erwartet: das ist der liegende Schnee.",
                            "Pas de chute de neige attendue dans les prochains jours : c'est la neige restée au sol.",
                            "No se esperan nevadas en los próximos días: es la nieve que queda en el suelo.",
                        )
                    } else {
                        fraseGiorni(giorni.size, previsione.days.size).let { inizio ->
                            // Senza zero termico (una scorta vecchia) la frase si chiude qui.
                            quotaPiuBassa?.let { "$inizio ${fraseQuota(it, unita)}" } ?: (inizio.dropLast(1) + ".")
                        } +
                            (posto?.let { " " + frasePosto(it) } ?: "")
                    },
                    palette,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = cm(if (giorni.isEmpty()) alSuolo ?: 0.0 else totale),
                    style = SalaType.numeroSala,
                    color = palette.ink,
                )
                Text(
                    text = "cm",
                    style = SalaType.hourLabel,
                    color = palette.accent,
                    modifier = Modifier.padding(start = 3.dp, top = 8.dp),
                )
            }
        }

        if (giorni.isNotEmpty()) {
            Text(
                text = tr("I GIORNI DI NEVE", "SNOW DAYS", "SCHNEETAGE", "LES JOURS DE NEIGE", "LOS DÍAS DE NIEVE"),
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // La quota sotto ogni giorno solo se cambia: quattro volte lo
                // stesso numero e' gia' nella frase in testa.
                val quoteDiverse = giorni.mapNotNull { it.quota }.distinct().size > 1
                giorni.take(GIORNI_DI_NEVE_IN_FILA).forEachIndexed { i, g -> ColonnaNeve(g, massimo, unita, palette, quoteDiverse, ritardo = i * 60) }
                repeat(GIORNI_DI_NEVE_IN_FILA - giorni.size.coerceAtMost(GIORNI_DI_NEVE_IN_FILA)) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }

        // Le tre celle seguono l'ora scelta nella barra, come nelle altre schede.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            CellaValore(
                etichetta = tr("AL SUOLO", "ON GROUND", "LIEGEND", "AU SOL", "EN SUELO"),
                valore = alSuolo?.let { "${cm(it)} cm" } ?: "--",
                palette = palette,
            )
            CellaValore(
                etichetta = tr("ZERO TERMICO", "FREEZING LV.", "NULLGRAD", "ISO 0 °C", "ISO 0 °C"),
                valore = ora?.freezingLevel?.let { altezza(it.roundToInt(), unita) } ?: "--",
                palette = palette,
            )
            CellaValore(
                etichetta = tr("QUOTA NEVE", "SNOW LINE", "SCHNEEGR.", "LIMITE NEIGE", "COTA NIEVE"),
                valore = quotaNeve(ora?.freezingLevel)?.let { "~" + altezza(it, unita) } ?: "--",
                palette = palette,
            )
        }

        Text(
            text = tr(
                "Quota neve stimata: zero termico meno circa 300 m. In montagna valgono i bollettini valanghe.",
                "Estimated snow line: freezing level minus about 300 m. In the mountains, avalanche bulletins come first.",
                "Geschätzte Schneefallgrenze: Nullgradgrenze minus etwa 300 m. In den Bergen gelten die Lawinenlageberichte.",
                "Limite de la neige estimée : isotherme 0 °C moins environ 300 m. En montagne, les bulletins d'avalanches font foi.",
                "Cota de nieve estimada: isoterma 0 °C menos unos 300 m. En montaña mandan los boletines de aludes.",
            ),
            style = SalaType.rowNote,
            color = palette.inkSoft,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun fraseGiorni(n: Int, su: Int): String = tr(
    "Neve in $n ${if (n == 1) "giorno" else "giorni"} su $su,",
    "Snow on $n ${if (n == 1) "day" else "days"} out of $su,",
    "Schnee an $n von $su ${if (n == 1) "Tag" else "Tagen"},",
    "Neige $n ${if (n == 1) "jour" else "jours"} sur $su,",
    "Nieve $n ${if (n == 1) "día" else "días"} de $su,",
)

private fun fraseQuota(quota: Int, unita: TempUnit): String = tr(
    "fino a circa ${altezza(quota, unita)}.",
    "down to about ${altezza(quota, unita)}.",
    "bis etwa ${altezza(quota, unita)}.",
    "jusqu'à environ ${altezza(quota, unita)}.",
    "hasta unos ${altezza(quota, unita)}.",
)

/**
 * Il rimando che la scheda della pioggia mostra quando la neve e' in arrivo:
 * i centimetri attesi e dove guardare. Nullo senza neve prevista.
 */
internal fun rimandoNeve(previsione: Forecast?): String? {
    val giorni = previsione?.let(::nevePrevista).orEmpty()
    if (giorni.isEmpty()) return null
    val c = cm(giorni.sumOf { it.cm })
    return tr(
        "Neve in arrivo: $c cm, vedi La neve",
        "Snow coming: $c cm, see Snow",
        "Schnee kommt: $c cm, siehe Schnee",
        "Neige à venir : $c cm, voir La neige",
        "Llega nieve: $c cm, ver La nieve",
    )
}

private const val GIORNI_DI_NEVE_IN_FILA = 7

@Composable
private fun androidx.compose.foundation.layout.RowScope.ColonnaNeve(g: NeveDelGiorno, massimo: Double, unita: TempUnit, palette: SalaPalette, conQuota: Boolean, ritardo: Int) {
    val crescita = crescitaAllArrivo(ritardo)
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(misuraCheArriva((6f + 34f * (g.cm / massimo).coerceIn(0.0, 1.0).toFloat()).dp) * crescita)
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
