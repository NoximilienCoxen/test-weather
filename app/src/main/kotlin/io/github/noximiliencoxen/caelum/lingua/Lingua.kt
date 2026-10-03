package io.github.noximiliencoxen.caelum.lingua

import android.content.Context
import java.util.Locale

/**
 * In che lingua parla l'app: italiano o inglese (CONTESTO §49).
 *
 * **Un testo si scrive dove lo si usa, in tutte e due le lingue**: `tr("Pioggia
 * in arrivo", "Rain on the way")`. Non le risorse di Android, e la ragione e'
 * dove stanno i testi: nelle composable, ma anche in funzioni pure (i titoli
 * delle sale, le notifiche, i nomi dei fenomeni nei dati, le scritte dipinte
 * nei widget) che le prove leggono senza Android, e accanto a commenti che
 * ragionano proprio su quelle parole. Le risorse vorrebbero un `Context` in
 * ognuno di quei posti, e allontanerebbero la frase dalla sua spiegazione.
 *
 * **In italiano non cambia niente**: [tr] restituisce la stessa stringa di
 * prima, e gli scatti della CI lo dimostrano (`scripts/confronta_scatti.py`).
 * Il prezzo e' che una terza lingua vorrebbe un terzo argomento ovunque: se
 * servira', e' il momento di passare alle risorse, e tutte le frasi saranno gia'
 * censite dalle chiamate a [tr].
 */
enum class Lingua { ITALIANO, INGLESE }

/** La scelta di chi usa l'app: seguire il telefono, o una lingua fissa. */
enum class SceltaLingua { AUTOMATICA, ITALIANO, INGLESE }

object Lingue {

    /**
     * La scelta salvata. Non in DataStore come le altre impostazioni ma in una
     * SharedPreferences, che si legge **in modo sincrono**: la lingua serve
     * prima del primo fotogramma, e ai widget e ai lavori in sottofondo che non
     * hanno un flusso da raccogliere.
     */
    @Volatile
    var scelta: SceltaLingua = SceltaLingua.AUTOMATICA
        private set

    /**
     * Per la cattura in CI e per le prove: una lingua imposta, che vince su
     * tutto. L'emulatore della CI e' in inglese, e senza questa gli scatti
     * italiani cambierebbero lingua.
     */
    @Volatile
    var forzata: Lingua? = null

    val corrente: Lingua
        get() = forzata ?: when (scelta) {
            SceltaLingua.ITALIANO -> Lingua.ITALIANO
            SceltaLingua.INGLESE -> Lingua.INGLESE
            // Chi ha il telefono in italiano legge l'italiano; tutti gli altri
            // l'inglese, che e' la lingua che un tedesco o un giapponese ha
            // piu' probabilita' di leggere dell'italiano.
            SceltaLingua.AUTOMATICA ->
                if (Locale.getDefault().language == "it") Lingua.ITALIANO else Lingua.INGLESE
        }

    /** Il `Locale` da usare per numeri e date nella lingua corrente. */
    val locale: Locale
        get() = if (corrente == Lingua.INGLESE) Locale.UK else Locale.ITALY

    fun inizializza(context: Context) {
        val salvata = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(CHIAVE, null)
        scelta = SceltaLingua.entries.firstOrNull { it.name == salvata } ?: SceltaLingua.AUTOMATICA
    }

    fun scegli(context: Context, nuova: SceltaLingua) {
        scelta = nuova
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(CHIAVE, nuova.name).apply()
    }

    private const val FILE = "lingua"
    private const val CHIAVE = "scelta"
}

/** Il testo nella lingua corrente: italiano o inglese. */
fun tr(italiano: String, inglese: String): String =
    if (Lingue.corrente == Lingua.INGLESE) inglese else italiano

/** Per le poche cose che cambiano forma e non solo parole (l'ordine, una lista). */
fun inInglese(): Boolean = Lingue.corrente == Lingua.INGLESE
