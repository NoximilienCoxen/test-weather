package com.caelumweather.ui.sala

import com.caelumweather.lingua.tr
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.caelumweather.prefs.CaptionStyle

/**
 * Le didascalie di Sala I: titolo e corpo per ognuna delle ventiquattro
 * combinazioni fase x tempo, portate dal prototipo. Sono testo, non dati —
 * i numeri che le accompagnano (temperatura, ora, percepiti) restano quelli
 * veri della previsione.
 */
private val SalaTitles: Map<SalaCondition, Map<SalaPhase, String>> get() = mapOf(
    SalaCondition.SERENO to mapOf(
        SalaPhase.ALBA to tr("Alba limpida sopra la pianura", "Clear dawn over the plain", "Klare Morgendämmerung über der Ebene", "Aube limpide sur la plaine", "Amanecer limpio sobre la llanura"),
        SalaPhase.GIORNO to tr("Pieno sole, aria calda", "Full sun, warm air", "Volle Sonne, warme Luft", "Plein soleil, air chaud", "Pleno sol, aire cálido"),
        SalaPhase.TRAMONTO to tr("Tramonto senza una nuvola", "Sunset without a cloud", "Sonnenuntergang ohne eine Wolke", "Coucher de soleil sans un nuage", "Atardecer sin una nube"),
        SalaPhase.NOTTE to tr("Notte serena, aria ferma", "Clear night, still air", "Klare Nacht, ruhige Luft", "Nuit claire, air calme", "Noche despejada, aire en calma"),
    ),
    SalaCondition.NUVOLOSO to mapOf(
        SalaPhase.ALBA to tr("Luce filtrata dalle nubi alte", "Light filtered by high clouds", "Licht, gefiltert von hohen Wolken", "Lumière filtrée par les nuages élevés", "Luz filtrada por las nubes altas"),
        SalaPhase.GIORNO to tr("Nuvole di passaggio", "Passing clouds", "Durchziehende Wolken", "Nuages de passage", "Nubes de paso"),
        SalaPhase.TRAMONTO to tr("Cielo coperto verso sera", "Clouding over towards evening", "Bedeckt gegen Abend", "Ciel couvert vers le soir", "Cielo cubierto hacia la tarde"),
        // Diceva "niente stelle", e da questo giro le stelle si vedono
        // **attraverso** le nuvole - le nubi non le spengono, le coprono. Una
        // didascalia che smentisce cio' che si ha sotto gli occhi toglie
        // credito anche alle altre sei.
        SalaPhase.NOTTE to tr("Notte coperta, poche stelle", "Overcast night, few stars", "Bedeckte Nacht, wenige Sterne", "Nuit couverte, peu d'étoiles", "Noche cubierta, pocas estrellas"),
    ),
    SalaCondition.PIOGGIA to mapOf(
        SalaPhase.ALBA to tr("Piove dalle prime luci", "Raining since first light", "Regen seit dem ersten Licht", "Il pleut depuis les premières lueurs", "Llueve desde las primeras luces"),
        SalaPhase.GIORNO to tr("Rovescio di metà pomeriggio", "Mid-afternoon shower", "Schauer am Nachmittag", "Averse de milieu d'après-midi", "Chubasco de media tarde"),
        SalaPhase.TRAMONTO to tr("Pioggia fino a notte", "Rain until nightfall", "Regen bis in die Nacht", "Pluie jusqu'à la nuit", "Lluvia hasta la noche"),
        SalaPhase.NOTTE to tr("Pioggia nella notte", "Rain in the night", "Regen in der Nacht", "Pluie dans la nuit", "Lluvia en la noche"),
    ),
    // **Qui c'era la grandine, e sopra una nevicata si leggeva "Pioggia".** I
    // codici della neve - 71, 73, 75 - cadevano in PIOGGIA, e quelli dei
    // rovesci di neve - 77, 85, 86 - in una "grandine" che i codici WMO di
    // questa app non hanno mai contenuto. Adesso la neve ha le sue parole, e la
    // grandine resta dov'e' davvero: dentro il temporale che la fa.
    SalaCondition.NEVE to mapOf(
        SalaPhase.ALBA to tr("Neve dalle prime luci", "Snow since first light", "Schnee seit dem ersten Licht", "Neige depuis les premières lueurs", "Nieve desde las primeras luces"),
        SalaPhase.GIORNO to tr("Nevica", "Snowing", "Es schneit", "Il neige", "Nieva"),
        SalaPhase.TRAMONTO to tr("Neve fino a sera", "Snow until evening", "Schnee bis zum Abend", "Neige jusqu'au soir", "Nieve hasta la tarde"),
        SalaPhase.NOTTE to tr("Neve nella notte", "Snow in the night", "Schnee in der Nacht", "Neige dans la nuit", "Nieve en la noche"),
    ),
    SalaCondition.TEMPORALE to mapOf(
        SalaPhase.ALBA to tr("Temporale all'alba", "Thunderstorm at dawn", "Gewitter in der Morgendämmerung", "Orage à l'aube", "Tormenta al amanecer"),
        SalaPhase.GIORNO to tr("Temporale sul pomeriggio", "Afternoon thunderstorm", "Gewitter am Nachmittag", "Orage dans l'après-midi", "Tormenta por la tarde"),
        SalaPhase.TRAMONTO to tr("Temporale al tramonto", "Thunderstorm at sunset", "Gewitter bei Sonnenuntergang", "Orage au coucher du soleil", "Tormenta al atardecer"),
        SalaPhase.NOTTE to tr("Temporale notturno", "Night-time thunderstorm", "Nächtliches Gewitter", "Orage nocturne", "Tormenta nocturna"),
    ),
    SalaCondition.TEMPORALE_GRANDINE to mapOf(
        SalaPhase.ALBA to tr("Temporale e grandine all'alba", "Thunderstorm and hail at dawn", "Gewitter und Hagel in der Morgendämmerung", "Orage et grêle à l'aube", "Tormenta y granizo al amanecer"),
        SalaPhase.GIORNO to tr("Temporale con grandine", "Thunderstorm with hail", "Gewitter mit Hagel", "Orage avec grêle", "Tormenta con granizo"),
        SalaPhase.TRAMONTO to tr("Temporale e grandine a sera", "Thunderstorm and hail in the evening", "Gewitter und Hagel am Abend", "Orage et grêle le soir", "Tormenta y granizo al anochecer"),
        SalaPhase.NOTTE to tr("Temporale e grandine di notte", "Thunderstorm and hail at night", "Gewitter und Hagel in der Nacht", "Orage et grêle la nuit", "Tormenta y granizo de noche"),
    ),
)

private val SalaBodies: Map<SalaCondition, String> get() = mapOf(
    SalaCondition.SERENO to tr("Cielo aperto e visibilità ottima.", "Open sky and excellent visibility.", "Offener Himmel und ausgezeichnete Sicht.", "Ciel dégagé et excellente visibilité.", "Cielo abierto y visibilidad excelente."),
    SalaCondition.NUVOLOSO to tr("Nubi medie che coprono il sole a intervalli. Non portano pioggia, ma tengono la temperatura ferma.", "Mid-level clouds hiding the sun now and then. No rain in them, but they hold the temperature steady.", "Mittelhohe Wolken, die die Sonne zeitweise verdecken. Sie bringen keinen Regen, halten aber die Temperatur stabil.", "Des nuages moyens qui cachent le soleil par moments. Ils n'apportent pas de pluie, mais maintiennent la température.", "Nubes medias que tapan el sol a ratos. No traen lluvia, pero mantienen estable la temperatura."),
    SalaCondition.PIOGGIA to tr("Pioggia in corso: i millimetri e la probabilità ora per ora sono nella schermata della pioggia.", "Rain falling: millimetres and chance hour by hour are on the Rain screen.", "Es regnet: Millimeter und Wahrscheinlichkeit Stunde für Stunde stehen auf dem Regen-Bildschirm.", "Il pleut : les millimètres et la probabilité heure par heure sont sur l'écran de la pluie.", "Está lloviendo: los milímetros y la probabilidad hora a hora están en la pantalla de la lluvia."),
    SalaCondition.NEVE to tr("Neve in caduta: fondo scivoloso e visibilità ridotta, soprattutto dove non passa nessuno.", "Snow falling: slippery ground and poor visibility, especially where nobody has walked.", "Es schneit: rutschiger Boden und schlechte Sicht, besonders dort, wo niemand gegangen ist.", "Il neige : sol glissant et visibilité réduite, surtout là où personne n'est passé.", "Está nevando: suelo resbaladizo y poca visibilidad, sobre todo donde no ha pasado nadie."),
    SalaCondition.TEMPORALE to tr("Fulminazione attiva. Meglio non stare all'aperto fino a mezz'ora dopo l'ultimo tuono.", "Lightning about. Best not to stay outdoors until half an hour after the last thunder.", "Blitze in der Nähe. Besser nicht im Freien bleiben, bis eine halbe Stunde nach dem letzten Donner.", "Foudre active. Mieux vaut ne pas rester dehors jusqu'à une demi-heure après le dernier coup de tonnerre.", "Hay rayos. Mejor no quedarse al aire libre hasta media hora después del último trueno."),
    SalaCondition.TEMPORALE_GRANDINE to tr("Cella temporalesca con grandine: raffiche improvvise e visibilità ridotta.", "Storm cell with hail: sudden gusts and poor visibility.", "Gewitterzelle mit Hagel: plötzliche Böen und schlechte Sicht.", "Cellule orageuse avec grêle : rafales soudaines et visibilité réduite.", "Célula de tormenta con granizo: rachas repentinas y poca visibilidad."),
)

fun salaTitle(condition: SalaCondition, phase: SalaPhase): String =
    SalaTitles.getValue(condition).getValue(phase)

/**
 * Titolo e corpo di Sala I **quando una previsione non c'e'**: ne' arrivata,
 * ne' salvata su disco da un'apertura precedente.
 *
 * Prima si scriveva la didascalia del cielo sereno - "Pieno sole, aria calda" -
 * sopra una fila di trattini, perche' senza un codice del tempo la condizione
 * ripiega su SERENO. Era un'affermazione sul tempo fatta senza nessun dato
 * (CONTESTO §39.3). Qui si dice cosa succede davvero: o si sta ancora
 * chiedendo, o la richiesta e' fallita, con le parole di `failureMessage`.
 */
fun titoloSenzaPrevisione(errore: String?): String = errore ?: tr("Sto chiedendo il tempo", "Asking for the weather", "Ich frage das Wetter ab", "Je demande la météo", "Estoy pidiendo el tiempo")

fun corpoSenzaPrevisione(errore: String?): String =
    if (errore == null) tr("La previsione di questo posto arriva fra un momento.", "The forecast for this place is on its way.", "Die Vorhersage für diesen Ort ist unterwegs.", "La prévision pour ce lieu arrive dans un instant.", "La previsión de este lugar llega en un momento.")
    else tr("Di questo posto non c'è una previsione salvata da mostrare. Trascina in giù la schermata per riprovare.", "There is no saved forecast for this place. Pull the screen down to try again.", "Für diesen Ort gibt es keine gespeicherte Vorhersage. Ziehe den Bildschirm nach unten, um es erneut zu versuchen.", "Il n'y a pas de prévision enregistrée pour ce lieu. Tirez l'écran vers le bas pour réessayer.", "No hay una previsión guardada de este lugar. Desliza la pantalla hacia abajo para volver a intentarlo.")

/**
 * I corpi che una fase sola smentirebbe.
 *
 * Il corpo di NUVOLOSO parlava del sole coperto "a intervalli" anche alle due
 * di notte, sotto un titolo che diceva *Notte coperta*. Vale qui la stessa
 * ragione gia' scritta sopra per quel titolo: una didascalia contraddetta da
 * cio' che si ha sotto gli occhi toglie credito anche alle altre cinque. La
 * tabella resta per condizione — e' li' che i corpi si somigliano — e questa
 * elenca le poche caselle in cui la fase cambia le parole.
 */
private val SalaBodiesPerFase: Map<Pair<SalaCondition, SalaPhase>, String> get() = mapOf(
    (SalaCondition.NUVOLOSO to SalaPhase.NOTTE) to
        tr(
            "Nubi medie che scoprono le stelle a tratti. Non portano pioggia, ma trattengono il calore del giorno.",
            "Mid-level clouds showing the stars now and then. No rain in them, but they hold in the day's warmth.",
            "Mittelhohe Wolken, die zeitweise die Sterne freigeben. Sie bringen keinen Regen, halten aber die Wärme des Tages.",
            "Des nuages moyens qui laissent voir les étoiles par moments. Ils n'apportent pas de pluie, mais retiennent la chaleur du jour.",
            "Nubes medias que dejan ver las estrellas a ratos. No traen lluvia, pero retienen el calor del día.",
        ),
)

fun salaBody(condition: SalaCondition, phase: SalaPhase): String =
    SalaBodiesPerFase[condition to phase] ?: SalaBodies.getValue(condition)

fun SalaCondition.label(): String = when (this) {
    SalaCondition.SERENO -> tr("sereno", "clear", "klar", "dégagé", "despejado")
    SalaCondition.NUVOLOSO -> tr("nuvoloso", "cloudy", "bewölkt", "nuageux", "nublado")
    SalaCondition.PIOGGIA -> tr("pioggia", "rain", "Regen", "pluie", "lluvia")
    SalaCondition.NEVE -> tr("neve", "snow", "Schnee", "neige", "nieve")
    SalaCondition.TEMPORALE -> tr("temporale", "thunderstorm", "Gewitter", "orage", "tormenta")
    SalaCondition.TEMPORALE_GRANDINE -> tr("temporale con grandine", "thunderstorm with hail", "Gewitter mit Hagel", "orage avec grêle", "tormenta con granizo")
}

fun SalaPhase.label(): String = when (this) {
    SalaPhase.ALBA -> tr("alba", "dawn", "Morgendämmerung", "aube", "amanecer")
    SalaPhase.GIORNO -> tr("giorno", "day", "Tag", "jour", "día")
    SalaPhase.TRAMONTO -> tr("tramonto", "sunset", "Sonnenuntergang", "coucher du soleil", "atardecer")
    SalaPhase.NOTTE -> tr("notte", "night", "Nacht", "nuit", "noche")
}

// ─────────────────────────────────────────────────────────────────────────────
// Le didascalie, e l'interruttore che finalmente le comanda
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Quanto lunghe le vuole chi guarda.
 *
 * **La preferenza c'era da sempre e non la leggeva nessuno.** Nelle impostazioni
 * si sceglieva fra "Brevi" e "Complete", la scelta sopravviveva alla chiusura
 * dell'app, e nelle sette sale non cambiava una parola. Un comando che non
 * comanda niente non e' neutro: insegna a non fidarsi anche degli altri, e in
 * una schermata di impostazioni gli altri sono tutti li' accanto.
 *
 * Passa da un local e non da sei parametri: il corpo del testo lo scrivono sei
 * sale diverse, e infilare la preferenza in sei firme avrebbe voluto dire
 * toccarle tutte ogni volta che cambia. `compositionLocalOf` e non
 * `staticCompositionLocalOf` perche' questo valore **cambia** mentre l'app e'
 * aperta, ed e' esattamente il caso per cui i due si distinguono.
 */
val LocalDidascalie = compositionLocalOf { CaptionStyle.COMPLETE }

/**
 * Il corpo di una didascalia: sparisce quando si sono chieste brevi.
 *
 * Sparisce **il corpo e non il titolo**: chi chiede didascalie brevi vuole meno
 * parole, non meno informazione. Titolo e riga dei dati restano, e sono loro a
 * dire che tempo fa; il paragrafo e' quello che spiega, e lo si puo' togliere
 * senza perdere un fatto.
 */
@Composable
fun Didascalia(testo: String, palette: SalaPalette, modifier: Modifier = Modifier) {
    if (LocalDidascalie.current == CaptionStyle.BREVI) return
    Text(text = testo, style = SalaType.body, color = palette.inkSoft, modifier = modifier)
}

// ─────────────────────────────────────────────────────────────────────────────
// Le ore, gia' scritte
// ─────────────────────────────────────────────────────────────────────────────
//
// **Un giorno ha ventiquattro ore e le loro etichette sono sempre le stesse.**
// Erano un `String.format` ciascuna, e le colonne sono tante: sedici nella sala
// dei raggi, dodici nella pioggia, dodici nel vento, quattro nell'aria, una
// nella barra. `format` non e' una sostituzione di caratteri - compila il
// modello, cerca la posizione decimale della lingua in uso e costruisce due
// oggetti intermedi - e qui lo faceva a ogni ricomposizione, cioe' a ogni
// fotogramma mentre il cielo si muove.
//
// Un effetto collaterale che vale la pena dire: `%d` scrive le cifre **della
// lingua del telefono**, quindi su un telefono in arabo la barra delle ore
// usciva in cifre indo-arabe sotto un testo italiano. Qui escono sempre le
// stesse, come gia' fanno i decimali, che passano apposta da `Locale.ITALY`.

/** Le ventiquattro ore a due cifre: `00`, `01`, ... `23`. */
private val OreDueCifre: Array<String> = Array(24) { if (it < 10) "0$it" else "$it" }

/** Le stesse con i minuti: `00:00`, `01:00`, ... `23:00`. */
private val OrePiene: Array<String> = Array(24) { "${OreDueCifre[it]}:00" }

/**
 * L'ora a due cifre, senza minuti: l'etichetta sotto una colonna.
 *
 * Stringe fra 0 e 23 invece di lasciar passare un indice fuori posto: chi la
 * chiama passa sempre un `LocalDateTime.hour`, ma un'etichetta sbagliata e'
 * meno grave di una schermata che si chiude.
 */
fun oraDueCifre(ora: Int): String = OreDueCifre[ora.coerceIn(0, 23)]

/** L'ora piena, `HH:00`: l'etichetta grande della barra e le celle "PICCO". */
fun oraPiena(ora: Int): String = OrePiene[ora.coerceIn(0, 23)]
