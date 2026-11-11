package com.caelumweather.data

import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Le maree, dalle ore del livello del mare.
 *
 * **Non c'e' un elenco di "alte e basse" nell'API**: c'e' `sea_level_height_msl`,
 * il livello del mare sul livello medio, ora per ora, **maree comprese**. Gli
 * estremi si ricavano dalla curva: e' la stessa cosa che fa chi legge una
 * tabella di marea, solo fatta dal telefono.
 *
 * Valgono le avvertenze del resto del mare: e' un modello globale a celle di
 * chilometri. Nei porti, nelle lagune e con vento o pressione insolita il
 * livello vero si scosta anche di decine di centimetri dalla marea
 * astronomica - a Venezia e' proprio l'acqua alta. La sala lo scrive sotto i
 * numeri e dice di non usarli per navigare.
 */
enum class TipoMarea { ALTA, BASSA }

/** Un'alta o una bassa marea: quando, e a che livello (metri sul medio mare). */
data class EstremoMarea(val tipo: TipoMarea, val quando: LocalDateTime, val metri: Double)

/**
 * Quanto deve allontanarsi la curva da un estremo perche' sia una marea: sotto
 * i dieci centimetri e' un'ondina del modello, non un'alta o una bassa.
 */
const val ESCURSIONE_MINIMA_M = 0.10

/**
 * Gli estremi della curva, con l'ora raffinata al minuto. Puro.
 *
 * **Isteresi, non tre punti.** Cercare "un'ora piu' alta delle due accanto"
 * trova anche gli scalini da un centimetro che il modello fa in cima a una
 * marea bassa (visto su Venezia: 0,09 - 0,09 - 0,10 diventava una bassa e
 * un'alta a un'ora di distanza). Qui un massimo e' tale solo quando la curva
 * **scende** di almeno [ESCURSIONE_MINIMA_M] dopo di lui, e viceversa: gli
 * estremi escono per forza alternati, e quello in fondo alla serie - di cui
 * non si sa ancora se e' un picco - non si dichiara.
 *
 * L'ora e' intera, quindi il massimo vero cade quasi sempre **fra** due ore:
 * una parabola per i tre punti attorno all'estremo da' l'istante e il livello
 * con un errore di pochi minuti, che basta e non finge una precisione che i
 * dati non hanno. Le ore senza livello spezzano la curva: non si interpola
 * sopra un buco.
 */
fun estremiDiMarea(ore: List<OraMare>): List<EstremoMarea> {
    val trovati = mutableListOf<EstremoMarea>()
    // I tratti continui: ore consecutive, tutte con un livello.
    val tratti = ArrayList<ArrayList<OraMare>>()
    var corrente: ArrayList<OraMare>? = null
    for (o in ore) {
        if (o.livello == null) { corrente = null; continue }
        val ultima = corrente?.lastOrNull()
        if (corrente == null || ultima == null ||
            java.time.Duration.between(ultima.time, o.time).toMinutes() != 60L
        ) {
            corrente = ArrayList<OraMare>().also { tratti.add(it) }
        }
        corrente.add(o)
    }
    for (tratto in tratti) {
        if (tratto.size < 3) continue
        val y = tratto.map { it.livello!! }
        var alto = 0
        var basso = 0
        var salendo: Boolean? = null // vero: l'ultimo estremo confermato e' una bassa
        for (i in 1 until y.size) {
            if (y[i] > y[alto]) alto = i
            if (y[i] < y[basso]) basso = i
            when (salendo) {
                null -> {
                    // Il primo punto della serie non si dichiara mai un estremo:
                    // non si sa cosa faceva prima. Serve solo a dare il verso.
                    if (y[alto] - y[i] >= ESCURSIONE_MINIMA_M && alto != i) {
                        if (alto != 0) trovati += estremo(tratto, y, alto, TipoMarea.ALTA)
                        salendo = false; basso = i; alto = i
                    } else if (y[i] - y[basso] >= ESCURSIONE_MINIMA_M && basso != i) {
                        if (basso != 0) trovati += estremo(tratto, y, basso, TipoMarea.BASSA)
                        salendo = true; alto = i; basso = i
                    }
                }
                true -> if (y[alto] - y[i] >= ESCURSIONE_MINIMA_M) {
                    trovati += estremo(tratto, y, alto, TipoMarea.ALTA)
                    salendo = false; basso = i; alto = i
                }
                false -> if (y[i] - y[basso] >= ESCURSIONE_MINIMA_M) {
                    trovati += estremo(tratto, y, basso, TipoMarea.BASSA)
                    salendo = true; alto = i; basso = i
                }
            }
        }
    }
    return trovati.sortedBy { it.quando }
}

private fun estremo(tratto: List<OraMare>, y: List<Double>, i: Int, tipo: TipoMarea): EstremoMarea {
    if (i == 0 || i == y.size - 1) return EstremoMarea(tipo, tratto[i].time, y[i])
    val y0 = y[i - 1]
    val y1 = y[i]
    val y2 = y[i + 1]
    val denominatore = y0 - 2 * y1 + y2
    val scarto = if (abs(denominatore) < 1e-9) 0.0 else (0.5 * (y0 - y2) / denominatore).coerceIn(-0.5, 0.5)
    return EstremoMarea(
        tipo = tipo,
        quando = tratto[i].time.plusSeconds((scarto * 3600).toLong()),
        metri = y1 - 0.25 * (y0 - y2) * scarto,
    )
}

/** I prossimi [quanti] estremi da [da] in avanti. */
fun prossimiEstremi(mare: Mare, da: LocalDateTime, quanti: Int = 4): List<EstremoMarea> =
    estremiDiMarea(mare.ore).filter { !it.quando.isBefore(da) }.take(quanti)

/** Vero se il mare di questa localita' ha una marea da raccontare. */
fun haMarea(mare: Mare): Boolean = estremiDiMarea(mare.ore).isNotEmpty()
