package io.github.noximiliencoxen.caelum.lingua

import android.content.Context

/**
 * La scelta della lingua, salvata sul telefono. La parte pura (`Lingue`, `tr`)
 * sta nel modulo `core`, che gira anche sull'orologio e non ha un `Context`.
 */
private const val FILE = "lingua"
private const val CHIAVE = "scelta"

fun Lingue.inizializza(context: Context) {
    val salvata = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(CHIAVE, null)
    impostaScelta(SceltaLingua.entries.firstOrNull { it.name == salvata } ?: SceltaLingua.AUTOMATICA)
}

fun Lingue.scegli(context: Context, nuova: SceltaLingua) {
    impostaScelta(nuova)
    context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(CHIAVE, nuova.name).apply()
}
