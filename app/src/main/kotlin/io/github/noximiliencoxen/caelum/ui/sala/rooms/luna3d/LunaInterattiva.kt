package io.github.noximiliencoxen.caelum.ui.sala.rooms.luna3d

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.motion.rememberDeviceTilt
import io.github.noximiliencoxen.caelum.ui.motion.rememberVibrazioniMeteo
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.widget.paint.render3d.Camera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * La luna in 3D, a cui si gira intorno col dito.
 *
 * **Il sole sta fermo, ci si muove noi.** Trascinando non si fa ruotare la
 * luna su se stessa - quella mostra sempre la stessa faccia alla Terra - ma si
 * sposta il punto da cui la si guarda. E allora si vede la cosa che le fasi
 * nascondono: la meta' illuminata e' sempre una meta', e la falce e' solo quel
 * poco che ne vediamo da qui. Lasciando il dito si torna a casa, alla vista
 * dalla Terra, cioe' alla fase vera di [fase].
 *
 * Tutto si disegna con una chiamata sola a `drawVertices`, un colore per
 * vertice: la sfera e' convessa, quindi basta scartare i triangoli girati di
 * spalle e non c'e' niente da ordinare.
 */
@Composable
internal fun LunaInterattiva(
    fase: Float,
    /** Come si chiama la fase e quanto e' illuminata, per chi non vede la sfera. */
    descrizione: String,
    palette: SalaPalette,
    movimento: Boolean,
    modifier: Modifier = Modifier,
) {
    val globo = remember { Globo() }
    val colori = remember(fase) { coloriPer(globo, fase) }
    val buffer = remember { BufferGlobo(globo) }
    // La carta si fa lontano dal filo principale, e intanto la sfera usa i
    // colori dei vertici: una luna un po' piu' morbida per un istante, invece
    // di un fotogramma saltato a ogni cambio di fase.
    //
    // **Col cursore che corre si aspetta.** Ogni giorno trascinato cambia la
    // fase e annulla l'attesa di quello prima: la carta si fa solo quando il
    // dito si ferma, e intanto i vertici bastano. Senza, ogni giorno ne
    // avviava una, e le vecchie - che non si fermavano - tenevano occupato il
    // telefono finche' non si lasciava il cursore.
    val tessitura by produceState<TessituraGlobo?>(initialValue = null, globo, fase) {
        delay(ATTESA_CARTA_MS)
        value = withContext(Dispatchers.Default) { TessituraGlobo.per(globo, fase) }
    }
    // L'albedo della carta si prepara appena la sala compare, una volta sola:
    // al primo arresto del cursore resta da fare solo la luce.
    LaunchedEffect(globo) {
        withContext(Dispatchers.Default) { globo.albedoCarta(LARGHEZZA_CARTA, LARGHEZZA_CARTA / 2) }
    }

    val yaw = remember { Animatable(0f) }
    val pitch = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val vibrazioni = rememberVibrazioniMeteo()

    // Solo un accenno: ±6 gradi. Serve a dire che e' un corpo e non un
    // disegno, non a spostarla mentre la si guarda.
    val inclinazione by rememberDeviceTilt(enabled = movimento, maxDegrees = 10f)

    val frazione by remember(fase) {
        derivedStateOf { Globo.frazioneIlluminataVista(fase, yaw.value, pitch.value, passi = 40) }
    }
    val daCasa by remember { derivedStateOf { abs(yaw.value) < 2f && abs(pitch.value) < 2f } }

    // Un colpetto quando, girandole intorno, la si vede piena: e' il momento in
    // cui si capisce dove sta il sole.
    LaunchedEffect(fase) {
        snapshotFlow { !daCasa && frazione > 0.97f }
            .distinctUntilChanged()
            .collect { piena -> if (piena && movimento) vibrazioni.scatto() }
    }

    fun tornaACasa() {
        scope.launch {
            // Al giro piu' vicino, non a zero: dopo due giri non deve svolgersi.
            val meta = (yaw.value / 360f).roundToInt() * 360f
            if (movimento) {
                launch { pitch.animateTo(0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow)) }
                yaw.animateTo(meta, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow))
            } else {
                pitch.snapTo(0f)
            }
            yaw.snapTo(0f)
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .size(220.dp)
                .semantics { contentDescription = "Luna, $descrizione. Trascina per girarle intorno." }
                .pointerInput(movimento) {
                    // Il gesto si consuma: con il dito sulla luna ne' le sale
                    // ne' lo scorrimento del pannello lo devono prendere.
                    // Fuori dalla sfera tutto resta com'era.
                    detectDragGestures(
                        onDragEnd = { tornaACasa() },
                        onDragCancel = { tornaACasa() },
                    ) { cambio, spinta ->
                        cambio.consume()
                        val gradiPerPixel = 180f / size.width
                        scope.launch {
                            yaw.snapTo(yaw.value - spinta.x * gradiPerPixel)
                            pitch.snapTo((pitch.value + spinta.y * gradiPerPixel).coerceIn(-75f, 75f))
                        }
                    }
                },
        ) {
            disegnaGlobo(
                globo = globo,
                colori = colori,
                buffer = buffer,
                yawDeg = yaw.value + inclinazione.x * 6f,
                pitchDeg = pitch.value + inclinazione.y * 6f,
                alone = Color(colori.alone).copy(alpha = 0.22f * frazione),
                tessitura = tessitura,
            )
        }

        Text(
            text = if (daCasa) {
                "Vista dalla Terra · trascina per girarle intorno"
            } else {
                "Da qui è illuminato il ${(frazione * 100f).roundToInt()} % del disco"
            },
            style = SalaType.body,
            color = palette.inkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** I colori dei vertici per una fase, e il tono dell'alone. */
internal class ColoriGlobo(val vertici: IntArray, val alone: Int)

/**
 * La tinta di ogni vertice: dal nero dello spazio al bianco caldo della luna
 * piena, passando per i grigi del bordo. Le tinte sono quelle di
 * [SalaTokens], fisse nei due temi: un corpo celeste non cambia colore con la
 * pagina che lo mostra.
 *
 * I vertici si usano ancora, ma solo per il primo fotogramma: finche' la
 * carta di [TessituraGlobo] non e' pronta, e nelle prove che disegnano senza.
 */
internal fun coloriPer(globo: Globo, fase: Float): ColoriGlobo {
    val luce = FloatArray(globo.vertici)
    globo.luce(fase, luce)
    val out = IntArray(globo.vertici) { v -> TintaLuna.di(luce[v], globo.albedo[v]) }
    return ColoriGlobo(out, SalaTokens.lunaLuce.toArgb())
}

/**
 * Da quanta luce e quanto albedo al colore, per vertici e carta allo stesso modo.
 *
 * **Con due tabelle e non con `lerp`**, e non per pignoleria: il `lerp` di
 * Compose passa per Oklab, e sulla carta si chiama mezzo milione di volte a
 * ogni fase. Le tabelle si fanno una volta, con lo stesso `lerp`, e dopo
 * resta una media fra due interi.
 */
internal object TintaLuna {
    private const val PASSI = 256
    private const val LUCE_MASSIMA = 1.1f

    private val ombra = Color(0xFF151A24)

    // I mari non sono solo piu' scuri: sono di un grigio piu' freddo degli
    // altipiani, e la differenza di tinta si legge anche dove quella di
    // chiarezza si perde - su uno schermo luminoso, sotto il sole.
    private val mare = Color(0xFF5E5F63)

    /** Il tono per la luce, da 0 a [LUCE_MASSIMA]. */
    private val toni = IntArray(PASSI) { k ->
        val b = LUCE_MASSIMA * k / (PASSI - 1)
        when {
            b < 0.40f -> lerp(ombra, SalaTokens.lunaBordo, b / 0.40f)
            b < 0.78f -> lerp(SalaTokens.lunaBordo, SalaTokens.lunaMezzo, (b - 0.40f) / 0.38f)
            else -> lerp(SalaTokens.lunaMezzo, SalaTokens.lunaLuce, ((b - 0.78f) / 0.25f).coerceAtMost(1f))
        }.toArgb()
    }

    /** Il colore del mare per quanto e' illuminato, da 0 a 1. */
    private val mari = IntArray(PASSI) { k -> lerp(ombra, mare, k / (PASSI - 1f)).toArgb() }

    fun di(luce: Float, albedo: Float): Int {
        val tono = toni[((luce / LUCE_MASSIMA).coerceIn(0f, 1f) * (PASSI - 1)).toInt()]
        // Quanto questo punto e' mare, da 0 a 1, e quanto e' illuminato.
        val quantoMare = ((0.85f - albedo) / 0.45f).coerceIn(0f, 1f) * 0.55f
        if (quantoMare <= 0f) return tono
        val acceso = (luce / albedo.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
        return misto(tono, mari[(acceso * (PASSI - 1)).toInt()], quantoMare)
    }

    private fun misto(a: Int, b: Int, t: Float): Int {
        fun canale(s: Int): Int {
            val x = a shr s and 0xFF
            val y = b shr s and 0xFF
            return (x + (y - x) * t).roundToInt() and 0xFF
        }
        return (0xFF shl 24) or (canale(16) shl 16) or (canale(8) shl 8) or canale(0)
    }
}

/**
 * La luna per una fase, dipinta su una carta e pronta da stendere sulla sfera.
 *
 * Si costruisce **fuori dal filo principale** (vedi [LunaInterattiva]): la
 * prima volta ci sono da fare mezzo milione di punti con i loro crateri
 * ([Globo.albedoCarta]), dalle volte dopo solo la luce.
 */
internal class TessituraGlobo private constructor(globo: Globo, private val immagine: Bitmap) {
    private val larghezza = immagine.width
    private val altezza = immagine.height

    companion object {
        /**
         * La carta per una fase, o niente se chi la chiedeva ha rinunciato a
         * meta': la luce si ferma a ogni riga, appena la coroutine e' annullata.
         */
        suspend fun per(globo: Globo, fase: Float, larghezza: Int = LARGHEZZA_CARTA): TessituraGlobo? {
            val altezza = larghezza / 2
            val luce = FloatArray(larghezza * altezza)
            val contesto = currentCoroutineContext()
            if (!globo.carta(fase, larghezza, altezza, luce, continua = { contesto.isActive })) return null
            val albedo = globo.albedoCarta(larghezza, altezza).albedo
            val pixel = IntArray(luce.size) { TintaLuna.di(luce[it], albedo[it]) }
            contesto.ensureActive()
            return TessituraGlobo(globo, Bitmap.createBitmap(pixel, larghezza, altezza, Bitmap.Config.ARGB_8888))
        }
    }

    /** Dove cade ogni vertice sulla carta, in pixel: le `texs` di `drawVertices`. */
    val coordinate = FloatArray(globo.vertici * 2).also { c ->
        for (v in 0 until globo.vertici) {
            c[v * 2] = globo.uCarta(v) * larghezza
            c[v * 2 + 1] = globo.vCarta(v) * altezza
        }
    }

    /**
     * Col filtro: fra un punto della carta e l'altro si interpola, e la luna
     * ingrandita non si sgrana. Nessun colore di vertice insieme alla carta:
     * e' la moltiplicazione che su alcuni telefoni faceva sparire i mari (vedi
     * [BufferGlobo.pennello]), e cosi' non c'e' niente da moltiplicare.
     */
    val pennello = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(immagine, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
}

/** Le posizioni sullo schermo e gli indici visibili: allocati una volta. */
internal class BufferGlobo(globo: Globo) {
    val posizioni = FloatArray(globo.vertici * 2)
    val profondita = FloatArray(globo.vertici)
    val visibili = ShortArray(globo.triangoli.size)
    // **Bianco, e detto.** Il pennello nasce nero, e su alcune versioni del
    // disegno accelerato i colori dei vertici vengono moltiplicati per il suo:
    // sull'emulatore la luna usciva giusta, su un telefono vero i mari
    // sparivano. Col bianco la moltiplicazione lascia i colori come sono,
    // ovunque.
    val pennello = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
}

/**
 * Proietta la sfera e la disegna, centrata e grande quanto la tela consente.
 *
 * Fuori dal composable perche' la usa anche la prova che la disegna su
 * un'immagine (`LunaRenderTest`).
 */
internal fun DrawScope.disegnaGlobo(
    globo: Globo,
    colori: ColoriGlobo,
    buffer: BufferGlobo,
    yawDeg: Float,
    pitchDeg: Float,
    alone: Color,
    /** La carta della fase, se e' pronta: senza, i colori dei vertici. */
    tessitura: TessituraGlobo? = null,
) {
    val r = size.minDimension * 0.40f
    val centro = Offset(size.width / 2f, size.height / 2f)
    val distanza = r * 5f
    val camera = Camera(yawDeg = yawDeg, pitchDeg = pitchDeg, distance = distanza, origin = centro)

    // L'alone viene prima e sta sotto: quanto e' forte lo dice quanta luna
    // accesa si vede, cosi' al novilunio non c'e'.
    if (alone.alpha > 0.01f) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(alone, alone.copy(alpha = 0f)),
                center = centro,
                radius = r * 1.25f,
            ),
            radius = r * 1.25f,
            center = centro,
        )
    }

    for (v in 0 until globo.vertici) {
        camera.place(globo.x[v] * r, globo.y[v] * r, globo.z[v] * r)
        buffer.posizioni[v * 2] = camera.sx
        buffer.posizioni[v * 2 + 1] = camera.sy
        camera.normal(globo.x[v], globo.y[v], globo.z[v])
        buffer.profondita[v] = camera.nvz
    }

    // Un punto della sfera guarda l'occhio - che sta a `distanza` davanti,
    // cioe' a z negativo - quando r + distanza * nz < 0. Per il triangolo si
    // usa la media dei tre: sul bordo e' la scelta che non fa spuntare facce
    // di dietro sopra quelle davanti.
    val soglia = -r / distanza * 3f
    val t = globo.triangoli
    var n = 0
    var i = 0
    while (i < t.size) {
        val a = t[i].toInt(); val b = t[i + 1].toInt(); val c = t[i + 2].toInt()
        if (buffer.profondita[a] + buffer.profondita[b] + buffer.profondita[c] < soglia) {
            buffer.visibili[n++] = t[i]; buffer.visibili[n++] = t[i + 1]; buffer.visibili[n++] = t[i + 2]
        }
        i += 3
    }
    if (n == 0) return

    drawContext.canvas.nativeCanvas.drawVertices(
        AndroidCanvas.VertexMode.TRIANGLES,
        buffer.posizioni.size,
        buffer.posizioni,
        0,
        tessitura?.coordinate,
        0,
        if (tessitura == null) colori.vertici else null,
        0,
        buffer.visibili,
        0,
        n,
        tessitura?.pennello ?: buffer.pennello,
    )
}

/** Quanto aspettare che il cursore si fermi prima di fare la carta. */
private const val ATTESA_CARTA_MS = 120L

/** La carta e' larga il doppio di quanto e' alta: 360 gradi per 180. */
internal const val LARGHEZZA_CARTA = 1024
