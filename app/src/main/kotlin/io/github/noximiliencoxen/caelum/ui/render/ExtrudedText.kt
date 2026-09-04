package io.github.noximiliencoxen.caelum.ui.render

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import io.github.noximiliencoxen.caelum.R
import io.github.noximiliencoxen.caelum.ui.render3d.SceneContact
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.LocalTemperatureRenderer
import io.github.noximiliencoxen.caelum.ui.theme.toNumberPalette

/**
 * Corpo del carattere della cifra. Archivo e' variabile su peso e larghezza,
 * quindi la proporzione si regola qui senza cambiare file di font.
 */
object NumberType {
    const val WEIGHT = 700
    const val WIDTH = 72

    /** Gli assi, nella forma che vuole `Paint.fontVariationSettings`. */
    fun axes(weight: Int = WEIGHT, width: Int = WIDTH): String =
        "'wght' $weight, 'wdth' $width"
}

/**
 * Il carattere della cifra, preso dalle risorse.
 *
 * **Dalle risorse e non dagli assets**, e non e' un dettaglio: lo stesso file
 * serve anche a tutti i testi dell'interfaccia, e Compose sa costruire una
 * famiglia da un identificativo di risorsa **senza un contesto**. Dagli assets
 * avrebbe voluto un `AssetManager`, quindi una famiglia costruibile solo dentro
 * un composable, quindi uno stato da far girare per tutta l'app - oppure una
 * seconda copia dello stesso mezzo mega di font.
 *
 * Gli assi variabili non stanno piu' sul `Typeface` ma sul `Paint` che lo usa:
 * `Paint.fontVariationSettings` li applica al momento della misura, ed e'
 * disponibile dallo stesso Android 8 che l'app dichiara come minimo.
 */
@Composable
fun rememberNumberTypeface(): Typeface {
    val context = LocalContext.current
    return remember(context) {
        runCatching { ResourcesCompat.getFont(context, R.font.archivo_variable) }
            .getOrNull() ?: Typeface.DEFAULT_BOLD
    }
}

/**
 * Disegna testo come solido, passando dal renderer corrente.
 *
 * L'orientamento arriva come funzione e non come valore: viene letto dentro il
 * disegno, quindi ruotare ridipinge e basta. Passandolo come parametro, ogni
 * grado di rotazione ricomporrebbe l'albero, e la rotazione e' un gesto
 * continuo che ne produce centinaia.
 */
@Composable
fun ExtrudedText(
    text: String,
    fontSize: Dp,
    modifier: Modifier = Modifier,
    depth: Dp = fontSize * 0.17f,
    verticalBias: Float = 0f,
    /** Quanti caratteri finali sono un simbolo in corpo ridotto. */
    smallTail: Int = 0,
    motion: () -> NumberMotion = { NumberMotion.Fermo },
    /** Chi vuole sapere dove l'oggetto offre superficie. Di norma la pioggia. */
    contact: SceneContact? = null,
    typeface: Typeface = rememberNumberTypeface(),
) {
    val colors = LocalMeteoColors.current
    val renderer = LocalTemperatureRenderer.current
    val density = LocalDensity.current
    val palette = remember(colors) { colors.toNumberPalette() }

    BoxWithConstraints(modifier) {
        val fontPx = with(density) { fontSize.toPx() }
        val depthPx = with(density) { depth.toPx() }
        val availableWidthPx = with(density) { maxWidth.toPx() }

        val spec = remember(text, typeface, fontPx, depthPx, availableWidthPx, smallTail) {
            NumberSpec(
                text = text,
                typeface = typeface,
                fontSizePx = fontPx,
                depthPx = depthPx,
                maxWidthPx = availableWidthPx,
                smallTail = smallTail,
                variationSettings = NumberType.axes(),
            )
        }

        val prepared = remember(spec, renderer) { renderer.prepare(spec) }

        // L'entrata: la cifra sale al suo posto invece di comparirci.
        //
        // Una volta sola, quando l'oggetto entra in scena - cioe' quando i dati
        // arrivano e c'e' finalmente un numero da mostrare. Scorrendo le ore non
        // si ripete: li' il valore cambia di continuo, e un'animazione a ogni
        // cambio non fa in tempo a finire prima di ricominciare. E' esattamente
        // il motivo per cui il contachilometri sulle cifre e' stato tolto.
        val entrance = remember { Animatable(ENTRANCE_LIFT) }
        LaunchedEffect(Unit) {
            entrance.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 170f))
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    contact?.skyline?.origin = coordinates.positionInRoot()
                },
        ) {
            val current = prepared ?: return@Canvas
            renderer.draw(
                scope = this,
                prepared = current,
                center = Offset(size.width / 2f, size.height * (0.5f + verticalBias)),
                palette = palette,
                motion = motion(),
                silhouette = contact?.skyline,
                // Letto qui e non in composizione: l'entrata ridipinge, non
                // ricompone.
                lift = entrance.value,
                glare = contact?.glare ?: 0f,
            )
        }
    }
}

/**
 * Da quanto in basso arriva la cifra all'entrata, in altezze di se' stessa.
 *
 * Un terzo, non uno: deve sembrare che si assesti, non che venga lanciata da
 * fuori campo.
 */
private const val ENTRANCE_LIFT = 0.34f
