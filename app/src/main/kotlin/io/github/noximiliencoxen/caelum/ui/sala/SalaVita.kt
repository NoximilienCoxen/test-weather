package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

/**
 * Quello che si muove in Sala I: precipitazioni, uccelli, stelle.
 *
 * **L'orologio e' esplicito, non una comodita' della libreria.** Il progetto ha
 * gia' pagato questa lezione (trappola #17): `rememberInfiniteTransition` letto
 * solo dentro il disegno non anima affatto, e le gocce del vecchio feed
 * sembravano cadere mentre erano ferme - misurato, zero fotogrammi. Qui il
 * tempo lo batte un `withFrameNanos` dentro un `LaunchedEffect`, e ogni battito
 * scrive un numero che il disegno legge.
 *
 * **E si ferma davvero.** La trappola #8 vuole zero fotogrammi da fermi, e
 * l'orologio gira solo quando c'e' qualcosa da muovere **e** la sala e' quella
 * in vista: sfogliando alla settimana, la pioggia di Sala I smette di costare.
 * Lasciarlo acceso sotto le altre sei sale sarebbe stata la versione moderna
 * dello stesso difetto.
 */
@Composable
fun rememberTempoScena(attivo: Boolean): () -> Float {
    val tempo = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(attivo) {
        if (!attivo) return@LaunchedEffect
        var inizio = 0L
        while (true) {
            withFrameNanos { ora ->
                if (inizio == 0L) inizio = ora
                tempo.floatValue = (ora - inizio) / 1_000_000_000f
            }
        }
    }
    return { tempo.floatValue }
}

private const val PI_F = PI.toFloat()

/**
 * Un uccello: la corsia in cui vola, la fase, quanto e' veloce, quanto grande.
 *
 * Tre e non uno stormo: uno solo si legge come un difetto del disegno, dieci
 * come un'invasione. Tre a distanze diverse dicono "cielo aperto" e basta.
 * Sono gli stessi tre del vecchio feed - la taratura era gia' giusta.
 */
private class Uccello(val corsia: Float, val fase: Float, val passo: Float, val taglia: Float)

private val Uccelli = listOf(
    Uccello(corsia = -0.62f, fase = 0.00f, passo = 0.055f, taglia = 0.105f),
    Uccello(corsia = -0.46f, fase = 0.38f, passo = 0.043f, taglia = 0.078f),
    Uccello(corsia = -0.74f, fase = 0.68f, passo = 0.068f, taglia = 0.062f),
)

/**
 * Gli uccelli del sereno: due archi che si toccano.
 *
 * E' la sagoma con cui chiunque disegna un uccello lontano, e a questa
 * dimensione qualunque tentativo di fare di piu' diventa una macchia. Quello
 * che li rende vivi non e' la forma: e' che **le ali battono** e che ognuno
 * attraversa con un passo suo. Attraversano e basta, entrando e uscendo in
 * dissolvenza: farli girare in tondo li legherebbe a un centro, e un uccello
 * che orbita attorno alla temperatura e' un carillon, non un cielo.
 */
fun DrawScope.uccelli(unita: Float, origine: Offset, tempo: Float, inchiostro: Color) {
    Uccelli.forEach { u ->
        val attraverso = (tempo * u.passo + u.fase) % 1f
        val x = origine.x + (attraverso * 2.6f - 1.3f) * unita
        val ondeggio = sin(tempo * 0.9f + u.fase * PI_F * 2f) * unita * 0.02f
        val y = origine.y + u.corsia * unita + ondeggio
        val bordo = (attraverso / 0.12f).coerceAtMost(1f) *
            ((1f - attraverso) / 0.12f).coerceAtMost(1f)
        val velo = bordo * 0.5f
        if (velo <= 0.01f) return@forEach

        // Il battito: le punte salgono e scendono attorno al corpo. E' l'unica
        // cosa che distingue un uccello che vola da un accento circonflesso.
        val battito = sin(tempo * 5.2f + u.fase * PI_F * 2f)
        val w = unita * u.taglia
        val alzata = w * 0.44f * battito
        val ala = Path().apply {
            moveTo(x - w, y - alzata)
            quadraticTo(x - w * 0.45f, y + w * 0.18f, x, y)
            quadraticTo(x + w * 0.45f, y + w * 0.18f, x + w, y - alzata)
        }
        drawPath(
            path = ala,
            color = inchiostro.copy(alpha = velo),
            style = Stroke(width = (w * 0.15f).coerceAtLeast(2.2f), cap = StrokeCap.Round),
        )
    }
}

/** Le stelle: posizioni fisse, in unita' dell'oggetto. Sempre le stesse notti. */
private val Stelle = listOf(
    floatArrayOf(-0.86f, -0.62f, 1.0f), floatArrayOf(-0.54f, -0.88f, 0.7f),
    floatArrayOf(-0.18f, -0.70f, 0.5f), floatArrayOf(0.22f, -0.92f, 0.9f),
    floatArrayOf(0.58f, -0.66f, 0.6f), floatArrayOf(0.88f, -0.84f, 0.8f),
    floatArrayOf(-0.72f, -0.28f, 0.5f), floatArrayOf(0.74f, -0.32f, 0.6f),
    floatArrayOf(-0.34f, -0.44f, 0.4f), floatArrayOf(0.40f, -0.50f, 0.45f),
)

/**
 * Le stelle e, ogni tanto, una che cade.
 *
 * **Non pulsano tutte insieme.** Ognuna ha una fase sua ricavata dalla propria
 * posizione, cosi' il cielo respira invece di lampeggiare: un tremolio
 * sincronizzato si legge come un difetto dello schermo, non come un cielo.
 *
 * La stella cadente passa a intervalli lunghi e irregolari. Se ne passasse una
 * ogni due secondi non sarebbe piu' un avvenimento, e un avvenimento che si
 * ripete smette di essere guardato.
 */
fun DrawScope.stelle(unita: Float, origine: Offset, tempo: Float, inchiostro: Color, velo: Float) {
    if (velo <= 0.01f) return
    Stelle.forEach { s ->
        val fase = (s[0] * 7.3f + s[1] * 11.7f)
        val tremolio = 0.62f + 0.38f * sin(tempo * 1.6f + fase)
        drawCircle(
            color = inchiostro.copy(alpha = (velo * s[2] * tremolio).coerceIn(0f, 1f)),
            radius = unita * 0.012f * (0.6f + s[2] * 0.6f),
            center = Offset(origine.x + s[0] * unita, origine.y + s[1] * unita),
        )
    }

    // Una ogni dodici secondi circa, e dura mezzo secondo: il resto del tempo
    // il cielo sta fermo, che e' quello che fa un cielo.
    val ciclo = 12f
    val dentro = tempo % ciclo
    if (dentro < 0.55f) {
        val t = dentro / 0.55f
        val quale = (tempo / ciclo).toInt()
        val partenzaX = -0.8f + ((quale * 0.37f) % 1f) * 1.4f
        val partenzaY = -0.95f + ((quale * 0.61f) % 1f) * 0.35f
        val lunghezza = unita * 0.34f
        val cx = origine.x + partenzaX * unita + t * lunghezza * 1.6f
        val cy = origine.y + partenzaY * unita + t * lunghezza * 0.55f
        val svanire = sin(t * PI_F)
        drawLine(
            color = inchiostro.copy(alpha = (velo * svanire * 0.9f).coerceIn(0f, 1f)),
            start = Offset(cx, cy),
            end = Offset(cx - lunghezza * 0.5f, cy - lunghezza * 0.17f),
            strokeWidth = unita * 0.008f,
            cap = StrokeCap.Round,
        )
    }
}

/** Di che natura e' cio' che cade. */
enum class Caduta { PIOGGIA, NEVE, GRANDINE }

/**
 * Cosa cade, e come.
 *
 * Le tre cadute non sono la stessa cosa a velocita' diverse, ed e' per questo
 * che sono tre rami e non un parametro:
 *
 * - la **pioggia** e' un tratto, e cade dritta e in fretta;
 * - la **neve** e' un fiocco che **deriva** di lato mentre scende, e senza
 *   quella deriva sembra pioggia bianca;
 * - la **grandine** e' piu' piccola, piu' veloce e **non** deriva: il vento non
 *   la sposta, per questo fa male.
 */
fun DrawScope.caduta(
    tipo: Caduta,
    acquerello: Acquerello,
    unita: Float,
    origine: Offset,
    tempo: Float,
    tinta: Color,
    quanti: Int,
) {
    val altezzaCorsa = unita * 1.15f
    val cima = origine.y + unita * 0.18f
    for (i in 0 until quanti) {
        val fx = (i + 0.5f) / quanti
        val x0 = origine.x + (fx - 0.5f) * 1.25f * unita
        val sfasatura = ((i * 0.37f) % 1f)
        val velocita = when (tipo) {
            Caduta.PIOGGIA -> 0.55f
            Caduta.NEVE -> 0.16f
            Caduta.GRANDINE -> 0.85f
        }
        val avanzamento = (tempo * velocita + sfasatura) % 1f
        val y = cima + avanzamento * altezzaCorsa
        // In coda e in testa alla corsa si entra e si esce in dissolvenza, se no
        // le gocce comparirebbero dal nulla su una riga netta.
        val velo = (avanzamento / 0.12f).coerceAtMost(1f) * ((1f - avanzamento) / 0.18f).coerceAtMost(1f)
        if (velo <= 0.02f) continue

        when (tipo) {
            Caduta.PIOGGIA -> {
                val larghezza = unita * 0.10f
                val altezza = unita * 0.30f
                timbra(
                    timbro = acquerello.pennellate[i % acquerello.pennellate.size],
                    centro = Offset(x0, y),
                    larghezza = larghezza,
                    altezza = altezza,
                    tinta = tinta,
                    alfa = 0.70f * velo,
                )
            }
            Caduta.NEVE -> {
                // La deriva: un seno lungo, diverso per fiocco. E' l'unica cosa
                // che distingue la neve dalla pioggia bianca.
                val deriva = sin(tempo * 0.7f + i * 1.9f) * unita * 0.10f
                val r = unita * (0.016f + 0.010f * ((i % 3) / 2f))
                drawCircle(
                    color = tinta.copy(alpha = (0.75f * velo).coerceIn(0f, 1f)),
                    radius = r,
                    center = Offset(x0 + deriva, y),
                )
            }
            Caduta.GRANDINE -> {
                val r = unita * 0.013f
                drawCircle(
                    color = tinta.copy(alpha = (0.85f * velo).coerceIn(0f, 1f)),
                    radius = r,
                    center = Offset(x0, y),
                )
                // Il chicco ha un lato illuminato: e' ghiaccio, non una goccia.
                drawCircle(
                    color = Color.White.copy(alpha = (0.5f * velo).coerceIn(0f, 1f)),
                    radius = r * 0.42f,
                    center = Offset(x0 - r * 0.3f, y - r * 0.3f),
                )
            }
        }
    }
}

/** Il fulmine del temporale: compare a scatti, e per pochissimo. */
fun DrawScope.fulmine(unita: Float, origine: Offset, tempo: Float, tinta: Color) {
    // Due lampi ravvicinati ogni sette secondi: un temporale non lampeggia a
    // ritmo, e due vicini si leggono come uno vero meglio di uno regolare.
    val dentro = tempo % 7f
    val acceso = dentro < 0.10f || (dentro > 0.22f && dentro < 0.28f)
    if (!acceso) return
    val x = origine.x + unita * 0.10f
    val y = origine.y + unita * 0.20f
    val h = unita * 0.42f
    val saetta = Path().apply {
        moveTo(x, y)
        lineTo(x - h * 0.22f, y + h * 0.46f)
        lineTo(x + h * 0.04f, y + h * 0.46f)
        lineTo(x - h * 0.10f, y + h)
        lineTo(x + h * 0.30f, y + h * 0.38f)
        lineTo(x + h * 0.04f, y + h * 0.38f)
        close()
    }
    drawPath(path = saetta, color = tinta.copy(alpha = 0.92f))
}

/** Il riverbero del lampo sulla carta: un alone largo e brevissimo. */
fun DrawScope.riverbero(tempo: Float, tinta: Color) {
    val dentro = tempo % 7f
    val forza = when {
        dentro < 0.10f -> 1f - dentro / 0.10f
        dentro > 0.22f && dentro < 0.28f -> 1f - (dentro - 0.22f) / 0.06f
        else -> 0f
    }
    if (forza <= 0.01f) return
    drawRect(color = tinta.copy(alpha = 0.10f * forza), size = Size(size.width, size.height))
}
