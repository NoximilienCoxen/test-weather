package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
// Gli uccelli
// ─────────────────────────────────────────────────────────────────────────────

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
 * **Non sono piu' dei soli giorni sereni.** Stavano nel ramo "cielo aperto", e
 * bastava una nuvola perche' il cielo si svuotasse di colpo: sotto le nuvole
 * gli uccelli continuano a volare, e se ne vedono meno, non nessuno. Adesso il
 * loro velo scende con la copertura invece di spegnersi, ed e' il chiamante a
 * deciderlo.
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
 * sia aria fra chi guarda e la scultura.
 */
fun DrawScope.pulviscolo(tempo: Float, inchiostro: Color, velo: Float) {
    if (velo <= 0.01f) return
    val quanti = 14
    for (i in 0 until quanti) {
        val passo = 0.014f + sparso(i, 3) * 0.020f
        val attraverso = (tempo * passo + sparso(i, 4)) % 1f
        val y = size.height * (0.10f + sparso(i, 5) * 0.62f) +
            sin(tempo * 0.5f + i) * size.height * 0.012f
        val x = attraverso * (size.width * 1.2f) - size.width * 0.1f
        val bordo = (attraverso / 0.15f).coerceAtMost(1f) * ((1f - attraverso) / 0.15f).coerceAtMost(1f)
        drawCircle(
            color = inchiostro.copy(alpha = (0.10f * bordo * velo).coerceIn(0f, 1f)),
            radius = size.width * (0.0035f + sparso(i, 6) * 0.0040f),
            center = Offset(x, y),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Il cielo di notte
// ─────────────────────────────────────────────────────────────────────────────

/** Quante stelle ha il cielo di Sala I. */
private const val STELLE = 72

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
        // Piu' fitte in alto: verso il basso c'e' il testo, e una stella dietro
        // una didascalia e' sporco sulla pagina, non un astro.
        val alto = sparso(i, 2)
        val y = alto * alto * size.height * 0.72f
        val luce = 0.35f + sparso(i, 7) * 0.65f
        val tremolio = 0.62f + 0.38f * sin(tempo * 1.6f + (x * 0.031f + y * 0.017f))
        drawCircle(
            color = inchiostro.copy(alpha = (velo * luce * tremolio * 0.85f).coerceIn(0f, 1f)),
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

/**
 * Cosa cade, e come.
 *
 * Le tre cadute non sono la stessa cosa a velocita' diverse, ed e' per questo
 * che sono tre rami e non un parametro:
 *
 * - la **pioggia** e' un tratto, e cade dritta e in fretta;
 * - la **neve** e' un fiocco che **deriva** di lato mentre scende, e senza
 *   quella deriva sembra pioggia bianca;
 * - la **grandine** e' piu' piccola, piu' veloce e **non** deriva: il vento non
 *   la sposta, per questo fa male.
 *
 * **E adesso arrivano da qualche parte.** Prima le gocce svanivano a mezz'aria
 * sopra il numero dei gradi: erano oggetti sospesi in una stanza, non pioggia
 * che cade su qualcosa. Il [suolo] e' la riga su cui tutte e tre finiscono - la
 * stessa altezza dell'ombra della scultura - e li' ognuna lascia il proprio
 * segno: la goccia una fioritura d'acquerello che si allarga e sbiadisce, il
 * chicco un rimbalzo, il fiocco un velo che si posa.
 *
 * @param presenza quanto piove, da 0 a 1. A zero non disegna niente **per
 *   costruzione**: non c'e' piu' un `if` a monte che accende la pioggia, e
 *   quindi non c'e' piu' un istante in cui compare tutta insieme.
 * @param peso quanto conta questa caduta nella mescola. Passando dalla pioggia
 *   alla neve le due si sfumano l'una nell'altra condividendo corsie e
 *   orologio, che e' esattamente com'e' fatto il nevischio.
 */
fun DrawScope.caduta(
    tipo: Caduta,
    acquerello: Acquerello,
    unita: Float,
    origine: Offset,
    suolo: Float,
    tempo: Float,
    tinta: Color,
    presenza: Float,
    peso: Float = 1f,
) {
    if (presenza <= 0.004f || peso <= 0.004f) return
    val cima = origine.y - unita * 0.55f
    val corsa = (suolo - cima).coerceAtLeast(1f)

    for (i in 0 until Corsie.QUANTE) {
        val quota = Corsie.accesa(i, presenza)
        if (quota <= 0.01f) continue
        val prof = Corsie.profondita(i)
        val x0 = origine.x + Corsie.x(i) * unita
        val u = Corsie.corsa(tipo, i, tempo)
        val avanzamento = u - floor(u)
        val y = cima + avanzamento * corsa
        // Le vicine sono piu' nitide delle lontane: e' aria fra le due, ed e'
        // cio' che rende una pioggia profonda invece che una grata.
        val velo = quota * peso * (0.35f + 0.65f * prof)
        // In testa si entra in dissolvenza, se no le gocce comparirebbero dal
        // nulla su una riga netta. In coda **no**: adesso la coda e' il suolo,
        // e una goccia che sbiadisce prima di toccare non tocca.
        val entrata = (avanzamento / 0.10f).coerceAtMost(1f)
        val opacita = velo * entrata
        if (opacita > 0.01f) {
            when (tipo) {
                Caduta.PIOGGIA -> {
                    // **Molto piu' piccole di prima.** Erano larghe un decimo e
                    // alte tre decimi dell'unita', cioe' quasi un quarto di
                    // schermo: negli scatti sembravano matite appoggiate sopra
                    // la temperatura.
                    val h = unita * (0.055f + 0.055f * prof)
                    timbra(
                        timbro = acquerello.pennellate[i % acquerello.pennellate.size],
                        centro = Offset(x0, y),
                        larghezza = h * 0.30f,
                        altezza = h,
                        tinta = tinta,
                        alfa = (0.72f * opacita).coerceIn(0f, 1f),
                    )
                }
                Caduta.NEVE -> {
                    // La deriva: un seno lungo, diverso per fiocco. E' l'unica
                    // cosa che distingue la neve dalla pioggia bianca.
                    val deriva = sin(tempo * 0.7f + i * 1.9f) * unita * 0.10f
                    val r = unita * (0.010f + 0.010f * prof)
                    drawCircle(
                        color = tinta.copy(alpha = (0.80f * opacita).coerceIn(0f, 1f)),
                        radius = r,
                        center = Offset(x0 + deriva, y),
                    )
                }
                Caduta.GRANDINE -> {
                    val r = unita * (0.009f + 0.008f * prof)
                    drawCircle(
                        color = tinta.copy(alpha = (0.88f * opacita).coerceIn(0f, 1f)),
                        radius = r,
                        center = Offset(x0, y),
                    )
                    // Il chicco ha un lato illuminato: e' ghiaccio, non una goccia.
                    drawCircle(
                        color = Color.White.copy(alpha = (0.52f * opacita).coerceIn(0f, 1f)),
                        radius = r * 0.42f,
                        center = Offset(x0 - r * 0.3f, y - r * 0.3f),
                    )
                }
            }
        }

        // ── Il segno che resta, appena sotto ─────────────────────────────────
        //
        // L'eta' del segno si ricava dalla stessa corsa: l'ultimo impatto e'
        // stato all'ultimo intero scavalcato, quindi da allora e' passato
        // `avanzamento / velocita`. Nessuno stato da tenere, nessuna lista di
        // gocce vive: la scena si puo' disegnare **da un solo numero**, che e'
        // anche il motivo per cui la vibrazione riesce a stare in fase con lei.
        val eta = avanzamento / Corsie.velocita(tipo, i)
        if (eta > Corsie.FIORITURA) continue
        val maturo = eta / Corsie.FIORITURA
        val svanire = (1f - maturo) * (1f - maturo)
        when (tipo) {
            Caduta.PIOGGIA -> {
                // La fioritura: pigmento che si allarga sulla carta bagnata.
                // Un cerchio vuoto e non pieno - l'acqua spinge il colore
                // **verso il bordo** della macchia, ed e' quello che rende un
                // acquerello riconoscibile a colpo d'occhio.
                val raggio = unita * (0.02f + 0.10f * maturo) * (0.5f + prof)
                drawCircle(
                    color = tinta.copy(alpha = (0.34f * svanire * quota * peso).coerceIn(0f, 1f)),
                    radius = raggio,
                    center = Offset(x0, suolo),
                    style = Stroke(width = (unita * 0.008f).coerceAtLeast(1f)),
                )
            }
            Caduta.GRANDINE -> {
                // Il rimbalzo: sale e ricade una volta sola. Il ghiaccio non si
                // spiaccica, e questo e' il modo piu' corto per dirlo.
                val salto = sin(maturo * PI_F) * unita * 0.14f
                drawCircle(
                    color = tinta.copy(alpha = (0.55f * svanire * quota * peso).coerceIn(0f, 1f)),
                    radius = unita * 0.008f,
                    center = Offset(x0 + unita * 0.03f * maturo, suolo - salto),
                )
            }
            Caduta.NEVE -> Unit // la neve non batte: si posa, e lo fa la coltre.
        }
    }

    // La coltre: la neve che si e' posata. Non si anima, si **ispessisce** con
    // l'intensita', ed e' l'unica cosa in scena che dice che la neve resta.
    if (tipo == Caduta.NEVE) {
        val spessore = unita * 0.020f * presenza * peso
        if (spessore > 0.4f) {
            drawRect(
                color = tinta.copy(alpha = (0.30f * presenza * peso).coerceIn(0f, 1f)),
                topLeft = Offset(origine.x - unita * 0.85f, suolo - spessore),
                size = Size(unita * 1.70f, spessore * 2f),
            )
        }
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

/** Il fulmine del temporale: compare a scatti, e per pochissimo. */
fun DrawScope.fulmine(unita: Float, origine: Offset, tempo: Float, tinta: Color, forza: Float) {
    if (forza <= 0.01f || forzaLampo(tempo) <= 0.01f) return
    val x = origine.x + unita * 0.10f
    val y = origine.y + unita * 0.20f
    val h = unita * 0.42f
    val saetta = Path().apply {
        moveTo(x, y)
        lineTo(x - h * 0.22f, y + h * 0.46f)
        lineTo(x + h * 0.04f, y + h * 0.46f)
        lineTo(x - h * 0.10f, y + h)
        lineTo(x + h * 0.30f, y + h * 0.38f)
        lineTo(x + h * 0.04f, y + h * 0.38f)
        close()
    }
    drawPath(path = saetta, color = tinta.copy(alpha = (0.92f * forza).coerceIn(0f, 1f)))
}

/** Il riverbero del lampo sulla carta: un alone largo e brevissimo. */
fun DrawScope.riverbero(tempo: Float, tinta: Color, forza: Float) {
    val acceso = forzaLampo(tempo) * forza
    if (acceso <= 0.01f) return
    drawRect(color = tinta.copy(alpha = 0.10f * acceso), size = Size(size.width, size.height))
}

/** Quanto e' lontano dallo zero e dall'uno: serve a sapere se una transizione
 *  e' in volo, e quindi se l'orologio deve restare acceso. */
internal fun inMezzo(valore: Float): Boolean = valore > 0.01f && abs(valore - 1f) > 0.01f
