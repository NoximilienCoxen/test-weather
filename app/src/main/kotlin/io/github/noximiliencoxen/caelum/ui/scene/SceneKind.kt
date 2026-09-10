package io.github.noximiliencoxen.caelum.ui.scene

/**
 * Le otto scene dipinte, e il codice meteo che le sceglie.
 *
 * **Otto e non sei**, cioe' non le famiglie di `Wmo.Family`. Quelle servono a
 * colorare la barra delle ore e mettono insieme il sereno e il poco nuvoloso,
 * che come colore di una tacca sono la stessa cosa e come **quadro** non lo sono
 * per niente: un cielo terso e un cielo con tre nuvole sono due dipinti diversi.
 * Allo stesso modo il temporale e la grandine condividono la famiglia ma non
 * l'immagine.
 *
 * **La mappa e' sui codici e non sulla nuvolosita'.** Si potrebbe ricavare la
 * scena da `Wmo.cloudiness`, che e' un valore continuo, ma vorrebbe dire due
 * soglie inventate qui che nessuno terrebbe allineate con quelle di la'. I
 * codici WMO sono pochi, sono fermi da decenni, e scritti per esteso si leggono
 * come una tabella invece che come un calcolo.
 *
 * I file stanno in `assets/` e non fra le risorse, e non e' un dettaglio: il
 * sistema delle risorse riscala le immagini per densita' dello schermo, e la
 * mappa di profondita' **non va interpolata da nessuno** tranne che dal motore.
 * Un pixel di profondita' mediato col vicino e' una distanza che non esiste.
 */
enum class SceneKind(private val slug: String) {
    SERENO("sereno"),
    POCO_NUVOLOSO("poco_nuvoloso"),
    COPERTO("coperto"),
    NEBBIA("nebbia"),
    PIOGGIA("pioggia"),
    TEMPORALE("temporale"),
    NEVE("neve"),
    GRANDINE("grandine");

    /** Il dipinto: il luogo e l'umore, a luce neutra di mezzogiorno. */
    val painting: String get() = "scene/scena_$slug.png"

    /** La profondita': bianco vicino, nero lontano. */
    val depth: String get() = "scene/scena_${slug}_z.png"
}

/**
 * Da un codice WMO alla scena da dipingere.
 *
 * Senza codice si sta sul sereno e non su un segnaposto grigio: la scena e' il
 * fondo di tutta la schermata, e un fondo che dichiara "non lo so" mentre sopra
 * ci sono i numeri della previsione e' peggio di un cielo ottimista. Chi non ha
 * dati lo dice con le parole, che e' il posto giusto.
 */
fun sceneOf(code: Int?): SceneKind = when (code) {
    null, 0 -> SceneKind.SERENO
    1, 2 -> SceneKind.POCO_NUVOLOSO
    3 -> SceneKind.COPERTO
    45, 48 -> SceneKind.NEBBIA
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> SceneKind.PIOGGIA
    71, 73, 75, 77, 85, 86 -> SceneKind.NEVE
    95 -> SceneKind.TEMPORALE
    96, 99 -> SceneKind.GRANDINE
    else -> SceneKind.SERENO
}
