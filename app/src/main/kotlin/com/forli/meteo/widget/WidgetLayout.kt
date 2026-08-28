package com.forli.meteo.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.SizeMode
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * Le due forme in cui un widget puo' trovarsi.
 *
 * Non sono due widget diversi nella lista: e' lo stesso, che cambia
 * disposizione quando lo si allarga. Due voci separate per la stessa cosa
 * costringerebbero a scegliere la larghezza prima di averla vista.
 */
private val QUADRATO = DpSize(140.dp, 140.dp)
private val LARGO = DpSize(300.dp, 140.dp)

internal val WidgetSizes = SizeMode.Responsive(setOf(QUADRATO, LARGO))

/** Sopra questa larghezza il numero e la scritta stanno affiancati. */
private val SOGLIA_LARGO = 240.dp

/**
 * L'impaginazione comune ai tre widget.
 *
 * Un solo posto per tutti: cio' che cambia fra meteo, luna e aria sono tre
 * stringhe e un'icona, non il modo di disporle. Quando la riga sotto il numero
 * non si leggeva, e' bastato correggere qui.
 *
 * @param value il numero grande: gradi, percentuale, indice.
 * @param label la riga piccola in alto: la localita', o cosa si sta guardando.
 * @param caption la parola sotto il numero: la condizione, la fase, la banda.
 */
@Composable
internal fun WidgetFrame(
    value: String,
    label: String,
    caption: String,
    icon: Int,
    palette: WidgetPalette,
    onClick: Action,
) {
    val larghezza = LocalSize.current.width
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(onClick),
    ) {
        if (larghezza >= SOGLIA_LARGO) {
            FormaLarga(value, label, caption, icon, palette)
        } else {
            FormaQuadrata(value, label, caption, icon, palette)
        }
    }
}

/**
 * Quadrato: la scritta sta **sotto** il numero e non gli sta accanto.
 *
 * Prima erano tre righe schiacciate in una cella d'altezza, e l'ultima restava
 * tagliata fuori: e' quella che dice se piove, cioe' la sola che si legge
 * davvero.
 */
@Composable
private fun FormaQuadrata(
    value: String,
    label: String,
    caption: String,
    icon: Int,
    palette: WidgetPalette,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.Top,
        ) {
            Text(text = label, style = etichetta(palette), maxLines = 1)
            Spacer(modifier = GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(icon),
                contentDescription = caption,
                colorFilter = ColorFilter.tint(palette.accent),
                modifier = GlanceModifier.size(34.dp),
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(text = value, style = numero(palette, 48.sp), maxLines = 1)
        Text(text = caption, style = didascalia(palette), maxLines = 2)
    }
}

/** Largo: il numero a sinistra, e accanto le due righe che lo spiegano. */
@Composable
private fun FormaLarga(
    value: String,
    label: String,
    caption: String,
    icon: Int,
    palette: WidgetPalette,
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(text = value, style = numero(palette, 56.sp), maxLines = 1)
        Column(modifier = GlanceModifier.padding(start = 12.dp)) {
            Text(text = label, style = etichetta(palette), maxLines = 1)
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(text = caption, style = didascalia(palette), maxLines = 2)
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Image(
            provider = ImageProvider(icon),
            contentDescription = caption,
            colorFilter = ColorFilter.tint(palette.accent),
            modifier = GlanceModifier.size(56.dp),
        )
    }
}

// Glance non sa caricare il carattere dell'app: il TextStyle dei widget accetta
// solo le famiglie di sistema. Il peso massimo e' quanto piu' ci si avvicina
// alla cifra stretta e piena della schermata principale.
private fun numero(palette: WidgetPalette, size: androidx.compose.ui.unit.TextUnit) = TextStyle(
    color = palette.accent,
    fontSize = size,
    fontWeight = FontWeight.Bold,
)

private fun etichetta(palette: WidgetPalette) = TextStyle(
    color = palette.secondary,
    fontSize = 11.sp,
    fontWeight = FontWeight.Medium,
)

private fun didascalia(palette: WidgetPalette) = TextStyle(
    color = palette.accent,
    fontSize = 13.sp,
    fontWeight = FontWeight.Bold,
)
