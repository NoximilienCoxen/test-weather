package io.github.noximiliencoxen.caelum.ui.feed

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import io.github.noximiliencoxen.caelum.ui.theme.onColor
import io.github.noximiliencoxen.caelum.ui.theme.readableOn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * La gente che passa sotto la finestra, tagliata al busto dal davanzale.
 *
 * **Non e' il personaggio che questo progetto ha gia' provato e bocciato.**
 * Quello era un esploratore *articolato*, costruito da una gerarchia di sfere -
 * braccia a collana, il cappello che spariva dietro la testa, la mano che
 * salutava invece di riparare lo sguardo - e il verdetto fu *"se il risultato e'
 * questo, lasciamo perdere"*. Qui non c'e' rig, non c'e' articolazione, non c'e'
 * faccia: sono **contorni chiusi visti di profilo**, e il taglio al busto toglie
 * proprio le gambe, cioe' la parte che rende difficile far camminare qualcuno.
 * Il rischio che resta e' un altro, ed e' estetico: quattro sagome che si
 * ripetono in una via si notano. Si mitiga specchiando, cambiando misura e
 * corsia, e variando l'ombrello - ma si giudica solo guardandolo.
 *
 * **Come sono autorate**: tabelle di coordinate normalizzate in un riquadro che
 * va da -0,35 a 0,35 in larghezza e da 0 (la linea del taglio) a 1 (la cima
 * della testa). E' lo stile con cui il progetto tiene gia' la sua unica
 * geometria disegnata a mano, i profili delle coste del globo: numeri in fila,
 * non `Path` scritti riga per riga.
 *
 * **Cosa dicono, oltre a essere belle.** Non sono un salvaschermo: **chi ha
 * l'ombrello aperto** dice se in quell'ora piove e quanto e' probabile che
 * piova, e quanto vanno curvi dice l'intensita'. Con il sereno camminano dritti
 * e l'ombrello resta chiuso, che e' anche il motivo per cui la strada non e'
 * vuota nelle giornate belle.
 */

/**
 * Una sagoma: profilo rivolto a destra, dal taglio del busto alla cima del capo.
 *
 * Sedici punti bastano perche' a questa misura - una cinquantina di punti di
 * altezza - il naso e' un pixel. Quello che si legge e' la nuca, la spalla e il
 * petto; il resto e' fiducia.
 */
private val BUSTO_CAPPUCCIO = floatArrayOf(
    -0.30f, 0.00f, -0.29f, 0.34f, -0.27f, 0.55f, -0.24f, 0.66f,
    -0.20f, 0.78f, -0.14f, 0.90f, -0.04f, 0.98f, 0.07f, 0.97f,
    0.15f, 0.88f, 0.17f, 0.76f, 0.12f, 0.70f, 0.09f, 0.64f,
    0.19f, 0.58f, 0.25f, 0.36f, 0.27f, 0.14f, 0.28f, 0.00f,
)

/** A capo scoperto: la nuca rientra, il naso sporge, il collo si vede. */
private val BUSTO_SCOPERTO = floatArrayOf(
    -0.29f, 0.00f, -0.28f, 0.32f, -0.25f, 0.54f, -0.20f, 0.63f,
    -0.12f, 0.69f, -0.13f, 0.80f, -0.11f, 0.91f, -0.03f, 0.98f,
    0.07f, 0.96f, 0.12f, 0.88f, 0.14f, 0.82f, 0.19f, 0.79f,
    0.13f, 0.76f, 0.15f, 0.71f, 0.09f, 0.67f, 0.07f, 0.62f,
    0.17f, 0.57f, 0.24f, 0.34f, 0.26f, 0.12f, 0.27f, 0.00f,
)

/** Con la coda: la nuca porta un ciuffo che sporge indietro. */
private val BUSTO_CODA = floatArrayOf(
    -0.30f, 0.00f, -0.28f, 0.33f, -0.26f, 0.55f, -0.21f, 0.64f,
    -0.24f, 0.72f, -0.29f, 0.78f, -0.22f, 0.82f, -0.14f, 0.86f,
    -0.10f, 0.94f, -0.01f, 0.99f, 0.08f, 0.95f, 0.13f, 0.86f,
    0.11f, 0.78f, 0.07f, 0.71f, 0.06f, 0.63f, 0.16f, 0.57f,
    0.23f, 0.35f, 0.25f, 0.13f, 0.26f, 0.00f,
)

private val SAGOME = listOf(BUSTO_CAPPUCCIO, BUSTO_SCOPERTO, BUSTO_CODA)

/**
 * Chi passa: quale sagoma, in che corsia, quante traversate, da che parte.
 *
 * Sono quattro, seminati a mano e non a caso. Erano sei, e sei in un'apertura
 * larga duecentocinquanta punti sono una folla schiacciata contro il vetro:
 * quattro, con le corsie e le fasi distanti, sono una via.
 */
private class Walker(
    val shape: Int,
    /** 0 lontano, 1 vicino. Decide misura e quanto e' sbiadito. */
    val lane: Float,
    val phase: Float,
    /**
     * Quante volte traversa a ogni giro dell'orologio lento. **E' un intero, e
     * dev'esserlo**: e' quello che rende invisibile il ritorno a zero.
     * Chi sta piu' vicino ne fa due, e cosi' la corsia vicina e' anche la piu'
     * veloce - la parallasse fatta con la velocita' invece che con la
     * geometria, che a questa scala e' quella che si legge.
     */
    val laps: Int,
    /** Vero se cammina verso destra. Al contrario la sagoma si specchia. */
    val rightward: Boolean,
    /** Se ha l'ombrello, quando serve. Chi non ce l'ha resta senza anche sotto l'acqua. */
    val hasUmbrella: Boolean,
)

private val WALKERS = listOf(
    Walker(0, 1.00f, 0.05f, laps = 2, rightward = true, hasUmbrella = true),
    Walker(1, 0.42f, 0.37f, laps = 1, rightward = false, hasUmbrella = true),
    Walker(2, 0.78f, 0.62f, laps = 2, rightward = false, hasUmbrella = true),
    Walker(1, 0.60f, 0.88f, laps = 1, rightward = true, hasUmbrella = false),
)

/**
 * I colori della via e di chi ci cammina, ricavati dal cielo di quell'ora.
 *
 * **Le sagome sono scure per scelta**: una silhouette e' un buco nella luce, non
 * una figura grigia. Ma quattro buchi neri su un cielo notturno si fondono in
 * una macchia sola, e la risposta non e' schiarire la gente - **e' accendere la
 * strada**, che e' anche come stanno le cose davvero: di notte una via e'
 * illuminata, ed e' per questo che chi ci passa ci si staglia contro.
 *
 * Quindi la via si ricava **dalle sagome**, con lo stesso attrezzo con cui
 * questo progetto sceglie il colore di ogni segno disegnato sul cielo.
 *
 * **Non e' roba da fotogramma.** `readableOn` costa una manciata di elevamenti a
 * potenza per passo, e la sua documentazione lo dice: va chiamata una volta per
 * ora mostrata, dentro il `remember` di chi disegna, non dentro il disegno.
 */
internal class StreetInk(
    /** Le sagome. */
    val figure: Color,
    /** Gli ombrelli: un gradino sopra le sagome, se no la calotta sparisce nella testa. */
    val umbrella: Color,
    /** L'asfalto, dove e' piu' vicino. */
    val road: Color,
    /** L'asfalto dove si allontana: quasi il cielo, ed e' quello che gli da' fondo. */
    val haze: Color,
    /** I riflessi sull'asfalto bagnato. */
    val sheen: Color,
)

/** I colori della via sotto un cielo di quel colore. */
internal fun streetInk(sky: Color): StreetInk {
    val figure = lerp(sky, Color.Black, FIGURE_INK)
    val road = sky.readableOn(figure, STREET_LIFT)
    return StreetInk(
        figure = figure,
        umbrella = lerp(sky, Color.Black, UMBRELLA_INK),
        road = road,
        haze = lerp(sky, road, 0.42f),
        // Bianco o nero secondo cosa si vede sull'asfalto: su una via accesa
        // dalla notte un riflesso bianco non c'e' piu', e il riflesso e' il
        // segno che dice che e' bagnata.
        sheen = road.onColor(),
    )
}

/**
 * La via sotto la finestra.
 *
 * [walk] e' l'**orologio lento**, quello che avanza invece di ripetersi: la
 * pioggia gira su un ciclo di un secondo e mezzo, e leggere quel ciclo come se
 * fosse il tempo vuol dire tornare indietro di un pezzo di traversata ogni volta
 * che ricomincia. E' il tremolio che si vedeva.
 *
 * [chance] e' la probabilita' di pioggia da 0 a 1, ed e' cio' che decide quanti
 * ombrelli sono aperti **quando ancora non piove**.
 */
internal fun DrawScope.drawWalkers(
    bounds: Size,
    origin: Offset,
    walk: Float,
    /** Da 0 a 1: quanto forte piove. Piega le schiene e inclina gli ombrelli. */
    wetness: Float,
    /** Da 0 a 1: quanti ombrelli sono aperti anche senza acqua. */
    chance: Float,
    ink: StreetInk,
) {
    val street = origin.y + bounds.height
    val tall = bounds.height * WALKER_TALL

    for ((index, w) in WALKERS.withIndex()) {
        // **Il giro chiude per costruzione.** Quando `walk` torna da 1 a 0,
        // `walk * laps` cala di un intero, e il resto su 1 non se ne accorge:
        // il passante prosegue dov'era invece di scattare indietro. E' il
        // motivo per cui `laps` e' un intero e non una velocita'.
        val travel = (w.phase + walk * w.laps) % 1f
        val across = if (w.rightward) travel else 1f - travel

        val scale = tall * (0.62f + w.lane * 0.38f)
        // Fuori dall'apertura di tutta la sua mezza larghezza - ombrello
        // compreso, che e' la parte piu' larga - se no il rientro si vede.
        // Ricavato e non scelto: con il margine scritto a mano l'ombrello era
        // piu' largo del margine, e spuntava.
        val margin = scale * (UMBRELLA_WIDE + LEAN)
        val x = origin.x - margin + across * (bounds.width + margin * 2f)
        // Il saliscendi del passo: senza gambe e' l'unica cosa che dice che sta
        // camminando invece di scivolare. I passi per traversata sono interi
        // anche loro, se no il giro chiuderebbe nella posizione e non nel passo.
        val bob = sin(travel * PI.toFloat() * 2f * STEPS_PER_CROSSING) * scale * BOB
        // **La via ha profondita', e la profondita' e' in altezza.** Prima
        // avevano tutti i piedi sulla stessa riga, quindi due che si
        // avvicinavano si accavallavano e basta: un mucchio, non una via. Chi
        // e' lontano sta piu' in alto sulla fascia dell'asfalto - la sua testa
        // arriva appena all'orizzonte, mentre quella di chi passa sotto casa
        // lo supera - ed e' cosi' che una strada si guarda dall'alto.
        val depth = (1f - w.lane) * bounds.height * STREET_BAND * STREET_DEPTH
        val feet = street - depth + bob

        // Piu' lontano, piu' sbiadito: e' l'aria che sta in mezzo, ed e' quello
        // che separa una figura dall'altra senza contorni.
        val fade = 0.30f + w.lane * 0.55f
        // Sotto l'acqua si cammina curvi, e piu' forte piove piu' ci si piega.
        val lean = (if (w.rightward) 1f else -1f) * wetness * LEAN

        // **Se piove, l'ombrello e' aperto.** Prima lo decideva la sola
        // probabilita', ed era la domanda sbagliata: la probabilita' dice se
        // piovera', ma la scena mostra **un'ora**, e in quell'ora o piove o non
        // piove. Con il codice imposto degli scatti - pioggia certa, previsione
        // a zero - si vedeva la scena piovere con quattro persone a spasso
        // senza niente in mano. La probabilita' resta a decidere **quanti** si
        // coprono quando l'acqua non e' ancora arrivata, che e' la lettura
        // giusta di "il modello non lo esclude".
        val open = w.hasUmbrella && (wetness > 0f || chance > (index + 0.5f) / WALKERS.size)
        drawWalker(
            shape = SAGOME[w.shape],
            centre = Offset(x, feet),
            scale = scale,
            mirrored = !w.rightward,
            lean = lean,
            ink = ink.figure.copy(alpha = fade),
        )
        if (open) {
            drawUmbrella(
                centre = Offset(x, feet),
                scale = scale,
                mirrored = !w.rightward,
                // L'ombrello oscilla **in ritardo** rispetto al corpo: seguendolo
                // esatto sembrerebbe incollato alla testa.
                sway = sin(travel * PI.toFloat() * 2f * STEPS_PER_CROSSING - 0.9f) * SWAY,
                lean = lean,
                colour = ink.umbrella.copy(alpha = fade),
            )
        }
    }
}

/** Una sagoma, scalata e messa al suo posto. */
private fun DrawScope.drawWalker(
    shape: FloatArray,
    centre: Offset,
    scale: Float,
    mirrored: Boolean,
    lean: Float,
    ink: Color,
) {
    val flip = if (mirrored) -1f else 1f
    val path = Path()
    for (i in shape.indices step 2) {
        val sx = shape[i] * flip
        val sy = shape[i + 1]
        // L'inclinazione cresce con l'altezza: i piedi restano dove sono e le
        // spalle vanno avanti, che e' come ci si piega davvero.
        val x = centre.x + (sx + lean * sy) * scale
        val y = centre.y - sy * scale
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = ink)
}

/**
 * L'ombrello: una calotta e un'asta.
 *
 * Non un cerchio tagliato a meta': una calotta vera ha le punte che scendono
 * oltre il diametro, ed e' quello che la fa leggere come un ombrello invece che
 * come un fungo.
 */
private fun DrawScope.drawUmbrella(
    centre: Offset,
    scale: Float,
    mirrored: Boolean,
    sway: Float,
    lean: Float,
    colour: Color,
) {
    val flip = if (mirrored) -1f else 1f
    val head = Offset(
        centre.x + (lean * 1f + sway * 0.4f) * scale,
        centre.y - scale * UMBRELLA_HIGH,
    )
    val half = scale * UMBRELLA_WIDE
    val drop = scale * UMBRELLA_DROP

    val canopy = Path().apply {
        moveTo(head.x - half, head.y + drop)
        // Tre archi: le due falde e la cima. Con un arco solo la calotta e'
        // troppo tonda e diventa un cappello.
        quadraticTo(head.x - half * 0.55f, head.y - drop * 0.4f, head.x, head.y - drop * 0.9f)
        quadraticTo(head.x + half * 0.55f, head.y - drop * 0.4f, head.x + half, head.y + drop)
        quadraticTo(head.x + half * 0.5f, head.y + drop * 0.35f, head.x, head.y + drop * 0.42f)
        quadraticTo(head.x - half * 0.5f, head.y + drop * 0.35f, head.x - half, head.y + drop)
        close()
    }
    drawPath(canopy, color = colour)

    // L'asta, verso la mano: corta, perche' la mano sta appena sotto il taglio.
    drawLine(
        color = colour,
        start = Offset(head.x, head.y - drop * 0.9f),
        end = Offset(head.x + flip * scale * 0.10f, centre.y - scale * 0.34f),
        strokeWidth = (scale * 0.035f).coerceAtLeast(1f),
        cap = StrokeCap.Round,
    )
}

/**
 * La via sotto la finestra, e le pozze che ci stanno sopra.
 *
 * Serve perche' senza, le sagome galleggiano: una figura tagliata a meta' in
 * mezzo al cielo si legge come un errore di ritaglio, non come qualcuno visto
 * da una finestra. **E serve anche come fondo**: e' la fascia contro cui le
 * sagome si staccano, e per questo e' alta abbastanza da prendere i busti e
 * lasciare al cielo solo le teste.
 */
internal fun DrawScope.drawStreet(
    bounds: Size,
    origin: Offset,
    wetness: Float,
    road: Color,
    haze: Color,
    sheen: Color,
) {
    val street = origin.y + bounds.height
    val band = bounds.height * STREET_BAND
    // **Non una lastra.** Un rettangolo di un tono solo si legge come una
    // parete: e' una superficie vista di taglio, quindi in fondo va verso il
    // cielo e sotto verso l'asfalto, e la riga dell'orizzonte smette di essere
    // un taglio netto.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(haze, road),
            startY = street - band,
            endY = street,
        ),
        topLeft = Offset(origin.x, street - band),
        size = Size(bounds.width, band),
    )
    if (wetness <= 0f) return
    // L'asfalto bagnato riflette, e a questa misura riflettere vuol dire due
    // strisce chiare che corrono per il lungo: una pozza disegnata come una
    // macchia diventa una toppa.
    for (i in 0 until 3) {
        val y = street - band * (0.14f + i * 0.22f)
        val w = bounds.width * (0.5f - abs(i - 1) * 0.14f)
        drawLine(
            // Appena accennati: sono il luccichio dell'asfalto, e alzandoli si
            // trasformano in tre sbarre appoggiate sopra la scena.
            color = sheen.copy(alpha = 0.05f + 0.07f * wetness),
            start = Offset(origin.x + bounds.width * 0.5f - w * 0.5f, y),
            end = Offset(origin.x + bounds.width * 0.5f + w * 0.5f, y),
            strokeWidth = bounds.height * 0.004f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Quanto e' alta una sagoma della corsia piu' vicina, sull'apertura.
 *
 * Era 0,30, cioe' un terzo della finestra, e una persona alta un terzo della
 * finestra e' una persona **dentro la stanza**. Questa e' la misura di qualcuno
 * visto dall'altra parte della via.
 */
private const val WALKER_TALL = 0.13f

/** Quanti passi in una traversata: **intero**, se no il giro non chiude nel passo. */
private const val STEPS_PER_CROSSING = 24f

private const val BOB = 0.022f
private const val LEAN = 0.13f
private const val SWAY = 0.06f

private const val UMBRELLA_HIGH = 1.16f
private const val UMBRELLA_WIDE = 0.46f
private const val UMBRELLA_DROP = 0.13f

/** Quanto le sagome sono piu' scure del cielo dietro, e gli ombrelli con loro. */
private const val FIGURE_INK = 0.85f
private const val UMBRELLA_INK = 0.70f

/**
 * Di quanto la via si stacca dalle sagome.
 *
 * **Non e' la soglia da testo**, ed e' una scelta e non una dimenticanza: 3:1 e'
 * quanto serve a una scritta per essere letta, e applicato qui spinge l'asfalto
 * a un grigio medio - cioe' trasforma la notte in pieno giorno pur di far
 * risaltare quattro sagome. Una sagoma alta sessanta pixel non e' una
 * didascalia: le basta staccare.
 */
private const val STREET_LIFT = 1.9f

/** Quanto in alto sulla fascia arriva chi cammina in fondo alla via. */
private const val STREET_DEPTH = 0.80f

/**
 * Quanta parte in basso dell'apertura e' via, e non cielo.
 *
 * Presa alta apposta: i busti stanno contro l'asfalto, che e' il fondo che li
 * fa leggere, e al cielo restano solo le teste.
 */
private const val STREET_BAND = 0.20f
