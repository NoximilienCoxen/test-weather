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
private data class AirQualityHourlyDto(
    val time: List<String>? = null,
    @SerialName("european_aqi") val europeanAqi: List<Double?>? = null,
    @SerialName("us_aqi") val usAqi: List<Double?>? = null,
    @SerialName("alder_pollen") val ontano: List<Double?>? = null,
    @SerialName("birch_pollen") val betulla: List<Double?>? = null,
    @SerialName("olive_pollen") val olivo: List<Double?>? = null,
    @SerialName("grass_pollen") val graminacee: List<Double?>? = null,
    @SerialName("mugwort_pollen") val artemisia: List<Double?>? = null,
    @SerialName("ragweed_pollen") val ambrosia: List<Double?>? = null,
)

@Serializable
private data class AirQualityDto(
    val current: AirQualityCurrentDto? = null,
    val hourly: AirQualityHourlyDto? = null,
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
    /**
     * L'indice ora per ora, oggi.
     *
     * **C'era e non si chiedeva.** Lo stesso endpoint che da' il valore di
     * adesso da' anche le ventiquattro ore, e la sala mostrava un numero solo:
     * "27, discreta", vero e muto. Un indice che alle otto vale venti e alle
     * quattordici sessanta racconta una giornata; lo stesso indice detto una
     * volta sola non racconta niente, e soprattutto non dice **quando uscire**,
     * che e' l'unica domanda che uno si fa guardando l'aria.
     *
     * Vuota quando l'API non le manda: chi legge lo dichiara invece di
     * disegnare una riga piatta.
     */
    val oreOggi: List<OraAria> = emptyList(),
    /**
     * Il polline di oggi e dei due giorni dopo. Vuota fuori dall'Europa, dove
     * il modello non arriva, e quando l'API non lo manda: la sala allora la
     * sezione non la mostra, invece di mostrare tre "assente" che non sono veri.
     */
    val polline: List<GiornoPolline> = emptyList(),
) {
    val band: AirBand? get() = scale.band(index)

    /**
     * L'inquinante che **comanda** l'indice, e quanto e' vicino al suo limite.
     *
     * L'indice europeo e' il peggiore dei suoi componenti, non la loro media:
     * dire "27, discreta" senza dire chi l'ha deciso lascia fuori la parte
     * utile. Con l'ozono al settanta per cento del limite e le polveri al
     * dieci, la giornata si comporta in un modo solo - e non e' quello delle
     * polveri.
     *
     * I limiti sono le linee guida dell'OMS del 2021 per la media di
     * ventiquattro ore, tranne l'ozono che le ha sulle otto ore. Sono piu'
     * severi dei limiti di legge europei, e sono quelli giusti da mostrare a
     * qualcuno che decide se uscire a correre: la legge dice cosa e' punibile,
     * l'OMS cosa fa male.
     */
    val dominante: Inquinante?
        get() = listOfNotNull(
            pm25?.let { Inquinante("PM 2,5", "il PM 2,5", it, 15.0) },
            pm10?.let { Inquinante("PM 10", "il PM 10", it, 45.0) },
            nitrogenDioxide?.let { Inquinante("Biossido d'azoto", "il biossido d'azoto", it, 25.0) },
            ozone?.let { Inquinante("Ozono", "l'ozono", it, 100.0) },
        ).maxByOrNull { it.quota }
}

/** Un inquinante, col suo valore e la soglia con cui va confrontato. */
data class Inquinante(
    /** Come si scrive da solo, in una tabella: "PM 2,5". */
    val nome: String,
    /**
     * Come si scrive **dentro una frase**, articolo compreso: "il PM 2,5",
     * "l'ozono".
     *
     * Serve un campo in piu' perche' l'italiano non ricava l'articolo dal
     * nome, e perche' abbassare le maiuscole con `lowercase()` - che era la
     * prima versione - trasforma una sigla in un rumore: *"l'indice lo decide
     * pm 2,5"*. Le sigle non hanno un minuscolo.
     */
    val inFrase: String,
    val valore: Double,
    val limite: Double,
) {
    /** Quanto del limite e' occupato. Sopra uno, il limite e' superato. */
    val quota: Double get() = if (limite > 0) valore / limite else 0.0
}

/** L'indice dell'aria a una certa ora. */
data class OraAria(val ora: java.time.LocalDateTime, val indice: Int)

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
                append("&hourly=").append(HOURLY_VARS)
                // Tre giorni e non due: il polline si guarda anche per
                // dopodomani. Le ore dell'aria la sala le filtra gia' per giorno.
                append("&forecast_days=3")
            }
            val dto = json.decodeFromString<AirQualityDto>(httpGet(url, fonte = "la qualità dell'aria"))
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
                oreOggi = oreDi(dto, scale),
                polline = pollineDi(dto),
            )
        }
    }


    /**
     * Le ore, dalla stessa scala del valore corrente.
     *
     * **La scala dev'essere la stessa, o il grafico mente.** L'indice europeo e
     * quello americano vanno da zero a cento e da zero a cinquecento: mescolarli
     * in una curva sola - il numero grande in cima dall'uno, le colonne
     * dall'altro - darebbe una giornata che crolla a mezzogiorno per il solo
     * fatto di aver cambiato metro. Si sceglie una volta, in alto, e si tiene.
     */
    private fun oreDi(dto: AirQualityDto, scale: AirScale): List<OraAria> {
        val orari = dto.hourly ?: return emptyList()
        val tempi = orari.time ?: return emptyList()
        val valori = when (scale) {
            AirScale.EUROPEA -> orari.europeanAqi
            AirScale.STATUNITENSE -> orari.usAqi
        } ?: return emptyList()
        return tempi.zip(valori).mapNotNull { (t, v) ->
            val quando = runCatching { java.time.LocalDateTime.parse(t) }.getOrNull()
            if (quando == null || v == null) null else OraAria(quando, v.roundToInt())
        }
    }

    /** Il polline dalle stesse ore: nessuna richiesta in piu'. */
    private fun pollineDi(dto: AirQualityDto): List<GiornoPolline> {
        val orari = dto.hourly ?: return emptyList()
        val tempi = orari.time ?: return emptyList()
        val valori = mapOf(
            SpeciePolline.ONTANO to orari.ontano,
            SpeciePolline.BETULLA to orari.betulla,
            SpeciePolline.OLIVO to orari.olivo,
            SpeciePolline.GRAMINACEE to orari.graminacee,
            SpeciePolline.ARTEMISIA to orari.artemisia,
            SpeciePolline.AMBROSIA to orari.ambrosia,
        ).mapNotNull { (specie, serie) -> serie?.let { specie to it } }.toMap()
        return Polline.giorni(tempi, valori)
    }

    companion object {
        const val ENDPOINT = "https://air-quality-api.open-meteo.com/v1/air-quality"
        const val CURRENT_VARS = "european_aqi,us_aqi,pm2_5,pm10,nitrogen_dioxide,ozone"
        val HOURLY_VARS = "european_aqi,us_aqi," + SpeciePolline.CAMPI
    }
}
