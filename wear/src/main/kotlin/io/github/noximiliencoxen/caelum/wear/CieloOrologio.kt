package io.github.noximiliencoxen.caelum.wear

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.data.SunClock
import io.github.noximiliencoxen.caelum.ui.sala.FaseContinua
import io.github.noximiliencoxen.caelum.ui.sala.SalaCondition
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaPhase
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.cieloBrush
import io.github.noximiliencoxen.caelum.ui.sala.cieloStops
import io.github.noximiliencoxen.caelum.ui.sala.crepuscolezza
import io.github.noximiliencoxen.caelum.ui.sala.faseContinua
import io.github.noximiliencoxen.caelum.ui.sala.livelloCielo
import io.github.noximiliencoxen.caelum.ui.sala.salaConditionOf
import io.github.noximiliencoxen.caelum.ui.sala.salaPalette
import io.github.noximiliencoxen.caelum.ui.sala.temaScuro
import io.github.noximiliencoxen.caelum.ui.sala.temaScuroPerTempesta
import io.github.noximiliencoxen.caelum.ui.theme.CONTRAST_AA
import io.github.noximiliencoxen.caelum.ui.theme.contrastRatio
import io.github.noximiliencoxen.caelum.ui.theme.mutedOnBoth
import io.github.noximiliencoxen.caelum.ui.theme.readableOn
import io.github.noximiliencoxen.caelum.ui.theme.readableOnBoth
import io.github.noximiliencoxen.caelum.ui.theme.relativeLuminance
import java.time.LocalDateTime

/**
 * Il cielo dell'orologio: lo stesso di Sala sul telefono, con gli inchiostri
 * ricavati per il polso.
 *
 * Fase del giorno, chiusura del cielo e tavole dei colori sono quelle di
 * `ui/sala/SalaTheme.kt` (modulo `core`): non c'e' una seconda tavolozza da
 * tenere d'accordo. Cambia solo **dove cade il testo**. Sul telefono quasi
 * tutto sta dentro un pannello; qui le scritte di "Adesso" stanno sul cielo
 * nudo, dalla cima a poco sopra le colline, quindi l'inchiostro si misura su
 * **tutti** i colori che attraversa ([sottoIlTesto]).
 *
 * ### Il velo
 *
 * All'alba e al tramonto quella fascia va da un viola scuro a un pesca chiaro,
 * e nessun inchiostro regge la soglia AA su tutti e due: misurato, il migliore
 * arrivava a 3,9. Il telefono non ha il problema perche' scrive dentro i
 * pannelli. Qui c'e' un [velo] al centro del quadrante - scuro col tema scuro,
 * chiaro col tema chiaro - e la sua opacita' e' **la minima** che riporta il
 * contrasto sopra soglia. Sulla gran parte dei cieli vale zero, e il cielo
 * resta quello del telefono.
 */
@Immutable
data class CieloOrologio(
    /** Le tre fermate della sfumatura: cima, a un terzo, orizzonte. */
    val stops: List<Color>,
    val palette: SalaPalette,
    /** Il velo al centro del quadrante, gia' con la sua opacita' (spesso zero). */
    val velo: Color,
    /** I colori su cui cade davvero il testo di "Adesso", velo compreso. */
    val sottoIlTesto: List<Color>,
    /** Il testo che sta sul cielo nudo: AA su tutto [sottoIlTesto]. */
    val ink: Color,
    /** Il testo secondario sul cielo nudo: si fa da parte senza scendere sotto AA. */
    val inkSoft: Color,
    /** Il testo dentro le righe a pastiglia ([SalaPalette.panelSolido]). */
    val inkPanel: Color,
    val inkPanelSoft: Color,
    /** Le percentuali di pioggia, in blu, leggibili sulla pastiglia. */
    val acqua: Color,
    /** Il testo sulla pastiglia della pioggia, che ha per fondo l'accento. */
    val inkAccento: Color,
) {
    val brush: Brush get() = cieloBrush(stops)

    /**
     * Il velo come pennello per un quadrante di lato [lato]: pieno su tutta la
     * colonna del testo, si spegne solo verso il bordo, dove non c'e' niente da
     * leggere. Il centro e il raggio stanno con [FASCIA_TESTO]: la colonna
     * intera cade dentro la parte piena.
     */
    fun veloBrush(lato: Float): Brush = Brush.radialGradient(
        0.00f to velo,
        0.70f to velo,
        1.00f to velo.copy(alpha = 0f),
        center = Offset(lato * 0.5f, lato * 0.42f),
        radius = lato * 0.62f,
    )
}

/**
 * Dove sta il testo di "Adesso", in frazioni dell'altezza: dall'ora in cima
 * alla riga della giornata. Sotto c'e' la pastiglia, che ha un fondo suo.
 */
private val FASCIA_TESTO = 0.04f..0.68f

/**
 * Il cielo di un momento, per la previsione data.
 *
 * Senza previsione (caricamento, errore) si tiene una notte serena: e' il cielo
 * piu' scuro, quello che su uno schermo OLED costa meno, e non promette niente.
 */
fun cieloOrologio(previsione: Forecast?, adesso: LocalDateTime): CieloOrologio {
    if (previsione == null) {
        return componi(
            fase = FaseContinua(SalaPhase.NOTTE, SalaPhase.NOTTE, 0f),
            sky = SkyState.of(altitude = -1f),
            condizione = SalaCondition.SERENO,
            copertura = 0.1f,
        )
    }
    val giorno = previsione.dayOf(adesso)
    val sky = SunClock.skyAt(adesso, giorno?.sunrise, giorno?.sunset, previsione.current.isDay)
    val condizione = salaConditionOf(previsione.current.weatherCode)
    val copertura = nuvolositaAdesso(previsione, adesso)?.let { it / 100f } ?: coperturaDi(condizione)
    return componi(faseContinua(sky), sky, condizione, copertura)
}

/** Una copertura plausibile quando la nuvolosita' oraria manca. */
private fun coperturaDi(condizione: SalaCondition): Float = when (condizione) {
    SalaCondition.SERENO -> 0.1f
    SalaCondition.NUVOLOSO -> 0.75f
    else -> 1f
}

private fun componi(fase: FaseContinua, sky: SkyState, condizione: SalaCondition, copertura: Float): CieloOrologio {
    val tempesta = condizione == SalaCondition.TEMPORALE || condizione == SalaCondition.TEMPORALE_GRANDINE
    val neve = if (condizione == SalaCondition.NEVE) 1f else 0f
    val livello = livelloCielo(copertura, if (tempesta) 1f else 0f)
    val stops = cieloStops(fase, livello, neve)

    // Come in `SalaShell`, ma senza molle: l'orologio mostra un istante e non
    // anima il passaggio, quindi il tema e' il suo bersaglio, gia' fuori dalla
    // fascia illeggibile che lo scalino di `temaScuro` salta.
    val dk = if (temaScuroPerTempesta(condizione)) 1f else temaScuro(sky)
    val palette = salaPalette(dk, crepuscolezza(fase), livello)

    // Il velo va verso il fondo dei pannelli del tema: e' lo stesso colore su
    // cui il telefono scrive, quindi l'inchiostro di Sala ci si legge di sicuro.
    val tintaVelo = if (palette.dark) palette.schermoPieno else SalaTokens.neutral100
    val cielo = (0..8).map { i ->
        sfumatura(stops, FASCIA_TESTO.start + (FASCIA_TESTO.endInclusive - FASCIA_TESTO.start) * i / 8f)
    }

    var velo = tintaVelo.copy(alpha = 0f)
    var sotto = cielo
    var ink = inchiostroSu(palette.ink, sotto)
    for (passo in 1..16) {
        if (sotto.all { ink.contrastRatio(it) >= CONTRAST_AA }) break
        velo = tintaVelo.copy(alpha = passo * 0.05f)
        sotto = cielo.map { velo.compositeOver(it) }
        ink = inchiostroSu(palette.ink, sotto)
    }

    val pannello = palette.panelSolido
    val acqua = lerp(SalaTokens.acquaScura, SalaTokens.acquaChiara, palette.buio)
    return CieloOrologio(
        stops = stops,
        palette = palette,
        velo = velo,
        sottoIlTesto = sotto,
        ink = ink,
        inkSoft = ink.mutedOnBoth(sotto.minBy { it.relativeLuminance() }, sotto.maxBy { it.relativeLuminance() }),
        inkPanel = palette.ink.readableOn(pannello),
        inkPanelSoft = palette.inkSoft,
        acqua = acqua.readableOn(pannello),
        inkAccento = palette.accentInk.readableOn(palette.accent),
    )
}

/** L'inchiostro leggibile sul piu' chiaro e sul piu' scuro dei colori dati, che sono i due casi peggiori. */
private fun inchiostroSu(base: Color, fondi: List<Color>): Color =
    base.readableOnBoth(fondi.minBy { it.relativeLuminance() }, fondi.maxBy { it.relativeLuminance() }, CONTRAST_AA)

/**
 * Il colore della sfumatura di [cieloBrush] all'altezza [y] (da 0 a 1).
 *
 * Mescola i canali sRGB come fa il disegno di un gradiente lineare sullo
 * schermo, non in Oklab come `lerp`: si misura il colore che si vede.
 */
private fun sfumatura(stops: List<Color>, y: Float): Color {
    fun mix(a: Color, b: Color, t: Float) = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
    )
    return when {
        y <= 0.34f -> mix(stops[0], stops[1], y / 0.34f)
        y <= 0.74f -> mix(stops[1], stops[2], (y - 0.34f) / 0.40f)
        else -> stops[2]
    }
}
