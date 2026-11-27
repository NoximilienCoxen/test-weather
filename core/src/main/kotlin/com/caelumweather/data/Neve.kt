package com.caelumweather.data

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * La neve attesa, per chi sta in montagna: quanti centimetri e fino a che
 * quota.
 *
 * **"Quota neve" non e' un dato dell'API, e questa funzione lo dice.** Open-Meteo
 * da' la quota dello zero termico (`freezing_level_height`), cioe' dove l'aria
 * e' a 0 °C. La neve arriva di solito qualche centinaio di metri **sotto**:
 * una falda ci mette del tempo a sciogliersi, e fondendosi raffredda l'aria che
 * attraversa. La regola dei bollettini di montagna e' tre-quattrocento metri;
 * qui si prendono trecento. E' una stima, e le righe che la mostrano la
 * chiamano "circa".
 */
const val SCARTO_QUOTA_NEVE_M = 300

/** Sotto questo, una "nevicata" e' un'ora di nevischio che non si vede. */
const val NEVE_MINIMA_CM = 0.1

/** La quota a cui si stima che nevichi, in metri, a passi di cinquanta. Nulla senza zero termico. */
fun quotaNeve(zeroTermico: Double?): Int? {
    if (zeroTermico == null || !zeroTermico.isFinite()) return null
    val metri = (zeroTermico - SCARTO_QUOTA_NEVE_M).coerceAtLeast(0.0)
    return ((metri / 50.0).roundToInt() * 50)
}

/**
 * Una giornata con la neve davanti.
 *
 * @param cm centimetri attesi nel giorno (`snowfall_sum`).
 * @param quota la quota piu' bassa a cui si stima che nevichi, fra le ore in
 *   cui nevica: e' la domanda di chi risale una valle. Nulla se l'API non da'
 *   lo zero termico (una scorta vecchia).
 */
data class NeveDelGiorno(val data: LocalDate, val etichetta: String, val cm: Double, val quota: Int?)

/**
 * I giorni della previsione in cui nevica, in ordine. Puro.
 *
 * Un giorno conta dai [NEVE_MINIMA_CM] in su; la quota si prende dalle ore con
 * neve e, se l'API dice neve nel giorno ma nessuna ora ne porta traccia, da
 * quelle in cui c'e' precipitazione.
 */
fun nevePrevista(previsione: Forecast): List<NeveDelGiorno> =
    previsione.days.mapNotNull { g ->
        val cm = g.snowfallSum ?: return@mapNotNull null
        if (cm < NEVE_MINIMA_CM) return@mapNotNull null
        val ore = previsione.hoursOf(g.date)
        val conNeve = ore.filter { (it.snowfall ?: 0.0) > 0.0 }
        val daGuardare = conNeve.ifEmpty { ore.filter { (it.precipitation ?: 0.0) > 0.0 } }
        val quota = daGuardare.mapNotNull { quotaNeve(it.freezingLevel) }.minOrNull()
        NeveDelGiorno(g.date, g.label, cm, quota)
    }

/** Dove sta il posto rispetto alla quota neve. */
enum class PostoSottoLaNeve {
    /** Il posto e' piu' in alto della quota: qui nevica. */
    NEVICA_QUI,

    /** A meno di duecento metri dalla quota: dipende dall'ora, dal vento, dal versante. */
    AL_LIMITE,

    /** Piu' in basso: la neve resta sopra. */
    PIU_IN_ALTO,
}

/**
 * Il posto rispetto alla quota: nullo se la quota del posto o della neve non si
 * conoscono.
 */
fun postoRispettoAllaNeve(quotaPosto: Double?, quotaNeve: Int?): PostoSottoLaNeve? {
    if (quotaPosto == null || quotaNeve == null) return null
    val scarto = quotaPosto - quotaNeve
    return when {
        scarto >= 200 -> PostoSottoLaNeve.NEVICA_QUI
        scarto > -200 -> PostoSottoLaNeve.AL_LIMITE
        else -> PostoSottoLaNeve.PIU_IN_ALTO
    }
}

/**
 * Sotto questo la neve al suolo non si conta: i modelli lasciano spesso qualche
 * millimetro dopo una spruzzata, e una scheda per un velo che non si vede
 * sarebbe rumore.
 */
const val NEVE_AL_SUOLO_MINIMA_CM = 1.0

/**
 * La neve al suolo all'ora [indiceOra] delle ventiquattro di oggi
 * (`Forecast.hours`, la stessa lista di `UiState.nowIndex`), in centimetri.
 * Nulla se l'API non la da' (una scorta vecchia, o un modello che non la ha).
 */
fun neveAlSuolo(previsione: Forecast, indiceOra: Int): Double? =
    previsione.hours.getOrNull(indiceOra)?.snowDepth
        ?: previsione.hours.firstNotNullOfOrNull { it.snowDepth }

/**
 * Vero se la scheda "La neve" ha motivo di esserci: neve prevista nei giorni
 * della previsione, o neve al suolo adesso.
 *
 * **La quota del posto non basta piu'.** Finche' la neve era una sezione in
 * fondo alla pioggia, sopra i mille metri compariva sempre, anche ad agosto, per
 * dire "niente neve, zero termico a 3800 m". Come scheda a se' sarebbe una
 * pagina in piu' da sfogliare per dire che non c'e' niente. Lo zero termico si
 * legge nella scheda quando la neve c'e'; senza neve, ne' prevista ne' al
 * suolo, non risponde a una domanda che uno si stia facendo (CONTESTO §85).
 */
fun serveLaNeve(previsione: Forecast?, indiceOra: Int = 0): Boolean {
    if (previsione == null) return false
    if (nevePrevista(previsione).isNotEmpty()) return true
    return (neveAlSuolo(previsione, indiceOra) ?: 0.0) >= NEVE_AL_SUOLO_MINIMA_CM
}
