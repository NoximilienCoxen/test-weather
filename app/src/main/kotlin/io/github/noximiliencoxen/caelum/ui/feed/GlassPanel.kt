package io.github.noximiliencoxen.caelum.ui.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.theme.CONTRAST_AA
import io.github.noximiliencoxen.caelum.ui.theme.CONTRAST_AA_LARGE
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.contrastRatio
import io.github.noximiliencoxen.caelum.ui.theme.MeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.mutedOnBoth
import io.github.noximiliencoxen.caelum.ui.theme.readableOn
import io.github.noximiliencoxen.caelum.ui.theme.readableOnBoth
import kotlin.math.min

/**
 * La scheda traslucida che si appoggia sopra il bordo di sotto della cornice.
 *
 * **Traslucida e basta: niente sfocatura.** Due ragioni, e nessuna delle due e'
 * il gusto. `minSdk` e' 26 e `Modifier.blur` non fa niente sotto la 31, quindi
 * meta' dei telefoni vedrebbe comunque il ripiego - cioe' esattamente quello che
 * si disegna qui - e l'altra meta' vedrebbe una scheda diversa. E soprattutto
 * c'e' una misura gia' pagata: `WindowGlass.kt` racconta che **un solo
 * rettangolo traslucido a piena larghezza** aveva portato un fotogramma da 18 a
 * 36 millisecondi con il settanta per cento di scatti. Un vero vetro smerigliato
 * vorrebbe ridisegnare la cornice una seconda volta dentro un livello sfocato, e
 * dentro la cornice c'e' la finestra della pioggia, che e' la tela piu' cara
 * dell'app. Il velo si paga in una passata di composizione; la sfocatura si
 * pagherebbe a ogni fotogramma.
 *
 * ## Il vetro riprovvede i colori
 *
 * E' la decisione che tiene in piedi l'intero stile, e va capita prima di
 * toccare questo file.
 *
 * La scheda e' **pallida a qualunque ora**: bianco al quarantacinque per cento
 * sopra il cielo di mezzanotte da' comunque un grigio medio-chiaro. Ma i colori
 * del feed sono tarati sul cielo, dove di notte `text` e' quasi bianco: scritto
 * sul vetro sparirebbe, ed e' il difetto a 1,01:1 della sezione 8-bis di
 * CONTESTO che rientra dalla porta di servizio ogni volta che nasce una
 * superficie nuova.
 *
 * La strada corta sarebbe passare tinte nuove a mano a ogni figlio. Ma i figli
 * sono la barra delle ore, la striscia della settimana, la fascia delle
 * ventiquattro ore e la barra delle metriche - quattro file, una quindicina di
 * punti in cui si legge `LocalMeteoColors`, e dimenticarne uno e' la regola, non
 * l'eccezione: e' la stessa ragione per cui `skyColors` corregge il contrasto
 * **alla sorgente e non nei chiamanti**.
 *
 * Quindi qui il vetro **fornisce al proprio sottoalbero una `MeteoColors`
 * derivata**, costruita con la stessa ricetta di `skyColors` ma contro i due
 * capi del vetro invece che contro i due capi del cielo. Ogni figlio continua a
 * leggere `LocalMeteoColors` come ha sempre fatto ed esce corretto da solo, e
 * `rememberSkyAccents()` chiamata dentro la scheda si ritara sulla scheda.
 *
 * **Il vincolo da rispettare** e' scritto in `RainWindow.kt`: `LocalMeteoColors`
 * e' `staticCompositionLocalOf`, e riprovvederlo invalida tutto il sottoalbero.
 * Il valore derivato sta percio' dietro un `remember` sul cielo: cambia quando
 * cambia l'ora - un pugno di volte al giorno - e **non** a ogni ora scorsa sulla
 * fascia. Chi lo ricalcolasse nel corpo della composizione rifarebbe la barra
 * delle ore a ogni fotogramma del dito.
 */
@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ink = rememberGlassInk()
    val shape = RoundedCornerShape(GLASS_CORNER)
    CompositionLocalProvider(LocalMeteoColors provides ink.colors) {
        Column(
            modifier = modifier
                .clip(shape)
                .drawBehind {
                    // **Il bordo di sopra entra da trasparente.** Sul cielo il
                    // velo si posava su una tinta e il salto non si vedeva;
                    // sopra una scena dipinta scura il velo cede - deve, o il
                    // testo non si legge - e il risultato era una lastra di
                    // grigio chiaro appoggiata sopra il quadro, con uno scalino
                    // netto lungo tutto il bordo. Abbassare l'opacita'
                    // riporterebbe il difetto di contrasto; farla **entrare** no,
                    // perche' il testo comincia comunque sotto la fascia.
                    val fade = FADE.toPx().coerceAtMost(size.height)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, ink.veilTop),
                            startY = 0f,
                            endY = fade,
                        ),
                        size = Size(size.width, fade),
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(ink.veilTop, ink.veilBottom),
                            startY = fade,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, fade),
                        size = Size(size.width, size.height - fade),
                    )
                }
                .padding(top = GLASS_TOP_PAD, bottom = GLASS_BOTTOM_PAD),
            content = content,
        )
    }
}

/**
 * Il vetro come lo si dipinge e i colori che ci si scrivono sopra, insieme.
 *
 * **Vanno insieme perche' il velo puo' cedere**, e se cedesse solo per il conto
 * del contrasto si dipingerebbe un vetro diverso da quello su cui il testo e'
 * stato tarato. Vedi [onGlass].
 */
@Immutable
internal class GlassInk(
    /** La palette da fornire al sottoalbero: i due capi qui dentro sono il vetro. */
    val colors: MeteoColors,
    /** I due capi del velo, **traslucidi**, con le opacita' che il conto ha scelto. */
    val veilTop: Color,
    val veilBottom: Color,
)

/**
 * I colori da usare **sopra** il vetro, ricavati e non scelti.
 *
 * Stessa ricetta di `skyColors`, applicata ai due capi del vetro: il testo si
 * spinge finche' regge la soglia su entrambi, l'etichetta gli sta un gradino
 * sotto senza mai scendere sotto la soglia, la linea e' un segno e le basta la
 * soglia del testo grande.
 *
 * I due capi si compositano sullo **zenit e sull'orizzonte**, non sul solo
 * orizzonte che e' il cielo che sta davvero dietro la scheda. E' la scelta
 * conservativa, e su una scheda alta un quarto di schermo non costa quasi
 * niente: i due composti cadono vicini, e il testo che regge il peggiore regge
 * anche l'altro.
 *
 * Costa una manciata di elevamenti a potenza per tinta, quindi vive dentro un
 * `remember` sul cielo. Vedi il KDoc di [GlassPanel] per il perche' non basta
 * ricalcolarlo dove serve.
 */
@Composable
internal fun rememberGlassInk(): GlassInk {
    val colors = LocalMeteoColors.current
    return remember(colors) { colors.onGlass() }
}

/**
 * **Quando nessun colore di testo regge il vetro, e' il velo a cedere.**
 *
 * E' la stessa medicina di `legibleSky`, applicata a una superficie invece che
 * al cielo, e il meccanismo del difetto e' identico: sotto un testo solo ci sono
 * due colori diversi, e quando uno sta sopra e l'altro sotto la luminanza di
 * mezzo il bianco perde in cima e il nero perde in fondo. Una terza risposta non
 * esiste, quindi a cedere dev'essere qualcos'altro.
 *
 * Qui l'altro e' l'opacita' del velo. Un velo chiaro **piu' denso** spinge i due
 * capi del vetro verso il bianco e li avvicina fra loro, finche' il nero li
 * regge tutti e due. Misurato su una griglia di ottocentoquarantacinque coppie
 * zenit/orizzonte - tutte quelle su cui un testo esiste gia' sul cielo nudo,
 * cioe' quelle che `legibleSky` lascia passare:
 *
 * | | |
 * |---|---|
 * | col velo di progetto vanno gia' bene | 83% |
 * | passi di cedimento necessari, al massimo | 2 su 8 |
 * | coppie irrisolvibili anche a velo pieno | nessuna |
 *
 * Quindi nella grande maggioranza dei casi la scheda ha esattamente l'opacita'
 * che le e' stata disegnata, e nei pochi in cui non basta si fa piu' densa di un
 * quarto invece di lasciare una scritta illeggibile. **La leggibilita' non e'
 * negoziabile, l'opacita' si.**
 */
internal fun MeteoColors.onGlass(): GlassInk {
    var veilTop = GLASS_TOP
    var veilBottom = GLASS_BOTTOM
    // I due capi vanno **opachi**: `readableOnBoth` misura la luminanza, e un
    // colore con trasparenza non ne ha una propria.
    var top = veilTop.compositeOver(skyZenith)
    var bottom = veilBottom.compositeOver(skyHorizon)

    for (step in 1..VEIL_STEPS) {
        if (bothPolesBest(top, bottom) >= CONTRAST_AA) break
        val t = step.toFloat() / VEIL_STEPS
        veilTop = GLASS_TOP.copy(alpha = GLASS_TOP.alpha + (1f - GLASS_TOP.alpha) * t)
        veilBottom = GLASS_BOTTOM.copy(alpha = GLASS_BOTTOM.alpha + (1f - GLASS_BOTTOM.alpha) * t)
        top = veilTop.compositeOver(skyZenith)
        bottom = veilBottom.compositeOver(skyHorizon)
    }

    val ink = text.readableOnBoth(top, bottom)
    val flat = lerp(top, bottom, 0.55f)
    val quiet = ink.mutedOnBoth(top, bottom)
    val rule = lerp(ink, flat, 0.55f).readableOnBoth(top, bottom, CONTRAST_AA_LARGE)

    return GlassInk(
        colors = copy(
            // `background`, `skyZenith` e `skyHorizon` diventano il vetro: chi
            // dentro la scheda si calcola una tinta contro "il fondo" - e
            // `skyAccents()` lo fa - deve trovare la scheda, non il cielo dietro.
            background = flat,
            skyZenith = top,
            skyHorizon = bottom,
            text = ink,
            label = quiet,
            line = rule,
            // La bolla sopra il cursore della barra delle ore: resta un chip
            // scuro con dentro una scritta chiara, che sul vetro pallido e'
            // l'unico verso che stacchi. E' la coppia di `skyColors` rovesciata.
            pillBackground = ink,
            // Il tono della scheda dentro la bolla, **ma garantito**: `flat` sta
            // fra i due capi del vetro, e in un cielo dai capi molto distanti
            // potrebbe cadere abbastanza vicino all'inchiostro da rendere la
            // bolla una macchia con dentro un'ombra. `readableOn` lo lascia dov'e'
            // quando gia' basta, e lo spinge solo quando no.
            pillText = flat.readableOn(ink),
            // **Il bianco della nuvola va spinto anche lui**, e non e' una
            // rifinitura opzionale: la fascia delle ventiquattro ore disegna in
            // `cloudCore` le ore di neve, per distinguerle dalla pioggia. Sul
            // cielo e' bianco pieno e si vede; su un vetro pallido sarebbe
            // **bianco su bianco**, cioe' le ore di neve sparirebbero dal
            // grafico senza che niente lo dica. Gli basta la soglia del segno
            // grande: e' una colonna, non una scritta.
            cloudCore = cloudCore.readableOnBoth(top, bottom, CONTRAST_AA_LARGE),
        ),
        veilTop = veilTop,
        veilBottom = veilBottom,
    )
}

/**
 * Il meglio che bianco e nero sanno fare sul capo peggiore dei due.
 *
 * E' la domanda che [readableOnBoth] si fa per decidere se arrendersi, chiesta
 * **prima** di chiamarla: se nemmeno un polo arriva alla soglia, spingere una
 * tinta non serve a niente e a doversi muovere e' il fondo.
 */
private fun bothPolesBest(first: Color, second: Color): Float =
    maxOf(
        min(Color.White.contrastRatio(first), Color.White.contrastRatio(second)),
        min(Color.Black.contrastRatio(first), Color.Black.contrastRatio(second)),
    )

/**
 * Il velo, dall'alto verso il basso.
 *
 * Sfumato e non piatto: una scheda a opacita' costante sopra un cielo sfumato si
 * legge come un adesivo, perche' il salto col fondo e' identico su tutta
 * l'altezza. Piu' densa in cima - dove si sovrappone alla cornice e deve
 * staccarla - e piu' sottile in fondo, dove sotto c'e' solo il bordo dello
 * schermo.
 */
internal val GLASS_TOP = Color.White.copy(alpha = 0.45f)
internal val GLASS_BOTTOM = Color.White.copy(alpha = 0.33f)

/** Di quanti passi il velo puo' farsi piu' denso prima di arrendersi. */
private const val VEIL_STEPS = 8

/** Quanto e' alta la fascia in cui il velo entra da trasparente. */
private val FADE = 22.dp

private val GLASS_CORNER = 20.dp

/**
 * Il margine di sopra della scheda.
 *
 * Valeva quanto la sovrapposizione, quando la scheda si appoggiava sopra il
 * bordo di una cornice e nei primi punti aveva sotto un disegno invece del
 * cielo. La cornice non c'e' piu' - le schede sono blocchi di una colonna - e
 * qui torna a essere quello che e': un margine.
 */
/**
 * Il margine di sopra, che deve stare **sotto la fascia sfumata**.
 *
 * Dentro [FADE] il velo non e' ancora pieno, quindi li' il conto del contrasto
 * non vale: una riga di testo in quella fascia starebbe su un fondo piu' sottile
 * di quello su cui e' stata tarata.
 */
private val GLASS_TOP_PAD = 26.dp
private val GLASS_BOTTOM_PAD = 10.dp


