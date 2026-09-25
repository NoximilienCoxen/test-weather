package io.github.noximiliencoxen.caelum.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp

/** Il campo di grano col vento, e un mulino che gira. Di notte le lucciole. */
internal fun DrawScope.scenaGrano(t: Float, notte: Boolean) {
    val orizzonte = H * 0.55f
    if (notte) {
        cielo(Scena.GRANO, notte, orizzonte)
        stelle(t, orizzonte)
        luna(Offset(W * 0.22f, orizzonte * 0.35f), W * 0.055f, Colori.cieloNotteAlto, falce = true)
    } else {
        cielo(Scena.GRANO, notte, orizzonte)
        sole(Offset(W * 0.24f, orizzonte * 0.4f), W * 0.08f)
    }
    collina(orizzonte, orizzonte - H * 0.05f, W * 0.7f, orizzonte + H * 0.01f, if (notte) Colori.verdeNotte else Colori.verde)

    // Il mulino sulla collina: torre e quattro pale che girano.
    val mozzo = Offset(W * 0.7f, orizzonte - H * 0.16f)
    val torre = Path().apply {
        moveTo(mozzo.x - W * 0.035f, orizzonte - H * 0.04f)
        lineTo(mozzo.x - W * 0.018f, mozzo.y)
        lineTo(mozzo.x + W * 0.018f, mozzo.y)
        lineTo(mozzo.x + W * 0.035f, orizzonte - H * 0.04f)
        close()
    }
    drawPath(torre, if (notte) Color(0xFF3A3A44) else Color(0xFFE9E2D4))
    rotate(t * 40f, mozzo) {
        for (k in 0 until 4) {
            rotate(k * 90f, mozzo) {
                drawRect(if (notte) Color(0xFF55555F) else Colori.terracotta, mozzo + Offset(-W * 0.008f, -H * 0.11f), Size(W * 0.016f, H * 0.11f))
            }
        }
    }
    drawCircle(Colori.inchiostro, W * 0.008f, mozzo)

    // Il grano: una distesa di spighe che si piegano col vento a onde.
    val campo = if (notte) Color(0xFF4A4330) else Colori.grano
    drawRect(campo, Offset(0f, orizzonte + H * 0.02f), Size(W, H))
    val file = 16
    for (r in 0 until file) {
        val y = orizzonte + H * 0.04f + r * (H - orizzonte) / file * 1.05f
        val alta = H * (0.02f + r * 0.004f)
        val colore = lerp(campo, if (notte) Color(0xFF2E2A1E) else Colori.granoScuro, (r % 3) * 0.3f)
        var x = -10f + (r % 2) * 6f
        while (x < W + 10f) {
            val piega = oscilla(t, 2.6f, x / W * 6f - r * 0.3f) * alta * 0.6f
            drawLine(colore, Offset(x, y), Offset(x + piega, y - alta), strokeWidth = 2f + r * 0.12f, cap = StrokeCap.Round)
            drawCircle(colore, 1.5f + r * 0.08f, Offset(x + piega, y - alta))
            x += 7f + r * 0.6f
        }
    }
    if (notte) {
        for (i in 0 until 26) {
            val x = caso(i, 70) * W + oscilla(t, 5f + caso(i, 71) * 4f, i.toFloat()) * W * 0.04f
            val y = orizzonte + H * 0.05f + caso(i, 72) * (H - orizzonte) * 0.8f + oscilla(t, 3f, i * 2f) * H * 0.02f
            val luce = (0.5f + 0.5f * oscilla(t, 1.4f + caso(i, 73), i.toFloat())).coerceAtLeast(0f)
            drawCircle(Color(0xFFE8F27A).copy(alpha = 0.25f * luce), 9f, Offset(x, y))
            drawCircle(Color(0xFFF4F9B0).copy(alpha = luce), 2.4f, Offset(x, y))
        }
    }
}

/** La montagna: la vetta innevata e il lago. Di notte stelle, luna piena e il suo riflesso. */
internal fun DrawScope.scenaMontagna(t: Float, notte: Boolean) {
    val lago = H * 0.66f
    if (notte) {
        cielo(Scena.MONTAGNA, notte, lago)
        stelle(t, lago * 0.8f, quante = 110)
        luna(Offset(W * 0.72f, lago * 0.22f), W * 0.06f, Color(0xFF0B1224))
    } else {
        cielo(Scena.MONTAGNA, notte, lago)
        sole(Offset(W * 0.2f, lago * 0.22f), W * 0.06f)
    }
    fun monte(sx: Float, cima: Offset, dx: Float, colore: Color, conNeve: Boolean) {
        val p = Path().apply {
            moveTo(sx, lago)
            lineTo(cima.x, cima.y)
            lineTo(dx, lago)
            close()
        }
        drawPath(p, colore)
        if (conNeve) {
            val f = 0.3f
            val neve = Path().apply {
                moveTo(cima.x, cima.y)
                lineTo(cima.x + (dx - cima.x) * f, cima.y + (lago - cima.y) * f)
                lineTo(cima.x + (dx - cima.x) * f * 0.5f, cima.y + (lago - cima.y) * f * 0.75f)
                lineTo(cima.x, cima.y + (lago - cima.y) * f * 1.05f)
                lineTo(cima.x - (cima.x - sx) * f * 0.55f, cima.y + (lago - cima.y) * f * 0.8f)
                lineTo(cima.x - (cima.x - sx) * f, cima.y + (lago - cima.y) * f)
                close()
            }
            drawPath(neve, if (notte) Color(0xFFC9D2E4) else Color.White)
        }
    }
    monte(-W * 0.2f, Offset(W * 0.22f, lago - H * 0.28f), W * 0.62f, if (notte) Color(0xFF2A3350) else Color(0xFF8B97A6), true)
    monte(W * 0.25f, Offset(W * 0.6f, lago - H * 0.42f), W * 1.1f, if (notte) Color(0xFF323C5C) else Color(0xFF6F7C8E), true)
    monte(W * 0.65f, Offset(W * 0.95f, lago - H * 0.22f), W * 1.3f, if (notte) Color(0xFF28304A) else Color(0xFF9AA5B2), true)
    // Il bosco ai piedi dei monti.
    for (i in 0 until 22) {
        val x = i * W / 21f
        val alto = H * (0.03f + caso(i, 80) * 0.03f)
        val p = Path().apply {
            moveTo(x - W * 0.02f, lago)
            lineTo(x, lago - alto)
            lineTo(x + W * 0.02f, lago)
            close()
        }
        drawPath(p, if (notte) Colori.verdeNotteScuro else Colori.verdeScuro)
    }
    // Il lago: acqua che riflette il cielo, e l'astro spezzato in strisce.
    drawRect(
        Brush.verticalGradient(
            if (notte) listOf(Color(0xFF1B2540), Color(0xFF0E1628)) else listOf(Color(0xFF9CC3D8), Color(0xFF5B8AA5)),
            startY = lago, endY = H,
        ),
        Offset(0f, lago), Size(W, H - lago),
    )
    val xAstro = if (notte) W * 0.72f else W * 0.2f
    for (i in 0 until 10) {
        val y = lago + H * 0.02f + i * H * 0.025f
        val larga = W * (0.05f - i * 0.003f) * (0.7f + 0.3f * oscilla(t, 1.8f, i.toFloat()))
        drawLine((if (notte) Colori.luna else Colori.alone).copy(alpha = 0.5f - i * 0.04f), Offset(xAstro - larga, y), Offset(xAstro + larga, y), strokeWidth = 2.5f, cap = StrokeCap.Round)
    }
    if (!notte) {
        // Due uccelli in volo, un tratto a V che batte le ali.
        for (k in 0 until 2) {
            val c = Offset((W * 0.45f + t * W * 0.02f + k * W * 0.08f) % W, lago * (0.3f + k * 0.06f))
            val ali = oscilla(t, 0.8f, k.toFloat()) * H * 0.008f
            drawLine(Colori.inchiostro, c, c + Offset(-W * 0.025f, -H * 0.01f + ali), strokeWidth = 2f, cap = StrokeCap.Round)
            drawLine(Colori.inchiostro, c, c + Offset(W * 0.025f, -H * 0.01f + ali), strokeWidth = 2f, cap = StrokeCap.Round)
        }
    }
}
