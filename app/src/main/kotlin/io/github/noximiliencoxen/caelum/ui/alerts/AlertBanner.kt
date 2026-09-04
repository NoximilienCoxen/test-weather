package io.github.noximiliencoxen.caelum.ui.alerts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.AlertLevel
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.ui.common.CloseIcon
import io.github.noximiliencoxen.caelum.ui.common.MeteoIconButton
import io.github.noximiliencoxen.caelum.ui.common.MinTouchTarget
import io.github.noximiliencoxen.caelum.ui.theme.CONTRAST_AA_LARGE
import io.github.noximiliencoxen.caelum.ui.theme.readableOn

/**
 * La riga che dice che c'e' un'allerta.
 *
 * **Se non ce ne sono non disegna niente**, e non e' pigrizia: una fascia che
 * dice "nessuna allerta" occupa lo stesso spazio di una che ne dichiara una, e
 * dopo qualche giorno di sereno l'occhio smette di leggerla. Quando compare
 * deve voler dire qualcosa.
 *
 * Sta sotto la barra di ogni schermata invece che dentro una pagina del
 * carosello: un avviso che si trova solo scorrendo fino alla sesta pillola non
 * avvisa nessuno.
 *
 * Del colore: il fondo e' il contenitore d'errore del tema, gia' ricavato dalla
 * tinta d'allerta, e il testo il suo `on`. Il colore del **livello** parte
 * dalla tinta grezza del giallo, arancione o rosso e passa da `readableOn`, che
 * la spinge quanto basta perche' si legga su quel fondo: e' lo stesso
 * meccanismo con cui il tema costruisce le tinte delle grandezze, non un colore
 * scelto a occhio.
 */
@Composable
fun AlertBanner(
    alerts: List<WeatherAlert>,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val worst = alerts.maxByOrNull { it.level.weight } ?: return

    val background = MaterialTheme.colorScheme.errorContainer
    val onBackground = MaterialTheme.colorScheme.onErrorContainer
    // `readableOn` costa una manciata di elevamenti a potenza per passo e non
    // va chiamata a ogni fotogramma: qui sta dentro un `remember` con il fondo
    // e il livello per chiave, cioe' si ricalcola solo quando cambia il tema.
    val levelTint = remember(worst.level, background) {
        rawTint(worst.level).readableOn(background, CONTRAST_AA_LARGE)
    }

    val others = alerts.size - 1
    val line = buildString {
        // **Senza il "ALLERTA " davanti.** Il triangolo tinto accanto dice gia'
        // che e' un'allerta e di che colore: ripeterlo a parole costa otto
        // caratteri su una riga sola, e sono gli otto che facevano finire il
        // colore in "ALLERTA ARANCI...". Il nome per esteso resta dove serve -
        // nel bollettino e in cio' che legge il lettore di schermo.
        append(worst.level.label.removePrefix("ALLERTA "))
        append("  ·  ")
        append(worst.kind.label)
        if (others > 0) append(if (others == 1) "  ·  +1 ALTRA" else "  ·  +$others ALTRE")
    }
    // Letta tutta insieme: un lettore di schermo che dice "ALLERTA ARANCIONE",
    // pausa, "TEMPORALI", pausa, il titolo, costringe a ricucire i pezzi.
    val spoken = listOfNotNull(worst.level.label, worst.kind.label, worst.headline)
        .joinToString(". ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clip(MaterialTheme.shapes.large)
            .background(background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La parte che si legge e' un bersaglio, la croce e' l'altro. Prima
        // tutta la riga passava da `clearAndSetSemantics`, e qui non si puo'
        // piu': cancellerebbe anche il pulsante di chiusura, e un lettore di
        // schermo resterebbe senza il modo di ridurre la fascia.
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = MinTouchTarget)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp)
                .clearAndSetSemantics {
                    contentDescription = "$spoken. Tocca per il bollettino."
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WarningTriangle(levelTint, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            // **Senza peso**: prende lo spazio che gli serve, per primo. E' il
            // pezzo per cui la fascia esiste - quanto e' grave e di cosa - e
            // non puo' troncarsi. Con il peso che aveva prima si spartiva la
            // riga col titolo, e appena la croce si e' presa i suoi 48dp il
            // colore usciva come "ALLERTA ARANCI...".
            Text(
                text = line,
                style = MaterialTheme.typography.labelMedium,
                color = levelTint,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            // Il titolo invece cede: prende quel che avanza e si accorcia. Per
            // esteso sta nel bollettino, a un tocco di distanza.
            Text(
                text = worst.headline,
                style = MaterialTheme.typography.labelSmall,
                color = onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // Ridurre la fascia e' un gesto che va dichiarato, non indovinato: la
        // croce e' lo stesso segno che chiude i pannelli del dettaglio, e sta
        // in un bersaglio da 48dp come tutti gli altri dell'app.
        MeteoIconButton(
            onClick = onDismiss,
            contentDescription = "Riduci l'avviso a un pallino",
        ) {
            CloseIcon(onBackground)
        }
    }
}

/**
 * Le tinte grezze dei tre gradini, prima di essere rese leggibili.
 *
 * Sono i colori che l'allerta ha per convenzione in Italia e in mezza Europa, e
 * qui vanno dichiarati grezzi apposta: chi li usa li passa da `readableOn` sul
 * proprio fondo, che e' l'unico punto in cui si decide quanto schiarirli.
 */
internal fun rawTint(level: AlertLevel): Color = when (level) {
    AlertLevel.GIALLA -> Color(0xFFF2C230)
    AlertLevel.ARANCIONE -> Color(0xFFF08A2B)
    AlertLevel.ROSSA -> Color(0xFFE0402F)
}

/**
 * Il triangolo col punto esclamativo, disegnato e non importato.
 *
 * Il progetto non ha `material-icons-extended` - la freccia e la croce sono
 * disegnate a mano in `MeteoSurfaces` - e non vale mezzo megabyte di
 * dipendenza per tre linee e un punto.
 */
@Composable
internal fun WarningTriangle(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = h * 0.10f
        val path = Path().apply {
            moveTo(w / 2f, h * 0.06f)
            lineTo(w * 0.97f, h * 0.92f)
            lineTo(w * 0.03f, h * 0.92f)
            close()
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawLine(
            color = color,
            start = Offset(w / 2f, h * 0.38f),
            end = Offset(w / 2f, h * 0.64f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(color, radius = stroke * 0.6f, center = Offset(w / 2f, h * 0.78f))
    }
}
