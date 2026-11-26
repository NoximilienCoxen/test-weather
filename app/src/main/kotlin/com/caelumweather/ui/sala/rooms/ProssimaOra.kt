package com.caelumweather.ui.sala.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caelumweather.data.QuartoDOra
import com.caelumweather.data.Wmo
import com.caelumweather.lingua.tr
import com.caelumweather.ui.UiState
import com.caelumweather.ui.sala.SalaPalette
import com.caelumweather.ui.sala.SalaTokens
import com.caelumweather.ui.sala.SalaType
import com.caelumweather.ui.sala.misuraCheArriva
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Cosa dice la riga "prossima ora": la frase, e un valore per ogni quarto
 * d'ora delle prossime due ore (millimetri, e se e' bagnato).
 */
internal data class RaccontoProssimaOra(
    val frase: String,
    val millimetri: List<Double>,
    val bagnati: List<Boolean>,
)

/**
 * **La pioggia dei prossimi minuti, in una frase.** Nulla se nelle prossime
 * due ore non piove: allora la riga non compare, e la scheda non spende spazio
 * per dire che non succede niente - lo dice gia' il titolo.
 *
 * Un quarto d'ora e' bagnato con almeno un decimo di millimetro e un codice
 * di precipitazione (o un temporale): sotto, e' l'umido che i modelli
 * spargono dappertutto, e una riga che annunciasse "pioggia fra 15 minuti"
 * a ogni goccia insegnerebbe a ignorarla.
 */
internal fun raccontoProssimaOra(quarti: List<QuartoDOra>, adesso: LocalDateTime): RaccontoProssimaOra? {
    // I quarti che toccano le prossime due ore, compreso quello in corso.
    val finestra = quarti.filter { it.inizio.plusMinutes(15).isAfter(adesso) }.take(8)
    if (finestra.size < 4) return null
    val bagnati = finestra.map(::bagnato)
    if (bagnati.none { it }) return null
    val mm = finestra.map { it.millimetri ?: 0.0 }
    val neve = finestra.any { q -> q.codice?.let { Wmo.family(it) == Wmo.Family.NEVE } == true }
    val cosa = if (neve) tr("Neve", "Snow", "Schnee", "Neige", "Nieve") else tr("Pioggia", "Rain", "Regen", "Pluie", "Lluvia")
    val ora = DateTimeFormatter.ofPattern("HH:mm")

    val frase = if (bagnati.first()) {
        val smette = bagnati.indexOfFirst { !it }
        if (smette < 0) {
            tr("$cosa adesso, per le prossime due ore", "$cosa now, for the next two hours", "$cosa jetzt, die nächsten zwei Stunden", "$cosa maintenant, pour les deux prochaines heures", "$cosa ahora, durante las próximas dos horas")
        } else {
            val quando = finestra[smette].inizio.format(ora)
            tr("$cosa adesso, smette verso le $quando", "$cosa now, stopping around $quando", "$cosa jetzt, hört gegen $quando auf", "$cosa maintenant, s'arrête vers $quando", "$cosa ahora, para hacia las $quando")
        }
    } else {
        val primo = bagnati.indexOfFirst { it }
        val minuti = Duration.between(adesso, finestra[primo].inizio).toMinutes().coerceAtLeast(5)
        // Arrotondati a cinque: "fra 17 minuti" promette una precisione che un
        // modello a quarti d'ora non ha.
        val tondi = ((minuti + 2) / 5 * 5).coerceAtLeast(5)
        val intensita = intensita(mm.drop(primo).take(4).maxOrNull() ?: 0.0)
        tr(
            "$cosa $intensita fra $tondi minuti",
            "${intensita.replaceFirstChar { it.uppercase() }} ${cosa.lowercase()} in $tondi minutes",
            "${intensita.replaceFirstChar { it.uppercase() }}er ${cosa} in $tondi Minuten",
            "$cosa $intensita dans $tondi minutes",
            "$cosa $intensita en $tondi minutos",
        )
    }
    return RaccontoProssimaOra(frase, mm, bagnati)
}

private fun bagnato(q: QuartoDOra): Boolean {
    val codice = q.codice
    if (codice != null && codice >= 95) return true
    return (q.millimetri ?: 0.0) >= 0.1 && (codice == null || codice >= 51)
}

/** Millimetri in un quarto d'ora, detti a parole sulla scala oraria consueta. */
private fun intensita(mmQuarto: Double): String {
    val orari = mmQuarto * 4
    return when {
        orari >= 7.6 -> tr("forte", "heavy", "stark", "forte", "fuerte")
        orari >= 2.5 -> tr("moderata", "moderate", "mäßig", "modérée", "moderada")
        else -> tr("debole", "light", "leicht", "faible", "débil")
    }
}

/**
 * La riga "prossima ora" sotto le celle di Oggi: la frase a sinistra, otto
 * colonnine a destra, una per quarto d'ora. Solo per oggi e all'ora di adesso
 * - sul giorno dopo, o spostata la barra, "fra 20 minuti" non vorrebbe dire
 * niente - e solo se piove o pioverà entro due ore.
 */
@Composable
internal fun ProssimaOra(state: UiState, palette: SalaPalette, modifier: Modifier = Modifier) {
    if (state.selectedDay != 0 || state.selectedHour != state.nowIndex) return
    val adesso = state.forecast?.nowThere() ?: state.quartiAdesso ?: return
    val racconto = remember(state.quarti, adesso.withSecond(0).withNano(0)) { raccontoProssimaOra(state.quarti, adesso) } ?: return
    ProssimaOraVista(racconto, palette, modifier)
}

/** Il disegno della riga, separato dal conto: lo misurano anche le prove. */
@Composable
internal fun ProssimaOraVista(racconto: RaccontoProssimaOra, palette: SalaPalette, modifier: Modifier = Modifier) {
    val massimo = (racconto.millimetri.maxOrNull() ?: 0.0).coerceAtLeast(0.6)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.chip)
            .semantics { contentDescription = racconto.frase }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = racconto.frase,
            style = SalaType.rowNote,
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier.height(20.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            racconto.millimetri.forEachIndexed { i, mm ->
                val bagnato = racconto.bagnati[i]
                val quota = if (bagnato) (mm / massimo).coerceIn(0.2, 1.0).toFloat() else 0f
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(misuraCheArriva((3f + quota * 17f).dp))
                        .clip(CircleShape)
                        .background(
                            if (bagnato) {
                                Brush.verticalGradient(listOf(SalaTokens.acquaChiara, SalaTokens.acquaScura))
                            } else {
                                Brush.verticalGradient(listOf(palette.maniglia, palette.maniglia))
                            },
                        ),
                )
            }
        }
    }
}
