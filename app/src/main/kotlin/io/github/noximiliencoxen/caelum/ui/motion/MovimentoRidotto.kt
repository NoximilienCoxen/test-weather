package io.github.noximiliencoxen.caelum.ui.motion

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Il "meno movimento" che chi usa il telefono ha chiesto **al sistema**.
 *
 * Su una pagina web questo si chiama `prefers-reduced-motion` e lo si legge da
 * una media query. Su Android non c'e' una media query: c'e' un'impostazione di
 * sistema, `Settings.Global.ANIMATOR_DURATION_SCALE`, che vale zero quando si
 * spengono le animazioni. E' lo stesso interruttore, e chi lo mette a zero lo
 * mette a zero **per tutte le app**, non solo per quelle che si e' ricordato di
 * configurare a mano.
 *
 * Caelum un suo interruttore ce l'aveva gia', nelle impostazioni, e faceva il
 * suo mestiere: fermava l'orologio della scena, e con lui sole, nuvole,
 * pulviscolo, uccelli e vibrazioni. Solo che **lo leggeva solo li'**. Chi
 * spegneva le animazioni nel telefono - per vertigini, per mal d'auto, o
 * perche' un telefono lento va meglio cosi' - apriva Caelum e trovava un cielo
 * che si muoveva comunque, e doveva scoprire che c'era una seconda levetta da
 * abbassare. Due interruttori per la stessa richiesta sono un interruttore di
 * troppo.
 *
 * I due si sommano e non si sostituiscono: `o` e non `e`. Chi ha spento nel
 * sistema non deve spegnere anche qui; chi vuole meno movimento **solo** in
 * questa app puo' continuare a chiederlo qui.
 *
 * Si legge una volta per composizione e non si osserva: cambiare
 * quell'impostazione richiede di uscire dall'app e passare per le impostazioni
 * di sistema, e al ritorno l'attivita' si ricompone comunque.
 */
@Composable
fun sistemaSenzaAnimazioni(): Boolean {
    val contesto = LocalContext.current
    return remember(contesto) {
        runCatching {
            Settings.Global.getFloat(
                contesto.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}
