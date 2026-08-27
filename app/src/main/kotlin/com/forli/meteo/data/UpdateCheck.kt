package com.forli.meteo.data

import com.forli.meteo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * C'e' una versione piu' recente di questa?
 *
 * ## Perche' non basta guardare il numero di versione
 *
 * L'APK sta su un rilascio a **tag fisso**, `apk-latest`: il tag non cambia
 * mai, e quindi non dice niente su quale build ci sia sotto. Anche il nome del
 * rilascio e' sempre lo stesso. L'unica cosa che cambia davvero a ogni
 * pubblicazione e' l'istante in cui l'allegato e' stato caricato, ed e' quello
 * che si confronta - contro l'istante in cui questa copia e' stata costruita,
 * inciso in `BuildConfig` dalla CI.
 *
 * Due condizioni prima di dare fastidio a qualcuno:
 *
 * - una copia costruita **in locale** ha `BUILD_EPOCH` a zero, e allora non si
 *   controlla affatto: durante lo sviluppo la versione pubblicata e' quasi
 *   sempre piu' recente di quella che si sta provando, e un avviso a ogni
 *   avvio sarebbe rumore garantito;
 * - lo scarto deve superare un margine. Il commit e la pubblicazione distano
 *   qualche minuto - il tempo che la CI compili - quindi l'APK giusto risulta
 *   sempre "piu' recente" del proprio commit di qualche minuto. Senza margine,
 *   l'app annuncerebbe un aggiornamento verso se stessa.
 *
 * Se qualcosa va storto - rete assente, GitHub che risponde 403 per troppe
 * richieste, JSON cambiato - la risposta e' [None]. Un controllo aggiornamenti
 * che disturba per raccontare di non aver potuto controllare e' peggio di
 * nessun controllo.
 */
object UpdateCheck {

    sealed interface Result {
        data class Available(val publishedAt: Instant, val bytes: Long) : Result
        data object None : Result
    }

    const val RELEASE_PAGE =
        "https://github.com/NoximilienCoxen/test-weather/releases/tag/apk-latest"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val ENDPOINT =
        "https://api.github.com/repos/NoximilienCoxen/test-weather/releases/tags/apk-latest"

    /**
     * Sotto questo scarto, la copia pubblicata e' la copia in mano.
     *
     * Venti minuti: la CI compila, prova su emulatore e pubblica in una decina,
     * e il margine deve stare comodamente sopra il caso peggiore.
     */
    private const val SLACK_SECONDS = 20 * 60L

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        if (BuildConfig.BUILD_EPOCH <= 0L) return@withContext Result.None

        runCatching {
            val body = fetch() ?: return@runCatching Result.None
            val release = json.decodeFromString<GithubRelease>(body)

            // L'allegato, non il rilascio: il rilascio conserva la data della
            // prima creazione del tag, che essendo fisso e' vecchia di mesi.
            val asset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return@runCatching Result.None
            val published = Instant.parse(asset.updatedAt ?: return@runCatching Result.None)

            if (published.epochSecond > BuildConfig.BUILD_EPOCH + SLACK_SECONDS) {
                Result.Available(published, asset.size)
            } else {
                Result.None
            }
        }.getOrDefault(Result.None)
    }

    private fun fetch(): String? {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 6_000
            setRequestProperty("Accept", "application/vnd.github+json")
            // Senza intestazione di identificazione GitHub risponde 403 a una
            // parte delle richieste anonime, e il controllo tacerebbe sempre
            // senza che nessuno capisca perche'.
            setRequestProperty("User-Agent", "meteo-forli")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

@Serializable
private data class GithubRelease(
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    val size: Long = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
)
