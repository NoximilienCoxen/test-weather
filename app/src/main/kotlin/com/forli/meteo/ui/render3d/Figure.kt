package com.forli.meteo.ui.render3d

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * L'esploratore: un pupazzo fatto di sfere, con le stesse primitive della
 * nuvola e sotto la stessa luce.
 *
 * **Sfere e non un modello.** La nuvola e' gia' un grappolo di sfere a
 * profondita' diverse, e funziona: un personaggio fatto della stessa materia
 * appartiene al mondo dell'app senza dover insegnare al motore niente di nuovo
 * su come si illumina o si ombreggia una superficie. E una sfera e' convessa,
 * quindi ordinarla per la profondita' del proprio centro non e' un'
 * approssimazione: e' esatto.
 *
 * **Le braccia non hanno giunture.** Un gomito comandato ad angoli vorrebbe dire
 * cinematica inversa per far arrivare la mano esattamente sul tasto, e la mano
 * *deve* arrivarci - e' tutto il senso della posa. Invece ogni braccio e' una
 * fila di sfere lungo una curva che parte dalla spalla e finisce dove deve
 * finire, con un rigonfiamento a meta' che fa il gomito. La posa e' garantita
 * per costruzione, e il braccio si piega da solo quanto serve.
 *
 * Il [Rig] serve dove le giunture contano davvero: **la testa**. La mano che
 * fa da visiera sta sopra gli occhi, cioe' in un punto del sistema della testa;
 * girando la testa quel punto ruota con lei, e la mano lo insegue senza che
 * nessuno debba ricalcolare niente.
 */
class Explorer {

    private val rig = Rig()

    // Le sfere del fotogramma. Array paralleli e riusati: un pupazzo sono meno
    // di venti corpi, ma li si raccoglie, ordina e disegna sessanta volte al
    // secondo, e allocare per farlo darebbe al raccoglitore piu' lavoro del
    // disegno.
    private val px = FloatArray(MAX)
    private val py = FloatArray(MAX)
    private val pz = FloatArray(MAX)
    private val pr = FloatArray(MAX)
    private val hat = BooleanArray(MAX)
    private val wide = FloatArray(MAX)
    private val tall = FloatArray(MAX)
    private val depth = FloatArray(MAX)
    private val order = IntArray(MAX)
    private var count = 0

    private fun add(
        x: Float,
        y: Float,
        z: Float,
        radius: Float,
        isHat: Boolean = false,
        w: Float = 1f,
        t: Float = 1f,
    ) {
        if (count >= MAX) return
        px[count] = x; py[count] = y; pz[count] = z; pr[count] = radius
        hat[count] = isHat; wide[count] = w; tall[count] = t
        count++
    }

    /**
     * Una fila di sfere dalla spalla alla mano, con il gomito che sporge.
     *
     * La curva e' una quadratica: due estremi e un punto di controllo spostato
     * di lato. Spostandolo si decide da che parte si piega il braccio, e non c'e'
     * angolo da risolvere.
     */
    private fun arm(
        fromX: Float, fromY: Float, fromZ: Float,
        toX: Float, toY: Float, toZ: Float,
        bowX: Float, bowY: Float,
        thick: Float,
        handWide: Float = 1f,
        handTall: Float = 1f,
    ) {
        val cx = (fromX + toX) / 2f + bowX
        val cy = (fromY + toY) / 2f + bowY
        val cz = (fromZ + toZ) / 2f
        for (k in 0 until BEADS) {
            val t = (k + 1f) / (BEADS + 1f)
            val u = 1f - t
            add(
                x = u * u * fromX + 2f * u * t * cx + t * t * toX,
                y = u * u * fromY + 2f * u * t * cy + t * t * toY,
                z = u * u * fromZ + 2f * u * t * cz + t * t * toZ,
                // Si assottiglia verso il polso: un braccio di sfere tutte
                // uguali si legge come una collana, non come un arto.
                radius = thick * (1f - 0.28f * t),
            )
        }
        add(toX, toY, toZ, thick * 1.12f, w = handWide, t = handTall)
    }

    /**
     * Costruisce la posa. Va chiamata prima di [draw], una volta per fotogramma.
     *
     * @param reachX dove la mano d'appoggio deve arrivare, in coordinate del
     *   modello: e' il tasto, e la mano ci finisce sopra per costruzione.
     */
    fun pose(
        headYaw: Float,
        reachX: Float,
        reachY: Float,
        shrug: Float,
        breath: Float,
    ) {
        count = 0
        rig.reset()

        // Le spalle salgono e il collo rientra: e' l'unica differenza fra
        // "sto cercando" e "non lo so", e vale piu' di qualunque scritta.
        val lift = shrug * 0.055f
        val bob = breath * 0.012f

        // Busto, dal basso in alto, piu' due spalle. Senza le spalle la sagoma
        // e' due palle impilate e si legge pupazzo di neve: e' il restringimento
        // al collo e l'allargamento sotto a dire che quello e' un torace.
        add(0f, 0.17f + bob, 0f, 0.150f)
        add(0f, -0.02f + bob, 0f, 0.195f)
        add(-0.150f, -0.085f + bob - lift, 0.01f, 0.078f)
        add(0.150f, -0.085f + bob - lift * 0.7f, 0.01f, 0.078f)

        // Testa e cappello vivono nel sistema della testa: entrando qui, tutto
        // quello che si chiede sotto ruota con lei.
        rig.push(0f, -0.305f + bob - lift, 0f, ryDeg = headYaw)
        rig.at(0f, 0f, 0f)
        add(rig.x, rig.y, rig.z, 0.145f)
        // La calotta sta piu' in alto della tesa e sporge sopra la testa: sotto,
        // spariva dietro di lei e restava solo un disco che galleggiava.
        rig.at(0f, -0.120f, 0.005f)
        add(rig.x, rig.y, rig.z, 0.112f, isHat = true)
        rig.at(0f, -0.052f, 0.010f)
        add(rig.x, rig.y, rig.z, 0.124f, isHat = true, w = 1.5f, t = 0.30f)

        // Il punto sopra gli occhi, dalla parte in cui la testa sta guardando.
        // E' definito **nel sistema della testa**: girandola, la visiera lo
        // segue da sola.
        rig.at(-0.125f, -0.048f, -0.20f)
        val browX = rig.x
        val browY = rig.y
        val browZ = rig.z
        rig.pop()

        // Il braccio che scruta: dalla spalla al punto sopra gli occhi, col
        // gomito che sporge in fuori e in basso. La mano e' schiacciata e larga,
        // se no e' una pallina appoggiata alla fronte invece di una visiera.
        arm(
            fromX = -0.160f, fromY = -0.075f + bob - lift, fromZ = 0.01f,
            toX = browX, toY = browY, toZ = browZ,
            bowX = -0.17f, bowY = 0.11f,
            thick = 0.052f,
            handWide = 1.9f,
            handTall = 0.62f,
        )

        // Il braccio che si appoggia: dalla spalla al tasto. La mano ci arriva
        // esattamente, perche' e' l'estremo della curva e non il risultato di
        // una catena di angoli.
        arm(
            fromX = 0.160f, fromY = -0.075f + bob - lift * 0.7f, fromZ = 0.01f,
            toX = reachX, toY = reachY, toZ = -0.12f,
            bowX = 0.06f, bowY = 0.13f,
            thick = 0.052f,
            handWide = 1.5f,
            handTall = 0.78f,
        )
    }

    /**
     * Disegna la posa costruita, dal corpo piu' lontano al piu' vicino.
     *
     * L'ordine e' obbligatorio e non ereditato: la garanzia su cui regge il
     * resto del motore - profondita' monotona lungo l'asse orizzontale del
     * modello - vale finche' si ruota attorno a un asse solo, e un braccio che
     * si piega per conto suo la manda in pezzi. Qui si ordina davvero, con lo
     * stesso inserimento diretto della scultura meteo: meno di venti elementi,
     * e nessuna allocazione.
     */
    fun draw(
        scope: DrawScope,
        camera: Camera,
        unit: Float,
        skin: Color,
        shade: Color,
        hatSkin: Color,
        hatShade: Color,
        alpha: Float = 1f,
    ) = with(scope) {
        if (count == 0 || alpha <= 0.003f) return@with

        for (i in 0 until count) {
            camera.place(px[i] * unit, py[i] * unit, pz[i] * unit)
            depth[i] = camera.vz
            order[i] = i
        }
        for (i in 1 until count) {
            val d = depth[i]
            val which = order[i]
            var j = i - 1
            while (j >= 0 && depth[j] < d) {
                depth[j + 1] = depth[j]
                order[j + 1] = order[j]
                j--
            }
            depth[j + 1] = d
            order[j + 1] = which
        }

        for (k in 0 until count) {
            val i = order[k]
            sphere(
                camera = camera,
                x = px[i] * unit,
                y = py[i] * unit,
                z = pz[i] * unit,
                radius = pr[i] * unit,
                light = if (hat[i]) hatSkin else skin,
                dark = if (hat[i]) hatShade else shade,
                alpha = alpha,
                wide = wide[i],
                tall = tall[i],
            )
        }
    }

    private companion object {
        const val MAX = 24

        /** Sfere per braccio, oltre alla mano. Quattro bastano a leggere una piega. */
        const val BEADS = 4
    }
}
