package io.github.noximiliencoxen.caelum.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.MeteoType

/**
 * L'intestazione editoriale di una schermata: occhiello, titolo, filo.
 *
 * E' la didascalia da museo messa in cima invece che sotto. Tre elementi e
 * nessuno di piu': **di chi e di quando** si sta parlando in piccolo, **cosa**
 * in grande e tracciato largo, e un filo che chiude il blocco e apre la
 * composizione.
 *
 * **L'ordine e' rovesciato rispetto a prima**, ed e' la parte che si nota. Le
 * schede del feed avevano il titolo sopra e la localita' sotto, come una
 * schermata con una barra in cima; qui la localita' sale a fare da occhiello e
 * il titolo scende a fare da testata. E' la forma della pagina stampata, dove la
 * riga piccola dice il contesto e quella grande dice il soggetto.
 *
 * **Il posto sta nell'occhiello e non nel titolo.** La consegna scriveva
 * `NOCETO · PRECIPITAZIONI OGGI` su una riga sola, e su una riga sola non ci
 * sta: `QUALITA' DELL'ARIA` da solo, tracciato a 0,15em, occupa gia' quasi tutta
 * la larghezza di un telefono stretto, e con davanti il nome di una localita'
 * finirebbe troncato proprio sulla parola che dice di che scheda si tratta. Il
 * contenuto e' lo stesso, spartito sulle due righe che la forma editoriale ha
 * gia'.
 *
 * [leading] e [trailing] sono le due caselle laterali, e servono alla prima
 * scheda: il pulsante delle impostazioni da una parte, il pallino dell'allerta
 * dall'altra. **Quando ce n'e' una sola, l'altra resta uno spazio vuoto della
 * stessa misura**: e' il trucco che [MeteoTopBar] usa gia' per tenere il titolo
 * al centro dello **schermo** invece che al centro di quel che avanza, ed e' la
 * stessa regola che sulla prima scheda tiene fermo il nome della localita'
 * quando il pallino compare o sparisce.
 *
 * Nessun colore si sceglie qui: [accent] arriva da `rememberSkyAccents()`, che
 * lo ha gia' ricavato contro i due capi della sfumatura del cielo, e occhiello e
 * filo vengono da `LocalMeteoColors`, dove `readableOnBoth` e `mutedOnBoth`
 * hanno gia' fatto lo stesso lavoro. E' la regola della sezione 8-bis di
 * CONTESTO: sopra un fondo che cambia tutto il giorno il colore del testo non si
 * sceglie, si calcola.
 */
@Composable
fun EditorialHeader(
    kicker: String,
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    /** Il modificatore dell'occhiello: la prima scheda ci appende il tocco per ricaricare. */
    kickerModifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalMeteoColors.current
    val sides = leading != null || trailing != null
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sides) {
                Box(
                    modifier = Modifier.width(MinTouchTarget),
                    contentAlignment = Alignment.Center,
                ) { leading?.invoke() }
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = kicker,
                    style = MeteoType.kicker,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = kickerModifier.fillMaxWidth(),
                )
                Text(
                    // Il maiuscolo si applica qui e non si chiede a chi chiama:
                    // e' una regola della forma, non del contenuto, e lasciarla
                    // fuori vorrebbe dire sei chiamanti che se la ricordano.
                    text = title.uppercase(),
                    style = MeteoType.masthead,
                    color = accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
                )
            }
            if (sides) {
                Box(
                    modifier = Modifier.width(MinTouchTarget),
                    contentAlignment = Alignment.Center,
                ) { trailing?.invoke() }
            }
        }
        // Il filo. Non e' un divisore fra due contenuti: e' il segno che chiude
        // la testata, e per questo e' inserito ai lati invece di correre da
        // bordo a bordo - un filo che tocca i margini si legge come il bordo di
        // un pannello che non c'e'.
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp, start = RULE_INSET, end = RULE_INSET)
                .height(1.dp)
                .background(colors.line.copy(alpha = RULE_ALPHA)),
        )
    }
}

private val RULE_INSET = 12.dp
private const val RULE_ALPHA = 0.40f
