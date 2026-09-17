package io.github.noximiliencoxen.caelum.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos

/**
 * La pioggia **prevista** su una griglia di punti, ora per ora.
 *
 * ## Perche' esiste
 *
 * Il radar di RainViewer tiene due ore di storico. Alle 17:06 risponde dalle
 * 15:10 alle 17:00: **tre ore su ventiquattro** della barra. Chi usa l'app ha
 * scorso fino alle 11:00, ha trovato "niente per quest'ora", e ha detto la cosa
 * giusta - una mappa che risponde per tre ore non e' una mappa.
 *
 * Un radar **misura**, e quello che non ha misurato non lo sa: quel limite non
 * si aggira, e la sala continua a dirlo. Ma il **modello** una risposta per
 * ogni ora ce l'ha. E' lo stesso Open-Meteo che riempie le dodici colonne
 * sopra la carta, chiesto su una griglia di punti invece che su uno solo.
 *
 * ## Cosa non e'
 *
 * **Non e' radar, e non va disegnata come il radar.** Il radar dice cosa sta
 * cadendo adesso; questa dice cosa un modello si aspetta. Confonderle sarebbe
 * peggio che non averla: chi guarda una carta radar le crede, e crederebbe a
 * una previsione a sedici ore come se qualcuno l'avesse vista.
 *
 * Per questo la carta le tiene separate in tre modi: macchie **morbide**
 * invece di pixel netti, un'etichetta che dice "previsione" invece dell'ora di
 * scatto, e una riga sotto che lo scrive.
 *
 * ## Come e' fatta la richiesta
 *
 * Una sola, per tutta la griglia e per tutte le ore. Open-Meteo accetta liste
 * di coordinate e torna una **lista di risposte**, ed e' stato provato prima di
 * scriverlo (CONTESTO 20): quarantanove punti pesano settanta kilobyte,
 * centoquarantaquattro ne pesano duecento.
 *
 * `timeformat=unixtime` non e' un vezzo: con gli orari per esteso, le date
 * ripetute per ogni punto sono **la meta' della risposta**.
 *
 * ## Il dettaglio che si sarebbe sbagliato in silenzio
 *
 * Open-Meteo non risponde sui punti che gli si chiedono: risponde sui punti
 * della **sua** griglia, quello piu' vicino a ciascuno. Si chiede 42.9226 e
 * torna 42.9375. Sono venti chilometri, e usando le coordinate chieste invece
 * di quelle tornate la pioggia finirebbe spostata di quel tanto - poco per
 * accorgersene, abbastanza per mettere un rovescio sul paese sbagliato.
 */
class PioggiaPrevistaRepository(private val place: Place) {

    suspend fun load(): Result<MappaPrevista> = withContext(Dispatchers.IO) {
        runCatching {
            val (lat, lon) = grigliaAttorno(place)
            val url = buildString {
                append("https://api.open-meteo.com/v1/forecast")
                append("?latitude=").append(lat.joinToString(",") { formatta(it) })
                append("&longitude=").append(lon.joinToString(",") { formatta(it) })
                append("&hourly=precipitation")
                append("&forecast_days=").append(GIORNI)
                append("&timeformat=unixtime&timezone=UTC")
            }
            val risposte = Json { ignoreUnknownKeys = true }
                .decodeFromString<List<RispostaDto>>(
                    httpGet(url, fonte = "Open-Meteo", timeoutMs = 20_000),
                )
            val ore = risposte.firstOrNull()?.hourly?.time.orEmpty().map(Instant::ofEpochSecond)
            if (ore.isEmpty()) throw IllegalStateException("previsione senza ore")
            MappaPrevista(
                ore = ore,
                punti = risposte.map {
                    // Le coordinate **tornate**, non quelle chieste.
                    PuntoPrevisto(
                        lat = it.latitude,
                        lon = it.longitude,
                        mm = it.hourly?.precipitation.orEmpty().map { v -> v ?: 0f },
                    )
                },
            )
        }
    }

    /**
     * I punti da chiedere: una griglia larga quanto la finestra della carta.
     *
     * Tredici colonne e nove righe - centodiciassette punti - perche' e' la
     * forma della carta: larga quasi il doppio di quanto e' alta. Una griglia
     * quadrata sprecherebbe punti sopra e sotto il riquadro e ne lascerebbe
     * pochi dove si guarda.
     *
     * Il passo in longitudine si divide per il coseno della latitudine, come
     * la finestra disegnata: senza, a nord le celle verrebbero larghe il
     * doppio di quanto sono alte e la pioggia sembrerebbe stirata di lato.
     */
    private fun grigliaAttorno(place: Place): Pair<List<Double>, List<Double>> {
        val mezzaLat = ALTEZZA_GRADI / 2
        val mezzaLon = mezzaLat * PROPORZIONE / cos(Math.toRadians(place.latitude)).coerceAtLeast(0.2)
        val lat = mutableListOf<Double>()
        val lon = mutableListOf<Double>()
        for (r in 0 until RIGHE) {
            for (c in 0 until COLONNE) {
                lat += place.latitude + (r - (RIGHE - 1) / 2.0) * (mezzaLat * 2 / (RIGHE - 1))
                lon += place.longitude + (c - (COLONNE - 1) / 2.0) * (mezzaLon * 2 / (COLONNE - 1))
            }
        }
        return lat to lon
    }

    private fun formatta(v: Double): String = String.format(java.util.Locale.US, "%.4f", v)

    @Serializable
    private data class RispostaDto(
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val hourly: OrarieDto? = null,
    )

    @Serializable
    private data class OrarieDto(
        val time: List<Long>? = null,
        val precipitation: List<Float?>? = null,
    )

    private companion object {
        /** Quante ore avanti. Tre giorni coprono la barra con margine. */
        const val GIORNI = 3

        /** La finestra della carta: la stessa apertura in gradi di `RadarMappa`. */
        const val ALTEZZA_GRADI = 2.9
        const val PROPORZIONE = 1.5

        const val COLONNE = 13
        const val RIGHE = 9
    }
}

/** Un punto della griglia, con la pioggia prevista ora per ora. */
class PuntoPrevisto(val lat: Double, val lon: Double, val mm: List<Float>)

/**
 * La pioggia prevista su una griglia, per tutte le ore che il modello copre.
 *
 * Si scarica **una volta per localita'** e si tiene: sono tutte le ore in una
 * risposta sola, quindi scorrere la barra non costa niente. E' il contrario
 * del radar, che ha un'immagine per ogni fotogramma e le prende una alla volta.
 */
class MappaPrevista(val ore: List<Instant>, val punti: List<PuntoPrevisto>) {

    /**
     * I valori di un'ora, se quell'ora c'e'.
     *
     * La tolleranza e' mezz'ora come per il radar, e per la stessa ragione: il
     * modello da' un valore all'ora, e mostrarlo sotto un'ora diversa sarebbe
     * di nuovo un dato vero messo dove non e' vero.
     */
    fun a(istante: Instant): List<Pair<PuntoPrevisto, Float>>? {
        val i = ore.indices.minByOrNull { abs(ore[it].epochSecond - istante.epochSecond) } ?: return null
        if (abs(ore[i].epochSecond - istante.epochSecond) > 1800) return null
        return punti.mapNotNull { p -> p.mm.getOrNull(i)?.let { p to it } }
    }
}
