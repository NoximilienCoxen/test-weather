package io.github.noximiliencoxen.caelum.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.common.MeteoIconButton
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

/**
 * I due pezzi della testata che non sono testo.
 *
 * **Stavano in `HomeScreen.kt`**, che non c'e' piu': la prima scheda e' diventata
 * la prima sezione della colonna, e la sua intestazione e' salita in cima al
 * feed dove vale per tutte e sei. Questi due la seguono, perche' e' li' che
 * lavorano.
 */

/**
 * Il pulsante delle impostazioni: tre righe, disegnate.
 *
 * Il colore arriva per parametro e non da `LocalMeteoColors`, ed e' l'eccezione
 * che la scena dipinta impone: sopra un'immagine il contrasto non si calcola dal
 * fondo, lo garantisce la velatura della testata. Ovunque altro si passa il
 * colore del tema e si torna alla regola.
 */
@Composable
internal fun SettingsButton(tint: Color, onClick: () -> Unit) {
    MeteoIconButton(onClick = onClick, contentDescription = "Apri le impostazioni") {
        Canvas(Modifier.size(16.dp)) {
            val gap = size.height / 3f
            for (i in 0 until 3) {
                val y = gap * (i + 0.5f)
                drawLine(
                    color = tint,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = size.height * 0.10f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * Quanto e' vecchio il dato, in parole, e nullo finche' e' fresco.
 *
 * L'orologio batte ogni mezzo minuto ma **scrive solo quando la frase cambia**:
 * un valore uguale al precedente non ricompone niente, quindi una schermata
 * aperta a lungo non paga una ricomposizione ogni trenta secondi per riscrivere
 * la stessa parola.
 */
@Composable
internal fun rememberFreshness(fetchedAt: LocalDateTime?): String? {
    var label by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(fetchedAt) {
        while (true) {
            val next = freshnessOf(fetchedAt, LocalDateTime.now())
            if (next != label) label = next
            delay(FRESHNESS_TICK_MS)
        }
    }
    return label
}

internal fun freshnessOf(fetchedAt: LocalDateTime?, now: LocalDateTime): String? {
    if (fetchedAt == null) return null
    val minutes = Duration.between(fetchedAt, now).toMinutes()
    return when {
        minutes < STALE_MINUTES -> null
        minutes < 120L -> "$minutes MIN FA"
        else -> "${minutes / 60L} H FA"
    }
}

/** Sotto questa eta' il dato si considera fresco e non lo si dichiara. */
private const val STALE_MINUTES = 30L

private const val FRESHNESS_TICK_MS = 30_000L
