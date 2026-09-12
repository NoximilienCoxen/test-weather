package io.github.noximiliencoxen.caelum.ui.motion

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import io.github.noximiliencoxen.caelum.ui.sala.CICLO_LAMPO
import io.github.noximiliencoxen.caelum.ui.sala.Caduta
import io.github.noximiliencoxen.caelum.ui.sala.Corsie
import io.github.noximiliencoxen.caelum.ui.sala.Scena
import kotlin.math.floor
import kotlin.math.min

/**
 * La pioggia si sente in mano, e il tuono si sente di piu'.
 *
 * Il permesso `VIBRATE` era dichiarato nel manifesto da sempre con quel
 * commento sopra, e non lo usava nessuno: il file che lo usava e' uscito col
 * vecchio feed. Questo lo rimette al lavoro.
 *
 * ### Tre regole, e la prima e' la meno ovvia
 *
 * **1. Un tetto dichiarato, e non e' un tradimento della richiesta.** Con la
 * pioggia fitta le corsie toccano terra **decine di volte al secondo**: farle
 * sentire tutte non da' "la pioggia in mano", da' un ronzio continuo, che in
 * mano si sente come un guasto del telefono e non come tempo atmosferico. Il
 * tetto lascia passare il ritmo - piu' fitto quando piove piu' forte, rado
 * quando pioviggina - e taglia solo cio' che oltre non si distingueva comunque.
 *
 * **2. Gli istanti non li decide questo file.** Li calcola [Corsie], la stessa
 * che disegna le gocce, con la stessa formula. Un contatore suo andrebbe in
 * fase per un po' e poi scivolerebbe, e una vibrazione fuori tempo rispetto a
 * cio' che si vede e' peggio di nessuna vibrazione.
 *
 * **3. Non si vibra dal disegno.** Un `DrawScope` non fa effetti collaterali -
 * puo' essere invocato piu' volte per fotogramma, o nessuna. Il battito sta in
 * un `LaunchedEffect`, che e' anche l'unico posto da cui si puo' spegnere.
 */
class VibrazioniMeteo internal constructor(private val vibratore: Vibrator?) {

    private val dosabile: Boolean =
        vibratore != null && vibratore.hasVibrator() && vibratore.hasAmplitudeControl()

    private fun colpo(durataMs: Long, forza: Int) {
        val v = vibratore ?: return
        if (!v.hasVibrator()) return
        runCatching {
            // `hasAmplitudeControl` e' falso su parecchi telefoni: li' l'ampiezza
            // viene ignorata e resta solo la durata, quindi un colpetto lungo
            // dieci millisecondi resta comunque un colpetto.
            val ampiezza = if (dosabile) forza else VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(VibrationEffect.createOneShot(durataMs, ampiezza))
        }
    }

    /** La goccia: il piu' leggero che un motore riesca a dare. */
    internal fun goccia() = colpo(GOCCIA_MS, GOCCIA_FORZA)

    /** Il chicco: secco, e si sente che e' ghiaccio. */
    internal fun chicco() = colpo(CHICCO_MS, CHICCO_FORZA)

    /** Il tuono: un colpo pieno, e l'unico che si prende tutto il motore. */
    internal fun tuono() = colpo(TUONO_MS, TUONO_FORZA)

    /**
     * Lo scatto di un comando: l'ora che scavalca sulla barra.
     *
     * Non e' meteo, e' un **fermo**: dice al dito che quel continuo in realta'
     * ha ventiquattro tacche. Per questo e' piu' secco di una goccia e piu'
     * corto di un chicco - va sentito, non ascoltato.
     */
    fun scatto() = colpo(SCATTO_MS, SCATTO_FORZA)

    private companion object {
        const val GOCCIA_MS = 9L
        const val GOCCIA_FORZA = 48
        const val CHICCO_MS = 14L
        const val CHICCO_FORZA = 130
        const val TUONO_MS = 70L
        const val TUONO_FORZA = 255
        const val SCATTO_MS = 7L
        const val SCATTO_FORZA = 90
    }
}

@Composable
fun rememberVibrazioniMeteo(): VibrazioniMeteo {
    val context = LocalContext.current
    return remember(context) { VibrazioniMeteo(vibratoreDi(context)) }
}

private fun vibratoreDi(context: Context): Vibrator? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val gestore = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        gestore?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}.getOrNull()

/**
 * Fa sentire in mano cio' che cade in [scena], finche' [attiva].
 *
 * @param tempo lo **stesso** orologio che disegna la scena. Passarne un altro
 *   riaprirebbe lo scivolamento di fase che la regola 2 esiste per evitare.
 */
@Composable
fun VibrazioniDellaScena(scena: Scena, tempo: () -> Float, attiva: Boolean) {
    val vibrazioni = rememberVibrazioniMeteo()
    // Ogni valore letto dentro un ciclo che vive piu' a lungo della
    // composizione va preso da `rememberUpdatedState`: e' la trappola #7, che
    // in questo progetto e' gia' costata un'ora del giorno irraggiungibile per
    // sempre perche' un riconoscitore confrontava un valore congelato
    // all'apertura.
    val scenaOra by rememberUpdatedState(scena)
    val tempoOra by rememberUpdatedState(tempo)

    LaunchedEffect(attiva) {
        if (!attiva) return@LaunchedEffect
        var prima = tempoOra()
        var ultimaGoccia = -1f
        var ultimoLampo = -1f
        while (true) {
            withFrameNanos { }
            val ora = tempoOra()
            if (ora <= prima) { prima = ora; continue }
            val s = scenaOra

            // ── Cio' che cade ────────────────────────────────────────────────
            val asciutto = (1f - s.neve) * (1f - s.ghiaccio)
            if (s.bagnato > 0.01f) {
                val chicchi = Corsie.impatti(Caduta.GRANDINE, s.bagnato, prima, ora)
                val gocce = Corsie.impatti(Caduta.PIOGGIA, s.bagnato, prima, ora)
                // La grandine viene prima e ha la precedenza: quando grandina,
                // quello che si sente e' la grandine.
                if (chicchi > 0 && s.ghiaccio > 0.5f) {
                    if (ora - ultimaGoccia >= PAUSA_CHICCO) {
                        vibrazioni.chicco()
                        ultimaGoccia = ora
                    }
                } else if (gocce > 0 && asciutto > 0.5f) {
                    // Il tetto si allenta col crescere della pioggia: due colpi
                    // al secondo per una pioviggine, sei per un rovescio. E' il
                    // ritmo a dire quanto piove, non il singolo colpo.
                    val pausa = PAUSA_GOCCIA_MAX -
                        (PAUSA_GOCCIA_MAX - PAUSA_GOCCIA_MIN) * s.bagnato
                    if (ora - ultimaGoccia >= pausa) {
                        vibrazioni.goccia()
                        ultimaGoccia = ora
                    }
                }
            }

            // ── Il tuono ─────────────────────────────────────────────────────
            //
            // Il lampo e' periodico, quindi "ne e' passato uno" si chiede allo
            // stesso modo di una goccia: contando gli interi scavalcati. Il
            // colpo arriva col **bagliore**, non dopo: qui il temporale e' a
            // pochi metri, dentro una cassa da museo.
            if (s.tempesta > 0.5f) {
                val passati = floor(ora / CICLO_LAMPO) - floor(prima / CICLO_LAMPO)
                if (passati > 0 && ora - ultimoLampo >= CICLO_LAMPO * 0.5f) {
                    vibrazioni.tuono()
                    ultimoLampo = ora
                }
            }
            prima = ora
        }
    }
}

/** Il piu' fitto che si lascia passare: sei colpi al secondo. */
private const val PAUSA_GOCCIA_MIN = 1f / 6f

/** Il piu' rado: due al secondo, che e' una pioviggine. */
private const val PAUSA_GOCCIA_MAX = 1f / 2f

/** La grandine batte piu' rada della pioggia fitta: i chicchi sono piu' grossi
 *  e piu' radi, e sentirne otto al secondo non somiglierebbe a niente. */
private val PAUSA_CHICCO = min(0.22f, PAUSA_GOCCIA_MAX)
