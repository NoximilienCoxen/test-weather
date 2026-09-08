package io.github.noximiliencoxen.caelum.ui.feed

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.noximiliencoxen.caelum.data.PrecipKind
import io.github.noximiliencoxen.caelum.ui.render3d.DROPS
import kotlin.math.roundToInt

/**
 * Cosa fa il tempo su una finestra: dietro il vetro e sopra il vetro.
 *
 * Due fenomeni diversi che si somigliano solo di nome. **Dietro** cade la
 * pioggia, e si vede come tratti brevi e veloci che attraversano l'apertura.
 * **Sopra**, le gocce si posano, restano, ingrassano, e a un certo punto
 * scivolano lasciando una scia - ed e' questa la cosa che dice "vetro" senza che
 * si debba disegnare il vetro.
 *
 * **Il vetro non e' un velo.** Non c'e' nessun rettangolo traslucido a tutta
 * larghezza sopra la scena, e non e' una dimenticanza: un solo velo traslucido
 * grande ha gia' portato un fotogramma di questa app da diciotto a trentasei
 * millisecondi, con il settanta per cento in ritardo. Il vetro si vede dalle
 * gocce, ed e' insieme la scelta piu' bella e l'unica che sta nel budget.
 *
 * Le posizioni vengono da [DROPS], il campo gia' seminato una volta sola: due
 * elenchi di gocce sarebbero due elenchi destinati a divergere. Qui `x` e `z`
 * diventano le due coordinate sul vetro, e la fase e la velocita' restano quelle.
 */

/**
 * La pioggia dietro il vetro.
 *
 * Tratti brevi, quasi verticali, che attraversano l'apertura dall'alto in basso.
 * L'eta' di ciascuno sta nella sua posizione, quindi fra un fotogramma e l'altro
 * non c'e' niente da ricordare - la stessa regola della pioggia della scultura.
 */
internal fun DrawScope.drawBehindGlass(
    bounds: Size,
    origin: Offset,
    progress: Float,
    wetness: Float,
    kind: PrecipKind,
    colour: Color,
) {
    if (wetness <= 0f) return
    // Almeno sei: **se il codice dice che piove, deve piovere**, anche quando i
    // millimetri di quell'ora sono zero.
    val count = (DROPS.size * wetness).roundToInt().coerceIn(6, DROPS.size)
    val snowy = kind == PrecipKind.SNOW
    val stroke = (bounds.width * 0.006f).coerceAtLeast(1.2f)

    for (i in 0 until count) {
        val drop = DROPS[i]
        val travel = (drop.phase + progress * drop.speed * if (snowy) 0.35f else 1f) % 1f
        val x = origin.x + (drop.x * 0.5f + 0.5f) * bounds.width
        val y = origin.y + travel * bounds.height

        if (snowy) {
            // Il fiocco ondeggia scendendo, se no cade come un sasso bianco.
            val sway = kotlin.math.sin((travel + drop.phase) * 12f) * bounds.width * 0.03f
            drawCircle(
                color = colour.copy(alpha = 0.85f),
                radius = stroke * (0.9f + drop.length * 6f),
                center = Offset(x + sway, y),
            )
        } else {
            drawLine(
                color = colour.copy(alpha = 0.55f),
                start = Offset(x, y - drop.length * bounds.height * 0.9f),
                end = Offset(x, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Le gocce **sul** vetro.
 *
 * Il ciclo di ciascuna ha due tempi. Prima sta ferma dove si e' posata e
 * ingrassa: e' la parte che fa leggere il vetro, perche' una goccia che scivola
 * subito e' pioggia, una che resta attaccata e' pioggia **su qualcosa**. Poi
 * cede e scende accelerando, e si lascia dietro una scia che si assottiglia.
 *
 * Niente stato per fotogramma: dove sta nel suo ciclo lo dice la posizione, e
 * quanto e' vecchia lo dice quanto e' scesa.
 */
internal fun DrawScope.drawOnGlass(
    bounds: Size,
    origin: Offset,
    progress: Float,
    wetness: Float,
    colour: Color,
) {
    if (wetness <= 0f) return
    val count = (DROPS.size * wetness * 0.7f).roundToInt().coerceIn(4, DROPS.size)
    val unit = bounds.width

    for (i in 0 until count) {
        val drop = DROPS[i]
        // Ogni goccia ha il suo ciclo, piu' lento di quello della pioggia: una
        // goccia sul vetro resta li' molto piu' a lungo di quanto ci metta a
        // cadere quella dietro.
        val cycle = (drop.phase + progress * GLASS_SPEED * drop.speed) % 1f
        val x = origin.x + (drop.x * 0.5f + 0.5f) * bounds.width
        val rest = origin.y + (drop.z * 0.5f + 0.5f) * bounds.height * 0.72f
        val radius = unit * (0.010f + drop.length * 0.10f)

        if (cycle < GLASS_HOLD) {
            // Ferma, e cresce. Compare sfumando: una goccia che appare gia'
            // grande si legge come uno sbaglio del disegno.
            val grow = cycle / GLASS_HOLD
            drawCircle(
                color = colour.copy(alpha = 0.34f * grow),
                radius = radius * (0.4f + 0.6f * grow),
                center = Offset(x, rest),
            )
            continue
        }

        // Ceduta: scende, e accelera. Il quadrato e' quello che la fa partire
        // piano e finire veloce, come fa una goccia vera quando si stacca.
        val slid = (cycle - GLASS_HOLD) / (1f - GLASS_HOLD)
        val fall = slid * slid * bounds.height * 1.15f
        val y = rest + fall
        val fade = (1f - slid * 0.35f).coerceIn(0f, 1f)
        if (y - radius > origin.y + bounds.height) continue

        // La scia: parte dal punto in cui si era posata e si assottiglia.
        drawLine(
            color = colour.copy(alpha = 0.16f * fade),
            start = Offset(x, rest),
            end = Offset(x, y),
            strokeWidth = radius * 0.7f,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = colour.copy(alpha = 0.40f * fade),
            radius = radius,
            center = Offset(x, y),
        )
        // Un accenno di luce in alto sulla goccia: e' cio' che la fa sembrare
        // bombata invece che un pallino.
        drawCircle(
            color = Color.White.copy(alpha = 0.22f * fade),
            radius = radius * 0.34f,
            center = Offset(x - radius * 0.3f, y - radius * 0.34f),
        )
    }
}

/**
 * Quanto forte piove, da 0 a 1.
 *
 * **Il codice decide se, i millimetri decidono quanto.** E' la trappola gia'
 * pagata: un temporale previsto all'ottanta per cento puo' avere zero millimetri
 * in quell'ora esatta, e sotto la scritta TEMPORALE deve comunque piovere. Da
 * qui il pavimento: bagnato vuol dire almeno un terzo.
 */
internal fun wetnessOf(wet: Boolean, mm: Double?): Float =
    if (!wet) 0f else ((mm ?: 0.0).toFloat() / 6f).coerceIn(0.34f, 1f)

/** Quanto a lungo una goccia resta attaccata prima di cedere. */
private const val GLASS_HOLD = 0.55f

/** Il ciclo delle gocce sul vetro, rispetto a quello della pioggia dietro. */
private const val GLASS_SPEED = 0.42f
