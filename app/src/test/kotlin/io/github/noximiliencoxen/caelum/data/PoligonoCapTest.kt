package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Il punto nel poligono CAP, sul poligono vero della regione di Tomsk (IFRC, 5 ottobre 2026). */
class PoligonoCapTest {

    private val tomsk = "57.153,88.562 57.634,89.392 57.951,89.318 58.502,87.919 59.026,88.827 59.957,86.571 " +
        "59.9,84.694 60.358,84.783 60.855,84.26 60.825,83.994 61.049,83.509 60.518,82.167 60.857,77.135 " +
        "58.663,75.127 58.581,75.105 58.473,75.356 58.345,75.054 58.22,75.24 58.118,75.066 57.934,75.564 " +
        "57.646,75.583 57.247,76.136 56.931,79.587 56.43,80.286 56.54,81.18 56.253,81.536 56.553,83.088 " +
        "55.726,83.226 55.697,83.639 56.015,83.966 56.057,84.211 55.988,84.371 56.044,84.411 56.554,87.766 " +
        "56.833,88.624 57.097,88.524 57.153,88.562"

    @Test
    fun `Tomsk sta nella sua regione`() {
        assertEquals(true, dentroPoligonoCap(tomsk, 56.50, 84.97))
    }

    @Test
    fun `Novosibirsk e Mosca no`() {
        assertEquals(false, dentroPoligonoCap(tomsk, 55.03, 82.92))
        assertEquals(false, dentroPoligonoCap(tomsk, 55.75, 37.62))
    }

    @Test
    fun `un poligono illeggibile non decide`() {
        assertNull(dentroPoligonoCap("", 0.0, 0.0))
        assertNull(dentroPoligonoCap("1,2 3,4", 0.0, 0.0))
    }
}
