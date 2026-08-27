package com.forli.meteo.data

/**
 * Una localita' scelta dall'utente.
 *
 * Il nome viaggia insieme alle coordinate invece di essere ricavato da esse:
 * la geocodifica inversa della piattaforma non risolve nulla su un'immagine
 * AOSP, e in generale chiedere di nuovo alla rete un nome che si e' gia' avuto
 * in mano e' lavoro sprecato.
 */
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
