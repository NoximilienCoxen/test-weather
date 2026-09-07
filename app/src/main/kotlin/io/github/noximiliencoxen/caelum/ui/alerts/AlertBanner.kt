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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.AlertLevel
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.data.badgeLabel
import io.github.noximiliencoxen.caelum.data.shortBadge
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
    val levelTint = alertTint(worst, background)

    val others = alerts.size - 1
    val line = buildString {
        // **Senza il "ALLERTA " davanti.** Il segno tinto accanto dice gia'
        // che e' un avviso e di che colore: ripeterlo a parole costa otto
        // caratteri su una riga sola, e sono gli otto che facevano finire il
        // colore in "ALLERTA ARANCI...". Il nome per esteso resta dove serve -
        // nel bollettino e in cio' che legge il lettore di schermo.
        //
        // Per un avviso calcolato qui non c'e' un colore ma la parola SOGLIA:
        // vedi `WeatherAlert.badgeLabel`.
        append(worst.shortBadge)
        append("  ·  ")
        append(worst.kind.label)
        if (others > 0) append(if (others == 1) "  ·  +1 ALTRA" else "  ·  +$others ALTRE")
    }
    val spoken = worst.spoken()

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
            AlertMark(worst, levelTint, Modifier.size(18.dp))
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
 *
 * **Si passa sempre da [alertTint], mai di qui direttamente**: sono i colori
 * dei bollettini ufficiali, e chiamarla su un avviso calcolato gli metterebbe
 * addosso il grado di un ente che non l'ha diramato.
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
        drawPath(path, color, style = Stroke(stroke))
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

/**
 * Il colore con cui un avviso si presenta sul suo fondo.
 *
 * **I tre colori ufficiali sono solo dei bollettini ufficiali.** Giallo,
 * arancione e rosso sono il codice del sistema di allertamento, non una scala
 * di gravita' che chiunque possa usare: un avviso nato da un confronto fra una
 * raffica e una costante prende invece il colore del testo del contenitore,
 * cioe' si legge come una nota e non come un grado. La gravita' del calcolato
 * la dice l'ordine in cui compare - le allerte sono gia' ordinate per peso - e
 * il bollettino, dove c'e' spazio per le parole.
 *
 * `readableOn` costa una manciata di elevamenti a potenza e non va rifatta a
 * ogni fotogramma: sta dietro un `remember` chiavato su cio' che la puo'
 * cambiare, cioe' il tema e il livello.
 */
@Composable
internal fun alertTint(alert: WeatherAlert, background: Color): Color {
    val notice = MaterialTheme.colorScheme.onErrorContainer
    return remember(alert.official, alert.level, background, notice) {
        if (alert.official) {
            rawTint(alert.level).readableOn(background, CONTRAST_AA_LARGE)
        } else {
            notice
        }
    }
}

/**
 * Cio' che un lettore di schermo dice di un avviso.
 *
 * Tutto insieme e non a pezzi: "ALLERTA ARANCIONE", pausa, "TEMPORALI", pausa,
 * il titolo, costringe a ricucire. E per un avviso calcolato la frase si chiude
 * dicendolo - e' l'unico posto in cui la cosa va detta a parole, perche' un
 * lettore di schermo il segno e il colore non li vede.
 */
internal fun WeatherAlert.spoken(): String {
    val corpo = listOfNotNull(badgeLabel, kind.label, headline).joinToString(". ")
    return if (official) corpo else "$corpo. Non e' un bollettino ufficiale."
}

/**
 * Il segno dell'avviso: il triangolo se lo dice un ente, il cerchio se lo dice
 * un conto.
 *
 * Due forme e non due colori soltanto, perche' il colore da solo non basta:
 * chi non lo distingue - e sono uno su dodici fra gli uomini - resterebbe senza
 * la differenza. Il triangolo e' il segno del pericolo e resta ai bollettini;
 * il cerchio e' il segno della nota.
 */
@Composable
internal fun AlertMark(alert: WeatherAlert, color: Color, modifier: Modifier = Modifier) {
    if (alert.official) WarningTriangle(color, modifier) else NoticeCircle(color, modifier)
}

/**
 * Il cerchio col punto esclamativo, disegnato come il triangolo che affianca.
 *
 * Stesse proporzioni interne di [WarningTriangle] - stesso spessore, asta e
 * punto alle stesse quote - cosi' i due si leggono come due stati dello stesso
 * segno invece che come due icone prese da due parti.
 */
@Composable
internal fun NoticeCircle(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = h * 0.10f
        drawCircle(
            color = color,
            // Il raggio tiene conto della meta' del tratto: con `h / 2` il
            // cerchio uscirebbe dalla tela di mezzo spessore e verrebbe tagliato.
            radius = (minOf(w, h) - stroke) / 2f,
            center = Offset(w / 2f, h / 2f),
            style = Stroke(stroke),
        )
        drawLine(
            color = color,
            start = Offset(w / 2f, h * 0.30f),
            end = Offset(w / 2f, h * 0.58f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(color, radius = stroke * 0.6f, center = Offset(w / 2f, h * 0.72f))
    }
}
