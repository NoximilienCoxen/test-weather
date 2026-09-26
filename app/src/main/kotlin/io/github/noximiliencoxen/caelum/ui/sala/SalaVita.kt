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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

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
internal fun sparso(i: Int, sale: Int): Float {
    val x = sin(i * 12.9898f + sale * 78.233f) * 43758.547f
    return x - floor(x)
}

// ─────────────────────────────────────────────────────────────────────────────
// Le tabelle davanti a `sparso`
// ─────────────────────────────────────────────────────────────────────────────
//
// **Millesettecento seni per fotogramma calcolavano numeri che non cambiano
// mai.** Cinque per ognuna delle centosessanta stelle, sei per ognuna delle
// centocinquanta gocce, quattro per ogni granello di pulviscolo: tutti funzione
// del **solo indice**. A sessanta fotogrammi al secondo erano centomila `sin()`
// al secondo per riottenere sempre gli stessi valori.
//
// Si calcolano una volta all'avvio, come fa gia' `Nuvole` in `SalaCielo`. Tre
// cose da sapere prima di toccare queste righe:
//
// - **e' un memo davanti a `sparso`, non un rimpiazzo.** Le stelle cadenti
//   chiamano `sparso(quale, ...)` dove `quale` cresce **col tempo** e non ha un
//   limite: una tabella al posto della funzione passerebbe tutti gli scatti
//   della CI - che congelano l'orologio a zero - e andrebbe fuori indice sul
//   telefono dopo pochi secondi;
// - **l'aritmetica resta dov'era.** In tabella va il risultato nudo di
//   `sparso`, non il valore composto: cosi' l'espressione che lo usa e' la
//   stessa di prima, carattere per carattere, e il disegno non puo' spostarsi
//   di un pixel;
// - **le tabelle delle corsie stanno dentro `Corsie`**, non nei punti di
//   disegno. Le stesse funzioni le legge chi fa vibrare il telefono
//   (`VibrazioniMeteo`): due strade - una che calcola e una che guarda in
//   tabella - sono due strade che un giorno danno due numeri diversi, e il
//   colpetto andrebbe fuori tempo rispetto alla goccia.
//
// `SparsoTest` tiene ferme le uguaglianze **a livello di bit**: e' l'unico
// controllo che copre anche `t > 0`, che gli scatti non vedono.

// ─────────────────────────────────────────────────────────────────────────────
// Il cielo di notte
// ─────────────────────────────────────────────────────────────────────────────

/** Quante stelle ha il cielo di Sala I. */
private const val STELLE = 160

/**
 * Le cinque grandezze di ogni stella che dipendono solo dal suo indice.
 *
 * Erano cinque `sparso()` per stella **per fotogramma**: ottocento seni al
 * fotogramma per posizioni, luminosita' e passi che non cambiano mai. Qui
 * dentro c'e' il risultato nudo di `sparso`; l'aritmetica che lo usa e' rimasta
 * dov'era, parola per parola.
 */
internal object TavolaStelle {
    val x = FloatArray(STELLE) { sparso(it, 1) }
    val alto = FloatArray(STELLE) { sparso(it, 2) }
    val luce = FloatArray(STELLE) { sparso(it, 7) }
    val luminosa = FloatArray(STELLE) { sparso(it, 11) }
    val passo = FloatArray(STELLE) { sparso(it, 12) }

    /** Tabella e sale di provenienza, per la prova di identita'. */
    val tutte: List<Pair<FloatArray, Int>>
        get() = listOf(x to 1, alto to 2, luce to 7, luminosa to 11, passo to 12)
}

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
        val x = TavolaStelle.x[i] * size.width
        // **Si fermano dove comincia il testo.** Erano fitte in alto e rade in
        // basso, il che non bastava: qualcuna finiva comunque dietro la
        // didascalia e accanto al numero dei gradi, e una stella dietro una
        // parola non e' un astro, e' sporco sulla pagina.
        val alto = TavolaStelle.alto[i]
        val y = alto * alto * size.height * SOFFITTO
        val luce = 0.30f + TavolaStelle.luce[i] * 0.70f
        // **Ogni stella ha il suo passo.** Con un periodo solo per tutte, il
        // cielo lampeggia invece di respirare - si legge come un difetto dello
        // schermo. Qui il periodo va da poco piu' di un secondo a quasi tre.
        val passo = 1.1f + TavolaStelle.passo[i] * 1.7f
        val tremolio = 0.58f + 0.42f * sin(tempo * passo + (x * 0.031f + y * 0.017f))
        val sfumo = ((SOFFITTO * size.height - y) / (size.height * 0.18f)).coerceIn(0f, 1f)
        val alfa = (velo * luce * tremolio * sfumo).coerceIn(0f, 1f)
        if (alfa <= 0.012f) continue

        // **Una decina sono grosse, e sono quelle che si guardano.** Un cielo
        // di puntini tutti uguali e' una trama, non un cielo: le poche
        // luminose danno la scala a tutte le altre.
        val luminosa = TavolaStelle.luminosa[i] > 0.93f
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

/** Ogni quanti secondi passa un aereo. Lento apposta: e' un evento, come la
 *  stella cadente, non un traffico. */
private const val CICLO_AEREO = 38f

/** Quanto ci mette ad attraversare lo schermo. */
private const val VOLO_AEREO = 16f

/** Quanto dura la scia dietro di lui prima di sciogliersi del tutto. */
private const val VITA_SCIA = 7f

/** In quanti tratti si disegna la scia: abbastanza perche' allargarsi e
 *  svanire sembrino continui. */
private const val TRATTI_SCIA = 28

/**
 * Un aereo alto, e la scia di condensazione che si lascia dietro.
 *
 * **E' il doppio diurno della stella cadente**: di notte l'unica cosa che passa
 * e' una scia di luce, di giorno e' questa. Uno ogni quaranta secondi circa,
 * da un lato o dall'altro, a quote diverse, leggermente inclinato.
 *
 * La scia e' **due fili** vicino ai motori che si fondono in uno allargandosi, e
 * svanisce per esponenziale: e' quello che si vede alzando gli occhi, e la
 * differenza fra una scia e una riga tirata col righello.
 *
 * I tratti hanno le punte piatte e non tonde: a opacita' parziale due punte
 * tonde sovrapposte fanno un puntino piu' chiaro a ogni giunto, e la scia
 * diventerebbe una collana (la stessa lezione del fulmine in `SalaCielo`).
 */
fun DrawScope.aereo(tempo: Float, inchiostro: Color, velo: Float) {
    if (velo <= 0.01f) return
    val giro = floor(tempo / CICLO_AEREO).toInt()
    val dentro = tempo - giro * CICLO_AEREO
    if (dentro > VOLO_AEREO + VITA_SCIA) return

    val daSinistra = sparso(giro, 51) > 0.5f
    // Sotto il nome della localita' e sopra il numero dei gradi.
    val yPartenza = size.height * (0.15f + sparso(giro, 52) * 0.14f)
    val yArrivo = yPartenza + size.height * (sparso(giro, 53) - 0.5f) * 0.12f
    val xPartenza = if (daSinistra) -0.08f * size.width else 1.08f * size.width
    val xArrivo = if (daSinistra) 1.08f * size.width else -0.08f * size.width
    fun punto(s: Float) = Offset(
        xPartenza + (xArrivo - xPartenza) * s,
        yPartenza + (yArrivo - yPartenza) * s,
    )
    val dx = xArrivo - xPartenza
    val dyVolo = yArrivo - yPartenza
    val lunghezza = sqrt(dx * dx + dyVolo * dyVolo)
    // La perpendicolare alla rotta: da li' si scostano i due fili.
    val normale = Offset(-dyVolo / lunghezza, dx / lunghezza)

    for (j in 0 until TRATTI_SCIA) {
        val eta0 = j * VITA_SCIA / TRATTI_SCIA
        val eta1 = (j + 1) * VITA_SCIA / TRATTI_SCIA
        val s0 = ((dentro - eta0) / VOLO_AEREO).coerceAtMost(1f)
        val s1 = ((dentro - eta1) / VOLO_AEREO).coerceAtMost(1f)
        if (s0 <= 0f) break
        if (s0 - s1.coerceAtLeast(0f) <= 0f) continue
        val da = punto(s0)
        val a = punto(s1.coerceAtLeast(0f))
        // Nasce un poco dietro ai motori, poi si scioglie.
        val nascita = (eta0 / 0.35f).coerceAtMost(1f)
        val alfa = (0.42f * velo * nascita * exp(-eta0 / 2.4f)).coerceIn(0f, 1f)
        if (alfa <= 0.006f) continue
        val spessore = size.width * 0.0032f * (1f + eta0 * 0.9f)
        // I due fili si avvicinano mentre la scia invecchia, e si fondono.
        val scosta = size.width * 0.0042f * (1f - (eta0 / 2.2f).coerceAtMost(1f))
        if (scosta > 0.5f) {
            for (lato in intArrayOf(-1, 1)) {
                val o = normale * (scosta * lato)
                drawLine(
                    color = inchiostro.copy(alpha = alfa * 0.7f),
                    start = da + o,
                    end = a + o,
                    strokeWidth = spessore,
                    cap = StrokeCap.Butt,
                )
            }
        } else {
            drawLine(
                color = inchiostro.copy(alpha = alfa),
                start = da,
                end = a,
                strokeWidth = spessore * 1.6f,
                cap = StrokeCap.Butt,
            )
        }
    }

    // L'aereo, finche' e' in volo: la fusoliera, due ali a freccia a meta'
    // corpo e due alette in coda. Con le ali dritte si leggeva un "+", con le
    // sole ali in punta una freccia: e' la coda a dire "aereo".
    val testa = dentro / VOLO_AEREO
    if (testa in 0f..1f) {
        val p = punto(testa)
        val avanti = Offset(dx / lunghezza, dyVolo / lunghezza)
        val corpo = size.width * 0.012f
        val tinta = inchiostro.copy(alpha = (0.9f * velo).coerceIn(0f, 1f))
        drawLine(tinta, p - avanti * corpo, p + avanti * corpo, strokeWidth = size.width * 0.0034f, cap = StrokeCap.Round)
        val ali = p + avanti * (corpo * 0.1f)
        val coda = p - avanti * (corpo * 0.8f)
        for (lato in intArrayOf(-1, 1)) {
            drawLine(
                tinta,
                ali,
                ali - avanti * (corpo * 0.4f) + normale * (corpo * 0.95f * lato),
                strokeWidth = size.width * 0.0028f,
                cap = StrokeCap.Round,
            )
            drawLine(
                tinta,
                coda,
                coda - avanti * (corpo * 0.2f) + normale * (corpo * 0.38f * lato),
                strokeWidth = size.width * 0.0022f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Le quattro grandezze di ogni granello che dipendono solo dal suo indice. */
internal object TavolaPulviscolo {
    const val QUANTI = 9
    val passo = FloatArray(QUANTI) { sparso(it, 3) }
    val fase = FloatArray(QUANTI) { sparso(it, 4) }
    val alto = FloatArray(QUANTI) { sparso(it, 5) }
    val raggio = FloatArray(QUANTI) { sparso(it, 6) }

    /** Tabella e sale di provenienza, per la prova di identita'. */
    val tutte: List<Pair<FloatArray, Int>>
        get() = listOf(passo to 3, fase to 4, alto to 5, raggio to 6)
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
    for (i in 0 until TavolaPulviscolo.QUANTI) {
        val passo = 0.014f + TavolaPulviscolo.passo[i] * 0.020f
        val attraverso = (tempo * passo + TavolaPulviscolo.fase[i]) % 1f
        val y = size.height * (0.08f + TavolaPulviscolo.alto[i] * (SOFFITTO - 0.10f)) +
            sin(tempo * 0.5f + i) * size.height * 0.012f
        val x = attraverso * (size.width * 1.2f) - size.width * 0.1f
        val bordo = (attraverso / 0.15f).coerceAtMost(1f) * ((1f - attraverso) / 0.15f).coerceAtMost(1f)
        drawCircle(
            color = inchiostro.copy(alpha = (0.075f * bordo * velo).coerceIn(0f, 1f)),
            radius = size.width * (0.0028f + TavolaPulviscolo.raggio[i] * 0.0030f),
            center = Offset(x, y),
        )
    }
}

/** Le posizioni dei granelli di polline: come il pulviscolo, con sali loro. */
internal object TavolaPolline {
    const val QUANTI = 22
    val passo = FloatArray(QUANTI) { sparso(it, 31) }
    val fase = FloatArray(QUANTI) { sparso(it, 32) }
    val alto = FloatArray(QUANTI) { sparso(it, 33) }
    val raggio = FloatArray(QUANTI) { sparso(it, 34) }
}

/**
 * Il polline, quando ce n'e' tanto: granelli caldi che vagano nel cielo.
 *
 * **Solo da "alto" in su** (vedi `SalaShell`): con poco polline il cielo resta
 * quello di sempre, e i granelli dicono qualcosa proprio perche' di solito non
 * ci sono. Diversi dal pulviscolo apposta: piu' numerosi, piu' grandi, gialli,
 * e invece di attraversare il cielo in linea retta girano piano su se stessi
 * mentre scendono, come fanno davvero. Un bordo piu' scuro li tiene visibili
 * anche su un cielo chiaro.
 *
 * @param velo da 0 a 1: il livello del polline, gia' ridotto da notte e pioggia.
 */
fun DrawScope.polline(tempo: Float, velo: Float) {
    if (velo <= 0.01f) return
    val granello = SalaTokens.accent300
    val bordo = SalaTokens.accent600
    for (i in 0 until TavolaPolline.QUANTI) {
        val passo = 0.010f + TavolaPolline.passo[i] * 0.016f
        val attraverso = (tempo * passo + TavolaPolline.fase[i]) % 1f
        val giro = tempo * (0.6f + TavolaPolline.passo[i]) + i * 1.7f
        val x = attraverso * (size.width * 1.2f) - size.width * 0.1f + sin(giro) * size.width * 0.02f
        val y = size.height * (0.06f + TavolaPolline.alto[i] * (SOFFITTO - 0.06f)) +
            cos(giro) * size.height * 0.012f + attraverso * size.height * 0.04f
        val dentro = (attraverso / 0.12f).coerceAtMost(1f) * ((1f - attraverso) / 0.12f).coerceAtMost(1f)
        val alpha = (0.55f * dentro * velo).coerceIn(0f, 1f)
        val r = size.width * (0.0040f + TavolaPolline.raggio[i] * 0.0045f)
        drawCircle(color = bordo.copy(alpha = alpha * 0.5f), radius = r * 1.35f, center = Offset(x, y))
        drawCircle(color = granello.copy(alpha = alpha), radius = r, center = Offset(x, y))
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

    /**
     * Quante gocce si disegnano su ogni corsia.
     *
     * **Le corsie sono quattordici e non bastavano agli occhi.** Quattordici
     * segni su tutto lo schermo, con la scheda che ne copre la meta' bassa,
     * vuol dire **sette** tratti visibili: chi guardava non vedeva una pioggia
     * rada, vedeva graffi sul vetro, e da fuori il difetto si racconta come
     * "quando piove non succede niente".
     *
     * Aumentare le corsie non era la strada: quelle sono anche il battito che
     * si sente in mano, e quattordici e' il numero su cui e' tarato. Ogni
     * corsia porta invece una **fila** di gocce sfalsate lungo la stessa
     * discesa: la corsa resta una, quindi il tocco che fa vibrare il telefono
     * resta quello di prima, e a schermo i segni diventano quattro volte tanti.
     *
     * La neve ne vuole di piu' - fiocchi radi non fanno una nevicata, e
     * scendono cosi' piano che se ne vedono tanti insieme - e la grandine di
     * meno: i chicchi sono grossi, svelti e distanti.
     */
    fun ripetizioni(tipo: Caduta): Int = when (tipo) {
        Caduta.PIOGGIA -> 4
        Caduta.NEVE -> 5
        Caduta.GRANDINE -> 2
    }

    /** Il seme di una goccia: la sua corsia e il posto che occupa nella fila. */
    private fun seme(i: Int, k: Int): Int = i * 37 + k * 101

    /**
     * Di quanto la goccia [k] segue quella di testa, in frazioni di discesa.
     *
     * **La prima della fila ha scarto zero, e non e' un caso**: e' lei la
     * corsa che [impatti] conta, quindi e' lei a toccare terra nell'istante in
     * cui il telefono batte. Le altre le stanno dietro a distanze appena
     * irregolari - una fila perfettamente spaziata si legge come una
     * cucitura, non come pioggia.
     */
    fun scarto(i: Int, k: Int, quante: Int): Float =
        if (k == 0) 0f else k.toFloat() / quante + (tavolaScarto[i][k] - 0.5f) * 0.5f / quante

    /** Di quanto la goccia [k] sta a lato della propria corsia, da -0,5 a 0,5. */
    fun scostamento(i: Int, k: Int): Float = tavolaScostamento[i][k] - 0.5f

    /**
     * Quanto e' vicina questa goccia, da 0 a 1.
     *
     * Parte dalla profondita' della corsia e la sposta un po': senza, le
     * quattro gocce di una fila sarebbero grandi uguali e andrebbero alla
     * stessa velocita', cioe' sarebbero la stessa goccia ripetuta.
     */
    fun profonditaDi(i: Int, k: Int): Float =
        (profondita(i) * 0.55f + tavolaProfonditaDi[i][k] * 0.45f).coerceIn(0f, 1f)

    /** In che ordine le corsie si accendono. Sparso, se no la pioggia
     *  comincerebbe da un lato e si allargherebbe come una tenda. */
    private val Ordine = intArrayOf(7, 1, 11, 4, 13, 0, 9, 5, 2, 12, 8, 3, 10, 6)

    /**
     * L'inverso di [Ordine]: dato il numero di corsia, il suo posto in fila.
     *
     * [accesa] lo chiedeva con `Ordine.indexOf(i)`, cioe' scorrendo l'elenco
     * fino a trovarlo, quarantadue volte per fotogramma. Una permutazione ha
     * sempre un'inversa, e calcolarla una volta costa quattordici passi.
     */
    private val Posto = IntArray(QUANTE).also { posti ->
        Ordine.forEachIndexed { posto, corsia -> posti[corsia] = posto }
    }

    /** Il massimo che [ripetizioni] puo' restituire: la larghezza delle
     *  tabelle per goccia. */
    private const val FILA_MAX = 5

    // Le tabelle. Stanno **qui dentro** e non nei punti di disegno perche' le
    // stesse funzioni le legge chi fa vibrare il telefono: una strada sola.
    internal val tavolaX = FloatArray(QUANTE) { sparso(it, 21) }
    internal val tavolaProfondita = FloatArray(QUANTE) { sparso(it, 22) }
    internal val tavolaSfasatura = FloatArray(QUANTE) { sparso(it, 23) }
    internal val tavolaScarto =
        Array(QUANTE) { i -> FloatArray(FILA_MAX) { k -> sparso(seme(i, k), 24) } }
    internal val tavolaScostamento =
        Array(QUANTE) { i -> FloatArray(FILA_MAX) { k -> sparso(seme(i, k), 25) } }
    internal val tavolaProfonditaDi =
        Array(QUANTE) { i -> FloatArray(FILA_MAX) { k -> sparso(seme(i, k), 26) } }

    /** Le tabelle per corsia e il loro sale, per la prova di identita'. */
    internal val perCorsia: List<Pair<FloatArray, Int>>
        get() = listOf(tavolaX to 21, tavolaProfondita to 22, tavolaSfasatura to 23)

    /** Le tabelle per goccia e il loro sale, per la prova di identita'. */
    internal val perGoccia: List<Pair<Array<FloatArray>, Int>>
        get() = listOf(tavolaScarto to 24, tavolaScostamento to 25, tavolaProfonditaDi to 26)

    /** Il seme di una goccia, esposto alla sola prova. */
    internal fun semeDi(i: Int, k: Int): Int = seme(i, k)

    /** Da quale lato dello spazio-modello scende. */
    fun x(i: Int): Float = (tavolaX[i] - 0.5f) * 1.30f

    /**
     * Quanto e' vicina, da 0 (in fondo) a 1 (davanti).
     *
     * **Senza questo la pioggia era una fila di segni tutti uguali**, e una
     * pioggia in cui ogni goccia e' grande come le altre e va alla stessa
     * velocita' e' una grata, non un temporale. Le vicine sono piu' grandi,
     * piu' svelte e piu' opache; le lontane quasi un'ombra.
     */
    fun profondita(i: Int): Float = tavolaProfondita[i]

    fun sfasatura(i: Int): Float = tavolaSfasatura[i]

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
        val posto = Posto[i]
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
 * Una scarica: quando comincia dentro il ciclo, quanto dura, quanto forte.
 *
 * Un fulmine non e' un lampo solo. E' un canale che si accende e si riaccende
 * piu' volte in mezzo secondo - i **colpi di ritorno** - e ogni ripresa e' piu'
 * debole della precedente. E' la ragione per cui un temporale vero *sfarfalla*
 * invece di lampeggiare, ed e' quello che mancava: due rampe lineari lunghe
 * sessanta e sessanta millesimi davano un pulsare regolare, da insegna al neon.
 */
private class Scarica(val a: Float, val durata: Float, val forza: Float)

private val Scariche = listOf(
    Scarica(a = 0.00f, durata = 0.26f, forza = 1.00f),
    Scarica(a = 0.19f, durata = 0.20f, forza = 0.78f),
    Scarica(a = 0.40f, durata = 0.44f, forza = 0.42f),
)

/**
 * Quanto e' acceso il lampo in questo istante, da 0 a 1.
 *
 * **Sale di colpo e si spegne per esponenziale**, e nessuna delle due cose e'
 * un vezzo: una scarica raggiunge il massimo in microsecondi - un fotogramma
 * non la vede salire - mentre il canale caldo si raffredda nel modo in cui si
 * raffredda tutto, cioe' perdendo ogni volta una frazione di quello che resta.
 * La vecchia rampa in salita dava al fulmine il tempo di *arrivare*, e un
 * fulmine che arriva non e' un fulmine.
 *
 * Al tempo zero vale **uno**, ed e' voluto: la cattura della CI ferma
 * l'orologio li', quindi lo scatto del temporale ritrae il colpo e non il buio
 * fra un colpo e l'altro.
 *
 * Sta fuori dal disegno per la stessa ragione delle corsie: **il tuono si
 * sente**, e chi fa vibrare il telefono deve poter chiedere "adesso?" senza
 * passare da una tela.
 */
fun forzaLampo(tempo: Float): Float = forzaDelle(Scariche, tempo)

/**
 * Quanto e' accesa la **saetta**, che non e' quanto e' acceso il cielo.
 *
 * Il canale si vede solo nelle due scariche brevi; il terzo battito e' il
 * riverbero che resta nelle nuvole dopo che il canale si e' spento, e una
 * saetta disegnata li' sopra sarebbe un fulmine fermo in aria per mezzo
 * secondo.
 */
fun forzaSaetta(tempo: Float): Float = forzaDelle(Scariche.take(2), tempo)

private fun forzaDelle(quali: List<Scarica>, tempo: Float): Float {
    val dentro = tempo % CICLO_LAMPO
    var massimo = 0f
    quali.forEach { s ->
        val t = (dentro - s.a) / s.durata
        if (t >= 0f && t <= 1f) {
            val forma = exp(-t * 5.4f)
            if (s.forza * forma > massimo) massimo = s.forza * forma
        }
    }
    return massimo.coerceIn(0f, 1f)
}

/**
 * Quale fulmine e' questo, contato dall'inizio.
 *
 * Serve al disegno per dare a ogni colpo un canale suo: un temporale che
 * scarica sempre nello stesso punto dello schermo si legge come un difetto
 * della grafica, non come un temporale. Le tre scariche di uno stesso ciclo
 * hanno lo stesso numero, quindi **riaccendono lo stesso canale** - che e'
 * esattamente cio' che fa un colpo di ritorno.
 */
fun indiceLampo(tempo: Float): Int = floor(tempo / CICLO_LAMPO).toInt()

/** Quanto e' lontano dallo zero e dall'uno: serve a sapere se una transizione
 *  e' in volo, e quindi se l'orologio deve restare acceso. */
internal fun inMezzo(valore: Float): Boolean = valore > 0.01f && abs(valore - 1f) > 0.01f
