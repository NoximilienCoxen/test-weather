package io.github.noximiliencoxen.caelum.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Qualita' dell'aria, **sulla scala del posto**.
 *
 * Fonte separata da quella delle previsioni: Open-Meteo la serve su un altro
 * host, e non ha senso appesantire ogni previsione con inquinanti che solo un
 * widget guarda.
 *
 * Si chiedeva il solo `european_aqi` e lo si mostrava ovunque. Il dato e'
 * mondiale, la scala no: l'indice europeo e' una convenzione dell'Agenzia
 * europea dell'ambiente, e applicarla a Tokyo da' **un numero giusto su una
 * scala sbagliata**, che e' un numero sbagliato. Adesso si chiedono tutte e
 * due - quella europea e quella statunitense, che e' la piu' usata fuori - e a
 * scegliere sono i dati: se l'endpoint riempie l'indice europeo si e' dentro il
 * suo dominio, se lo lascia nullo si e' fuori. Meglio che disegnare a mano un
 * rettangolo di longitudini, che sarebbe una bugia sui bordi.
 *
 * **Quale scala si sta usando va scritto sullo schermo**: due indici con lo
 * stesso nome e soglie diverse, senza etichetta, sono peggio di uno solo.
 */
@Serializable
private data class AirQualityDto(
    val current: AirQualityCurrentDto? = null,
    val error: Boolean? = null,
    val reason: String? = null,
)

@Serializable
private data class AirQualityCurrentDto(
    @SerialName("european_aqi") val europeanAqi: Double? = null,
    @SerialName("us_aqi") val usAqi: Double? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
    val pm10: Double? = null,
    @SerialName("nitrogen_dioxide") val nitrogenDioxide: Double? = null,
    val ozone: Double? = null,
)

/**
 * Con quale metro si sta misurando l'aria.
 *
 * Due scale con lo stesso mestiere e numeri che non si confrontano: cinquanta
 * sull'europea e' aria mediocre, cinquanta sulla statunitense e' aria buona. Per
 * questo [label] non e' un'etichetta di contorno - e' la sola cosa che rende
 * leggibile il numero accanto.
 */
enum class AirScale(val label: String, internal val cuts: IntArray) {
    /** Agenzia europea dell'ambiente: sei bande da venti in venti. */
    EUROPEA("INDICE EUROPEO", intArrayOf(20, 40, 60, 80, 100)),

    /** EPA: sei bande, e la prima da sola vale due e mezzo di quelle europee. */
    STATUNITENSE("INDICE STATUNITENSE", intArrayOf(50, 100, 150, 200, 300)),
    ;

    fun band(index: Int?): AirBand? {
        if (index == null) return null
        val step = cuts.indexOfFirst { index <= it }
        return AirBand.entries[if (step < 0) AirBand.entries.lastIndex else step]
    }
}

/**
 * Le sei bande, dalla migliore alla peggiore.
 *
 * I nomi sono quelli che l'AEA usa in italiano, e restano gli stessi su tutte e
 * due le scale: sono un **giudizio**, non una traduzione delle categorie
 * americane. A dire con quale metro si e' arrivati a quel giudizio ci pensa
 * [AirScale.label], scritto accanto al numero.
 */
enum class AirBand(val label: String) {
    BUONA("BUONA"),
    DISCRETA("DISCRETA"),
    MEDIA("MEDIA"),
    SCARSA("SCARSA"),
    MOLTO_SCARSA("MOLTO SCARSA"),
    ESTREMAMENTE_SCARSA("PESSIMA"),
    ;

    companion object {
        /**
         * Le soglie europee, per chi ha in mano solo un numero.
         *
         * Resta per compatibilita' con chi non sa da quale scala venga il
         * valore; chi lo sa passa da [AirScale.band], che e' l'unica strada che
         * non applica le soglie di un continente ai numeri di un altro.
         */
        fun of(aqi: Int?): AirBand? = AirScale.EUROPEA.band(aqi)
    }
}

/** Quel che serve a dire com'e' l'aria adesso, e con quale metro. */
data class AirQuality(
    val index: Int?,
    /** Da quale delle due scale viene [index]. Va scritto accanto al numero. */
    val scale: AirScale,
    val pm25: Double?,
    val pm10: Double?,
    val nitrogenDioxide: Double? = null,
    val ozone: Double? = null,
) {
    val band: AirBand? get() = scale.band(index)
}

class AirQualityRepository(private val place: Place = Place.FORLI) {

    // Tollerante di proposito: se un giorno l'API aggiunge o rinomina un
    // campo, il widget mostra "--" invece di sparire con un errore.
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    suspend fun load(): Result<AirQuality> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append(ENDPOINT)
                append("?latitude=").append(place.latitude)
                append("&longitude=").append(place.longitude)
                append("&timezone=auto")
                append("&current=").append(CURRENT_VARS)
            }
            val dto = json.decodeFromString<AirQualityDto>(httpGet(url, fonte = "la qualita' dell'aria"))
            if (dto.error == true) error(dto.reason ?: "Open-Meteo ha risposto con un errore")
            // **Sceglie il dato, non un rettangolo di longitudini.** Se
            // l'endpoint riempie l'indice europeo si e' dentro il dominio della
            // sua convenzione; se lo lascia nullo si e' fuori, e li' quella che
            // si usa e' la scala americana. Un confine disegnato a mano
            // sbaglierebbe sui bordi - la Turchia, il Nordafrica, gli Urali - e
            // sbaglierebbe in silenzio.
            val european = dto.current?.europeanAqi?.roundToInt()
            val american = dto.current?.usAqi?.roundToInt()
            val scale = if (european != null) AirScale.EUROPEA else AirScale.STATUNITENSE
            AirQuality(
                index = european ?: american,
                scale = scale,
                pm25 = dto.current?.pm25,
                pm10 = dto.current?.pm10,
                nitrogenDioxide = dto.current?.nitrogenDioxide,
                ozone = dto.current?.ozone,
            )
        }
    }


    companion object {
        const val ENDPOINT = "https://air-quality-api.open-meteo.com/v1/air-quality"
        const val CURRENT_VARS = "european_aqi,us_aqi,pm2_5,pm10,nitrogen_dioxide,ozone"
    }
}
