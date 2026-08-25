package com.forli.meteo.ui.render

import android.graphics.Typeface
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Materiale della cifra: plastica bianca opaca fresata, spigoli smussati netti.
 * Nessun bagliore, nessun alone, nessun riflesso speculare.
 */
@Immutable
data class NumberPalette(
    val face: Color,
    val sideNear: Color,
    val sideFar: Color,
    val chamfer: Color,
    val iridescence: List<Color>,
    val iridescenceAlpha: Float,
    /** Quanto stacca l'ombra portata. Su fondo grigio serve sempre un po'. */
    val shadowAlpha: Float,
)

/**
 * Cosa disegnare e quanto grande.
 *
 * Il materiale non fa parte della specifica, ed e' voluto: la geometria si
 * estrae una volta e vale per sempre, mentre i colori cambiano a ogni ora del
 * giorno. Tenerli insieme farebbe rifare l'estrazione a ogni sfumatura del
 * cielo, che e' la parte cara del lavoro.
 */
@Immutable
data class NumberSpec(
    val text: String,
    val typeface: Typeface,
    val fontSizePx: Float,
    /** Spessore del prisma, in pixel. */
    val depthPx: Float,
    /** Larghezza disponibile: oltre questa la cifra viene rimpicciolita. */
    val maxWidthPx: Float = Float.MAX_VALUE,
    val letterSpacingEm: Float = -0.02f,
    /**
     * Quanti caratteri finali sono un simbolo e non una cifra: vanno in corpo
     * ridotto, a filo della cima.
     *
     * Il grado passa di qui e non da un disegno a parte perche' e' parte
     * dell'oggetto: dev'essere estruso, illuminato e girato con le cifre. Un
     * simbolo sovrapposto in coordinate di schermo resterebbe appiccicato al
     * vetro mentre tutto il resto gira, ed e' esattamente il tipo di dettaglio
     * che smonta l'illusione.
     */
    val smallTail: Int = 0,
)

/**
 * Come l'oggetto e' orientato nello spazio.
 *
 * Sono angoli veri, non piu' una direzione di estrusione: [yawDeg] gira attorno
 * all'asse verticale ed e' quello che il dito comanda, [pitchDeg] attorno a
 * quello orizzontale ed e' il contributo minuto del sensore.
 */
@Immutable
data class NumberMotion(
    val yawDeg: Float = 0f,
    val pitchDeg: Float = 0f,
) {
    companion object {
        val Fermo = NumberMotion()
    }
}

/** Geometria gia' estratta, pronta a essere vista da qualunque angolo. */
interface PreparedNumber {
    val width: Float
    val height: Float
}

/**
 * Unico punto da cui passa il disegno della cifra gigante.
 *
 * [prepare] estrae la geometria, che dipende solo dal testo e dal corpo.
 * [draw] la guarda da un certo angolo. Separarli permette di ruotare senza
 * rifare il lavoro di estrazione, che e' la parte cara.
 */
interface TemperatureRenderer {
    fun prepare(spec: NumberSpec): PreparedNumber?
    fun draw(
        scope: DrawScope,
        prepared: PreparedNumber,
        center: Offset,
        palette: NumberPalette,
        motion: NumberMotion = NumberMotion.Fermo,
        /**
         * Dove riporre la sagoma dell'oggetto, per chi deve sapere dove trova
         * superficie. Nullo quando a nessuno interessa.
         */
        silhouette: com.forli.meteo.ui.render3d.Skyline? = null,
    )
}
