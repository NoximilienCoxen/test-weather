package com.forli.meteo.ui.render3d

/**
 * Le coste del mondo, molto semplificate.
 *
 * **Perche' non bastavano le macchie.** Prima i continenti erano quattordici
 * dischi sparsi per longitudine e latitudine, schiacciati verso il bordo. Da
 * lontano davano un'idea di terre, e infatti il commento che e' arrivato e'
 * stato "sembra una palla con delle smerdate sopra": e' esattamente quello che
 * sono. Un mappamondo o si riconosce o non serve a niente - la schermata parla
 * di **dove sei**, e una palla astratta non lo dice.
 *
 * Ogni terra e' un anello chiuso: longitudine e latitudine in gradi, a coppie,
 * nell'ordine in cui si percorre la costa. Non e' una carta nautica e non prova
 * a esserlo - una decina di gradi di fedelta', quel tanto che basta perche'
 * l'Africa sia l'Africa e lo stivale sia lo stivale. Su un disco di duecento
 * pixel, di piu' non si vedrebbe comunque.
 *
 * **L'Italia ha piu' vertici del resto.** Non e' campanilismo: e' l'unica costa
 * che chi usa questa app riconoscerebbe sbagliata a colpo d'occhio.
 *
 * L'Antartide non c'e'. Un anello che gira attorno al polo attraversa tutte le
 * longitudini, e proiettato ortograficamente si richiude su se stesso invece di
 * restare una calotta; il prezzo per farlo bene non vale il bordo inferiore di
 * una sfera che si vede per due secondi.
 */
val WORLD_COASTS: List<FloatArray> = listOf(
    // --- Africa ---------------------------------------------------------
    floatArrayOf(
        -17f, 21f, -17f, 15f, -16f, 12f, -13f, 9f, -8f, 4f,
        0f, 5f, 6f, 4f, 9f, 4f, 9f, -1f, 12f, -6f,
        12f, -16f, 15f, -23f, 18f, -34f, 25f, -34f, 32f, -29f,
        35f, -24f, 40f, -16f, 41f, -2f, 51f, 12f, 43f, 12f,
        38f, 18f, 34f, 28f, 32f, 31f, 25f, 32f, 15f, 32f,
        10f, 37f, 3f, 37f, -6f, 36f, -10f, 31f,
    ),
    // --- Eurasia --------------------------------------------------------
    // Si percorre da Gibilterra verso nord, tutto il giro, e si rientra dal
    // Mediterraneo. Si ferma a Suez: l'Africa e' un anello suo.
    floatArrayOf(
        -9f, 37f, -9f, 43f, -4f, 44f, -1f, 46f, -4f, 48f,
        2f, 51f, 4f, 53f, 7f, 53f, 8f, 57f, 10f, 59f,
        5f, 62f, 11f, 64f, 14f, 68f, 20f, 70f, 28f, 71f,
        33f, 70f, 44f, 68f, 55f, 70f, 69f, 73f, 80f, 74f,
        95f, 78f, 110f, 77f, 130f, 73f, 145f, 72f, 160f, 70f,
        170f, 68f, 180f, 65f, 175f, 62f, 163f, 58f, 156f, 51f,
        142f, 54f, 140f, 45f, 131f, 43f, 127f, 37f, 122f, 31f,
        117f, 24f, 110f, 21f, 105f, 10f, 100f, 3f, 98f, 8f,
        95f, 16f, 92f, 21f, 88f, 22f, 80f, 15f, 78f, 8f,
        73f, 15f, 70f, 21f, 67f, 24f, 60f, 25f, 57f, 26f,
        50f, 29f, 57f, 23f, 54f, 17f, 45f, 13f, 43f, 13f,
        39f, 21f, 35f, 28f, 34f, 31f, 36f, 36f, 30f, 37f,
        27f, 37f, 26f, 40f, 23f, 38f, 21f, 39f, 19f, 40f,
        16f, 43f, 14f, 45f,
        // Lo stivale, dall'Adriatico alla riviera.
        13f, 46f, 14f, 42f, 16f, 42f, 18f, 40f, 17f, 39f,
        16f, 38f, 15f, 40f, 14f, 41f, 12f, 41f, 11f, 43f,
        10f, 44f, 8f, 44f,
        7f, 44f, 4f, 43f, 3f, 42f, 0f, 40f, -1f, 38f,
        -5f, 36f,
    ),
    // --- Nord America ---------------------------------------------------
    floatArrayOf(
        -165f, 60f, -155f, 58f, -135f, 58f, -125f, 49f, -124f, 40f,
        -120f, 34f, -110f, 23f, -105f, 20f, -95f, 16f, -92f, 15f,
        -88f, 16f, -87f, 21f, -91f, 19f, -95f, 19f, -97f, 26f,
        -94f, 29f, -89f, 29f, -84f, 30f, -81f, 25f, -80f, 32f,
        -76f, 37f, -70f, 42f, -66f, 45f, -60f, 47f, -64f, 60f,
        -78f, 62f, -95f, 68f, -125f, 70f, -140f, 70f, -160f, 66f,
    ),
    // --- Sud America ----------------------------------------------------
    floatArrayOf(
        -77f, 8f, -72f, 11f, -62f, 10f, -52f, 5f, -44f, -2f,
        -38f, -5f, -39f, -13f, -40f, -20f, -48f, -25f, -57f, -35f,
        -62f, -40f, -65f, -45f, -68f, -52f, -71f, -54f, -75f, -45f,
        -73f, -37f, -71f, -30f, -70f, -18f, -77f, -12f, -81f, -6f,
        -80f, 0f, -78f, 7f,
    ),
    // --- Australia ------------------------------------------------------
    floatArrayOf(
        114f, -22f, 113f, -26f, 115f, -34f, 129f, -32f, 138f, -35f,
        146f, -39f, 150f, -37f, 153f, -28f, 146f, -19f, 142f, -11f,
        136f, -12f, 130f, -12f, 125f, -14f, 120f, -18f,
    ),
    // --- Groenlandia ----------------------------------------------------
    floatArrayOf(
        -45f, 60f, -52f, 66f, -55f, 71f, -60f, 76f, -45f, 82f,
        -25f, 78f, -20f, 72f, -38f, 65f,
    ),
    // --- Isole britanniche ----------------------------------------------
    floatArrayOf(
        -5f, 50f, -5f, 54f, -3f, 58f, -2f, 57f, 0f, 53f, 1f, 51f,
    ),
    // --- Madagascar -----------------------------------------------------
    floatArrayOf(
        44f, -16f, 50f, -15f, 50f, -25f, 45f, -25f,
    ),
    // --- Giappone -------------------------------------------------------
    floatArrayOf(
        130f, 33f, 135f, 34f, 140f, 36f, 142f, 40f, 141f, 45f,
        138f, 37f, 133f, 34f,
    ),
    // --- Nuova Zelanda --------------------------------------------------
    floatArrayOf(
        173f, -35f, 178f, -38f, 174f, -41f, 170f, -46f, 166f, -46f,
        172f, -41f,
    ),
)
