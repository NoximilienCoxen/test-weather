package io.github.noximiliencoxen.caelum.ui.scene

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import io.github.noximiliencoxen.caelum.ui.theme.relativeLuminance
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.hypot

/**
 * Il cielo di ogni scena, in cima e all'orizzonte: lo stesso che la scena
 * dipinge, tenuto in un posto solo perche' serve anche a [tingiCielo].
 */
internal fun cieloDi(scena: Scena, notte: Boolean): Pair<Color, Color> = if (notte) {
    when (scena) {
        Scena.MARE, Scena.GRANO, Scena.ARCOBALENO -> Colori.cieloNotteAlto to Colori.cieloNotteBasso
        Scena.CITTA_NATALE -> Color(0xFF101A30) to Color(0xFF34406A)
        Scena.TEMPESTA -> Color(0xFF0B0F18) to Color(0xFF1D2430)
        Scena.FERMATA -> Color(0xFF151B27) to Color(0xFF2E3747)
        Scena.MONTAGNA -> Color(0xFF0B1224) to Color(0xFF263252)
    }
} else {
    when (scena) {
        Scena.MARE -> Colori.cieloGiornoAlto to Colori.cieloGiornoBasso
        Scena.GRANO -> Color(0xFF9CC3D8) to Color(0xFFF7E3C4)
        Scena.ARCOBALENO -> Color(0xFF8FB9D2) to Color(0xFFF3E4CF)
        Scena.CITTA_NATALE -> Color(0xFFB9CCDB) to Color(0xFFE9EEF2)
        Scena.TEMPESTA -> Colori.cieloTempestaAlto to Colori.cieloTempestaBasso
        Scena.FERMATA -> Colori.cieloGrigioAlto to Colori.cieloGrigioBasso
        Scena.MONTAGNA -> Color(0xFF8DBAD6) to Color(0xFFEFE6D6)
    }
}

/**
 * Le tre fermate del cielo di Sala, tinte coi colori della scena d'apertura.
 *
 * **Si sposta la tinta, mai la luce.** Le fermate passano in CIELAB: si
 * avvicinano a* e b* a quelli della scena, e la chiarezza si ritocca finche' la
 * luminanza relativa - quella con cui si calcola il contrasto - torna quella di
 * prima, entro l'uno per cento. Cosi' i conti di `temaScuro` e dei test di
 * contrasto restano veri su un cielo tinto come su quello di partenza. Se una
 * tinta uscisse dallo sRGB, dove il taglio cambierebbe la luce, si dimezza
 * finche' non ci rientra.
 *
 * Fermata per fermata: la cima prende la cima della scena, l'orizzonte il suo
 * orizzonte, quella di mezzo il mezzo. Di notte, e a meta' crepuscolo, si
 * mescolano le due versioni della scena con [notte].
 */
fun tingiCielo(stops: List<Color>, scena: Scena, notte: Float, forza: Float): List<Color> {
    if (forza <= 0f) return stops
    val giorno = cieloDi(scena, notte = false).let { ab(it.first) to ab(it.second) }
    val buio = cieloDi(scena, notte = true).let { ab(it.first) to ab(it.second) }
    val n = notte.coerceIn(0f, 1f)
    val alto = mescola(giorno.first, buio.first, n)
    val basso = mescola(giorno.second, buio.second, n)
    val tinte = listOf(alto, mescola(alto, basso, 0.5f), basso)
    return stops.mapIndexed { i, c -> tingi(c, tinte[i.coerceAtMost(2)], forza.coerceAtMost(1f)) }
}

private fun ab(c: Color): FloatArray = c.convert(ColorSpaces.CieLab).let { floatArrayOf(it.green, it.blue) }

private fun mescola(a: FloatArray, b: FloatArray, t: Float) = floatArrayOf(misto(a[0], b[0], t), misto(a[1], b[1], t))

private fun tingi(c: Color, tinta: FloatArray, forza: Float): Color {
    val lab = c.convert(ColorSpaces.CieLab)
    val luce = c.relativeLuminance()
    // **Tinge cio' che il tempo lascia neutro.** Un grigio coperto prende tutta
    // la tinta; l'arancione del tramonto, che dice che ora e', ne prende un
    // quarto e resta arancione.
    var f = forza * (1f - hypot(lab.green, lab.blue) / CROMA_PIENA).coerceIn(0.25f, 1f)
    repeat(5) {
        val a = misto(lab.green, tinta[0], f)
        val b = misto(lab.blue, tinta[1], f)
        // L* di CIELAB sta sul bianco D50, la luminanza del contrasto su quello
        // D65: su un colore saturo le due divergono di qualche punto. Due
        // correzioni della chiarezza riportano la luminanza dove era.
        var l = lab.red
        var tinto = c
        repeat(3) {
            tinto = Color(l, a, b, c.alpha, ColorSpaces.CieLab).convert(ColorSpaces.Srgb)
            l = (l + 116f * (cbrt(luce) - cbrt(tinto.relativeLuminance()))).coerceIn(0f, 100f)
        }
        if (abs(tinto.relativeLuminance() - luce) <= (luce + 0.05f) * 0.01f) return tinto
        // Fuori dallo sRGB il taglio cambierebbe la luce: meno tinta.
        f /= 2f
    }
    return c
}

private fun misto(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** La croma CIELAB oltre la quale una fermata e' gia' un colore, e si tinge al minimo. */
private const val CROMA_PIENA = 50f
