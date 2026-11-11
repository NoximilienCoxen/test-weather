package com.caelumweather.ui.sala

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** Quanto dura il passaggio da una cifra all'altra. */
private const val PASSO_MS = 170

/**
 * Un numero le cui cifre scorrono quando cambia, come un contachilometri.
 *
 * **Si muove solo la cifra che cambia.** Da 19 a 20 scorrono tutte e due, da
 * 20 a 21 solo l'unita': e' cosi' che l'occhio capisce *di quanto* e' cambiato
 * senza rileggere il numero. Per questo ogni cifra e' una scritta a se', e le
 * cifre si contano **da destra**: l'unita' resta l'unita' anche quando il
 * numero prende o perde una cifra, o il segno.
 *
 * **Il verso dice la direzione**: se il numero sale la cifra nuova arriva dal
 * basso, se scende dall'alto. Scorrendo le ore verso il pomeriggio i gradi
 * salgono, e si vedono salire.
 *
 * **Da fermo non disegna niente** (trappola #8): l'animazione parte da un
 * cambio di valore e finisce in [PASSO_MS] millisecondi. Con [ferme] - le
 * animazioni ridotte, dell'app o del sistema - il numero si sostituisce di
 * colpo, com'era prima.
 *
 * Lo scorrimento e' corto, un terzo dell'altezza, e non viene ritagliato: lo
 * stile dei numeri grandi ha un'interlinea piu' bassa del carattere
 * (`SalaType.giant`), e un ritaglio sul riquadro mangerebbe la testa delle
 * cifre anche da ferme.
 */
@Composable
fun CifreCheScorrono(
    /** Il numero com'e' scritto: "21", "-3", "--". */
    testo: String,
    /** Il suo valore, per sapere se sale o scende; `null` se non c'e'. */
    valore: Int?,
    style: TextStyle,
    color: Color,
    ferme: Boolean,
    modifier: Modifier = Modifier,
) {
    if (ferme) {
        Text(text = testo, style = style, color = color, modifier = modifier)
        return
    }

    // Non uno stato osservabile: serve solo a ricordare da dove si viene, e
    // scriverlo non deve far ricomporre nessuno.
    val memoria = remember { Memoria(valore) }
    if (memoria.prima != valore) {
        memoria.sale = (valore ?: 0) >= (memoria.prima ?: 0)
        memoria.prima = valore
    }

    Row(modifier = modifier) {
        testo.forEachIndexed { indice, cifra ->
            key(testo.length - indice) {
                AnimatedContent(
                    targetState = cifra,
                    transitionSpec = {
                        val verso = if (memoria.sale) 1 else -1
                        (slideInVertically(tween(PASSO_MS)) { verso * it / 3 } + fadeIn(tween(PASSO_MS)))
                            .togetherWith(slideOutVertically(tween(PASSO_MS)) { -verso * it / 3 } + fadeOut(tween(PASSO_MS)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "cifra",
                ) { mostrata ->
                    Text(text = mostrata.toString(), style = style, color = color)
                }
            }
        }
    }
}

private class Memoria(var prima: Int?) {
    var sale: Boolean = true
}
