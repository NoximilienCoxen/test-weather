package io.github.noximiliencoxen.caelum

import android.app.Application
import io.github.noximiliencoxen.caelum.lingua.Lingue
import io.github.noximiliencoxen.caelum.lingua.inizializza
import io.github.noximiliencoxen.caelum.sync.SincronizzaOrologio

/**
 * L'applicazione, solo per una cosa: leggere la lingua scelta prima di tutto il
 * resto. Attivita', widget e lavori in sottofondo partono tutti da qui, quindi
 * nessuno di loro scrive una parola nella lingua sbagliata (CONTESTO §49).
 */
class CaelumApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Lingue.inizializza(this)
        // Localita', unita' e lingua all'orologio, se ce n'e' uno (CONTESTO §50).
        SincronizzaOrologio.avvia(this)
    }
}
