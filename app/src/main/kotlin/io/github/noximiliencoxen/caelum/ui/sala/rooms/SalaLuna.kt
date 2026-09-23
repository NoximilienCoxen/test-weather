package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.MoonPhase
import io.github.noximiliencoxen.caelum.data.MoonSegment
import io.github.noximiliencoxen.caelum.ui.sala.CellaValore
import io.github.noximiliencoxen.caelum.ui.sala.Didascalia
import io.github.noximiliencoxen.caelum.ui.sala.PannelloSala
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Sala IV — La luna.
 *
 * La fase e' **quella vera di stanotte**, la stessa che il cielo mostra sopra il
 * pannello: un unico valore le governa tutte e due, quindi le due immagini non
 * possono divergere.
 *
 * **Sorge e cala non ci sono.** Il prototipo li scriveva ("17:12", "04:38") ma
 * Open-Meteo, nei dati che questa applicazione chiede, non li porta: al loro
 * posto ci sono tre numeri veri - quanto e' illuminata, quanti giorni ha, quando
 * torna piena. Un orario inventato sarebbe stato piu' simile al disegno e falso.
 */
@Composable
fun SalaLunaScreen(
    palette: SalaPalette,
    /**
     * Il giorno mostrato, non l'oggi del telefono: scorrendo alla notte di
     * giovedi' anche le fasi che seguono partono da li'. Da questo si ricava
     * la fase con la stessa funzione che la ricava per il cielo, quindi le due
     * immagini non possono divergere: un ingresso, una funzione.
     */
    giorno: LocalDate,
    modifier: Modifier = Modifier,
) {
    val oggi = giorno
    val fase = MoonPhase.at(giorno)
    val illuminata = MoonPhase.illumination(fase)
    val segmento = MoonSegment.of(fase)
    val eta = MoonPhase.ageDays(fase)
    val prossimaPiena = MoonPhase.nextDate(oggi, 0.5f)

    PannelloSala(palette = palette, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Canvas(modifier = Modifier.size(96.dp)) { disegnaLuna(fase, 1f) }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "La luna", style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = "${segmento.label.lowercase().replaceFirstChar { it.uppercase() }} · " +
                        "${(illuminata * 100f).roundToInt()} %",
                    style = SalaType.rowTitle,
                    color = palette.accent,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Didascalia(
                    descrizione(segmento),
                    palette,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            CellaValore("ILLUMINATA", "${(illuminata * 100f).roundToInt()} %", palette)
            CellaValore("ETÀ", "${eta.roundToInt()} giorni", palette)
            CellaValore("PIENA", "${prossimaPiena.dayOfMonth} ${prossimaPiena.monthValue.mese()}", palette)
        }

        Text(
            text = "LE PROSSIME FASI",
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Le quattro tappe del ciclo, ognuna alla sua prossima data. Sono
            // quattro **calcoli**, non quattro date scritte a mano.
            listOf(
                0.25f to "Primo q.",
                0.5f to "Piena",
                0.75f to "Ultimo q.",
                0f to "Nuova",
            ).sortedBy { (obiettivo, _) -> MoonPhase.daysUntil(oggi, obiettivo) }
                .forEach { (obiettivo, nome) ->
                    val quando = MoonPhase.nextDate(oggi, obiettivo)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(palette.chip)
                            .padding(top = 12.dp, bottom = 10.dp, start = 4.dp, end = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Canvas(modifier = Modifier.size(26.dp)) { disegnaLuna(obiettivo, 1f) }
                        Text(
                            text = nome,
                            style = SalaType.giornoMax,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${quando.dayOfMonth} ${quando.monthValue.mese()}",
                            style = SalaType.giornoMin,
                            color = palette.inkFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
        }
    }
}

/**
 * Il disco lunare alla fase data.
 *
 * **Le tinte sono sue e fisse nei due temi**: un corpo celeste non ha il colore
 * dell'inchiostro della pagina che lo mostra. E' la lezione pagata quando la
 * parte illuminata veniva dipinta col nero del testo e al novilunio non restava
 * niente sullo schermo.
 */
private fun DrawScope.disegnaLuna(fase: Float, alpha: Float) {
    val r = size.minDimension / 2f
    val centro = Offset(size.width / 2f, size.height / 2f)

    drawCircle(color = SalaTokens.lunaOmbra.copy(alpha = 0.55f * alpha), radius = r, center = centro)

    val crescente = fase < 0.5f
    val terminatore = abs(cos(2.0 * PI * fase).toFloat())
    val gibbosa = ((1f - cos(2.0 * PI * fase).toFloat()) / 2f) > 0.5f
    val disco = Rect(centro.x - r, centro.y - r, centro.x + r, centro.y + r)
    val mediana = Rect(centro.x - r * terminatore, centro.y - r, centro.x + r * terminatore, centro.y + r)
    val illuminata = Path().apply {
        arcTo(disco, if (crescente) -90f else 90f, 180f, true)
        arcTo(mediana, if (crescente) 90f else -90f, if (gibbosa) 180f else -180f, false)
        close()
    }

    clipPath(illuminata) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to SalaTokens.lunaLuce,
                0.52f to SalaTokens.lunaMezzo,
                1f to SalaTokens.lunaBordo,
                center = Offset(centro.x - r * 0.32f, centro.y - r * 0.40f),
                radius = r * 1.5f,
            ),
            radius = r,
            center = centro,
            alpha = alpha,
        )
        listOf(
            Triple(-0.07f, -0.06f, 0.20f),
            Triple(0.24f, -0.23f, 0.14f),
            Triple(0.07f, 0.27f, 0.24f),
        ).forEach { (mx, my, md) ->
            drawOval(
                color = Color(0xFF463830).copy(alpha = 0.22f * alpha),
                topLeft = Offset(centro.x + mx * r - md * r, centro.y + my * r - md * r * 0.78f),
                size = Size(md * 2f * r, md * 1.56f * r),
            )
        }
    }

    drawCircle(
        color = SalaTokens.lunaBordo.copy(alpha = 0.30f * alpha),
        radius = r,
        center = centro,
        style = Stroke(width = r * 0.04f),
    )
}

private fun descrizione(segmento: MoonSegment): String = when (segmento) {
    MoonSegment.NOVILUNIO -> "Il disco è fra noi e il sole: stanotte il cielo resta al buio, ed è la notte giusta per le stelle deboli."
    MoonSegment.CRESCENTE -> "Una falce sottile a occidente, bassa e breve: cala poco dopo il sole."
    MoonSegment.PRIMO_QUARTO -> "Metà disco illuminato, alto a sud dopo il tramonto: cala attorno a mezzanotte."
    MoonSegment.GIBBOSA_CRESCENTE -> "Quasi piena e alta per gran parte della notte: illumina bene fino a notte fonda."
    MoonSegment.PLENILUNIO -> "Piena: sorge col tramonto e cala con l'alba, in cielo per tutta la notte."
    MoonSegment.GIBBOSA_CALANTE -> "Ancora larga ma in ritardo: sorge a notte già cominciata e resta fino al mattino."
    MoonSegment.ULTIMO_QUARTO -> "Metà disco, dall'altra parte: sorge a notte fonda e resta visibile di prima mattina."
    MoonSegment.CALANTE -> "Una falce che precede l'alba, bassa a oriente: l'ultima luce prima del novilunio."
}

private fun Int.mese(): String = when (this) {
    1 -> "gen"
    2 -> "feb"
    3 -> "mar"
    4 -> "apr"
    5 -> "mag"
    6 -> "giu"
    7 -> "lug"
    8 -> "ago"
    9 -> "set"
    10 -> "ott"
    11 -> "nov"
    else -> "dic"
}
