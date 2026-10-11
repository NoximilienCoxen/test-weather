package io.github.noximiliencoxen.caelum.data

import io.github.noximiliencoxen.caelum.lingua.SceltaLingua
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Cio' che il telefono dice all'orologio: la localita', le unita' e la lingua.
 *
 * Le previsioni non viaggiano: l'orologio le scarica da solo. Questo e' il
 * messaggio del Data Layer (`/caelum/stato`), scritto come JSON in un solo
 * campo cosi' lo leggono sia l'app sia le prove, senza Android.
 *
 * Le unita' e la lingua sono **stringhe col nome dell'enum**, e non gli enum:
 * `TempUnit` sta nell'app del telefono, e un valore che l'orologio non conosce
 * (una versione piu' nuova) deve ripiegare su quello di serie invece di
 * rompere la lettura.
 */
@Serializable
data class StatoSincronizzato(
    val nome: String,
    val admin: String? = null,
    val paese: String? = null,
    val latitudine: Double,
    val longitudine: Double,
    val unita: String = "CELSIUS",
    val unitaVento: String = "KMH",
    val lingua: String = "AUTOMATICA",
) {
    fun toPlace(): Place = Place(nome, admin, paese, latitudine, longitudine)

    val inFahrenheit: Boolean get() = unita == "FAHRENHEIT"

    val sceltaLingua: SceltaLingua
        get() = SceltaLingua.entries.firstOrNull { it.name == lingua } ?: SceltaLingua.AUTOMATICA

    fun toJson(): String = json.encodeToString(this)

    companion object {
        /** Il percorso del dato nel Data Layer. */
        const val PERCORSO = "/caelum/stato"

        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        /** Il messaggio letto dal JSON, o null se non e' leggibile o ha coordinate che non esistono. */
        fun fromJson(testo: String): StatoSincronizzato? =
            runCatching { json.decodeFromString<StatoSincronizzato>(testo) }
                .getOrNull()
                ?.takeIf { it.latitudine.isFinite() && it.longitudine.isFinite() }

        fun di(
            place: Place,
            unita: String,
            unitaVento: String,
            lingua: SceltaLingua,
        ) = StatoSincronizzato(
            nome = place.name,
            admin = place.admin,
            paese = place.country,
            latitudine = place.latitude,
            longitudine = place.longitude,
            unita = unita,
            unitaVento = unitaVento,
            lingua = lingua.name,
        )
    }
}
