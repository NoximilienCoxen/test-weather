package io.github.noximiliencoxen.caelum.ui.sala.rooms.luna3d

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * La luna come sfera vera: vertici, normali, quanto e' chiara ogni zona, e
 * da dove arriva il sole.
 *
 * **Niente Android qui dentro**, di proposito: e' la parte con i conti, e i
 * conti si provano sulla JVM (vedi `GloboTest`). Chi disegna - 
 * [LunaInterattiva] - prende questi numeri e li proietta con la `Camera` che
 * i widget usano gia'.
 *
 * Il sistema e' quello della camera: x a destra, y in basso, **z negativo
 * verso chi guarda**. La faccia che la luna mostra alla Terra e' quella con z
 * negativo, e resta sempre la stessa - la luna e' in rotazione sincrona -
 * quindi i mari stanno scritti li' una volta per tutte.
 */
internal class Globo(
    private val paralleli: Int = 40,
    private val meridiani: Int = 80,
) {
    /** Quanti vertici. Ognuno e' anche la sua normale: la sfera ha raggio 1. */
    val vertici: Int = (paralleli + 1) * (meridiani + 1)

    val x = FloatArray(vertici)
    val y = FloatArray(vertici)
    val z = FloatArray(vertici)

    /** Quanto riflette ogni punto, da 0 a 1: i mari sono piu' scuri degli altipiani. */
    val albedo = FloatArray(vertici)

    /** I triangoli, tre indici ciascuno, in un verso solo. */
    val triangoli: ShortArray

    init {
        require(vertici <= Short.MAX_VALUE) { "troppi vertici per indici a 16 bit" }
        var v = 0
        for (i in 0..paralleli) {
            // Dal polo nord (y = -1, in alto sullo schermo) al polo sud.
            val theta = PI * i / paralleli
            for (j in 0..meridiani) {
                val phi = 2.0 * PI * j / meridiani
                x[v] = (sin(theta) * sin(phi)).toFloat()
                y[v] = (-cos(theta)).toFloat()
                z[v] = (-sin(theta) * cos(phi)).toFloat()
                albedo[v] = albedoIn(x[v], y[v], z[v])
                v++
            }
        }

        val t = ShortArray(paralleli * meridiani * 6)
        var k = 0
        for (i in 0 until paralleli) {
            for (j in 0 until meridiani) {
                val a = i * (meridiani + 1) + j
                val b = a + meridiani + 1
                t[k++] = a.toShort(); t[k++] = b.toShort(); t[k++] = (a + 1).toShort()
                t[k++] = (a + 1).toShort(); t[k++] = b.toShort(); t[k++] = (b + 1).toShort()
            }
        }
        triangoli = t
    }

    /**
     * Quanto e' chiaro ogni vertice con il sole della fase [fase].
     *
     * Luce netta e non Lambert dimezzato come nel resto del motore: il terminatore
     * della luna e' una linea, non una sfumatura, perche' li' non c'e' aria a
     * diffondere la luce. Sulla parte in ombra resta la **luce cinerea** - il
     * chiaro di Terra - appena sopra il nero: si vede davvero, a occhio nudo,
     * nei giorni della falce sottile.
     */
    fun luce(fase: Float, out: FloatArray) {
        val (sx, sy, sz) = direzioneSole(fase)
        for (v in 0 until vertici) {
            // Radice e non coseno puro: la luna non e' una palla opaca, e' una
            // superficie di polvere che rimanda la luce quasi uguale fino al
            // bordo - per questo la luna piena sembra un disco piatto e non una
            // sfera che si scurisce ai lati. Col coseno la meta' accesa si
            // spegneva gradualmente verso il terminatore, che invece e' netto.
            val diretta = (x[v] * sx + y[v] * sy + z[v] * sz).coerceAtLeast(0f).pow(ESPONENTE_REGOLITE)
            out[v] = albedo[v] * (LUCE_CINEREA + (1f - LUCE_CINEREA) * diretta)
        }
    }

    companion object {
        /** Il chiaro di Terra sulla parte in ombra. */
        const val LUCE_CINEREA = 0.05f

        /** Quanto e' piatta la luce sulla meta' accesa: 1 sarebbe Lambert puro. */
        private const val ESPONENTE_REGOLITE = 0.3f

        /**
         * Da dove viene il sole, per una fase da 0 (novilunio) a 1.
         *
         * Al novilunio sta **dietro** la luna (z positivo, lontano da chi
         * guarda), al primo quarto a destra, al plenilunio davanti, all'ultimo
         * quarto a sinistra: e' il giro che la luna fa attorno alla Terra, visto
         * dalla Terra. A destra al primo quarto perche' cosi' la si vede
         * dall'emisfero nord, come fa gia' tutto il resto dell'app.
         */
        fun direzioneSole(fase: Float): Triple<Float, Float, Float> {
            val a = 2.0 * PI * fase
            return Triple(sin(a).toFloat(), 0f, cos(a).toFloat())
        }

        /**
         * Quanta parte del disco visto e' illuminata, da 0 a 1, guardando con
         * la camera girata di [yawDeg] e [pitchDeg].
         *
         * Si campiona il disco come lo vede un occhio lontano: per ogni punto
         * del disco si ricava la normale della sfera li' sotto, la si riporta
         * nel sistema della luna con la rotazione inversa della camera, e si
         * guarda se il sole la tocca. Dalla Terra (0, 0) deve dare la stessa
         * frazione di `MoonPhase.illumination`: e' il controllo che dice che
         * luce e fase sono d'accordo.
         */
        fun frazioneIlluminataVista(fase: Float, yawDeg: Float, pitchDeg: Float, passi: Int = 120): Float {
            val (sx, sy, sz) = direzioneSole(fase)
            val cy = cos(yawDeg * GRADI); val syaw = sin(yawDeg * GRADI)
            val cp = cos(pitchDeg * GRADI); val sp = sin(pitchDeg * GRADI)
            var dentro = 0
            var accesi = 0
            for (a in 0 until passi) {
                val u = -1f + 2f * (a + 0.5f) / passi
                for (b in 0 until passi) {
                    val w = -1f + 2f * (b + 0.5f) / passi
                    val r2 = u * u + w * w
                    if (r2 > 1f) continue
                    dentro++
                    // Normale in coordinate di vista: verso chi guarda.
                    val vx = u
                    val vy = w
                    val vz = -sqrt(1f - r2)
                    // Inversa di Camera.place: prima il beccheggio, poi l'imbardata.
                    val y0 = vy * cp + vz * sp
                    val z1 = -vy * sp + vz * cp
                    val x0 = vx * cy - z1 * syaw
                    val z0 = vx * syaw + z1 * cy
                    if (x0 * sx + y0 * sy + z0 * sz > 0f) accesi++
                }
            }
            return if (dentro == 0) 0f else accesi.toFloat() / dentro
        }

        private val GRADI = (PI / 180.0).toFloat()

        /**
         * I mari, sulla faccia visibile: direzione (verso di noi = z negativo)
         * e raggio angolare. Non una carta fedele - la luna disegnata non ne ha
         * bisogno - ma disposti come si riconoscono a occhio: l'Imbrium grande in
         * alto a sinistra, la catena Serenitatis-Tranquillitatis a destra,
         * l'Oceanus Procellarum largo sul bordo sinistro.
         */
        private val MARI: List<FloatArray> = listOf(
            mare(-0.30f, -0.45f, 0.30f, 0.62f), // Imbrium
            mare(0.22f, -0.40f, 0.20f, 0.55f), // Serenitatis
            mare(0.40f, -0.08f, 0.24f, 0.55f), // Tranquillitatis
            mare(-0.62f, -0.05f, 0.40f, 0.50f), // Procellarum
            mare(0.62f, -0.30f, 0.12f, 0.50f), // Crisium
            mare(0.28f, 0.30f, 0.18f, 0.45f), // Nectaris / Fecunditatis
            mare(-0.28f, 0.28f, 0.20f, 0.45f), // Nubium
        )

        /** Qualche cratere chiaro, per i punti di luce: Tycho e Copernicus i piu' noti. */
        private val CRATERI: List<FloatArray> = listOf(
            mare(-0.12f, 0.62f, 0.05f, -0.35f), // Tycho
            mare(-0.32f, -0.20f, 0.05f, -0.25f), // Copernicus
            mare(-0.55f, -0.30f, 0.04f, -0.20f), // Aristarchus
        )

        private fun mare(dx: Float, dy: Float, raggio: Float, profondita: Float): FloatArray {
            val dz = -sqrt((1f - dx * dx - dy * dy).coerceAtLeast(0f))
            return floatArrayOf(dx, dy, dz, raggio, profondita)
        }

        private fun albedoIn(px: Float, py: Float, pz: Float): Float {
            var a = 1f
            for (m in MARI + CRATERI) {
                val coseno = (px * m[0] + py * m[1] + pz * m[2]).coerceIn(-1f, 1f)
                val angolo = acos(coseno)
                // Alla quarta e non al quadrato: un mare ha una riva, non e' una
                // nebbia. Col quadrato le macchie sfumavano per mezza luna.
                val q = (angolo / m[3]) * (angolo / m[3])
                a -= m[4] * exp(-q * q)
            }
            // Una grana leggera e deterministica, perche' gli altipiani non
            // sembrino plastica: nessun caso, stessa luna a ogni avvio.
            val grana = sin(px * 23f + py * 17f) * sin(py * 19f - pz * 29f) * sin(pz * 31f + px * 13f)
            return (a + grana * 0.035f).coerceIn(0.35f, 1.1f)
        }
    }
}
