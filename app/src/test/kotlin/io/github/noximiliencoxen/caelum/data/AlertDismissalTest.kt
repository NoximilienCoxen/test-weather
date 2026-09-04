package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La regola che decide se la fascia dell'allerta resta ridotta a pallino.
 *
 * Sono i quattro casi che a mano non si provano: per vederli davvero servirebbe
 * aspettare che la Protezione Civile emetta un avviso, poi che ne emetta un
 * secondo, poi che il primo peggiori, poi che uno scada. Qui costano quattro
 * asserzioni.
 */
class AlertDismissalTest {

    private fun alert(id: String, level: AlertLevel) = WeatherAlert(
        id = id,
        level = level,
        kind = AlertKind.TEMPORALI,
        headline = "prova",
        source = "prova",
        official = true,
    )

    @Test
    fun `senza allerte non c'e' niente da ridurre`() {
        assertFalse(alertsAreDismissed(emptyList(), setOf("a"), 3))
    }

    @Test
    fun `le stesse allerte gia' chiuse restano chiuse`() {
        val shown = listOf(alert("a", AlertLevel.GIALLA), alert("b", AlertLevel.ARANCIONE))
        assertTrue(alertsAreDismissed(shown, setOf("a", "b"), 2))
    }

    @Test
    fun `un'allerta mai vista riapre la fascia`() {
        // E' il caso che conta: se questo passasse, un avviso appena arrivato
        // resterebbe un puntino in un angolo perche' ieri se n'e' chiuso un
        // altro.
        val shown = listOf(alert("a", AlertLevel.GIALLA), alert("nuova", AlertLevel.GIALLA))
        assertFalse(alertsAreDismissed(shown, setOf("a"), 1))
    }

    @Test
    fun `un peggioramento riapre la fascia anche senza allerte nuove`() {
        // Stesso identificativo, colore diverso: non e' piu' la stessa notizia.
        val shown = listOf(alert("a", AlertLevel.ARANCIONE))
        assertFalse(alertsAreDismissed(shown, setOf("a"), 1))
    }

    @Test
    fun `un'allerta che scade non riapre la fascia`() {
        // Ne restano due su tre gia' viste: non e' successo niente di nuovo, e
        // far ricomparire la fascia perche' un avviso e' *finito* sarebbe il
        // contrario di avvisare.
        val shown = listOf(alert("a", AlertLevel.GIALLA), alert("b", AlertLevel.ARANCIONE))
        assertTrue(alertsAreDismissed(shown, setOf("a", "b", "c"), 2))
    }

    @Test
    fun `un miglioramento non riapre la fascia`() {
        // La peggiore di adesso e' meno grave della peggiore di allora, e tutte
        // erano gia' state chiuse.
        val shown = listOf(alert("a", AlertLevel.GIALLA))
        assertTrue(alertsAreDismissed(shown, setOf("a", "b"), 3))
    }
}
