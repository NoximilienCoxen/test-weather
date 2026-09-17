package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
// Il cielo di notte
// ─────────────────────────────────────────────────────────────────────────────

/** Quante stelle ha il cielo di Sala I. */
private const val STELLE = 160

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
        // parola non e' un astro, e' sporco sulla pagina.
        val alto = sparso(i, 2)
        val y = alto * alto * size.height * SOFFITTO
        val luce = 0.30f + sparso(i, 7) * 0.70f
        // **Ogni stella ha il suo passo.** Con un periodo solo per tutte, il
        // cielo lampeggia invece di respirare - si legge come un difetto dello
        // schermo. Qui il periodo va da poco piu' di un secondo a quasi tre.
        val passo = 1.1f + sparso(i, 12) * 1.7f
        val tremolio = 0.58f + 0.42f * sin(tempo * passo + (x * 0.031f + y * 0.017f))
        val sfumo = ((SOFFITTO * size.height - y) / (size.height * 0.18f)).coerceIn(0f, 1f)
        val alfa = (velo * luce * tremolio * sfumo).coerceIn(0f, 1f)
        if (alfa <= 0.012f) continue

        // **Una decina sono grosse, e sono quelle che si guardano.** Un cielo
        // di puntini tutti uguali e' una trama, non un cielo: le poche
        // luminose danno la scala a tutte le altre.
        val luminosa = sparso(i, 11) > 0.93f
        val r = size.width * (0.0016f + luce * 0.0030f) * (if (luminosa) 2.3f else 1f)
        drawCircle(color = inchiostro.copy(alpha = alfa), radius = r, center = Offset(x, y))

        if (luminosa) {
            // Il bagliore attorno, e la croce di scintillio: due tratti sottili
            // che pulsano col tremolio. E' il modo in cui l'occhio legge
            // "brillante" a questa taglia - il raggio da solo darebbe un bollo.
            drawCircle(
                brush = Brush.radialGradient(
                    0f to inchiostro.copy(alpha = alfa * 0.34f),
                    1f to inchiostro.copy(alpha = 0f),
                    center = Offset(x, y),
                    radius = r * 5.5f,
                ),
                radius = r * 5.5f,
                center = Offset(x, y),
            )
            val punta = r * (3.2f + 1.6f * tremolio)
            val spessore = (r * 0.42f).coerceAtLeast(1f)
            listOf(Offset(punta, 0f), Offset(0f, punta)).forEach { d ->
                drawLine(
                    color = inchiostro.copy(alpha = alfa * 0.75f),
                    start = Offset(x - d.x, y - d.y),
                    end = Offset(x + d.x, y + d.y),
                    strokeWidth = spessore,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    // ── Le cadenti ───────────────────────────────────────────────────────────
    //
    // **Due tracce indipendenti, non una.** Ne passava una ogni dodici secondi:
    // con un cielo che ormai si guarda, dodici secondi sono un'attesa in cui
    // non succede niente. Due corsie con cadenze diverse e prime fra loro (4,3 e
    // 6,7 secondi) non tornano mai in fase, quindi a volte se ne vedono due
    // insieme e a volte nessuna - che e' come cadono davvero.
    listOf(0 to 4.3f, 1 to 6.7f).forEach { (traccia, ciclo) ->
        val dentro = tempo % ciclo
        val durata = 0.62f
        if (dentro >= durata) return@forEach
        val t = dentro / durata
        val quale = (tempo / ciclo).toInt() * 7 + traccia * 101
        val lunghezza = size.width * (0.20f + sparso(quale, 12) * 0.22f)
        // Angolo variabile: cadere tutte con la stessa inclinazione le
        // trasformerebbe in una pioggia obliqua.
        val pendenza = 0.28f + sparso(quale, 13) * 0.42f
        val cx = sparso(quale, 8) * size.width * 1.05f - size.width * 0.05f +
            t * lunghezza * 2.1f
        val cy = sparso(quale, 9) * size.height * 0.34f + t * lunghezza * pendenza * 2.1f
        val svanire = sin(t * PI_F)
        val alfa = (velo * svanire * 0.95f).coerceIn(0f, 1f)
        val coda = Offset(cx - lunghezza, cy - lunghezza * pendenza)
        // La scia **sfuma**: una riga di opacita' piena e' un graffio sul
        // vetro, non una stella che cade.
        drawLine(
            brush = Brush.linearGradient(
                0f to inchiostro.copy(alpha = 0f),
                1f to inchiostro.copy(alpha = alfa),
                start = coda,
                end = Offset(cx, cy),
            ),
            start = coda,
            end = Offset(cx, cy),
            strokeWidth = size.width * 0.0030f,
            cap = StrokeCap.Round,
        )
        // La testa, un punto piu' luminoso della scia.
        drawCircle(
            color = inchiostro.copy(alpha = alfa),
            radius = size.width * 0.0026f,
            center = Offset(cx, cy),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// La vita del giorno
// ─────────────────────────────────────────────────────────────────────────────
//
// **Erano usciti col vecchio feed, e tornano identici.** Li chiamava solo la
// scultura, quindi sono stati cancellati con lei; il cielo nuovo pero' aveva il
// difetto opposto - di giorno, a cielo sereno, non si muoveva niente. Il codice
// e' quello di allora ripreso da `git show`, perche' funzionava: riusarlo batte
// riscriverlo, e le note che porta dietro sono state pagate una volta.

/**
 * Un uccello: la corsia in cui vola, la fase, quanto e' veloce, quanto grande.
 *
 * Tre e non uno stormo: uno solo si legge come un difetto del disegno, dieci
 * come un'invasione. Tre a distanze diverse dicono "cielo aperto" e basta.
 */
private class Uccello(val corsia: Float, val fase: Float, val passo: Float, val taglia: Float)

private val Uccelli = listOf(
    Uccello(corsia = -0.62f, fase = 0.00f, passo = 0.055f, taglia = 0.105f),
    Uccello(corsia = -0.46f, fase = 0.38f, passo = 0.043f, taglia = 0.078f),
    Uccello(corsia = -0.74f, fase = 0.68f, passo = 0.068f, taglia = 0.062f),
)

/**
 * Gli uccelli del giorno: due archi che si toccano.
 *
 * E' la sagoma con cui chiunque disegna un uccello lontano, e a questa
 * dimensione qualunque tentativo di fare di piu' diventa una macchia. Quello
 * che li rende vivi non e' la forma: e' che **le ali battono** e che ognuno
 * attraversa con un passo suo. Attraversano e basta, entrando e uscendo in
 * dissolvenza: farli girare in tondo li legherebbe a un centro, e un uccello
 * che orbita attorno alla temperatura e' un carillon, non un cielo.
 *
 * **Non sono dei soli giorni sereni.** Sotto le nuvole gli uccelli continuano a
 * volare, e se ne vedono meno, non nessuno: il velo scende con la copertura
 * invece di spegnersi, ed e' il chiamante a deciderlo.
 */
fun DrawScope.uccelli(unita: Float, origine: Offset, tempo: Float, inchiostro: Color, velo: Float) {
    if (velo <= 0.01f) return
    Uccelli.forEach { u ->
        val attraverso = (tempo * u.passo + u.fase) % 1f
        val x = origine.x + (attraverso * 2.6f - 1.3f) * unita
        val ondeggio = sin(tempo * 0.9f + u.fase * PI_F * 2f) * unita * 0.02f
        val y = origine.y + u.corsia * unita + ondeggio
        val bordo = (attraverso / 0.12f).coerceAtMost(1f) *
            ((1f - attraverso) / 0.12f).coerceAtMost(1f)
        val opacita = bordo * 0.5f * velo
        if (opacita <= 0.01f) return@forEach

        // Il battito: le punte salgono e scendono attorno al corpo. E' l'unica
        // cosa che distingue un uccello che vola da un accento circonflesso.
        val battito = sin(tempo * 5.2f + u.fase * PI_F * 2f)
        val w = unita * u.taglia
        val alzata = w * 0.44f * battito
        val ala = Path().apply {
            moveTo(x - w, y - alzata)
            quadraticTo(x - w * 0.45f, y + w * 0.18f, x, y)
            quadraticTo(x + w * 0.45f, y + w * 0.18f, x + w, y - alzata)
        }
        drawPath(
            path = ala,
            color = inchiostro.copy(alpha = opacita.coerceIn(0f, 1f)),
            style = Stroke(width = (w * 0.15f).coerceAtLeast(2.2f), cap = StrokeCap.Round),
        )
    }
}

/**
 * Il pulviscolo delle belle giornate: pochi semi che attraversano piano.
 *
 * Serve a una cosa sola, e vale la pena dirla: **un cielo sereno in cui l'unica
 * cosa che si muove sono tre uccelli si legge come tre uccelli su uno sfondo
 * fermo**. Questi non si guardano, si notano - e bastano a far sembrare che ci
 * sia aria fra chi guarda e il resto.
 */
fun DrawScope.pulviscolo(tempo: Float, inchiostro: Color, velo: Float) {
    if (velo <= 0.01f) return
    // **Pochi, in alto e tenui.** Erano quattordici sparsi su due terzi di
    // schermo, e negli scatti finivano accanto al numero dei gradi: a quella
    // taglia e a quel contrasto non si leggevano come aria, si leggevano come
    // pixel morti. Un elemento decorativo che si puo' scambiare per un guasto
    // e' un elemento che toglie, non che aggiunge.
    val quanti = 9
    for (i in 0 until quanti) {
        val passo = 0.014f + sparso(i, 3) * 0.020f
        val attraverso = (tempo * passo + sparso(i, 4)) % 1f
        val y = size.height * (0.08f + sparso(i, 5) * (SOFFITTO - 0.10f)) +
            sin(tempo * 0.5f + i) * size.height * 0.012f
        val x = attraverso * (size.width * 1.2f) - size.width * 0.1f
        val bordo = (attraverso / 0.15f).coerceAtMost(1f) * ((1f - attraverso) / 0.15f).coerceAtMost(1f)
        drawCircle(
            color = inchiostro.copy(alpha = (0.075f * bordo * velo).coerceIn(0f, 1f)),
            radius = size.width * (0.0028f + sparso(i, 6) * 0.0030f),
            center = Offset(x, y),
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
