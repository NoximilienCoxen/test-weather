package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Il glifo del tempo: sole, nuvola, pioggia, neve, grandine, temporale.
 *
 * **La prima stesura era un cerchio, un'ellisse e tre trattini**, e a trenta
 * punti di lato non si distingueva una nuvola da una pioggia: le stesse due
 * forme con qualche riga in piu'. In una striscia di otto giorni il glifo e'
 * l'unica cosa che dice che tempo fara', quindi o si riconosce a colpo
 * d'occhio o tanto vale togliere la striscia.
 *
 * Adesso la nuvola e' una **sagoma a tre lobi con la base piatta** - quella con
 * cui chiunque disegna una nuvola - il sole ha i raggi, e cio' che cade ha una
 * forma sua: tratti inclinati per la pioggia, pallini per la grandine, la
 * saetta per il temporale.
 *
 * Disegnato e non importato, come le tre linee del menu: un'app che si disegna
 * le lune a mano non apre una libreria di icone per sei simboli.
 */
fun DrawScope.weatherGlyph(condition: SalaCondition, buio: Float) {
    val w = size.width
    val h = size.height
    val coperto = condition != SalaCondition.SERENO
    val temporale = condition == SalaCondition.TEMPORALE ||
        condition == SalaCondition.TEMPORALE_GRANDINE

    // **A colori, e non piu' in un inchiostro solo.** Erano sei sagome tutte del
    // medesimo turchese, e in una striscia di otto giorni si distinguevano solo
    // guardandole una per una: il sole e la nuvola avevano la stessa voce. Il
    // colore fa il lavoro che la forma da sola faceva a fatica - il giallo si
    // legge come sole prima ancora che l'occhio riconosca i raggi.
    //
    // Le tinte si interpolano sul buio della carta, come tutto il resto della
    // galleria: su carta scura le stesse valgono un passo piu' chiare, se no
    // affogano.
    val giallo = SalaTokens.processYellow
    val nube = lerp(SalaTokens.accent, SalaTokens.accent400, buio)
    val acqua = lerp(SalaTokens.accent700, SalaTokens.accent300, buio)
    val ghiaccio = lerp(SalaTokens.accent400, SalaTokens.accent200, buio)

    // ── Il sole ──────────────────────────────────────────────────────────────
    // Con la nuvola il sole si ritira in alto a sinistra e perde i raggi:
    // spunta da dietro, non illumina la scena.
    val cx = if (coperto) w * 0.34f else w * 0.50f
    val cy = if (coperto) h * 0.30f else h * 0.46f
    val r = if (coperto) w * 0.17f else w * 0.22f
    drawCircle(color = giallo.copy(alpha = if (coperto) 0.85f else 1f), radius = r, center = Offset(cx, cy))
    if (!coperto) {
        val lungo = w * 0.095f
        for (i in 0 until 8) {
            val a = (i * 2.0 * PI / 8.0).toFloat()
            val da = Offset(cx + cos(a) * r * 1.45f, cy + sin(a) * r * 1.45f)
            val a2 = Offset(cx + cos(a) * (r * 1.45f + lungo), cy + sin(a) * (r * 1.45f + lungo))
            drawLine(giallo, da, a2, strokeWidth = w * 0.042f, cap = StrokeCap.Round)
        }
        return
    }

    // ── La nuvola: tre gobbe e la base piatta ────────────────────────────────
    //
    // **Unione di sottotracciati, non una catena di archi.** La prima stesura
    // incatenava tre `arcTo` con angoli di spazzata indovinati a mano, e usciva
    // una macchia con la coda: gli angoli di un arco su un rettangolo non sono
    // intuitivi, e a occhio si sbagliano. Tre ovali piu' un rettangolo dentro
    // **un solo** `Path` si uniscono da soli col riempimento non-zero, e la
    // forma e' esattamente quella che si immagina guardando i numeri.
    val baseY = h * 0.64f
    val nuvola = Path().apply {
        addOval(Rect(w * 0.10f, baseY - w * 0.26f, w * 0.48f, baseY + w * 0.06f))
        addOval(Rect(w * 0.30f, baseY - w * 0.42f, w * 0.74f, baseY + w * 0.04f))
        addOval(Rect(w * 0.56f, baseY - w * 0.28f, w * 0.92f, baseY + w * 0.06f))
        addRect(Rect(w * 0.18f, baseY - w * 0.12f, w * 0.84f, baseY))
    }
    drawPath(nuvola, color = if (temporale) SalaTokens.accent900.copy(alpha = 0.92f) else nube)

    // ── Cio' che cade ────────────────────────────────────────────────────────
    val cima = baseY + h * 0.05f
    when (condition) {
        SalaCondition.PIOGGIA -> listOf(0.32f, 0.52f, 0.72f).forEach { fx ->
            drawLine(
                color = acqua,
                start = Offset(w * fx, cima),
                end = Offset(w * (fx - 0.07f), cima + h * 0.24f),
                strokeWidth = w * 0.045f,
                cap = StrokeCap.Round,
            )
        }
        SalaCondition.GRANDINE, SalaCondition.TEMPORALE_GRANDINE ->
            listOf(0.30f to 0.07f, 0.74f to 0.07f).forEach { (fx, dy) ->
                drawCircle(
                    color = ghiaccio,
                    radius = w * 0.055f,
                    center = Offset(w * fx, cima + h * dy),
                )
            }
        else -> Unit
    }

    if (temporale) {
        val x = w * 0.46f
        val saetta = Path().apply {
            moveTo(x + w * 0.10f, cima)
            lineTo(x - w * 0.06f, cima + h * 0.17f)
            lineTo(x + w * 0.04f, cima + h * 0.17f)
            lineTo(x - w * 0.02f, cima + h * 0.32f)
            lineTo(x + w * 0.18f, cima + h * 0.12f)
            lineTo(x + w * 0.07f, cima + h * 0.12f)
            close()
        }
        drawPath(saetta, color = SalaTokens.accent2)
    }
}

/** Un cerchietto vuoto: il segno del giorno scelto sul grafico. */
fun DrawScope.anello(centro: Offset, raggio: Float, colore: Color, spessore: Float) {
    drawCircle(color = colore, radius = raggio, center = centro, style = Stroke(width = spessore))
}
