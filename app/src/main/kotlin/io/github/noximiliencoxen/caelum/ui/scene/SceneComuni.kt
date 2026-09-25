package io.github.noximiliencoxen.caelum.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/*
 * I pezzi che tutte le scene usano: cielo, astri, nuvole, cio' che cade.
 *
 * Tutto si misura sulla tela: `W` e `H` sono larghezza e altezza, e le scene
 * mettono l'orizzonte a una frazione dell'altezza, cosi' lo stesso quadretto
 * sta nel riquadro del benvenuto e a tutto schermo all'avvio.
 *
 * Il caso e' una formula (`caso`): la stessa scena e' la stessa a ogni
 * fotogramma e a ogni prova, e nessuna goccia nasce o muore.
 */

internal val DrawScope.W: Float get() = size.width
internal val DrawScope.H: Float get() = size.height

/** Un numero "a caso" fra 0 e 1, sempre lo stesso per la stessa coppia. */
internal fun caso(i: Int, k: Int = 0): Float {
    val x = sin(i * 12.9898 + k * 78.233) * 43758.5453
    return (x - floor(x)).toFloat()
}

/** Il cielo dall'alto all'orizzonte. */
internal fun DrawScope.cielo(alto: Color, basso: Color, fino: Float = H) {
    drawRect(Brush.verticalGradient(listOf(alto, basso), startY = 0f, endY = fino), size = Size(W, fino))
    // Sotto l'orizzonte il colore dell'orizzonte, non il vuoto: le onde e le
    // colline salgono e scendono, e nel vuoto fra loro e il cielo restava una
    // riga bianca.
    if (fino < H) drawRect(basso, Offset(0f, fino - 1f), Size(W, H - fino + 1f))
}

internal fun DrawScope.sole(centro: Offset, r: Float, colore: Color = Colori.sole, alone: Color = Colori.alone) {
    drawCircle(alone.copy(alpha = 0.35f), r * 1.7f, centro)
    drawCircle(alone.copy(alpha = 0.55f), r * 1.3f, centro)
    drawCircle(colore, r, centro)
}

/**
 * Luna piena o falce. La falce e' una differenza fra due dischi, non un disco
 * coperto da un altro del colore del cielo: coprendo, l'alone restava visibile
 * sotto il disco "invisibile" come un buco grigio.
 */
internal fun DrawScope.luna(centro: Offset, r: Float, @Suppress("UNUSED_PARAMETER") cielo: Color, falce: Boolean = false) {
    drawCircle(
        Brush.radialGradient(listOf(Colori.lunaAlone.copy(alpha = 0.22f), Colori.lunaAlone.copy(alpha = 0f)), centro, r * 2.4f),
        r * 2.4f,
        centro,
    )
    if (falce) {
        val disco = Path().apply { addOval(Rect(centro, r)) }
        val ombra = Path().apply { addOval(Rect(centro + Offset(r * 0.45f, -r * 0.2f), r * 0.95f)) }
        drawPath(Path.combine(PathOperation.Difference, disco, ombra), Colori.luna)
    } else {
        drawCircle(Colori.luna, r, centro)
        drawCircle(Colori.lunaMare, r * 0.22f, centro + Offset(-r * 0.25f, -r * 0.2f))
        drawCircle(Colori.lunaMare, r * 0.15f, centro + Offset(r * 0.28f, r * 0.12f))
    }
}

/** Stelle sparse sopra [fino], che respirano ognuna col suo passo. */
internal fun DrawScope.stelle(tempo: Float, fino: Float, quante: Int = 70, colore: Color = Colori.stella) {
    for (i in 0 until quante) {
        val x = caso(i, 1) * W
        val y = caso(i, 2) * caso(i, 2) * fino
        val luce = 0.35f + 0.65f * (0.5f + 0.5f * sin(tempo * (1.2f + caso(i, 3) * 2f) + i))
        drawCircle(colore.copy(alpha = luce * (0.4f + caso(i, 4) * 0.6f)), 1f + caso(i, 5) * 1.8f, Offset(x, y))
    }
}

/** Una nuvola a gobbe, larga [larga] con la base in [base]. */
internal fun DrawScope.nuvola(base: Offset, larga: Float, colore: Color) {
    val r = larga / 4f
    drawCircle(colore, r * 1.05f, base + Offset(-larga * 0.22f, -r * 0.55f))
    drawCircle(colore, r * 1.35f, base + Offset(larga * 0.04f, -r * 0.95f))
    drawCircle(colore, r * 0.95f, base + Offset(larga * 0.28f, -r * 0.5f))
    drawRoundRect(
        colore,
        topLeft = base + Offset(-larga / 2f, -r * 0.8f),
        size = Size(larga, r * 0.8f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.4f),
    )
}

/** Pioggia a tratti obliqui, [quante] gocce che cadono in un secondo circa. */
internal fun DrawScope.pioggia(tempo: Float, quante: Int, colore: Color, pendenza: Float = 0.18f, lunga: Float = 0.035f) {
    val l = H * lunga
    for (i in 0 until quante) {
        val velocita = 0.9f + caso(i, 7) * 0.5f
        val f = (tempo * velocita + caso(i, 8)) % 1f
        val y = f * (H + l) - l
        val x = (caso(i, 9) * (W + H * pendenza)) - y * pendenza
        drawLine(
            colore.copy(alpha = 0.35f + caso(i, 10) * 0.45f),
            Offset(x, y),
            Offset(x + l * pendenza, y + l),
            strokeWidth = 1.6f + caso(i, 11),
            cap = StrokeCap.Round,
        )
    }
}

/** Neve che scende lenta e ondeggia. */
internal fun DrawScope.neve(tempo: Float, quanti: Int, colore: Color = Color.White, fino: Float = H) {
    for (i in 0 until quanti) {
        val f = (tempo * (0.06f + caso(i, 12) * 0.08f) + caso(i, 13)) % 1f
        val y = f * fino
        val x = caso(i, 14) * W + sin(tempo * 1.3f + i) * W * 0.012f
        drawCircle(colore.copy(alpha = 0.6f + caso(i, 15) * 0.4f), 1.5f + caso(i, 16) * 2.6f, Offset(x, y))
    }
}

/** Il mare: una striscia ondulata che scorre, piena fino in fondo. */
internal fun DrawScope.onda(base: Float, ampiezza: Float, lunghezza: Float, fase: Float, colore: Color) {
    val p = Path().apply {
        moveTo(0f, H)
        var x = 0f
        lineTo(0f, base)
        while (x <= W + 8f) {
            lineTo(x, base + sin((x / lunghezza) * 2 * PI.toFloat() + fase) * ampiezza)
            x += 8f
        }
        lineTo(W, H)
        close()
    }
    drawPath(p, colore)
}

/** Il profilo di una collina morbida, chiuso fino in fondo. */
internal fun DrawScope.profiloCollina(sinistra: Float, cima: Float, xCima: Float, destra: Float): Path = Path().apply {
    moveTo(0f, H)
    lineTo(0f, sinistra)
    quadraticBezierTo(xCima * 0.5f, cima, xCima, cima)
    quadraticBezierTo(xCima + (W - xCima) * 0.5f, cima, W, destra)
    lineTo(W, H)
    close()
}

/** Una collina morbida fra due quote, piena fino in fondo. */
internal fun DrawScope.collina(sinistra: Float, cima: Float, xCima: Float, destra: Float, colore: Color) {
    drawPath(profiloCollina(sinistra, cima, xCima, destra), colore)
}

/**
 * Quanto e' acceso il lampo, da 0 a 1: un colpo ogni [periodo] secondi, due
 * guizzi ravvicinati e poi buio. E' il modo in cui un temporale si vede da
 * lontano: non una luce che sale, un fotogramma bianco.
 */
internal fun lampo(tempo: Float, periodo: Float = 4.2f): Float {
    val f = tempo % periodo
    return when {
        f < 0.06f -> 1f
        f < 0.12f -> 0.25f
        f < 0.18f -> 0.8f
        f < 0.45f -> (0.45f - f) / 0.27f * 0.3f
        else -> 0f
    }
}

/** La saetta: una linea spezzata dall'alto, diversa a ogni colpo. */
internal fun DrawScope.saetta(tempo: Float, forza: Float, periodo: Float = 4.2f) {
    if (forza <= 0.05f) return
    val colpo = (tempo / periodo).toInt()
    var p = Offset(W * (0.25f + caso(colpo, 20) * 0.5f), 0f)
    val passi = 7
    for (k in 1..passi) {
        val q = Offset(p.x + (caso(colpo * 13 + k, 21) - 0.5f) * W * 0.12f, H * 0.5f * k / passi)
        drawLine(Color.White.copy(alpha = forza), p, q, strokeWidth = 3.5f, cap = StrokeCap.Round)
        p = q
    }
}

/** Oscilla fra -1 e 1, lento. */
internal fun oscilla(tempo: Float, periodo: Float, sfasamento: Float = 0f): Float =
    sin((tempo / periodo) * 2 * PI.toFloat() + sfasamento)

/** La tavolozza delle scene: i toni di SalaTokens, piu' quelli che servono al paesaggio. */
internal object Colori {
    val sole = Color(0xFFD67F48)
    val alone = Color(0xFFF6A06B)
    val luna = Color(0xFFF9F4ED)
    val lunaMare = Color(0xFFE2D9C7)
    val lunaAlone = Color(0xFFF9F4ED)
    val stella = Color(0xFFF9F4ED)

    val cieloGiornoAlto = Color(0xFF9CC3D8)
    val cieloGiornoBasso = Color(0xFFF7DCC4)
    val cieloNotteAlto = Color(0xFF0E1628)
    val cieloNotteBasso = Color(0xFF2A3350)
    val cieloGrigioAlto = Color(0xFF7D8894)
    val cieloGrigioBasso = Color(0xFFB9BEC2)
    val cieloTempestaAlto = Color(0xFF2B313B)
    val cieloTempestaBasso = Color(0xFF4F5864)

    val mare = Color(0xFF5B8AA5)
    val mareScuro = Color(0xFF3E6A85)
    val mareChiaro = Color(0xFF7EA7BD)
    val mareNotte = Color(0xFF1C2A40)
    val mareNotteScuro = Color(0xFF142034)

    val verdeChiaro = Color(0xFFAEBF92)
    val verde = Color(0xFF8FA073)
    val verdeScuro = Color(0xFF728157)
    val verdeNotte = Color(0xFF2C3627)
    val verdeNotteScuro = Color(0xFF1F271B)

    val grano = Color(0xFFE2B866)
    val granoScuro = Color(0xFFC99A45)
    val terracotta = Color(0xFFB2622D)
    val crema = Color(0xFFF5EAD8)
    val inchiostro = Color(0xFF2E2B25)
    val luceCalda = Color(0xFFFFD27A)
    val neveOmbra = Color(0xFFDCE6F0)
}
