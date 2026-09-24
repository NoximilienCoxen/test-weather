package io.github.noximiliencoxen.caelum.ui.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp

/** Una casa: muro, tetto a capanna, finestre. Restituisce la cima del camino. */
internal fun DrawScope.casa(x: Float, base: Float, larga: Float, alta: Float, muro: Color, tetto: Color, finestra: Color, neve: Boolean): Offset {
    drawRect(muro, Offset(x, base - alta), Size(larga, alta))
    val colmo = base - alta - larga * 0.45f
    val p = Path().apply {
        moveTo(x - larga * 0.08f, base - alta)
        lineTo(x + larga / 2f, colmo)
        lineTo(x + larga * 1.08f, base - alta)
        close()
    }
    drawPath(p, tetto)
    if (neve) {
        val cappa = Path().apply {
            moveTo(x - larga * 0.08f, base - alta)
            lineTo(x + larga / 2f, colmo)
            lineTo(x + larga * 1.08f, base - alta)
            lineTo(x + larga * 0.95f, base - alta + larga * 0.05f)
            lineTo(x + larga / 2f, colmo + larga * 0.12f)
            lineTo(x + larga * 0.05f, base - alta + larga * 0.05f)
            close()
        }
        drawPath(cappa, Color.White)
    }
    val lato = larga * 0.22f
    drawRect(finestra, Offset(x + larga * 0.18f, base - alta * 0.72f), Size(lato, lato))
    drawRect(finestra, Offset(x + larga * 0.6f, base - alta * 0.72f), Size(lato, lato))
    drawRect(lerp(muro, Colori.inchiostro, 0.45f), Offset(x + larga * 0.4f, base - alta * 0.38f), Size(larga * 0.2f, alta * 0.38f))
    val camino = Offset(x + larga * 0.72f, colmo + larga * 0.12f)
    drawRect(tetto, Offset(camino.x, camino.y - larga * 0.18f), Size(larga * 0.1f, larga * 0.2f))
    return Offset(camino.x + larga * 0.05f, camino.y - larga * 0.2f)
}

/** Il fumo che sale dal camino e si allarga. */
private fun DrawScope.fumo(da: Offset, t: Float, colore: Color) {
    for (k in 0 until 6) {
        val f = (t * 0.25f + k / 6f) % 1f
        drawCircle(
            colore.copy(alpha = 0.45f * (1f - f)),
            radius = W * (0.008f + f * 0.03f),
            center = da + Offset(oscilla(t, 3f, k.toFloat()) * W * 0.02f + f * W * 0.04f, -f * H * 0.16f),
        )
    }
}

/** La citta' innevata sotto Natale: tetti bianchi, camini che fumano, un albero con le luci. */
internal fun DrawScope.scenaCittaNatale(t: Float, notte: Boolean) {
    val suolo = H * 0.74f
    if (notte) {
        cielo(Color(0xFF101A30), Color(0xFF34406A))
        stelle(t, H * 0.35f)
        luna(Offset(W * 0.8f, H * 0.14f), W * 0.05f, Color(0xFF101A30), falce = true)
    } else {
        cielo(Color(0xFFB9CCDB), Color(0xFFE9EEF2))
    }
    val finestra = if (notte) Colori.luceCalda else Color(0xFF8FA6B8)
    val muri = listOf(Color(0xFFC98E62), Color(0xFFE2C9A0), Color(0xFFB36A48), Color(0xFFD9B98A), Color(0xFFA85C3C))
    val camini = ArrayList<Offset>()
    val larghe = listOf(0.2f, 0.16f, 0.22f, 0.17f, 0.2f)
    var x = -W * 0.03f
    muri.forEachIndexed { i, muro ->
        val larga = W * larghe[i]
        val alta = H * (0.12f + caso(i, 40) * 0.08f)
        camini += casa(x, suolo, larga, alta, if (notte) lerp(muro, Color(0xFF1A2238), 0.45f) else muro, Colori.terracotta, finestra, neve = true)
        x += larga + W * 0.01f
    }
    // Il manto di neve davanti, con un'ombra azzurra.
    drawRect(Colori.neveOmbra, Offset(0f, suolo), Size(W, H - suolo))
    drawRect(Color.White, Offset(0f, suolo + H * 0.02f), Size(W, H - suolo))
    camini.forEach { fumo(it, t, if (notte) Color(0xFF8D96AE) else Color.White) }

    // L'abete con le luci che si accendono a turno.
    val base = Offset(W * 0.82f, H * 0.93f)
    for (k in 0 until 3) {
        val larga = W * (0.16f - k * 0.04f)
        val y = base.y - k * H * 0.07f
        val p = Path().apply {
            moveTo(base.x - larga / 2f, y)
            lineTo(base.x, y - H * 0.11f)
            lineTo(base.x + larga / 2f, y)
            close()
        }
        drawPath(p, if (notte) Color(0xFF274A33) else Color(0xFF3F6B4A))
    }
    drawRect(Color(0xFF6B4A33), Offset(base.x - W * 0.012f, base.y), Size(W * 0.024f, H * 0.03f))
    val colori = listOf(Color(0xFFE05252), Colori.luceCalda, Color(0xFF6FB1E8), Color(0xFFF6A06B))
    for (k in 0 until 14) {
        val fy = caso(k, 41)
        val y = base.y - fy * H * 0.24f
        val largaQui = W * 0.16f * (1f - fy) * 0.45f
        val acceso = ((t * 2f + k) % 3f) < 2f
        drawCircle(colori[k % colori.size].copy(alpha = if (acceso) 1f else 0.35f), 3.2f, Offset(base.x + (caso(k, 42) - 0.5f) * 2f * largaQui, y))
    }
    drawCircle(Colori.luceCalda, 5f, Offset(base.x, base.y - H * 0.25f))
    neve(t, 120, fino = H)
}

/** La fermata dell'autobus sotto la pioggia: pensilina, panchina, un ombrello, le pozzanghere. */
internal fun DrawScope.scenaFermata(t: Float, notte: Boolean) {
    val marciapiede = H * 0.72f
    if (notte) cielo(Color(0xFF151B27), Color(0xFF2E3747)) else cielo(Colori.cieloGrigioAlto, Colori.cieloGrigioBasso)
    // I palazzi dietro, in silhouette.
    for (i in 0 until 6) {
        val larga = W * (0.13f + caso(i, 50) * 0.08f)
        val alta = H * (0.22f + caso(i, 51) * 0.2f)
        val x = i * W * 0.18f - W * 0.04f
        drawRect(if (notte) Color(0xFF202838) else Color(0xFF8E979F), Offset(x, marciapiede - alta), Size(larga, alta))
        if (notte) for (k in 0 until 6) {
            if (caso(i * 7 + k, 52) > 0.45f) {
                drawRect(Colori.luceCalda.copy(alpha = 0.8f), Offset(x + larga * (0.15f + (k % 2) * 0.45f), marciapiede - alta + H * (0.03f + (k / 2) * 0.05f)), Size(larga * 0.2f, H * 0.018f))
            }
        }
    }
    // Strada e marciapiede bagnati.
    drawRect(if (notte) Color(0xFF2A2F38) else Color(0xFF6F757C), Offset(0f, marciapiede), Size(W, H - marciapiede))
    drawRect(if (notte) Color(0xFF3A404B) else Color(0xFF9EA3A8), Offset(0f, marciapiede), Size(W, H * 0.05f))

    // Il lampione e il suo cono di luce, di notte.
    val lampione = Offset(W * 0.12f, marciapiede - H * 0.36f)
    drawLine(Colori.inchiostro, Offset(lampione.x, marciapiede), lampione, strokeWidth = 5f)
    drawLine(Colori.inchiostro, lampione, lampione + Offset(W * 0.07f, 0f), strokeWidth = 5f)
    if (notte) {
        val cono = Path().apply {
            moveTo(lampione.x + W * 0.05f, lampione.y + 4f)
            lineTo(lampione.x - W * 0.08f, marciapiede + H * 0.05f)
            lineTo(lampione.x + W * 0.2f, marciapiede + H * 0.05f)
            lineTo(lampione.x + W * 0.09f, lampione.y + 4f)
            close()
        }
        drawPath(cono, Brush.verticalGradient(listOf(Colori.luceCalda.copy(alpha = 0.45f), Colori.luceCalda.copy(alpha = 0.05f)), startY = lampione.y, endY = marciapiede))
        drawCircle(Colori.luceCalda, 7f, lampione + Offset(W * 0.07f, 4f))
    }

    // La pensilina: tetto, due montanti, il vetro di fondo, il cartello.
    val sx = W * 0.4f
    val larga = W * 0.42f
    val alta = H * 0.24f
    drawRect(Color(0xFFBFD3DE).copy(alpha = 0.35f), Offset(sx, marciapiede - alta), Size(larga, alta))
    drawRect(Colori.terracotta, Offset(sx - W * 0.02f, marciapiede - alta - H * 0.02f), Size(larga + W * 0.04f, H * 0.022f))
    drawLine(Colori.inchiostro, Offset(sx, marciapiede), Offset(sx, marciapiede - alta), strokeWidth = 4f)
    drawLine(Colori.inchiostro, Offset(sx + larga, marciapiede), Offset(sx + larga, marciapiede - alta), strokeWidth = 4f)
    drawRect(Colori.inchiostro, Offset(sx + larga * 0.1f, marciapiede - alta * 0.3f), Size(larga * 0.5f, H * 0.012f))
    // Il cartello della fermata: un rettangolo giallo con un autobus.
    val cartello = Offset(sx + larga + W * 0.06f, marciapiede - alta * 1.05f)
    drawLine(Colori.inchiostro, Offset(cartello.x, marciapiede), cartello, strokeWidth = 3f)
    drawRoundRect(Color(0xFFF0C51F), cartello - Offset(W * 0.045f, W * 0.035f), Size(W * 0.09f, W * 0.07f), CornerRadius(W * 0.01f))
    drawRoundRect(Colori.inchiostro, cartello - Offset(W * 0.03f, W * 0.018f), Size(W * 0.06f, W * 0.03f), CornerRadius(W * 0.006f))
    drawCircle(Colori.inchiostro, W * 0.007f, cartello + Offset(-W * 0.017f, W * 0.016f))
    drawCircle(Colori.inchiostro, W * 0.007f, cartello + Offset(W * 0.017f, W * 0.016f))

    // Una persona con l'ombrello rosso: gambe, cappotto, testa, e il manico
    // che dalla mano sale all'ombrello.
    val persona = Offset(sx + larga * 0.72f, marciapiede)
    val cappotto = Color(0xFF3E4A5C)
    drawLine(Colori.inchiostro, persona + Offset(-W * 0.008f, 0f), persona + Offset(-W * 0.006f, -H * 0.045f), strokeWidth = 5f, cap = StrokeCap.Round)
    drawLine(Colori.inchiostro, persona + Offset(W * 0.008f, 0f), persona + Offset(W * 0.006f, -H * 0.045f), strokeWidth = 5f, cap = StrokeCap.Round)
    val corpo = Path().apply {
        moveTo(persona.x - W * 0.028f, persona.y - H * 0.04f)
        lineTo(persona.x - W * 0.018f, persona.y - H * 0.115f)
        lineTo(persona.x + W * 0.018f, persona.y - H * 0.115f)
        lineTo(persona.x + W * 0.028f, persona.y - H * 0.04f)
        close()
    }
    drawPath(corpo, cappotto)
    val testa = persona - Offset(0f, H * 0.13f)
    drawCircle(Color(0xFFD9A77E), W * 0.016f, testa)
    val mano = persona + Offset(W * 0.02f, -H * 0.09f)
    val cima = persona - Offset(-W * 0.01f, H * 0.19f)
    drawLine(Colori.inchiostro, mano, cima, strokeWidth = 2.5f, cap = StrokeCap.Round)
    val ombrello = Path().apply {
        moveTo(cima.x - W * 0.085f, cima.y + H * 0.018f)
        quadraticBezierTo(cima.x, cima.y - H * 0.05f, cima.x + W * 0.085f, cima.y + H * 0.018f)
        close()
    }
    drawPath(ombrello, Color(0xFFD8503F))

    // Le pozzanghere, con i cerchi delle gocce che si allargano.
    listOf(0.2f to 0.84f, 0.62f to 0.9f, 0.88f to 0.82f).forEachIndexed { i, (fx, fy) ->
        val c = Offset(W * fx, H * fy)
        drawOval(if (notte) Color(0xFF3B4455) else Color(0xFF8A9199), c - Offset(W * 0.09f, H * 0.012f), Size(W * 0.18f, H * 0.024f))
        for (k in 0 until 2) {
            val f = (t * 0.9f + caso(i * 3 + k, 53)) % 1f
            drawOval(Color.White.copy(alpha = 0.5f * (1 - f)), c - Offset(W * 0.05f * f, H * 0.007f * f), Size(W * 0.1f * f, H * 0.014f * f), style = Stroke(1.5f))
        }
    }
    pioggia(t, 130, if (notte) Color(0xFFB8C4D6) else Color(0xFFE6EDF3), pendenza = 0.12f)
}

/** Dopo la pioggia: tetti bagnati e l'arcobaleno. Di notte la luna fra le nuvole che si aprono. */
internal fun DrawScope.scenaArcobaleno(t: Float, notte: Boolean) {
    val tetti = H * 0.7f
    if (notte) {
        cielo(Colori.cieloNotteAlto, Colori.cieloNotteBasso)
        stelle(t, H * 0.55f)
        luna(Offset(W * 0.5f, H * 0.26f), W * 0.075f, Colori.cieloNotteAlto)
        // Le nuvole si scostano, lente, da una parte e dall'altra.
        val apre = 0.5f + 0.5f * oscilla(t, 18f)
        nuvola(Offset(W * (0.28f - apre * 0.12f), H * 0.32f), W * 0.4f, Color(0xFF3A4462))
        nuvola(Offset(W * (0.74f + apre * 0.12f), H * 0.28f), W * 0.42f, Color(0xFF444F70))
    } else {
        cielo(Color(0xFF8FB9D2), Color(0xFFF3E4CF))
        val c = Offset(W * 0.5f, tetti + H * 0.06f)
        val colori = listOf(Color(0xFFE05252), Color(0xFFF08A3C), Color(0xFFF0C51F), Color(0xFF7DB85A), Color(0xFF4F8CC9), Color(0xFF7C5BB5))
        val presenza = 0.55f + 0.1f * oscilla(t, 6f)
        colori.forEachIndexed { k, col ->
            val r = W * (0.62f - k * 0.035f)
            drawArc(col.copy(alpha = presenza), 180f, 180f, false, c - Offset(r, r), Size(r * 2, r * 2), style = Stroke(W * 0.035f))
        }
        nuvola(Offset((W * 0.15f + t * W * 0.01f) % (W * 1.3f), H * 0.2f), W * 0.3f, Color.White.copy(alpha = 0.85f))
        nuvola(Offset(W * 1.1f - (W * 0.25f + t * W * 0.008f) % (W * 1.3f), H * 0.14f), W * 0.24f, Color.White.copy(alpha = 0.75f))
    }
    // Le case in fila, bagnate: una linea di luce sul tetto.
    val muri = listOf(Color(0xFFE2C9A0), Color(0xFFC98E62), Color(0xFFD9B98A), Color(0xFFB36A48), Color(0xFFE9DCC4), Color(0xFFC9A27A))
    var x = -W * 0.04f
    var i = 0
    while (x < W) {
        val larga = W * (0.17f + caso(i, 60) * 0.05f)
        val alta = H * (0.09f + caso(i, 61) * 0.06f)
        val muro = muri[i % muri.size]
        casa(
            x, tetti + H * 0.04f, larga, alta,
            if (notte) lerp(muro, Color(0xFF151B2B), 0.55f) else muro,
            if (notte) lerp(Colori.terracotta, Color(0xFF151B2B), 0.5f) else Colori.terracotta,
            if (notte) (if (caso(i, 62) > 0.4f) Colori.luceCalda else Color(0xFF2A3350)) else Color(0xFF8FA6B8),
            neve = false,
        )
        drawLine(Color.White.copy(alpha = 0.5f), Offset(x + larga * 0.1f, tetti + H * 0.04f - alta - larga * 0.05f), Offset(x + larga / 2f, tetti + H * 0.04f - alta - larga * 0.4f), strokeWidth = 2f)
        x += larga + W * 0.012f
        i++
    }
    // Le pozzanghere che riflettono il cielo, e l'ultima goccia che le increspa.
    listOf(0.25f, 0.7f).forEachIndexed { i, fx ->
        val c = Offset(W * fx, H * (0.84f + i * 0.06f))
        drawOval(if (notte) Color(0xFF34406A) else Color(0xFF9CC3D8), c - Offset(W * 0.12f, H * 0.014f), Size(W * 0.24f, H * 0.028f))
        val f = (t * 0.5f + i * 0.5f) % 1f
        drawOval(Color.White.copy(alpha = 0.6f * (1 - f)), c - Offset(W * 0.07f * f, H * 0.009f * f), Size(W * 0.14f * f, H * 0.018f * f), style = Stroke(1.5f))
    }
    // Qualche goccia che cade ancora dalle grondaie.
    for (i in 0 until 6) {
        val f = (t * 0.7f + caso(i, 62)) % 1f
        val x = W * (0.08f + i * 0.16f)
        drawCircle(Color(0xFFBFD3DE), 2.5f, Offset(x, tetti + H * 0.04f + f * H * 0.2f))
    }
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Colori.inchiostro.copy(alpha = 0.08f))), Offset(0f, tetti), Size(W, H - tetti))
}
