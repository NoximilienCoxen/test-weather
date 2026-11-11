package com.caelumweather.ui.sala

import com.caelumweather.lingua.tr
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Un passo della guida: cosa si illumina, e cosa se ne dice.
 *
 * [dove] e' nelle coordinate della radice, come le da' `boundsInRoot`; nullo
 * vuol dire che quel pezzo non c'e' (la colonna, con le schede larghe) e il
 * passo si salta.
 */
internal class PassoGuida(val titolo: String, val testo: String, val dove: Rect?)

/**
 * La guida all'uso: lo schermo si scurisce tranne un riquadro, e accanto una
 * scheda dice cosa c'e' li' e cosa ci si fa.
 *
 * **Perche' esiste.** Chi ha provato l'app non sapeva che la barra in fondo
 * si trascina per cambiare ora, ne' che le sale si sfogliano: i gesti non si
 * vedono, e le scritte che li spiegano sono piccole di proposito. Compare una
 * volta, al primo avvio dopo il benvenuto, e si rivede dalle impostazioni.
 *
 * Un tocco ovunque manda avanti, "Salta" chiude; l'indietro di sistema chiude
 * anche lui, perche' una guida che trattiene il tasto indietro e' una
 * trappola.
 */
@Composable
internal fun GuidaSala(
    passi: List<PassoGuida>,
    palette: SalaPalette,
    movimento: Boolean,
    onFine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val validi = remember(passi) { passi.filter { it.dove != null } }
    var indice by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onFine)
    if (validi.isEmpty()) return
    val passo = validi[indice.coerceIn(0, validi.lastIndex)]
    val ultimo = indice >= validi.lastIndex
    fun avanti() {
        if (ultimo) onFine() else indice++
    }

    // La radice della guida non e' per forza la radice della finestra: le
    // coordinate si riportano alle sue.
    var origine by remember { mutableStateOf(Offset.Zero) }
    val bersaglio = passo.dove!!.translate(-origine)

    // Il riquadro scivola da un passo all'altro, cosi' l'occhio lo segue;
    // con le animazioni ridotte salta.
    val molla = if (movimento) {
        spring<Float>(dampingRatio = 0.85f, stiffness = 300f)
    } else {
        spring(stiffness = 100_000f)
    }
    val l by animateFloatAsState(bersaglio.left, molla, label = "l")
    val t by animateFloatAsState(bersaglio.top, molla, label = "t")
    val r by animateFloatAsState(bersaglio.right, molla, label = "r")
    val b by animateFloatAsState(bersaglio.bottom, molla, label = "b")

    val densita = LocalDensity.current
    val margine = with(densita) { 8.dp.toPx() }
    val raggio = with(densita) { 26.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origine = it.boundsInRoot().topLeft }
            // Il tocco non passa sotto: mentre la guida parla, l'app sta ferma.
            .pointerInput(ultimo, indice) { detectTapGestures { avanti() } },
    ) {
        val altezza = constraints.maxHeight.toFloat()
        Canvas(modifier = Modifier.fillMaxSize()) {
            val buco = RoundRect(
                left = l - margine,
                top = t - margine,
                right = r + margine,
                bottom = b + margine,
                cornerRadius = CornerRadius(raggio),
            )
            val velo = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(buco)
            }
            drawPath(velo, Color(0xFF10141C).copy(alpha = 0.74f))
            drawPath(
                Path().apply { addRoundRect(buco) },
                color = SalaTokens.accent400,
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }

        // La scheda sta dalla parte dove c'e' piu' posto: sotto il riquadro
        // se il riquadro e' in alto, sopra se e' in basso.
        val sotto = (t + b) / 2f < altezza / 2f
        val spazio = with(densita) { 18.dp.toPx() }
        Column(
            modifier = Modifier
                .align(if (sotto) Alignment.TopCenter else Alignment.BottomCenter)
                .offset {
                    if (sotto) {
                        IntOffset(0, (b + margine + spazio).roundToInt())
                    } else {
                        IntOffset(0, -(altezza - t + margine + spazio).roundToInt())
                    }
                }
                .padding(horizontal = 20.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(palette.panelSolido)
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 14.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                text = tr("${indice + 1} DI ${validi.size}", "${indice + 1} OF ${validi.size}", "${indice + 1} VON ${validi.size}", "${indice + 1} SUR ${validi.size}", "${indice + 1} DE ${validi.size}"),
                style = SalaType.sectionLabel,
                color = palette.accent,
            )
            Text(
                text = passo.titolo,
                style = SalaType.cardTitle,
                color = palette.ink,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = passo.testo,
                style = SalaType.body,
                color = palette.inkSoft,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PuntiniGuida(totale = validi.size, corrente = indice, palette = palette)
                Spacer(modifier = Modifier.weight(1f))
                if (!ultimo) {
                    Text(
                        text = tr("Salta", "Skip", "Überspringen", "Passer", "Saltar"),
                        style = SalaType.rowTitle,
                        color = palette.inkSoft,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onFine)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
                Text(
                    text = if (ultimo) tr("Ho capito", "Got it", "Verstanden", "Compris", "Entendido") else tr("Avanti", "Next", "Weiter", "Suivant", "Siguiente"),
                    style = SalaType.rowTitle,
                    color = palette.accentInk,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(palette.accent)
                        .clickable { avanti() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun PuntiniGuida(totale: Int, corrente: Int, palette: SalaPalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(totale) { i ->
            Box(
                modifier = Modifier
                    .size(if (i == corrente) 8.dp else 6.dp)
                    .align(Alignment.CenterVertically)
                    .clip(CircleShape)
                    .background(if (i == corrente) palette.accent else palette.inkFaint),
            )
        }
    }
}

/** I passi, con i riquadri misurati nella cornice. */
internal fun passiGuida(
    cielo: Rect?,
    intestazione: Rect?,
    scheda: Rect?,
    colonna: Rect?,
    barra: Rect?,
): List<PassoGuida> = listOf(
    PassoGuida(
        titolo = tr("Le schermate", "The screens", "Die Bildschirme", "Les écrans", "Las pantallas"),
        testo = tr(
            "Scorri in su o in giù sulla scheda per passare da una schermata all'altra: " +
                "oggi, la settimana, la pioggia, la luna, l'aria, il vento e i raggi UV. " +
                "Basta un colpetto.",
            "Swipe up or down on the card to move from one screen to the next: " +
                "today, the week, rain, the moon, air, wind and UV rays. " +
                "A light flick is enough.",
            "Wische auf der Karte nach oben oder unten, um von einem Bildschirm zum nächsten zu wechseln: heute, die Woche, Regen, der Mond, die Luft, der Wind und die UV-Strahlung. Ein leichter Wisch genügt.",
            "Balayez la carte vers le haut ou vers le bas pour passer d'un écran à l'autre : aujourd'hui, la semaine, la pluie, la lune, l'air, le vent et les rayons UV. Un petit geste suffit.",
            "Desliza hacia arriba o hacia abajo sobre la tarjeta para pasar de una pantalla a otra: hoy, la semana, la lluvia, la luna, el aire, el viento y los rayos UV. Basta un toque ligero.",
        ),
        dove = scheda,
    ),
    PassoGuida(
        titolo = tr("Dentro le schede", "Inside the cards", "In den Karten", "Dans les cartes", "Dentro de las tarjetas"),
        testo = tr(
            "Le voci con › si aprono e portano alla loro schermata. " +
                "Tocca un giorno per vedere quel giorno in tutte le schermate.",
            "Items marked › open and take you to their screen. " +
                "Tap a day to see that day on every screen.",
            "Einträge mit › öffnen sich und führen zu ihrem Bildschirm. Tippe auf einen Tag, um diesen Tag auf allen Bildschirmen zu sehen.",
            "Les éléments marqués › s'ouvrent et mènent à leur écran. Touchez un jour pour voir ce jour sur tous les écrans.",
            "Los elementos con › se abren y llevan a su pantalla. Toca un día para ver ese día en todas las pantallas.",
        ),
        dove = scheda,
    ),
    PassoGuida(
        titolo = tr("La colonna delle icone", "The icon column", "Die Symbolspalte", "La colonne d'icônes", "La columna de iconos"),
        testo = tr(
            "Tocca un'icona per andare dritto a quella schermata, o fai scorrere il dito " +
                "lungo la colonna. Dalle impostazioni puoi nasconderla e avere schede più larghe.",
            "Tap an icon to go straight to that screen, or slide your finger " +
                "along the column. In settings you can hide it and get wider cards.",
            "Tippe auf ein Symbol, um direkt zu diesem Bildschirm zu gehen, oder fahre mit dem Finger die Spalte entlang. In den Einstellungen kannst du sie ausblenden und breitere Karten bekommen.",
            "Touchez une icône pour aller directement à cet écran, ou faites glisser le doigt le long de la colonne. Dans les réglages, vous pouvez la masquer et obtenir des cartes plus larges.",
            "Toca un icono para ir directamente a esa pantalla, o desliza el dedo por la columna. En los ajustes puedes ocultarla y tener tarjetas más anchas.",
        ),
        dove = colonna,
    ),
    PassoGuida(
        titolo = tr("Cambia ora", "Change the hour", "Stunde ändern", "Changer l'heure", "Cambiar la hora"),
        testo = tr(
            "Trascina il cerchio lungo la barra: cielo, temperatura e numeri " +
                "seguono l'ora scelta. Ogni tratto ha il colore del cielo di quell'ora. " +
                "Per tornare ad adesso tocca «Torna all'ora attuale».",
            "Drag the circle along the bar: sky, temperature and numbers " +
                "follow the hour you pick. Each stretch has the colour of that hour's sky. " +
                "To come back to now, tap \"Back to now\".",
            "Ziehe den Kreis die Leiste entlang: Himmel, Temperatur und Zahlen folgen der gewählten Stunde. Jeder Abschnitt hat die Farbe des Himmels dieser Stunde. Um zu jetzt zurückzukehren, tippe auf «Zurück zu jetzt».",
            "Faites glisser le cercle le long de la barre : le ciel, la température et les chiffres suivent l'heure choisie. Chaque segment a la couleur du ciel de cette heure. Pour revenir à maintenant, touchez « Retour à maintenant ».",
            "Arrastra el círculo por la barra: el cielo, la temperatura y los números siguen la hora elegida. Cada tramo tiene el color del cielo de esa hora. Para volver a ahora, toca «Volver a ahora».",
        ),
        dove = barra,
    ),
    PassoGuida(
        titolo = tr("Il cielo", "The sky", "Der Himmel", "Le ciel", "El cielo"),
        testo = tr(
            "Il disegno è il tempo dell'ora scelta: sole, nuvole, pioggia e luna " +
                "seguono la previsione. Inclina il telefono o toccalo, e si muove.",
            "The picture is the weather of the chosen hour: sun, clouds, rain and moon " +
                "follow the forecast. Tilt the phone or touch it, and it moves.",
            "Das Bild ist das Wetter der gewählten Stunde: Sonne, Wolken, Regen und Mond folgen der Vorhersage. Neige das Telefon oder berühre es, und es bewegt sich.",
            "Le dessin est le temps de l'heure choisie : soleil, nuages, pluie et lune suivent la prévision. Inclinez le téléphone ou touchez-le, et il bouge.",
            "El dibujo es el tiempo de la hora elegida: sol, nubes, lluvia y luna siguen la previsión. Inclina el teléfono o tócalo, y se mueve.",
        ),
        dove = cielo,
    ),
    PassoGuida(
        titolo = tr("Città e impostazioni", "City and settings", "Stadt und Einstellungen", "Ville et réglages", "Ciudad y ajustes"),
        testo = tr(
            "Tocca il nome della città per cambiarla o aggiungerne altre. " +
                "L'icona a sinistra apre le impostazioni; a destra, gli avvisi del giorno.",
            "Tap the city name to change it or add others. " +
                "The icon on the left opens settings; on the right, the day's warnings.",
            "Tippe auf den Stadtnamen, um ihn zu ändern oder weitere hinzuzufügen. Das Symbol links öffnet die Einstellungen; rechts die Warnungen des Tages.",
            "Touchez le nom de la ville pour en changer ou en ajouter d'autres. L'icône de gauche ouvre les réglages ; à droite, les alertes du jour.",
            "Toca el nombre de la ciudad para cambiarla o añadir otras. El icono de la izquierda abre los ajustes; a la derecha, los avisos del día.",
        ),
        dove = intestazione,
    ),
)
