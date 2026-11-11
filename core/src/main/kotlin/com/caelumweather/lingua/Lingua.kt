package com.caelumweather.lingua

import java.util.Locale

/**
 * In che lingua parla l'app: italiano, inglese, tedesco, francese o spagnolo
 * (CONTESTO §49 e §62).
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
 *
 * **Le altre tre lingue sono altri tre argomenti, e si e' scelto cosi'.** La
 * nota di prima diceva che alla terza lingua sarebbe stato il momento di
 * passare alle risorse. Non lo si e' fatto: le ragioni per cui i testi stanno
 * qui valgono uguali con cinque lingue, e settecento frasi spostate in un file
 * XML perderebbero il commento che le spiega e il `Context` che non hanno. Il
 * tedesco, il francese e lo spagnolo stanno **accanto** all'italiano e
 * all'inglese, nella stessa chiamata: chi cambia una frase le vede tutte e
 * cinque. Dove una manca - un testo aggiunto di fretta - si legge l'inglese,
 * che e' un difetto visibile e non un crash.
 */
enum class Lingua(
    /** Il codice a due lettere: per le API che chiedono una lingua, e per il sistema. */
    val codice: String,
) {
    ITALIANO("it"),
    INGLESE("en"),
    TEDESCO("de"),
    FRANCESE("fr"),
    SPAGNOLO("es"),
    ;

    companion object {
        /** La lingua di un codice ("de", "de-AT"), o nulla se l'app non la parla. */
        fun diCodice(codice: String?): Lingua? {
            val due = codice?.lowercase()?.take(2) ?: return null
            return entries.firstOrNull { it.codice == due }
        }
    }
}

/**
 * La scelta di chi usa l'app: seguire il telefono, o una lingua fissa.
 *
 * **I nomi non si cambiano**: viaggiano verso l'orologio come testo
 * (`StatoSincronizzato.lingua`) e stanno scritti nelle preferenze.
 */
enum class SceltaLingua(val lingua: Lingua?) {
    AUTOMATICA(null),
    ITALIANO(Lingua.ITALIANO),
    INGLESE(Lingua.INGLESE),
    TEDESCO(Lingua.TEDESCO),
    FRANCESE(Lingua.FRANCESE),
    SPAGNOLO(Lingua.SPAGNOLO),
    ;

    companion object {
        fun di(lingua: Lingua?): SceltaLingua = entries.firstOrNull { it.lingua == lingua } ?: AUTOMATICA
    }
}

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
        get() = forzata ?: scelta.lingua
            // Chi ha il telefono in una delle cinque lingue legge quella;
            // tutti gli altri l'inglese, che e' la lingua che un olandese o un
            // giapponese ha piu' probabilita' di leggere.
            ?: Lingua.diCodice(Locale.getDefault().language)
            ?: Lingua.INGLESE

    /** Il `Locale` da usare per numeri e date nella lingua corrente. */
    val locale: Locale
        get() = when (corrente) {
            Lingua.ITALIANO -> Locale.ITALY
            Lingua.INGLESE -> Locale.UK
            Lingua.TEDESCO -> Locale.GERMANY
            Lingua.FRANCESE -> Locale.FRANCE
            Lingua.SPAGNOLO -> LOCALE_SPAGNA
        }

    /**
     * Imposta la scelta. La persistenza non sta qui: sul telefono la tiene
     * `Lingue.inizializza`/`Lingue.scegli` (modulo `app`, una SharedPreferences),
     * sull'orologio arriva dal telefono. Questo file non conosce Android.
     */
    fun impostaScelta(nuova: SceltaLingua) {
        scelta = nuova
    }
}

private val LOCALE_SPAGNA = Locale.forLanguageTag("es-ES")

/**
 * Il testo nella lingua corrente.
 *
 * Italiano e inglese ci sono sempre; tedesco, francese e spagnolo quasi
 * sempre. **Dove uno manca si legge l'inglese**: e' la stessa regola con cui
 * si sceglie la lingua di chi ha il telefono in olandese.
 */
fun tr(
    italiano: String,
    inglese: String,
    tedesco: String? = null,
    francese: String? = null,
    spagnolo: String? = null,
): String = when (Lingue.corrente) {
    Lingua.ITALIANO -> italiano
    Lingua.INGLESE -> inglese
    Lingua.TEDESCO -> tedesco ?: inglese
    Lingua.FRANCESE -> francese ?: inglese
    Lingua.SPAGNOLO -> spagnolo ?: inglese
}

/**
 * Vero quando la lingua corrente **non e' l'italiano**.
 *
 * Il nome e' rimasto dai giorni in cui le lingue erano due. Chi lo chiama
 * vuole quasi sempre sapere questo - "sto scrivendo l'originale o una
 * traduzione?" - e non "e' proprio l'inglese?": per quello c'e'
 * `Lingue.corrente`.
 */
fun inInglese(): Boolean = Lingue.corrente != Lingua.ITALIANO

/**
 * Un nome da mettere in mezzo a una frase: minuscolo, **tranne in tedesco**,
 * dove i nomi tengono la maiuscola ("Warnstufe Gelb für Gewitter").
 *
 * Le etichette degli elenchi sono scritte tutte maiuscole ("TEMPORALI",
 * "GEWITTER") e le altre lingue le abbassano per intero. In tedesco si
 * rialza l'iniziale di ogni parola, lasciando basse le poche parole piccole e
 * alte le sigle ("UV-Strahlung"). Cio' che non e' tutto maiuscolo e' gia'
 * scritto come va scritto, e resta com'e'.
 */
fun String.inMezzoAllaFrase(): String {
    if (Lingue.corrente != Lingua.TEDESCO) return lowercase(Lingue.locale)
    if (this != uppercase(Locale.GERMANY)) return this
    return split(' ').joinToString(" ") { parola ->
        if (parola.lowercase(Locale.GERMANY) in PAROLE_PICCOLE) {
            parola.lowercase(Locale.GERMANY)
        } else {
            parola.split('-').joinToString("-") { pezzo ->
                if (pezzo.length <= 2) pezzo
                else pezzo.lowercase(Locale.GERMANY).replaceFirstChar { it.titlecase(Locale.GERMANY) }
            }
        }
    }
}

/**
 * Cio' che in tedesco resta minuscolo anche dentro un'etichetta: le parole
 * piccole, e gli aggettivi e avverbi che compaiono negli elenchi dei nomi
 * ("meist klar", "sehr schlecht", "zunehmende Sichel"). I colori delle allerte
 * non ci sono apposta: "Warnstufe Gelb" li vuole maiuscoli.
 */
private val PAROLE_PICCOLE = setOf(
    "und", "oder", "vor", "für", "von", "der", "die", "das", "im", "in", "mit",
    "klar", "meist", "teils", "bewölkt", "bedeckt", "gefrierender",
    "gut", "ordentlich", "mittel", "schlecht", "sehr", "extrem",
    "zunehmende", "zunehmender", "abnehmende", "abnehmender", "erstes", "letztes",
    "nächste",
)
