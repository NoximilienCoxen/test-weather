package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.common.MinTouchTarget
import io.github.noximiliencoxen.caelum.ui.home.MoonPhase
import io.github.noximiliencoxen.caelum.ui.home.MoonSegment
import io.github.noximiliencoxen.caelum.ui.render3d.Camera
import io.github.noximiliencoxen.caelum.ui.render3d.MOON_SEAS
import io.github.noximiliencoxen.caelum.ui.render3d.moon
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoomScaffold
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.giroConLancio
import io.github.noximiliencoxen.caelum.ui.sala.rememberGiro
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/**
 * Sala IV — La luna: la sfera vera dei widget e della scultura, non una
 * sagoma piatta. Trascinala in orizzontale per attraversare il mese; la fase
 * e' calcolata sulla data reale, non su un disegno fisso.
 */
@Composable
fun SalaLunaScreen(
    state: UiState,
    palette: SalaPalette,
    position: () -> Float,
    onPlaceClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    var offsetDays by remember { mutableIntStateOf(0) }
    val giroAnim = rememberGiro(state.forcedYawDeg)
    val today = LocalDate.now()
    val shownDate = today.plusDays(offsetDays.toLong())
    val phase = MoonPhase.at(shownDate)
    val illum = MoonPhase.illumination(phase)
    val segment = MoonSegment.of(phase)
    val age = MoonPhase.ageDays(phase)
    val nextFull = MoonPhase.nextDate(today, 0.5f)

    SalaRoomScaffold(
        palette = palette,
        room = SalaRoom.LUNA,
        placeName = state.place.name,
        position = position,
        onPlaceClick = onPlaceClick,
        onMenuClick = onMenuClick,
    ) { modifier ->
        Column(modifier = modifier) {
            // **Il dito adesso gira la luna, non sfoglia il mese.**
            // Erano due gesti sullo stesso asse e ne restava uno solo: il mese
            // e' passato ai due passi qui sotto, che lo dicono anche a chi non
            // prova a trascinare. Astronomicamente la Luna mostra sempre la
            // stessa faccia - i mari infatti stanno fermi rispetto a lei - e
            // farla girare e' una liberta': qui e' un oggetto in una sala, e
            // in una sala gli oggetti si guardano da tutti i lati.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .size(280.dp)
                    .giroConLancio(giroAnim),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(240.dp)) {
                    val minDim = minOf(size.width, size.height)
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)
                    val camera = Camera(yawDeg = giroAnim.gradi, pitchDeg = 0f, distance = minDim * 1.35f, origin = centerOffset)
                    val raggio = minDim * 0.42f

                    // **I colori sono della luna, non della pagina.** Prima
                    // qui passavano `palette.ink` come luce e una sua
                    // schiaritura come ombra: in tema scuro funzionava per
                    // combinazione - l'inchiostro **e'** quasi bianco li' - e in
                    // tema chiaro dava una parte illuminata quasi nera e un
                    // disco in ombra invisibile sulla carta. Vedi
                    // `SalaTokens.lunaLuce` per il perche' per esteso.
                    moon(
                        camera = camera,
                        x = 0f, y = 0f, z = 0f,
                        radius = raggio,
                        phase = phase,
                        light = SalaTokens.lunaLuce,
                        dark = SalaTokens.lunaOmbra,
                        alpha = 1f,
                        marks = MOON_SEAS,
                    )

                    // **Il filo di contorno.** Il disco in ombra si disegna a
                    // un quarto di opacita', perche' la parte non illuminata
                    // della Luna vera si intravede appena. Ma su carta chiara
                    // un quarto di ardesia e' ancora troppo poco per dire
                    // **dove finisce la sfera**, e senza il bordo una falce
                    // sottile galleggia senza corpo. Un filo sottile chiude la
                    // sagoma senza riempirla, e costa un tratto.
                    drawCircle(
                        color = SalaTokens.lunaOmbra.copy(alpha = 0.55f),
                        radius = raggio,
                        center = centerOffset,
                        style = Stroke(width = 1.dp.toPx()),
                    )

                    // **Il bordo che scurisce: e' questo che fa una sfera.**
                    // `moon` da' la fase giusta e i mari al posto giusto, ma
                    // riempie di tinta piatta, e una tinta piatta dentro un
                    // cerchio resta un cerchio - al novilunio si vedeva un
                    // disco grigio, non un corpo. Qui il pigmento si addensa
                    // verso il lembo, che e' come si legge la curvatura: piu'
                    // superficie per unita' di schermo dove la sfera fugge via.
                    //
                    // Il centro del degrade' e' spostato verso la luce, cosi'
                    // il lembo lontano e' piu' scuro del vicino invece che
                    // uniforme: e' l'ombreggiatura, non una vignettatura.
                    val versoLaLuce = Offset(-raggio * 0.30f, -raggio * 0.30f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.00f to Color.Transparent,
                            0.58f to Color.Transparent,
                            1.00f to SalaTokens.lunaOmbra.copy(alpha = 0.34f),
                            center = centerOffset + versoLaLuce,
                            radius = raggio * 1.45f,
                        ),
                        radius = raggio,
                        center = centerOffset,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PassoDelMese("‹", palette) { offsetDays = (offsetDays - 1).coerceAtLeast(-15) }
                    Text(
                        text = if (offsetDays == 0) "Stasera" else shownDate.format(DayMonth),
                        style = SalaType.sectionLabel,
                        color = palette.inkAccent,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    PassoDelMese("›", palette) { offsetDays = (offsetDays + 1).coerceAtMost(15) }
                }
                if (offsetDays != 0) {
                    TextButton(onClick = { offsetDays = 0 }) {
                        Text(text = "Torna a stasera", style = SalaType.sectionLabel, color = palette.inkAccent)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                Text(text = segment.label.lowercase().replaceFirstChar { it.uppercase() }, style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = "${if (offsetDays == 0) "Stasera" else shownDate.format(DayMonth)} · ${(illum * 100).roundToInt()} % illuminata · giorno ${age.toInt() + 1} del ciclo",
                    style = SalaType.sectionLabel,
                    color = palette.inkAccent,
                )
                Text(
                    text = if (offsetDays == 0) {
                        "Il disco qui sopra è la luna di questa notte, calcolata sulla data di oggi. Trascinala per girarla; i due passi qui sopra attraversano il mese."
                    } else {
                        "Stai guardando la luna del ${shownDate.format(DayMonth)}, a ${kotlin.math.abs(offsetDays)} giorni da oggi. Torna a stasera per rimetterla in pari con il cielo."
                    },
                    style = SalaType.body,
                    color = palette.ink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Stat("Fase", segment.label.lowercase().replaceFirstChar { it.uppercase() }, palette)
                    Stat("Illuminazione", "${(illum * 100).roundToInt()} %", palette)
                    Stat("Prossima piena", nextFull.format(DayMonth), palette)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, palette: SalaPalette) {
    Column {
        Text(text = label, style = SalaType.hourLabel, color = palette.inkSoft)
        Text(text = value, style = SalaType.value, color = palette.ink)
    }
}

/** Un passo avanti o indietro nel mese lunare. */
@Composable
private fun PassoDelMese(segno: String, palette: SalaPalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(MinTouchTarget).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = segno, style = SalaType.cardTitle, color = palette.inkAccent)
    }
}
