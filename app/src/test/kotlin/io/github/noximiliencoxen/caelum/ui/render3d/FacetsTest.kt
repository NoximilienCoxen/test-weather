package io.github.noximiliencoxen.caelum.ui.render3d

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * I casi che tengono fermo un difetto **gia' spedito**: la colonna d'acqua della
 * vasca usciva vuota per il 58%, perche' meta' delle facce girava al contrario e
 * il *non-zero* le annullava.
 *
 * Sono aritmetica, non disegno, ed e' per questo che stanno qui: girano senza
 * emulatore. Il `Path` che ci sta attorno vorrebbe Robolectric e non
 * aggiungerebbe niente a quello che questi casi provano.
 */
class FacetsTest {

    /** Un vertice del prisma esagonale, proiettato come lo proietta la vasca. */
    private fun corner(k: Int, y: Float, yawDeg: Float): Offset {
        val a = Math.toRadians((k * 60.0 - 30.0)).toFloat()
        val r = 85f
        val distance = 764f
        val pitch = Math.toRadians(14.0).toFloat()
        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()

        val x = r * sin(a)
        val z = -r * cos(a)
        val x1 = x * cos(yaw) + z * sin(yaw)
        val z1 = -x * sin(yaw) + z * cos(yaw)
        val vy = y * cos(pitch) - z1 * sin(pitch)
        val vz = y * sin(pitch) + z1 * cos(pitch)
        val scale = distance / maxOf(distance + vz, distance * 0.2f)
        return Offset(x1 * scale, vy * scale)
    }

    private fun prismPoints(yawDeg: Float): List<Offset> =
        (0 until 6).map { corner(it, 66f, yawDeg) } + (0 until 6).map { corner(it, -66f, yawDeg) }

    // ── Il difetto, colto sul fatto ─────────────────────────────────────────

    @Test
    fun `le facce di un prisma proiettato non girano tutte nello stesso verso`() {
        // E' la causa esatta del buco: se girassero tutte uguali non ci sarebbe
        // niente da normalizzare, e questo caso e' quello che dice **perche'**
        // `addFacet` esiste. Se un giorno fallisse, vorrebbe dire che la
        // proiezione e' cambiata - non che il difetto e' sparito.
        val versi = (0 until 6).map { k ->
            facetTwiceArea(
                corner(k, -66f, 0f),
                corner(k + 1, -66f, 0f),
                corner(k + 1, 66f, 0f),
                corner(k, 66f, 0f),
            ) >= 0f
        }.toSet()

        assertEquals(setOf(true, false), versi)
    }

    @Test
    fun `il verso si legge dal segno, e invertendo i vertici si inverte`() {
        val a = Offset(0f, 0f)
        val b = Offset(10f, 0f)
        val c = Offset(10f, 10f)
        val d = Offset(0f, 10f)

        val diritto = facetTwiceArea(a, b, c, d)
        val rovescio = facetTwiceArea(a, d, c, b)

        assertTrue("$diritto", diritto != 0f)
        assertEquals(-diritto, rovescio, 1e-3f)
    }

    // ── L'inviluppo ─────────────────────────────────────────────────────────

    @Test
    fun `un punto dentro non entra nell'inviluppo`() {
        val hull = convexHull(
            listOf(
                Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 10f), Offset(0f, 10f),
                Offset(5f, 5f),
            ),
        )
        assertEquals(4, hull.size)
        assertTrue(hull.toString(), Offset(5f, 5f) !in hull)
    }

    @Test
    fun `la sagoma del prisma contiene tutti i suoi vertici, a ogni angolo`() {
        // La prova che conta: qualunque cosa faccia la rotazione, nessun vertice
        // deve restare fuori dalla sagoma - se restasse, si vedrebbe uno spigolo
        // sporgere dall'acqua.
        for (yaw in intArrayOf(0, 23, 60, 137, 180, 271)) {
            val points = prismPoints(yaw.toFloat())
            val hull = convexHull(points)
            assertTrue("yaw $yaw: inviluppo degenere", hull.size >= 3)
            for (p in points) {
                assertTrue("yaw $yaw: $p fuori dalla sagoma", inside(hull, p))
            }
        }
    }

    @Test
    fun `meno di tre punti non fanno una sagoma`() {
        assertEquals(emptyList<Offset>(), convexHull(listOf(Offset(0f, 0f), Offset(1f, 1f))))
        // Tre punti allineati non racchiudono niente, e dirlo e' meglio che
        // restituire una scheggia che poi qualcuno riempie.
        assertEquals(
            emptyList<Offset>(),
            convexHull(listOf(Offset(0f, 0f), Offset(1f, 1f), Offset(2f, 2f))),
        )
    }

    /** Dentro o sul bordo, con una tolleranza che perdona i decimali. */
    private fun inside(hull: List<Offset>, p: Offset): Boolean {
        var positivi = 0
        var negativi = 0
        for (i in hull.indices) {
            val a = hull[i]
            val b = hull[(i + 1) % hull.size]
            val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
            if (cross > 1e-2f) positivi++
            if (cross < -1e-2f) negativi++
        }
        return positivi == 0 || negativi == 0
    }
}
