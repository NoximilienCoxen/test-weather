package com.caelumweather

import android.app.Application
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.inizializza
import com.caelumweather.sync.SincronizzaOrologio

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
