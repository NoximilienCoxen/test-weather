package com.caelumweather.lingua

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import com.caelumweather.sync.SincronizzaOrologio

/**
 * La scelta della lingua, salvata sul telefono. La parte pura (`Lingue`, `tr`)
 * sta nel modulo `core`, che gira anche sull'orologio e non ha un `Context`.
 *
 * Da Android 13 la stessa scelta sta anche nelle impostazioni di sistema
 * (Lingue delle app), e li' **comanda il sistema**: una voce che dice
 * "English" mentre l'app parla italiano sarebbe una bugia in casa d'altri.
 * Le due voci sono quindi una sola, scritta in due posti: cambiata qui si
 * scrive la', cambiata la' si rilegge qui a ogni avvio dell'attivita'.
 */
private const val FILE = "lingua"
private const val CHIAVE = "scelta"

/**
 * La scelta fatta nell'app prima che il sistema la conoscesse si porta di la'
 * una volta sola. Senza il segno, chi rimette "predefinita di sistema" si
 * vedrebbe tornare la vecchia lingua fissa a ogni avvio.
 */
private const val PASSATA_AL_SISTEMA = "passata_al_sistema"

/**
 * Legge la scelta. Vero se e' cambiata rispetto a quella in memoria: succede
 * quando la lingua e' stata cambiata dalle impostazioni di sistema.
 */
fun Lingue.inizializza(context: Context): Boolean {
    val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    val salvata = SceltaLingua.entries.firstOrNull { it.name == prefs.getString(CHIAVE, null) }
        ?: SceltaLingua.AUTOMATICA
    val nuova = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Un servizio di sistema che manca o rifiuta non deve fermare l'avvio.
        runCatching { allineaAlSistema(context, prefs, salvata) }.getOrDefault(salvata)
    } else {
        salvata
    }
    val cambiata = nuova != scelta
    impostaScelta(nuova)
    return cambiata
}

fun Lingue.scegli(context: Context, nuova: SceltaLingua) {
    impostaScelta(nuova)
    context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        .putString(CHIAVE, nuova.name)
        .putBoolean(PASSATA_AL_SISTEMA, true)
        .apply()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { context.getSystemService(LocaleManager::class.java)?.applicationLocales = nuova.perIlSistema() }
    }
    // La lingua non passa dal flusso delle impostazioni: l'orologio va avvisato a mano.
    SincronizzaOrologio.linguaCambiata()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun allineaAlSistema(context: Context, prefs: SharedPreferences, salvata: SceltaLingua): SceltaLingua {
    val gestore = context.getSystemService(LocaleManager::class.java) ?: return salvata
    if (!prefs.getBoolean(PASSATA_AL_SISTEMA, false)) {
        prefs.edit().putBoolean(PASSATA_AL_SISTEMA, true).apply()
        if (salvata != SceltaLingua.AUTOMATICA) {
            gestore.applicationLocales = salvata.perIlSistema()
            return salvata
        }
    }
    val delSistema = gestore.applicationLocales
    // Una lingua che l'app non parla - puo' arrivare solo da fuori
    // dell'elenco di `locales_config.xml` - si legge in inglese.
    val scelta = when {
        delSistema.isEmpty -> SceltaLingua.AUTOMATICA
        else -> SceltaLingua.di(Lingua.diCodice(delSistema[0].language) ?: Lingua.INGLESE)
    }
    if (scelta != salvata) prefs.edit().putString(CHIAVE, scelta.name).apply()
    return scelta
}

private fun SceltaLingua.perIlSistema(): LocaleList =
    lingua?.let { LocaleList.forLanguageTags(it.codice) } ?: LocaleList.getEmptyLocaleList()
