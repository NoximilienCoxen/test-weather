package com.caelumweather.data

import com.caelumweather.lingua.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

/**
 * Il mare davanti alla localita': onde e temperatura dell'acqua, ora per ora.
 *
 * **Solo dove c'e'.** L'API del mare di Open-Meteo risponde a qualunque
 * coordinata, ma a Forli', a Milano o a Denver lascia nulle tutte le colonne;
 * a Rimini, Genova, Venezia o Honolulu le riempie con la cella di mare piu'
 * vicina (risposte lette l'11 novembre 2026). Come per la scala dell'aria, a
 * decidere sono i dati e non un elenco di citta' di costa tenuto a mano:
 * [parseMare] torna nullo quando la risposta non porta nemmeno un numero, e la
 * sezione in Sala VI allora non esiste.
 *
 * **E' un quarto indirizzo di Open-Meteo**, `marine-api.open-meteo.com`: sta
 * scritto nell'informativa (`docs/privacy.md`) e nelle note legali, che vanno
 * tenute al passo con questo file.
 *
 * **E' una stima al largo.** Il modello d'onda lavora su celle di chilometri e
 * non vede moli, secche e risacca: la sala lo dice sotto i numeri, e dice di
 * non usarli per navigare ne' per decidere un bagno.
 */
data class OraMare(
    val time: LocalDateTime,
    /** Altezza significativa dell'onda, in metri. */
    val onda: Double? = null,
    /** Da dove arrivano le onde, in gradi: come il vento. */
    val direzione: Double? = null,
    /** Secondi fra un'onda e la successiva. */
    val periodo: Double? = null,
    /** Temperatura dell'acqua in superficie, in gradi Celsius. */
    val acqua: Double? = null,
)

data class Mare(val ore: List<OraMare>) {
    /** L'ora di mare che corrisponde a un'ora della previsione, se c'e'. */
    fun alle(momento: LocalDateTime?): OraMare? {
        if (momento == null) return null
        return ore.firstOrNull { it.time == momento }
    }
}

/**
 * Lo stato del mare secondo la scala Douglas, dall'altezza dell'onda.
 *
 * I nomi italiani sono quelli dei bollettini; gli inglesi quelli dell'OMM.
 * Dieci gradini, da 0 a 9, coi limiti della scala: 0,10 - 0,50 - 1,25 - 2,50 -
 * 4 - 6 - 9 - 14 metri.
 */
enum class StatoDelMare(
    private val ita: String,
    private val eng: String,
    private val ted: String,
    private val fra: String,
    private val spa: String,
    internal val finoA: Double,
) {
    CALMO("calmo", "calm", "spiegelglatt", "mer calme", "mar en calma", 0.05),
    QUASI_CALMO("quasi calmo", "rippled", "ruhig", "mer ridée", "mar rizada", 0.10),
    POCO_MOSSO("poco mosso", "smooth", "schwach bewegt", "mer belle", "marejadilla", 0.50),
    MOSSO("mosso", "slight", "leicht bewegt", "mer peu agitée", "marejada", 1.25),
    MOLTO_MOSSO("molto mosso", "moderate", "mäßig bewegt", "mer agitée", "fuerte marejada", 2.50),
    AGITATO("agitato", "rough", "grobe See", "mer forte", "mar gruesa", 4.0),
    MOLTO_AGITATO("molto agitato", "very rough", "sehr grobe See", "mer très forte", "mar muy gruesa", 6.0),
    GROSSO("grosso", "high", "hohe See", "mer grosse", "mar arbolada", 9.0),
    MOLTO_GROSSO("molto grosso", "very high", "sehr hohe See", "mer très grosse", "mar montañosa", 14.0),
    TEMPESTOSO("tempestoso", "phenomenal", "außergewöhnlich schwere See", "mer énorme", "mar enorme", Double.MAX_VALUE),
    ;

    val label: String get() = tr(ita, eng, ted, fra, spa)

    companion object {
        fun di(metri: Double?): StatoDelMare? {
            if (metri == null || !metri.isFinite() || metri < 0.0) return null
            return entries.first { metri <= it.finoA }
        }
    }
}

@Serializable
private data class MareDto(
    val hourly: MareOreDto? = null,
    val error: Boolean? = null,
    val reason: String? = null,
)

@Serializable
private data class MareOreDto(
    val time: List<String> = emptyList(),
    @SerialName("wave_height") val onda: List<Double?> = emptyList(),
    @SerialName("wave_direction") val direzione: List<Double?> = emptyList(),
    @SerialName("wave_period") val periodo: List<Double?> = emptyList(),
    @SerialName("sea_surface_temperature") val acqua: List<Double?> = emptyList(),
)

private val mareJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
}

/**
 * Legge una risposta dell'API del mare. **Nullo se il mare non c'e'**: nessuna
 * ora porta un'altezza d'onda ne' una temperatura dell'acqua. Un errore
 * dichiarato dall'API invece solleva, perche' "non lo so" e "qui non c'e'
 * mare" non sono la stessa risposta.
 */
internal fun parseMare(body: String): Mare? {
    val dto = mareJson.decodeFromString<MareDto>(body)
    if (dto.error == true) error(dto.reason ?: "Open-Meteo (mare) ha risposto con un errore")
    val h = dto.hourly ?: return null
    val ore = h.time.mapIndexedNotNull { i, iso ->
        val quando = runCatching { LocalDateTime.parse(iso) }.getOrNull() ?: return@mapIndexedNotNull null
        OraMare(
            time = quando,
            onda = h.onda.getOrNull(i),
            direzione = h.direzione.getOrNull(i),
            periodo = h.periodo.getOrNull(i),
            acqua = h.acqua.getOrNull(i),
        )
    }
    if (ore.none { it.onda != null || it.acqua != null }) return null
    return Mare(ore)
}

class MareRepository(private val place: Place) {

    /** Il mare della localita', o nullo (con successo) se non e' di costa. */
    suspend fun load(): Result<Mare?> = withContext(Dispatchers.IO) {
        runCatching {
            require(place.hasFiniteCoordinates) {
                "Coordinate non utilizzabili per ${place.name}: ${place.latitude}, ${place.longitude}"
            }
            parseMare(httpGet(buildUrl(), fonte = "Open-Meteo (mare)"))
        }
    }

    private fun buildUrl(): String = buildString {
        append(MARE_ENDPOINT)
        append("?latitude=").append(place.latitude)
        append("&longitude=").append(place.longitude)
        // Gli stessi otto giorni e lo stesso fuso della previsione: le ore
        // devono combaciare una per una con quelle della barra.
        append("&timezone=auto&forecast_days=8")
        append("&hourly=").append(MARE_VARS)
    }

    companion object {
        const val MARE_ENDPOINT = "https://marine-api.open-meteo.com/v1/marine"
        const val MARE_VARS = "wave_height,wave_direction,wave_period,sea_surface_temperature"
    }
}
