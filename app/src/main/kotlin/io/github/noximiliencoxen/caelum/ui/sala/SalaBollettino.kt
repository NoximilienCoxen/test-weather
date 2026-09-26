package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.AlertLevel
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.data.badgeLabel
import io.github.noximiliencoxen.caelum.ui.theme.onColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * Il bollettino: ogni avviso in scena, per intero.
 *
 * **La pastiglia in alto dice che c'e' un avviso, e finora nient'altro.** Il
 * vecchio bollettino (`ui/alerts/AlertsSheet.kt`) e' uscito col feed, e da
 * allora descrizione e istruzioni arrivavano dal documento CAP, finivano in
 * [WeatherAlert] e non le leggeva nessuno: di un'allerta arancione per
 * temporali si sapeva il colore, non la zona, non fino a quando, non cosa fare.
 * Si apre toccando la pastiglia.
 *
 * Le regole di `CONTESTO.md` §8-ter valgono qui per intero:
 *
 * - **"ALLERTA" e i tre colori sono di un ente.** Un avviso calcolato si
 *   chiama `SOGLIA SUPERATA` ([badgeLabel]), prende l'inchiostro del pannello
 *   invece di un colore di livello, e ha un **cerchio** al posto del
 *   triangolo: due forme e non due tinte soltanto, perche' il colore da solo
 *   lascia fuori chi non lo distingue.
 * - **Il titolo non dice "allerte" quando ci sono solo soglie**: era la domanda
 *   lasciata aperta da quella sezione.
 * - **La fonte si scrive sempre**, in fondo a ogni avviso.
 *
 * Gli avvisi sono **tutti** quelli in scena, non solo quelli dell'ora mostrata
 * come nella pastiglia: chi apre il bollettino vuole sapere anche cosa arriva
 * stasera. Prima quelli in corso, poi i piu' gravi.
 *
 * @param adesso l'ora del posto, per dire "in corso" e "oggi": e' l'ora in cui
 *   gli avvisi sono scritti (vedi `WeatherAlertsRepository.toAlert`).
 * @param senzaRete l'ultima richiesta non e' arrivata: un bollettino vuoto
 *   senza rete non vuol dire che nessuno abbia diramato niente, e lo si dice.
 */
@Composable
fun SalaBollettinoScreen(
    avvisi: List<WeatherAlert>,
    luogo: String,
    adesso: LocalDateTime,
    senzaRete: Boolean,
    palette: SalaPalette,
    onClose: () -> Unit,
) {
    val ordinati = remember(avvisi, adesso) {
        avvisi.sortedWith(
            compareByDescending<WeatherAlert> { inCorso(it, adesso) }
                .thenByDescending { it.level.weight }
                .thenBy { it.onset ?: LocalDateTime.MIN },
        )
    }
    val ufficiali = avvisi.any { it.official }
    val calcolati = avvisi.any { !it.official }
    val titolo = when {
        ufficiali && calcolati -> "Allerte e avvisi"
        ufficiali -> "Le allerte"
        else -> "Gli avvisi"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.schermoPieno)
            .systemBarsPadding()
            .padding(start = 26.dp, end = 26.dp, top = 12.dp, bottom = 30.dp),
    ) {
        IntestazioneServizio(titolo = titolo, palette = palette, onIndietro = onClose)
        Text(
            text = when (avvisi.size) {
                0 -> luogo
                1 -> "$luogo · un avviso"
                else -> "$luogo · ${avvisi.size} avvisi"
            },
            style = SalaType.rowNote,
            color = palette.inkSoft,
            modifier = Modifier.padding(start = 54.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (ordinati.isEmpty()) {
                BloccoImpostazioni(etichetta = "NESSUN AVVISO", palette = palette) {
                    Text(
                        text = "Per $luogo non è arrivata nessuna allerta ufficiale, e la previsione " +
                            "di oggi e domani non supera nessuna delle soglie che l'app controlla.",
                        style = SalaType.body,
                        color = palette.inkSoft,
                    )
                    if (senzaRete) {
                        Text(
                            text = "Adesso però la rete manca: le allerte ufficiali non si possono " +
                                "controllare finché non torna.",
                            style = SalaType.body,
                            color = palette.accent,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            ordinati.forEach { avviso -> SchedaAvviso(avviso, adesso, palette) }

            // Una volta sola, in fondo: la differenza fra le due parole e' la
            // cosa piu' importante da sapere leggendo questa pagina, e scritta
            // sotto ogni scheda diventerebbe rumore.
            if (ordinati.isNotEmpty()) {
                Text(
                    text = "ALLERTA vuol dire che l'ha diramata un ente, attraverso MeteoAlarm. " +
                        "SOGLIA SUPERATA vuol dire che l'ha calcolata l'app confrontando la " +
                        "previsione con delle soglie: non è un'allerta ufficiale e non sostituisce " +
                        "un bollettino.",
                    style = SalaType.rowNote,
                    color = palette.inkFaint,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Un avviso per intero: chi lo dice, di cosa, dove, quando, cosa fare. */
@Composable
private fun SchedaAvviso(avviso: WeatherAlert, adesso: LocalDateTime, palette: SalaPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(palette.chip)
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SegnoAvviso(avviso, palette)
            EtichettaLivello(avviso, palette)
            if (inCorso(avviso, adesso)) {
                Text(text = "IN CORSO", style = SalaType.sectionLabel, color = palette.accent, maxLines = 1)
            }
        }
        Text(
            text = avviso.headline,
            style = SalaType.rowTitle,
            color = palette.ink,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = finestra(avviso, adesso.toLocalDate()),
            style = SalaType.rowNote,
            color = palette.inkSoft,
            modifier = Modifier.padding(top = 4.dp),
        )
        // La zona solo per gli ufficiali: per un avviso calcolato e' il nome del
        // posto mostrato, che sta gia' in cima alla pagina.
        if (avviso.official) {
            avviso.areaDesc?.takeIf { it.isNotBlank() }?.let { zona ->
                Text(
                    text = "Zona: $zona",
                    style = SalaType.rowNote,
                    color = palette.inkSoft,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        avviso.description?.takeIf { it.isNotBlank() }?.let { testo ->
            Text(
                text = testo,
                style = SalaType.body,
                color = palette.inkSoft,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        avviso.instruction?.takeIf { it.isNotBlank() }?.let { testo ->
            Text(
                text = "COSA FARE",
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = testo,
                style = SalaType.body,
                color = palette.ink,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            text = if (avviso.official) {
                "Fonte: ${avviso.source}"
            } else {
                "Calcolato dall'app sui dati Open-Meteo: non è un'allerta ufficiale."
            },
            style = SalaType.rowNote,
            color = palette.inkFaint,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/**
 * Il livello per esteso: pastiglia del colore di livello per un ente, inchiostro
 * del pannello per una soglia.
 */
@Composable
private fun EtichettaLivello(avviso: WeatherAlert, palette: SalaPalette) {
    val fondo = if (avviso.official) coloreLivello(avviso.level) else palette.maniglia
    // Il testo non si sceglie: si calcola dal fondo (CONTESTO §8-bis).
    val inchiostro = if (avviso.official) fondo.onColor() else palette.ink
    Text(
        text = "${avviso.badgeLabel} · ${avviso.kind.label}",
        style = SalaType.sectionLabel,
        color = inchiostro,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(fondo)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    )
}

/** Il segno: triangolo pieno per un ente, cerchio vuoto per una soglia. */
@Composable
private fun SegnoAvviso(avviso: WeatherAlert, palette: SalaPalette) {
    val colore = if (avviso.official) coloreLivello(avviso.level) else palette.ink
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val tratto = w * 0.11f
        val punto: Color
        if (avviso.official) {
            val triangolo = Path().apply {
                moveTo(w * 0.5f, w * 0.06f)
                lineTo(w * 0.96f, w * 0.90f)
                lineTo(w * 0.04f, w * 0.90f)
                close()
            }
            drawPath(triangolo, colore)
            punto = colore.onColor()
            drawLine(punto, Offset(w * 0.5f, w * 0.38f), Offset(w * 0.5f, w * 0.60f), strokeWidth = tratto, cap = StrokeCap.Round)
            drawCircle(punto, radius = tratto * 0.62f, center = Offset(w * 0.5f, w * 0.75f))
        } else {
            drawCircle(colore, radius = w * 0.44f, style = Stroke(width = tratto))
            punto = colore
            drawLine(punto, Offset(w * 0.5f, w * 0.28f), Offset(w * 0.5f, w * 0.56f), strokeWidth = tratto, cap = StrokeCap.Round)
            drawCircle(punto, radius = tratto * 0.62f, center = Offset(w * 0.5f, w * 0.72f))
        }
    }
}

/**
 * I colori del sistema di allertamento: giallo, arancione, rosso.
 *
 * Solo per gli avvisi di un ente. Tenuti riconoscibili e non saturi fino al
 * neon, come il resto dell'app; il testo sopra lo sceglie [onColor].
 */
private fun coloreLivello(livello: AlertLevel): Color = when (livello) {
    AlertLevel.GIALLA -> Color(0xFFF4C542)
    AlertLevel.ARANCIONE -> Color(0xFFEE8A2A)
    AlertLevel.ROSSA -> Color(0xFFD23C33)
}

private fun inCorso(avviso: WeatherAlert, adesso: LocalDateTime): Boolean {
    val iniziato = avviso.onset?.let { !adesso.isBefore(it) } ?: true
    val nonFinito = avviso.expires?.let { !adesso.isAfter(it) } ?: true
    return iniziato && nonFinito
}

/**
 * Da quando a quando, detto come lo si direbbe: "Oggi, dalle 10:00 alle
 * 17:59", "Dalle 10:00 di oggi alle 01:59 di domani", "Domani, per tutta la
 * giornata".
 */
internal fun finestra(avviso: WeatherAlert, oggi: LocalDate): String {
    val inizio = avviso.onset
    val fine = avviso.expires
    fun giorno(d: LocalDate): String = when (d) {
        oggi -> "oggi"
        oggi.plusDays(1) -> "domani"
        else -> "${d.dayOfWeek.italiano()} ${d.dayOfMonth}"
    }
    fun ora(t: LocalDateTime): String = String.format(Locale.ITALY, "%02d:%02d", t.hour, t.minute)
    fun maiuscola(s: String): String = s.replaceFirstChar { it.uppercase() }

    if (inizio != null && fine != null && inizio.toLocalDate() == fine.toLocalDate()) {
        val tuttoIlGiorno = inizio.toLocalTime() == LocalTime.MIDNIGHT && !fine.toLocalTime().isBefore(LocalTime.of(23, 59))
        val quale = maiuscola(giorno(inizio.toLocalDate()))
        return if (tuttoIlGiorno) "$quale, per tutta la giornata" else "$quale, dalle ${ora(inizio)} alle ${ora(fine)}"
    }
    return when {
        inizio != null && fine != null ->
            "Dalle ${ora(inizio)} di ${giorno(inizio.toLocalDate())} alle ${ora(fine)} di ${giorno(fine.toLocalDate())}"
        fine != null -> "Fino alle ${ora(fine)} di ${giorno(fine.toLocalDate())}"
        inizio != null -> "Dalle ${ora(inizio)} di ${giorno(inizio.toLocalDate())}"
        else -> "La fonte non dice fino a quando vale"
    }
}
