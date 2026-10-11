package io.github.noximiliencoxen.caelum.wear

import android.content.Context
import android.content.SharedPreferences
import io.github.noximiliencoxen.caelum.data.StatoSincronizzato

/**
 * L'ultimo stato mandato dal telefono, tenuto sull'orologio.
 *
 * Una SharedPreferences e non un DataStore: il servizio che riceve il dato deve
 * scriverlo **prima di tornare**, e `commit()` e' sincrono. Una scrittura
 * asincrona poteva perdersi se il sistema fermava il servizio subito dopo.
 */
object StatoOrologio {

    private const val FILE = "stato_telefono"
    private const val CHIAVE = "json"

    fun prefs(contesto: Context): SharedPreferences =
        contesto.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun salva(contesto: Context, json: String) {
        prefs(contesto).edit().putString(CHIAVE, json).commit()
    }

    /** Lo stato salvato, o null se il telefono non ha mai scritto niente (o il testo e' illeggibile). */
    fun leggi(contesto: Context): StatoSincronizzato? =
        prefs(contesto).getString(CHIAVE, null)?.let(StatoSincronizzato::fromJson)

    const val CHIAVE_JSON = CHIAVE
}
