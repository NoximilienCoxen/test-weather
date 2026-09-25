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
    val globo = GLOBO
    val colori = remember(fase) { coloriPer(globo, fase) }
    val buffer = remember { BufferGlobo(globo) }
    // **La carta non dipende dalla fase, e si fa una volta sola.** Mari e
    // crateri stanno li', e la luce arriva sopra per vertice (vedi
    // [ColoriGlobo.modulatori]): trascinando il cursore la fase cambia a ogni
    // giorno **mentre** il dito si muove, senza rifare niente di pesante.
    // Finche' la carta non e' pronta - il primo mezzo secondo della sala - la
    // sfera usa i colori dei vertici.
    val tessitura by produceState(initialValue = tessituraPronta, globo) {
        if (value == null) value = withContext(Dispatchers.Default) { TessituraGlobo(globo) }.also { tessituraPronta = it }
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

/**
 * I colori dei vertici per una fase, e il tono dell'alone.
 *
 * [vertici] e' la luna intera a colori per vertice, per quando la carta non
 * c'e'. [modulatori] e' **solo la luce**, da moltiplicare sulla carta: per ogni
 * vertice, il suo colore vero diviso il colore che avrebbe in piena luce, canale
 * per canale. Moltiplicato per la carta ridà esattamente il colore vero sul
 * vertice, e in mezzo ai vertici porta i mari e i crateri della carta con la
 * luce interpolata - che e' cio' che la luce e': liscia.
 */
internal class ColoriGlobo(val vertici: IntArray, val modulatori: IntArray, val alone: Int)

/**
 * La tinta di ogni vertice: dal nero dello spazio al bianco caldo della luna
 * piena, passando per i grigi del bordo. Le tinte sono quelle di
 * [SalaTokens], fisse nei due temi: un corpo celeste non cambia colore con la
 * pagina che lo mostra.
 *
 * Sono settemila vertici: si rifa' a ogni giorno del cursore, nel filo
 * principale, e non si sente.
 */
internal fun coloriPer(globo: Globo, fase: Float): ColoriGlobo {
    val luce = FloatArray(globo.vertici)
    globo.luce(fase, luce)
    val vertici = IntArray(globo.vertici)
    val modulatori = IntArray(globo.vertici)
    for (v in 0 until globo.vertici) {
        val a = globo.albedo[v]
        val vero = TintaLuna.di(luce[v], a)
        vertici[v] = vero
        modulatori[v] = TintaLuna.rapporto(vero, TintaLuna.pieno(a))
    }
    return ColoriGlobo(vertici, modulatori, SalaTokens.lunaLuce.toArgb())
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

    /** Il colore di un punto d'albedo [albedo] in piena luce: e' cio' che sta sulla carta. */
    fun pieno(albedo: Float): Int = di(albedo, albedo)

    /** [vero] diviso [pieno], canale per canale, fra 0 e 1: la luce da moltiplicare. */
    fun rapporto(vero: Int, pieno: Int): Int {
        fun canale(s: Int): Int {
            val v = vero shr s and 0xFF
            val p = pieno shr s and 0xFF
            return if (p == 0) 0 else (v * 255 / p).coerceIn(0, 255)
        }
        return (0xFF shl 24) or (canale(16) shl 16) or (canale(8) shl 8) or canale(0)
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
 * La luna in piena luce, dipinta su una carta e pronta da stendere sulla sfera.
 *
 * **Non dipende dalla fase**: la luce arriva sopra, per vertice
 * ([ColoriGlobo.modulatori]). Si fa una volta sola, fuori dal filo principale
 * (vedi [LunaInterattiva]): sono mezzo milione di punti coi loro crateri.
 */
internal class TessituraGlobo(globo: Globo, larghezza: Int = LARGHEZZA_CARTA) {
    private val altezza = larghezza / 2
    private val immagine: Bitmap = run {
        val albedo = globo.albedoCarta(larghezza, altezza).albedo
        val pixel = IntArray(albedo.size) { TintaLuna.pieno(albedo[it]) }
        Bitmap.createBitmap(pixel, larghezza, altezza, Bitmap.Config.ARGB_8888)
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
     * ingrandita non si sgrana.
     *
     * **Carta e colori dei vertici si moltiplicano, ed e' voluto.** Con uno
     * shader `drawVertices` combina i due in `MODULATE` - e' cosi' in Android da
     * sempre, disegno accelerato compreso - e i colori dei vertici qui sono i
     * [ColoriGlobo.modulatori], cioe' la sola luce. Il vecchio guaio dei mari
     * spariti era un'altra moltiplicazione, col colore del pennello (vedi
     * [BufferGlobo.pennello]): con uno shader il colore del pennello non conta.
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
        if (tessitura == null) colori.vertici else colori.modulatori,
        0,
        buffer.visibili,
        0,
        n,
        tessitura?.pennello ?: buffer.pennello,
    )
}

/** La carta e' larga il doppio di quanto e' alta: 360 gradi per 180. */
internal const val LARGHEZZA_CARTA = 1024

/**
 * La sfera e la sua carta, una volta per tutta la vita dell'app: non cambiano
 * mai, e rifarle a ogni ritorno sulla sala voleva dire rivedere la luna morbida
 * per mezzo secondo ogni volta. `Globo` e' immutabile a parte la carta, che si
 * fa sotto chiave; `TessituraGlobo` non cambia dopo la nascita.
 */
private val GLOBO by lazy { Globo() }

@Volatile
private var tessituraPronta: TessituraGlobo? = null

