package io.github.noximiliencoxen.caelum.data

import kotlinx.serialization.Serializable

/**
 * Una localita' scelta dall'utente.
 *
 * Il nome viaggia insieme alle coordinate invece di essere ricavato da esse:
 * la geocodifica inversa della piattaforma non risolve nulla su un'immagine
 * AOSP, e in generale chiedere di nuovo alla rete un nome che si e' gia' avuto
 * in mano e' lavoro sprecato.
 */
@Serializable
data class Place(
    val name: String,
    /** Regione o provincia, quando l'API la fornisce. Distingue gli omonimi. */
    val admin: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
) {
    /** Riga di dettaglio sotto al nome nella lista dei risultati. */
    val detail: String get() = listOfNotNull(admin, country).joinToString(", ")

    companion object {
        val FORLI = Place(
            name = "Forlì",
            admin = "Emilia-Romagna",
            country = "Italia",
            latitude = 44.2226,
            longitude = 12.0407,
        )

        /**
         * Scorciatoie per provare l'app dove il tempo e' diverso da qui.
         *
         * Con una sola citta' cablata non c'era modo di vedere la pioggia senza
         * aspettare che piovesse: le prime tre di questa lista sono fra i posti
         * piu' piovosi che esistano, ed e' esattamente per questo che ci sono.
         */
        val SUGGESTIONS = listOf(
            // Prima voce: neve e gelo garantiti tutto l'anno per testare la neve.
            Place("Aoraki / Monte Cook", "Canterbury", "Nuova Zelanda", -43.5950, 170.1418),
            FORLI,
            Place("Bergen", "Vestland", "Norvegia", 60.3913, 5.3221),
            Place("Londra", "England", "Regno Unito", 51.5085, -0.1257),
            Place("Singapore", null, "Singapore", 1.2897, 103.8501),
            Place("Milano", "Lombardia", "Italia", 45.4643, 9.1895),
            Place("Roma", "Lazio", "Italia", 41.8933, 12.4829),
            Place("Reykjavík", null, "Islanda", 64.1355, -21.8954),
            Place("Tromsø", "Troms", "Norvegia", 69.6496, 18.9560),
        )
    }
}

/** Chiave stabile per confronti e chiavi di lista, senza portarsi dietro un id. */
val Place.key: String get() = "$name$latitude$longitude"

private val ITALY_LATITUDE_RANGE = 35.5..47.1
private val ITALY_LONGITUDE_RANGE = 6.6..18.5

/**
 * Vero se il paese testuale indica l'Italia, oppure se le coordinate
 * ricadono nel rettangolo geografico italiano (nessun countryCode ISO
 * e' disponibile nei dati di Open-Meteo/Geocoder usati da questa app).
 */
val Place.isItaly: Boolean
    get() {
        val normalizedCountry = country?.trim()?.uppercase()
        if (normalizedCountry == "IT" || normalizedCountry == "ITA" ||
            normalizedCountry == "ITALIA" || normalizedCountry == "ITALY"
        ) {
            return true
        }
        return latitude in ITALY_LATITUDE_RANGE && longitude in ITALY_LONGITUDE_RANGE
    }

/**
 * Vero se le coordinate sono due numeri veri.
 *
 * Non e' una cautela teorica: il `Location` di un emulatore appena avviato puo'
 * avere latitudine e longitudine a `NaN` pur avendo un nome che si geocodifica
 * benissimo, e una [Place] cosi' non si vede finche' non e' troppo tardi.
 * `NaN` non fa cadere niente - passa i confronti, si concatena in una stringa,
 * arriva fino alla rete - e Open-Meteo lo prende sul serio: risponde
 * `{"latitude":NaN,...}`, che nessun lettore JSON accetta come numero. Il guasto
 * si presenta quindi come un errore di lettura del formato, a due passaggi di
 * distanza da dove e' nato.
 */
val Place.hasFiniteCoordinates: Boolean
    get() = latitude.isFinite() && longitude.isFinite()
