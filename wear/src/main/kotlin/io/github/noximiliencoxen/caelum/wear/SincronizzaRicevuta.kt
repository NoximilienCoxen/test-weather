package io.github.noximiliencoxen.caelum.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import io.github.noximiliencoxen.caelum.data.StatoSincronizzato

/** Riceve dal telefono la localita', le unita' e la lingua (`/caelum/stato`). */
class SincronizzaRicevuta : WearableListenerService() {

    override fun onDataChanged(eventi: DataEventBuffer) {
        for (evento in eventi) {
            if (evento.type != DataEvent.TYPE_CHANGED) continue
            if (evento.dataItem.uri.path != StatoSincronizzato.PERCORSO) continue
            val json = evento.dataItem.data?.toString(Charsets.UTF_8) ?: continue
            // Solo un messaggio leggibile sostituisce quello di prima.
            if (StatoSincronizzato.fromJson(json) != null) StatoOrologio.salva(this, json)
        }
    }
}
