package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * Quello che si muove in Sala I: precipitazioni, uccelli, stelle.
 *
 * **L'orologio e' esplicito, non una comodita' della libreria.** Il progetto ha
 * gia' pagato questa lezione (trappola #17): `rememberInfiniteTransition` letto
 * solo dentro il disegno non anima affatto, e le gocce del vecchio feed
 * sembravano cadere mentre erano ferme - misurato, zero fotogrammi. Qui il
 * tempo lo batte un `withFrameNanos` dentro un `LaunchedEffect`, e ogni battito
 * scrive un numero che il disegno legge.
 *
 * **Il tempo non torna indietro.** Prima l'istante di partenza si azzerava a
 * ogni riaccensione, e bastava sfogliare a un'altra sala e tornare perche' la
 * pioggia risalisse in cima. Adesso quello che si accumula resta accumulato:
 * l'orologio puo' fermarsi e ripartire, ma non riavvolgersi. Da quando i valori
 * della scena si animano la cosa e' diventata portante, non piu' un dettaglio -
 * l'orologio si spegne e si riaccende **mentre** una transizione e' in volo.
 */
@Composable
fun rememberTempoScena(attivo: Boolean): () -> Float {
    val tempo = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(attivo) {
        if (!attivo) return@LaunchedEffect
        val accumulato = tempo.floatValue
        var inizio = 0L
        while (true) {
            withFrameNanos { ora ->
                if (inizio == 0L) inizio = ora
                tempo.floatValue = accumulato + (ora - inizio) / 1_000_000_000f
            }
        }
    }
    return { tempo.floatValue }
}

private const val PI_F = PI.toFloat()

/**
 * Numeri sparsi ma sempre gli stessi.
 *
 * Un generatore vero darebbe un cielo diverso a ogni avvio, e un cielo che
 * cambia quando si riapre l'app non e' un cielo. Questo e' deterministico,
 * dipende solo dall'indice, e basta a togliere l'aria di griglia.
 */
private fun sparso(i: Int, sale: Int): Float {
    val x = sin(i * 12.9898f + sale * 78.233f) * 43758.547f
    return x - floor(x)
}

// ─────────────────────────────────────────────────────────────────────────────
// Gli uccelli
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Un uccello: la corsia in cui vola, la fase, quanto e' veloce, quanto grande.
 *
 * Tre e non uno stormo: uno solo si legge come un difetto del disegno, dieci
 * come un'invasione. Tre a distanze diverse dicono "cielo aperto" e basta.
 */
private class Uccello(val corsia: Float, val fase: Float, val passo: Float, val taglia: Float)

// ─────────────────────────────────────────────────────────────────────────────
// Il cielo di notte
// ─────────────────────────────────────────────────────────────────────────────

/** Quante stelle ha il cielo di Sala I. */
private const val STELLE = 72

/** Fin dove scende il cielo, in frazione di schermo. Sotto c'e' la scultura,
 *  poi il numero, poi la didascalia: non e' cielo, e' pagina scritta. */
private const val SOFFITTO = 0.52f

/**
 * Il cielo stellato, **a tutta pagina e dietro ogni cosa**.
 *
 * Prima le stelle erano dieci, misurate in unita' della scultura e disegnate
 * dentro la sua cassa: erano un ornamento attorno a un oggetto, non un cielo.
 * E c'erano solo a notte serena, cioe' proprio nel caso in cui contano meno -
 * **le nuvole non spengono le stelle, le coprono**, e da sotto una notte
 * coperta qualcuna si vede lo stesso, negli strappi.
 *
 * Quindi: schermo intero, sempre di notte, e il velo che **cala** con la
 * copertura invece di azzerarsi. Lo decide il chiamante.
 *
 * Posizioni deterministiche: lo stesso cielo ogni notte. Il tremolio ha una
 * fase per stella ricavata dalla posizione, cosi' il cielo respira invece di
 * lampeggiare - un tremolio sincronizzato si legge come un difetto dello
 * schermo, non come un cielo.
 */
fun DrawScope.cieloStellato(tempo: Float, inchiostro: Color, velo: Float) {
    if (velo <= 0.01f) return
    for (i in 0 until STELLE) {
        val x = sparso(i, 1) * size.width
        // **Si fermano dove comincia il testo.** Erano fitte in alto e rade in
        // basso, il che non bastava: qualcuna finiva comunque dietro la
        // didascalia e accanto al numero dei gradi, e una stella dietro una
        // parola non e' un astro, e' sporco sulla pagina. Adesso il cielo
        // occupa la meta' alta e sfuma a zero prima di arrivarci.
        val alto = sparso(i, 2)
        val y = alto * alto * size.height * SOFFITTO
        val luce = 0.35f + sparso(i, 7) * 0.65f
        val tremolio = 0.62f + 0.38f * sin(tempo * 1.6f + (x * 0.031f + y * 0.017f))
        // L'ultimo quarto del cielo sfuma: un bordo netto si leggerebbe come
        // una riga, e una riga nel cielo e' peggio di una stella di troppo.
        val sfumo = ((SOFFITTO * size.height - y) / (size.height * 0.18f)).coerceIn(0f, 1f)
        drawCircle(
            color = inchiostro.copy(alpha = (velo * luce * tremolio * sfumo * 0.9f).coerceIn(0f, 1f)),
            radius = size.width * (0.0016f + luce * 0.0026f),
            center = Offset(x, y),
        )
    }

    // Una ogni dodici secondi circa, e dura mezzo secondo: il resto del tempo il
    // cielo sta fermo, che e' quello che fa un cielo. Se ne passasse una ogni
    // due secondi non sarebbe piu' un avvenimento, e un avvenimento che si
    // ripete smette di essere guardato.
    val ciclo = 12f
    val dentro = tempo % ciclo
    if (dentro < 0.55f) {
        val t = dentro / 0.55f
        val quale = (tempo / ciclo).toInt()
        val lunghezza = size.width * 0.26f
        val cx = sparso(quale, 8) * size.width * 0.9f + t * lunghezza * 1.9f
        val cy = sparso(quale, 9) * size.height * 0.32f + t * lunghezza * 0.62f
        val svanire = sin(t * PI_F)
        drawLine(
            color = inchiostro.copy(alpha = (velo * svanire * 0.9f).coerceIn(0f, 1f)),
            start = Offset(cx, cy),
            end = Offset(cx - lunghezza * 0.5f, cy - lunghezza * 0.17f),
            strokeWidth = size.width * 0.0022f,
            cap = StrokeCap.Round,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cio' che cade
// ─────────────────────────────────────────────────────────────────────────────

/** Di che natura e' cio' che cade. */
enum class Caduta { PIOGGIA, NEVE, GRANDINE }

/**
 * Le corsie di caduta, e la matematica che le governa.
 *
 * **Sta in un oggetto e non dentro il disegno perche' ha due lettori.** Le
 * gocce si disegnano, ma **si sentono anche**: la vibrazione deve battere
 * quando la goccia tocca, e un disegno non puo' produrre effetti collaterali.
 * Con gli istanti d'impatto ricavati da una formula - non da uno stato - le due
 * cose leggono lo stesso numero senza parlarsi, e non possono sfasarsi.
 */
object Corsie {
    /** Quante ce ne sono in tutto. Si accendono in ordine sparso al crescere
     *  dell'intensita': una soglia sola le farebbe comparire tutte insieme. */
    const val QUANTE = 14

    /** Quanto dura la fioritura sulla carta dopo che una goccia ha toccato. */
    const val FIORITURA = 0.5f

    /** In che ordine le corsie si accendono. Sparso, se no la pioggia
     *  comincerebbe da un lato e si allargherebbe come una tenda. */
    private val Ordine = intArrayOf(7, 1, 11, 4, 13, 0, 9, 5, 2, 12, 8, 3, 10, 6)

    /** Da quale lato dello spazio-modello scende. */
    fun x(i: Int): Float = (sparso(i, 21) - 0.5f) * 1.30f

    /**
     * Quanto e' vicina, da 0 (in fondo) a 1 (davanti).
     *
     * **Senza questo la pioggia era una fila di segni tutti uguali**, e una
     * pioggia in cui ogni goccia e' grande come le altre e va alla stessa
     * velocita' e' una grata, non un temporale. Le vicine sono piu' grandi,
     * piu' svelte e piu' opache; le lontane quasi un'ombra.
     */
    fun profondita(i: Int): Float = sparso(i, 22)

    fun sfasatura(i: Int): Float = sparso(i, 23)

    fun velocita(tipo: Caduta, i: Int): Float {
        val base = when (tipo) {
            Caduta.PIOGGIA -> 0.62f
            Caduta.NEVE -> 0.17f
            Caduta.GRANDINE -> 0.95f
        }
        return base * (0.72f + 0.56f * profondita(i))
    }

    /** Quanto e' accesa questa corsia, data l'intensita' 0..1. */
    fun accesa(i: Int, presenza: Float): Float {
        val posto = Ordine.indexOf(i).coerceAtLeast(0)
        return (presenza * QUANTE - posto).coerceIn(0f, 1f)
    }

    /**
     * Il giro in corso della corsia: la parte intera conta gli impatti gia'
     * avvenuti, la frazione dice a che punto della discesa si e'.
     */
    fun corsa(tipo: Caduta, i: Int, tempo: Float): Float =
        tempo * velocita(tipo, i) + sfasatura(i)

    /**
     * Quante gocce hanno toccato fra due istanti.
     *
     * E' la stessa formula che il disegno usa per sapere dove sta ogni goccia,
     * letta in un altro modo: **una corsia tocca quando la sua corsa scavalca
     * un intero**. Chi fa vibrare il telefono chiama questa, e non deve sapere
     * niente di come e' disegnata una goccia.
     */
    fun impatti(tipo: Caduta, presenza: Float, da: Float, a: Float): Int {
        if (presenza <= 0.004f || a <= da) return 0
        var conto = 0
        for (i in 0 until QUANTE) {
            if (accesa(i, presenza) <= 0.35f) continue
            conto += (floor(corsa(tipo, i, a)) - floor(corsa(tipo, i, da))).toInt()
        }
        return conto
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Il temporale
// ─────────────────────────────────────────────────────────────────────────────

/** Ogni quanti secondi torna la coppia di lampi. */
const val CICLO_LAMPO = 7f

/**
 * Quanto e' acceso il lampo in questo istante, da 0 a 1.
 *
 * Due lampi ravvicinati per ciclo: un temporale non lampeggia a ritmo, e due
 * vicini si leggono come uno vero meglio di uno regolare.
 *
 * Sta fuori dal disegno per la stessa ragione delle corsie: **il tuono si
 * sente**, e chi fa vibrare il telefono deve poter chiedere "adesso?" senza
 * passare da una tela.
 */
fun forzaLampo(tempo: Float): Float {
    val dentro = tempo % CICLO_LAMPO
    return when {
        dentro < 0.10f -> 1f - dentro / 0.10f
        dentro > 0.22f && dentro < 0.28f -> 1f - (dentro - 0.22f) / 0.06f
        else -> 0f
    }
}

/** Quanto e' lontano dallo zero e dall'uno: serve a sapere se una transizione
 *  e' in volo, e quindi se l'orologio deve restare acceso. */
internal fun inMezzo(valore: Float): Boolean = valore > 0.01f && abs(valore - 1f) > 0.01f
