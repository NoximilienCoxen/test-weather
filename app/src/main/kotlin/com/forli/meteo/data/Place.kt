package com.forli.meteo.data

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

    /**
     * Stesso posto, anche se scritto diversamente.
     *
     * Il confronto e' sulle coordinate e con una tolleranza: la stessa citta'
     * arriva dalla ricerca con quattro decimali e dalla geolocalizzazione con
     * la posizione vera del telefono, che non coincidera' mai. Un centesimo di
     * grado e' circa un chilometro: sotto quella distanza il meteo e' lo
     * stesso, e due voci sarebbero due voci per la stessa cosa.
     */
    fun samePlaceAs(other: Place): Boolean =
        kotlin.math.abs(latitude - other.latitude) < TOLERANCE &&
            kotlin.math.abs(longitude - other.longitude) < TOLERANCE

    companion object {
        /** Gradi entro cui due coordinate descrivono lo stesso posto. */
        private const val TOLERANCE = 0.01

        val FORLI = Place(
            name = "Forlì",
            admin = "Emilia-Romagna",
            country = "Italia",
            latitude = 44.2226,
            longitude = 12.0407,
        )

        /**
         * Aoraki / Monte Cook, la vetta.
         *
         * Sta in cima all'elenco per lo stesso motivo per cui ci stanno Bergen e
         * Singapore: e' un banco di prova. A tremilasettecento metri, e con le
         * stagioni rovesciate rispetto a qui, e' il posto dove neve e gelo si
         * trovano quasi sempre senza doverli aspettare - e senza doverli
         * imporre con l'aggancio di verifica, che mostra il disegno ma non
         * mette mai alla prova i dati veri che gli arrivano dietro.
         *
         * Le coordinate sono quelle della vetta e non del villaggio a valle:
         * settecento metri di dislivello cambiano la previsione, e il villaggio
         * d'estate e' semplicemente una valle verde.
         */
        val AORAKI = Place(
            name = "Aoraki / Monte Cook",
            admin = "Canterbury",
            country = "Nuova Zelanda",
            latitude = -43.5950,
            longitude = 170.1418,
        )

        /**
         * Scorciatoie per provare l'app dove il tempo e' diverso da qui.
         *
         * Con una sola citta' cablata non c'era modo di vedere la pioggia senza
         * aspettare che piovesse: le prime della lista sono fra i posti piu'
         * piovosi - o piu' gelidi - che esistano, ed e' esattamente per questo
         * che ci sono.
         */
        val SUGGESTIONS = listOf(
            AORAKI,
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
