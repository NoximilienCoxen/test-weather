package com.forli.meteo.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.WeatherAlert
import com.forli.meteo.ui.common.MeteoCard
import com.forli.meteo.ui.common.MeteoDivider
import com.forli.meteo.ui.common.MeteoEmptyState
import com.forli.meteo.ui.common.MeteoTopBar
import com.forli.meteo.ui.common.rememberMeteoLayout
import java.time.format.DateTimeFormatter

/**
 * I bollettini per esteso.
 *
 * La fascia dice **che** c'e' un'allerta; qui si legge cosa dice davvero chi
 * l'ha emessa, con le sue parole. Il testo di un avviso della Protezione Civile
 * non si riassume in una riga senza perderci qualcosa, e riscriverlo sarebbe
 * peggio che riportarlo.
 *
 * **La fonte e' scritta in fondo a ogni avviso, sempre.** E' la sola cosa che
 * distingue un bollettino di un ente da una soglia superata, e quella
 * differenza appartiene a chi legge, non a chi scrive l'app.
 */
@Composable
fun AlertsSheet(
    alerts: List<WeatherAlert>,
    unavailable: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = rememberMeteoLayout()
    Column(modifier = modifier.fillMaxSize()) {
        MeteoTopBar(
            title = "ALLERTE",
            subtitle = alerts.firstOrNull()?.areaDesc?.uppercase(),
            onBack = onBack,
            backLabel = "Chiudi le allerte",
        )

        if (alerts.isEmpty()) {
            MeteoEmptyState(
                title = if (unavailable) "BOLLETTINI NON RAGGIUNGIBILI" else "NESSUNA ALLERTA",
                message = if (unavailable) {
                    "Il servizio di allertamento non risponde. Le soglie sui dati " +
                        "della previsione non hanno comunque superato nessun limite."
                } else {
                    "Per questa localita' non risultano avvisi in corso."
                },
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = layout.gutter),
            verticalArrangement = Arrangement.spacedBy(layout.gap),
        ) {
            // Un avviso ufficiale mancato si dice **sopra** gli altri: chi
            // legge deve sapere che quello che sta guardando potrebbe non
            // essere tutto, prima di leggerlo, non dopo.
            if (unavailable) {
                Text(
                    text = "I bollettini ufficiali non sono raggiungibili: " +
                        "qui sotto ci sono solo le allerte calcolate dai dati della previsione.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            alerts.forEach { alert -> AlertCard(alert) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AlertCard(alert: WeatherAlert, modifier: Modifier = Modifier) {
    MeteoCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = alert.level.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = alert.kind.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = alert.headline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            window(alert)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            alert.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            alert.instruction?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            MeteoDivider()

            Text(
                text = if (alert.official) {
                    listOfNotNull("Fonte: ${alert.source}", alert.areaDesc).joinToString("  ·  ")
                } else {
                    "Non e' un bollettino ufficiale. ${alert.source}."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Dalle 08:00 alle 20:00 di oggi", quando la fonte dichiara le due ore. */
private fun window(alert: WeatherAlert): String? {
    val from = alert.onset?.format(CLOCK)
    val to = alert.expires?.format(CLOCK)
    return when {
        from != null && to != null -> "Dalle $from alle $to"
        from != null -> "Dalle $from"
        to != null -> "Fino alle $to"
        else -> null
    }
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
