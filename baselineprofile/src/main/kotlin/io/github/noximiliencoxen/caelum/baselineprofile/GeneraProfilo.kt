package io.github.noximiliencoxen.caelum.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Il percorso che il baseline profile insegna ad Android a compilare prima.
 *
 * Avvio, le sette sale avanti e indietro, la barra delle ore trascinata: e' il
 * giro che fa chi apre l'app, ed e' dove si sentiva lo scatto (§27: la barra
 * delle ore, e il primo scorrimento a freddo). Il cielo animato ci passa da
 * se', perche' sta dietro a tutto.
 *
 * Si genera in CI, su richiesta (`workflow_dispatch` con `profilo`), e il
 * risultato si copia in `app/src/main/baseline-prof.txt`.
 */
@RunWith(AndroidJUnit4::class)
class GeneraProfilo {

    @get:Rule
    val regola = BaselineProfileRule()

    @Test
    fun avvioESale() = regola.collect(
        packageName = PACCHETTO,
        // Anche nel profilo d'avvio, che ordina il codice nel dex: e' quello
        // che accorcia l'apertura a freddo.
        includeInStartupProfile = true,
    ) {
        pressHome()
        // Il benvenuto si salta con l'aggancio della build di debug
        // (`saltabenvenuto`); **non** si passa `cattura`, che fermerebbe ogni
        // animazione e toglierebbe dal profilo proprio il codice che si muove.
        startActivityAndWait { it.putExtra("saltabenvenuto", true) }

        // La guida all'uso parte da sola qualche secondo dopo l'avvio, quando
        // arrivano i dati: se compare, si salta.
        device.wait(Until.hasObject(By.text("Salta")), ATTESA_GUIDA_MS)
        device.findObject(By.text("Salta"))?.click()
        device.waitForIdle()

        val larghezza = device.displayWidth
        val altezza = device.displayHeight
        val x = larghezza * 2 / 5
        // Le sette sale, in su e poi in giu'.
        repeat(SALE - 1) {
            device.swipe(x, altezza * 7 / 10, x, altezza * 3 / 10, PASSI_COLPETTO)
            device.waitForIdle()
        }
        repeat(SALE - 1) {
            device.swipe(x, altezza * 3 / 10, x, altezza * 7 / 10, PASSI_COLPETTO)
            device.waitForIdle()
        }
        // La barra delle ore sta in fondo, sopra la barra di navigazione:
        // trascinata da un capo all'altro e ritorno.
        val yBarra = altezza * 89 / 100
        device.swipe(larghezza / 8, yBarra, larghezza * 6 / 8, yBarra, PASSI_TRASCINAMENTO)
        device.swipe(larghezza * 6 / 8, yBarra, larghezza / 8, yBarra, PASSI_TRASCINAMENTO)
        device.waitForIdle()
    }

    private companion object {
        const val PACCHETTO = "io.github.noximiliencoxen.caelum"
        const val SALE = 7
        const val ATTESA_GUIDA_MS = 8_000L
        /** Passi di `swipe`, cinque millesimi l'uno: un colpetto. */
        const val PASSI_COLPETTO = 12
        /** Un trascinamento lento, che passa per ogni ora. */
        const val PASSI_TRASCINAMENTO = 80
    }
}
