package io.github.noximiliencoxen.caelum.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp

/** Sole e mare: il sole basso, le onde che scorrono, una vela lontana. Di notte la luna e la sua scia. */
internal fun DrawScope.scenaMare(t: Float, notte: Boolean) {
    val orizzonte = H * 0.58f
    if (notte) {
        cielo(Scena.MARE, notte, orizzonte)
        stelle(t, orizzonte * 0.9f)
        val c = Offset(W * 0.68f, orizzonte * 0.42f)
        luna(c, W * 0.07f, Colori.cieloNotteAlto)
    } else {
        cielo(Scena.MARE, notte, orizzonte)
        sole(Offset(W * 0.62f, orizzonte * 0.62f), W * 0.11f)
        nuvola(Offset(W * 0.2f + oscilla(t, 40f) * W * 0.04f, orizzonte * 0.32f), W * 0.26f, Colori.crema.copy(alpha = 0.9f))
    }
    val mare = if (notte) Colori.mareNotte else Colori.mare
    val scuro = if (notte) Colori.mareNotteScuro else Colori.mareScuro
    onda(orizzonte, H * 0.004f, W * 0.3f, t * 0.8f, mare)

    // La scia di luce sull'acqua: trattini che tremolano sotto l'astro.
    val xScia = if (notte) W * 0.68f else W * 0.62f
    val scia = if (notte) Colori.luna else Colori.alone
    for (i in 0 until 14) {
        val y = orizzonte + H * 0.012f + i * H * 0.018f
        val larga = W * (0.03f + i * 0.012f) * (0.7f + 0.3f * oscilla(t, 1.6f, i.toFloat()))
        drawLine(scia.copy(alpha = 0.55f - i * 0.03f), Offset(xScia - larga, y), Offset(xScia + larga, y), strokeWidth = 2.2f, cap = StrokeCap.Round)
    }

    onda(orizzonte + H * 0.12f, H * 0.012f, W * 0.45f, t * 1.3f, lerp(mare, scuro, 0.5f))
    onda(orizzonte + H * 0.22f, H * 0.016f, W * 0.55f, -t * 1.1f + 1f, scuro)

    // La vela: un triangolo che dondola sull'orizzonte.
    val barca = Offset(W * 0.22f + oscilla(t, 30f) * W * 0.03f, orizzonte + H * 0.01f)
    rotate(oscilla(t, 3.2f) * 4f, barca) {
        val vela = Path().apply {
            moveTo(barca.x, barca.y - H * 0.07f)
            lineTo(barca.x, barca.y - H * 0.008f)
            lineTo(barca.x + W * 0.045f, barca.y - H * 0.008f)
            close()
        }
        drawPath(vela, if (notte) Color(0xFFB9BEC8) else Color.White)
        drawRect(if (notte) Colori.inchiostro else Colori.terracotta, Offset(barca.x - W * 0.025f, barca.y - H * 0.006f), Size(W * 0.08f, H * 0.012f))
    }
}

/** La nave nella tempesta: onde alte, pioggia di traverso, i lampi. */
internal fun DrawScope.scenaTempesta(t: Float, notte: Boolean) {
    val orizzonte = H * 0.52f
    val flash = lampo(t)
    val (alto, basso) = cieloDi(Scena.TEMPESTA, notte)
    cielo(lerp(alto, Color(0xFFDDE4EE), flash * 0.55f), lerp(basso, Color(0xFFDDE4EE), flash * 0.4f), orizzonte)
    // Le nuvole basse e cariche, che corrono.
    for (i in 0 until 5) {
        val x = ((caso(i, 30) * W + t * W * 0.03f * (1 + i % 2)) % (W * 1.4f)) - W * 0.2f
        nuvola(Offset(x, orizzonte * (0.25f + caso(i, 31) * 0.35f)), W * (0.35f + caso(i, 32) * 0.2f),
            lerp(if (notte) Color(0xFF232A36) else Color(0xFF5A636F), Color.White, flash * 0.3f))
    }
    saetta(t, flash)

    val mare = if (notte) Color(0xFF16202E) else Color(0xFF34506A)
    val scuro = if (notte) Color(0xFF0E1622) else Color(0xFF263D52)
    onda(orizzonte, H * 0.025f, W * 0.35f, t * 1.6f, lerp(mare, Color.White, flash * 0.2f))

    // La nave: scafo, cabina, albero; beccheggia con l'onda.
    val nave = Offset(W * 0.5f, orizzonte + H * 0.05f + oscilla(t, 2.4f) * H * 0.02f)
    rotate(oscilla(t, 2.4f, 1f) * 9f, nave) {
        val scafo = Path().apply {
            moveTo(nave.x - W * 0.2f, nave.y - H * 0.03f)
            lineTo(nave.x + W * 0.22f, nave.y - H * 0.03f)
            lineTo(nave.x + W * 0.16f, nave.y + H * 0.02f)
            lineTo(nave.x - W * 0.15f, nave.y + H * 0.02f)
            close()
        }
        drawPath(scafo, Color(0xFF8C3A20))
        drawRect(Color(0xFFE9E2D4), Offset(nave.x - W * 0.07f, nave.y - H * 0.075f), Size(W * 0.14f, H * 0.045f))
        for (k in 0 until 3) {
            drawRect(if (notte) Colori.luceCalda else Color(0xFF6A7682), Offset(nave.x - W * 0.055f + k * W * 0.04f, nave.y - H * 0.065f), Size(W * 0.022f, H * 0.014f))
        }
        drawLine(Colori.inchiostro, Offset(nave.x + W * 0.1f, nave.y - H * 0.03f), Offset(nave.x + W * 0.1f, nave.y - H * 0.13f), strokeWidth = 3f)
        drawCircle(if (notte) Color(0xFFFF6B4A) else Color(0xFFB2622D), 4f, Offset(nave.x + W * 0.1f, nave.y - H * 0.13f))
    }

    onda(orizzonte + H * 0.14f, H * 0.04f, W * 0.4f, -t * 1.9f, lerp(mare, scuro, 0.5f))
    onda(orizzonte + H * 0.26f, H * 0.05f, W * 0.5f, t * 2.1f + 2f, scuro)
    // La cresta bianca delle onde davanti.
    for (i in 0 until 9) {
        val x = ((caso(i, 33) * W + t * W * 0.08f) % W)
        drawCircle(Color.White.copy(alpha = 0.35f), W * 0.012f, Offset(x, orizzonte + H * 0.22f + oscilla(t, 1.2f, i.toFloat()) * H * 0.02f))
    }
    pioggia(t, 110, Color(0xFFC9D6E3), pendenza = 0.35f, lunga = 0.05f)
}
