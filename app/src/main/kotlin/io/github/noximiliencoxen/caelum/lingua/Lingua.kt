package io.github.noximiliencoxen.caelum.lingua

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.noximiliencoxen.caelum.prefs.SettingsPrefs
import kotlinx.coroutines.flow.first
import java.util.Locale

/** La lingua scelta: quella del telefono, o una delle due a mano. */
enum class SceltaLingua { AUTO, ITALIANO, INGLESE }

/**
 * In che lingua parla l'app: italiano o inglese.
 *
 * **I testi stanno nel codice, accanto alla loro traduzione, e non in
 * `strings.xml`.** Sono centinaia di frasi composte con numeri, citta' e ore,
 * scritte vicino al disegno che accompagnano; separarle in un file di risorse
 * vorrebbe dire leggere ogni schermata con due file aperti. [tr] le tiene
 * affiancate: chi cambia l'italiano vede subito l'inglese da cambiare.
 *
 * Lo stato e' osservabile da Compose: cambiando lingua nelle impostazioni ogni
 * schermata si ricompone da se'. Widget, notifiche e lavori in background, che
 * non hanno una schermata, chiamano [carica] prima di scrivere.
 */
object Lingua {

    /**
     * Vero quando si parla inglese. Parte in italiano, la lingua in cui l'app
     * e' nata, e passa all'altra appena le impostazioni sono lette (vedi
     * [applica]): cosi' le prove sul computer, che gira in inglese, leggono
     * le stesse frasi di sempre.
     */
    var inglese: Boolean by mutableStateOf(false)
        private set

    /** Il `Locale` per numeri e date: la virgola italiana o il punto inglese. */
    val locale: Locale get() = if (inglese) Locale.ENGLISH else Locale.ITALIAN

    fun applica(scelta: SceltaLingua) {
        inglese = when (scelta) {
            SceltaLingua.AUTO -> sistemaInglese()
            SceltaLingua.ITALIANO -> false
            SceltaLingua.INGLESE -> true
        }
    }

    /** Legge la scelta salvata: per chi scrive fuori da una schermata. */
    suspend fun carica(context: Context) {
        runCatching { applica(SettingsPrefs(context).settings.first().lingua) }
    }

    /** L'italiano resta l'italiano; ogni altra lingua del telefono diventa inglese. */
    private fun sistemaInglese(): Boolean = Locale.getDefault().language != "it"
}

/** Il testo nella lingua corrente: prima l'italiano, poi l'inglese. */
fun tr(it: String, en: String): String = if (Lingua.inglese) en else it
