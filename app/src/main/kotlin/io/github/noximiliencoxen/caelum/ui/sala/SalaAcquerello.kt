package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.noximiliencoxen.caelum.R
import io.github.noximiliencoxen.caelum.ui.render3d.Camera
import kotlin.math.roundToInt

/**
 * L'acquerello di Sala: **forme vive, materia dipinta**.
 *
 * Il concept non e' fatto di cerchi pieni, e i cerchi pieni infatti non gli
 * somigliavano. Un acquerello si riconosce da tre cose che un `drawCircle` non
 * ha: il bordo **spostato** dall'acqua invece che netto, il pigmento che si
 * **accumula sul filo** di quel bordo, e la **granulazione** dentro il lavaggio.
 * Nessuna delle tre si calcola: si dipingono.
 *
 * Da qui la divisione. La **composizione** resta viva e la fa il codice - dove
 * stanno le masse, quanto sono grandi, come girano, di che colore sono all'ora
 * mostrata - mentre la **materia** arriva da timbri dipinti che il codice
 * ritinge. Cosi' la scultura continua a ruotare e a cambiare col tempo, ma ha
 * addosso un acquerello vero invece di una tinta piatta.
 *
 * **Dei timbri conta solo l'alfa.** Sono bianchi su trasparente e vengono
 * ritinti con la tavolozza della sala: il colore dipinto dentro il file viene
 * buttato via. Chi li ridipinge non deve preoccuparsi della tinta, solo della
 * forma e della densita'.
 *
 * I file di adesso sono **provvisori**, generati da `scripts/texture_acquerello.py`
 * perche' la catena - carica, tinge, timbra - fosse viva e fotografabile prima
 * che qualcuno aprisse Blender. Sostituirli e' una copia di file: nessuna riga
 * di Kotlin cambia.
 *
 * **Niente sfocatura a runtime, e non e' una scorciatoia**: `Modifier.blur` e'
 * API 31, il minimo di questo progetto e' 26. Il bordo bagnato deve *essere
 * dipinto*, non calcolato - il che poi e' anche il modo giusto di ottenerlo.
 */
@Immutable
class Acquerello(
    val macchie: List<ImageBitmap>,
    val disco: ImageBitmap,
    val pennellate: List<ImageBitmap>,
    val ombra: ImageBitmap,
    val carta: ImageBitmap,
)

/**
 * I timbri, caricati **una volta sola per tutta Sala**.
 *
 * Non e' un vezzo architetturale, e' memoria: `SalaBackground` sta dentro ogni
 * sala, e le sette sale del carosello sono composizioni separate. Chiamando li'
 * il caricamento, le stesse tredici immagini verrebbero decodificate sette
 * volte - una decina di megabyte per giro, settanta in tutto, per disegnare i
 * medesimi pixel. `SalaShell` le carica e le passa di qui.
 */
val LocalAcquerello = staticCompositionLocalOf<Acquerello> {
    error("Acquerello non fornito: le sale vanno avvolte da SalaShell")
}

@Composable
fun rememberAcquerello(): Acquerello = Acquerello(
    macchie = listOf(
        ImageBitmap.imageResource(R.drawable.sala_macchia_1),
        ImageBitmap.imageResource(R.drawable.sala_macchia_2),
        ImageBitmap.imageResource(R.drawable.sala_macchia_3),
        ImageBitmap.imageResource(R.drawable.sala_macchia_4),
    ),
    disco = ImageBitmap.imageResource(R.drawable.sala_disco),
    pennellate = listOf(
        ImageBitmap.imageResource(R.drawable.sala_pennellata_1),
        ImageBitmap.imageResource(R.drawable.sala_pennellata_2),
        ImageBitmap.imageResource(R.drawable.sala_pennellata_3),
        ImageBitmap.imageResource(R.drawable.sala_pennellata_4),
        ImageBitmap.imageResource(R.drawable.sala_pennellata_5),
        ImageBitmap.imageResource(R.drawable.sala_pennellata_6),
    ),
    ombra = ImageBitmap.imageResource(R.drawable.sala_ombra),
    carta = ImageBitmap.imageResource(R.drawable.sala_carta),
)

/** Timbra un'immagine centrata, ritinta, scalata. L'unico modo in cui si disegna qui. */
fun DrawScope.timbra(
    timbro: ImageBitmap,
    centro: Offset,
    larghezza: Float,
    altezza: Float,
    tinta: Color,
    alfa: Float = 1f,
) {
    val w = larghezza.roundToInt()
    val h = altezza.roundToInt()
    if (w <= 0 || h <= 0) return
    drawImage(
        image = timbro,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(timbro.width, timbro.height),
        dstOffset = IntOffset(
            (centro.x - larghezza / 2f).roundToInt(),
            (centro.y - altezza / 2f).roundToInt(),
        ),
        dstSize = IntSize(w, h),
        alpha = alfa.coerceIn(0f, 1f),
        colorFilter = ColorFilter.tint(tinta),
    )
}

/**
 * La grana della carta, moltiplicata sopra tutto.
 *
 * Moltiplicata e non sovrapposta: la grana **toglie** luce dove la fibra e'
 * in rilievo, non aggiunge grigio sopra. Tenuta bassissima - su un fondo chiaro
 * una grana che si nota non fa carta, fa sporco.
 */
fun DrawScope.granaDiCarta(acquerello: Acquerello, forza: Float = 0.10f) {
    val lato = maxOf(size.width, size.height)
    drawImage(
        image = acquerello.carta,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(acquerello.carta.width, acquerello.carta.height),
        dstOffset = IntOffset(((size.width - lato) / 2f).roundToInt(), ((size.height - lato) / 2f).roundToInt()),
        dstSize = IntSize(lato.roundToInt(), lato.roundToInt()),
        alpha = forza.coerceIn(0f, 1f),
        blendMode = BlendMode.Multiply,
    )
}

/**
 * Le masse della nuvola, **in tre dimensioni vere**.
 *
 * Sono le stesse sette di `WeatherSculpture.CLOUD_MASSES`, con la stessa
 * ragione dietro: sparse anche in profondita', se no ruotando la nuvola si
 * rivela un ritaglio di cartone. Le ultime due sono del temporale - larghe e
 * dietro, perche' un temporale e' un fronte, non una nuvola piu' fitta.
 */
private val MasseNuvola = listOf(
    floatArrayOf(-0.26f, 0.02f, 0.16f, 0.19f),
    floatArrayOf(0.00f, -0.09f, -0.06f, 0.25f),
    floatArrayOf(0.26f, 0.03f, 0.12f, 0.20f),
    floatArrayOf(-0.11f, 0.10f, -0.19f, 0.18f),
    floatArrayOf(0.15f, 0.11f, -0.14f, 0.17f),
    floatArrayOf(-0.42f, -0.04f, -0.24f, 0.21f),
    floatArrayOf(0.44f, -0.02f, -0.21f, 0.22f),
)
private const val MASSE_TEMPORALE = 2

/** Quanto le masse si compenetrano. Sotto 1,2 si leggono come dischi separati. */
private const val FUSIONE = 1.24f

/** Le tinte della scultura, prese dai token e non dall'inchiostro del testo. */
private fun tintaNuvola(palette: SalaPalette): Color =
    if (palette.dark) SalaTokens.accent400 else SalaTokens.accent

/** Di notte il disco e' la luna, e la luna non e' gialla. */
private fun tintaSole(notte: Boolean): Color =
    if (notte) SalaTokens.neutral200 else SalaTokens.processYellow

private fun tintaPioggia(palette: SalaPalette): Color =
    if (palette.dark) SalaTokens.accent300 else SalaTokens.accent700

/**
 * La scultura di Sala I: disco, masse, pioggia, ombra.
 *
 * @param giroDeg quanto e' girata attorno alla verticale. E' il dito, e basta
 *   quello: l'asse verticale in Sala e' del carosello fra le sale, e contenderlo
 *   riaccenderebbe la trappola #5 per un tocco decorativo.
 */
fun DrawScope.scultura(
    acquerello: Acquerello,
    condition: SalaCondition,
    palette: SalaPalette,
    notte: Boolean,
    giroDeg: Float,
) {
    // L'unita' si misura sulla **larghezza**, non sul lato corto: la scultura
    // deve occupare la cassa come nel concept, e prendendo il minimo restava un
    // francobollo in mezzo a una pagina vuota.
    val unita = size.width * 0.80f
    val camera = Camera(
        yawDeg = giroDeg,
        pitchDeg = 0f,
        distance = unita * 2.6f,
        origin = Offset(size.width * 0.52f, size.height * 0.46f),
    )

    val coperto = condition != SalaCondition.SERENO
    val temporale = condition == SalaCondition.TEMPORALE ||
        condition == SalaCondition.TEMPORALE_GRANDINE
    val bagnato = condition == SalaCondition.PIOGGIA ||
        condition == SalaCondition.GRANDINE || temporale

    // ── L'ombra portata, per prima: sta sotto tutto ──────────────────────────
    timbra(
        timbro = acquerello.ombra,
        centro = Offset(size.width * 0.52f, size.height * 0.82f),
        larghezza = unita * 1.15f,
        altezza = unita * 0.26f,
        tinta = if (palette.dark) Color.Black else SalaTokens.neutral900,
        alfa = if (palette.dark) 0.22f else 0.13f,
    )

    // ── Il disco: dietro le masse quando c'e' nuvola, al centro quando e' sereno ─
    val discoRaggio = if (coperto) unita * 0.46f else unita * 0.66f
    camera.place(
        if (coperto) -0.30f * unita else 0f,
        if (coperto) -0.32f * unita else -0.04f * unita,
        0.30f * unita,
    )
    timbra(
        timbro = acquerello.disco,
        centro = Offset(camera.sx, camera.sy),
        larghezza = discoRaggio * 2f * camera.scale,
        altezza = discoRaggio * 2f * camera.scale,
        tinta = tintaSole(notte),
        alfa = if (coperto) 0.78f else 0.88f,
    )

    // ── Le masse, dalla piu' lontana alla piu' vicina ────────────────────────
    if (coperto) {
        val quante = if (temporale) MasseNuvola.size else MasseNuvola.size - MASSE_TEMPORALE
        val tinta = tintaNuvola(palette)
        // L'ordine di disegno e' la profondita': chi sta dietro va posato prima,
        // se no le sovrapposizioni si scuriscono nel verso sbagliato.
        val indici = (0 until quante).sortedByDescending { i ->
            camera.place(MasseNuvola[i][0] * unita, MasseNuvola[i][1] * unita, MasseNuvola[i][2] * unita)
            camera.vz
        }
        indici.forEachIndexed { posto, i ->
            val m = MasseNuvola[i]
            camera.place(m[0] * unita, m[1] * unita, m[2] * unita)
            // Il fattore allarga le masse fino a farle **compenetrare**. Ai
            // raggi nudi restavano dischi affiancati - si leggevano come bolle,
            // non come una nuvola sola.
            val d = m[3] * FUSIONE * unita * 2f * camera.scale
            timbra(
                timbro = acquerello.macchie[i % acquerello.macchie.size],
                centro = Offset(camera.sx, camera.sy),
                larghezza = d,
                altezza = d * 0.92f,
                tinta = tinta,
                // Le piu' lontane un filo piu' tenui: e' aria fra le due, ed e'
                // cio' che rende la sovrapposizione un volume invece di un mucchio.
                alfa = if (temporale) 0.62f else 0.52f - 0.06f * (1f - posto.toFloat() / quante),
            )
        }
    }

    // ── La pioggia: pennellate, non gocce ────────────────────────────────────
    if (bagnato) {
        val tinta = tintaPioggia(palette)
        val quanti = if (temporale) 8 else 6
        val larghezza = unita * 0.115f
        val altezza = unita * 0.42f
        for (i in 0 until quanti) {
            val fx = (i + 0.5f) / quanti
            camera.place((fx - 0.5f) * 1.05f * unita, 0.30f * unita, (if (i % 2 == 0) 0.10f else -0.12f) * unita)
            val sfalsa = if (i % 3 == 0) 0.10f else if (i % 3 == 1) 0f else 0.06f
            timbra(
                timbro = acquerello.pennellate[i % acquerello.pennellate.size],
                centro = Offset(camera.sx, camera.sy + (altezza * (0.5f + sfalsa))),
                larghezza = larghezza * camera.scale,
                altezza = altezza * camera.scale,
                tinta = tinta,
                alfa = 0.72f,
            )
        }
    }
}
