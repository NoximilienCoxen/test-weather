package io.github.noximiliencoxen.caelum.ui.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Le due lastre di una scena: il dipinto e la sua profondita'.
 *
 * Restano `android.graphics.Bitmap` e non `ImageBitmap` perche' e' quello che
 * serve a valle: `BitmapShader` per la strada AGSL, e la lettura dei pixel per
 * le fasce del ripiego. Convertire a `ImageBitmap` e riconvertire sarebbe un
 * giro per tornare al punto di partenza.
 */
@Immutable
class ScenePlates(
    val kind: SceneKind,
    val painting: Bitmap,
    val depth: Bitmap,
    /** Le fasce del ripiego, gia' ritagliate. Vuote dove AGSL c'e'. */
    val bands: List<Bitmap>,
)

/**
 * Carica le scene, una volta sola, e ne tiene poche in mano.
 *
 * **Tre e non una.** Durante una transizione ne servono due contemporaneamente -
 * quella che se ne va e quella che arriva - e la terza copre il caso comune di
 * chi torna indietro subito dopo, che altrimenti pagherebbe una decodifica per
 * un'immagine che aveva in mano un secondo prima. Oltre la terza si butta la
 * meno usata di recente: sedici megabyte di scene tenute per sempre sono
 * sedici megabyte che il sistema si riprende quando gli servono, e riprenderseli
 * mentre si scorre e' esattamente il momento peggiore.
 *
 * **La profondita' si decodifica a colori pieni**, non in `ALPHA_8` che pure
 * costerebbe un quarto. `BitmapShader` su una bitmap di sola trasparenza ha un
 * comportamento che cambia da versione a versione, e qui il valore letto e' la
 * geometria della scena: se sbaglia, non sbaglia una tinta, sbaglia dove
 * stanno le cose.
 */
object SceneLoader {

    private const val KEEP = 3

    private val lock = Mutex()
    private val cache = LinkedHashMap<SceneKind, ScenePlates>()

    /**
     * [maxWidth] e' la larghezza in pixel a cui la scena verra' disegnata: si
     * decodifica al primo sottocampionamento che ci sta sopra, invece di tenere
     * in memoria una risoluzione che nessuno vedra'. Su uno schermo stretto e'
     * un quarto della memoria per lo stesso identico risultato.
     */
    suspend fun load(context: Context, kind: SceneKind, maxWidth: Int, bandsNeeded: Boolean): ScenePlates =
        lock.withLock {
            cache[kind]?.let { cached ->
                if (!bandsNeeded || cached.bands.isNotEmpty()) {
                    // Rimessa in coda: la mappa e' ordinata per inserimento, e
                    // riscriverla e' come dire "questa l'ho appena usata".
                    cache.remove(kind)
                    cache[kind] = cached
                    return@withLock cached
                }
            }
            val plates = withContext(Dispatchers.IO) {
                decode(context, kind, maxWidth, bandsNeeded)
            }
            cache[kind] = plates
            while (cache.size > KEEP) {
                val oldest = cache.keys.first()
                cache.remove(oldest)
            }
            plates
        }

    private fun decode(context: Context, kind: SceneKind, maxWidth: Int, bandsNeeded: Boolean): ScenePlates {
        val painting = readBitmap(context, kind.painting, maxWidth)
        val depth = readBitmap(context, kind.depth, maxWidth)
        val bands = if (bandsNeeded) sliceBands(painting, depth) else emptyList()
        return ScenePlates(kind, painting, depth, bands)
    }

    private fun readBitmap(context: Context, path: String, maxWidth: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleFor(bounds.outWidth, maxWidth)
        }
        return context.assets.open(path).use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("scena mancante o illeggibile: $path")
    }

    /** Potenze di due, come `inSampleSize` pretende: 1, 2, 4, 8. */
    private fun sampleFor(sourceWidth: Int, maxWidth: Int): Int {
        if (sourceWidth <= 0 || maxWidth <= 0) return 1
        var sample = 1
        while (sourceWidth / (sample * 2) >= maxWidth) sample *= 2
        return sample
    }

    /**
     * Il ripiego per chi non ha AGSL: la scena in tre lastre.
     *
     * Sotto la 33 `RuntimeShader` non esiste e non c'e' modo di spostare un
     * pixel in funzione della sua profondita'. Quello che si puo' fare e'
     * spostare **tre lastre intere** a velocita' diverse, che e' la parallasse
     * come la facevano i cartoni animati con i piani di vetro.
     *
     * **Tre e non sei.** Ogni fascia e' una bitmap piena quanto la scena, quindi
     * sei fasce sono sei volte la memoria di una scena, per un guadagno che a
     * occhio si ferma alla terza: cielo, mezzo, primo piano sono le tre
     * distanze che una parallasse fa leggere. Le altre sarebbero memoria spesa
     * per una differenza che nessuno vede.
     *
     * I pixel fuori dalla fascia restano trasparenti, e i bordi si tengono
     * **morbidi**: un taglio netto sulla soglia fa comparire una scalinata
     * lungo il profilo delle colline appena le lastre si muovono.
     */
    private fun sliceBands(painting: Bitmap, depth: Bitmap): List<Bitmap> {
        val w = painting.width
        val h = painting.height
        val pixels = IntArray(w * h)
        painting.getPixels(pixels, 0, w, 0, 0, w, h)
        val z = IntArray(w * h)
        depth.getPixels(z, 0, w, 0, 0, w, h)

        return BAND_EDGES.indices.map { band ->
            val low = if (band == 0) -1f else BAND_EDGES[band - 1]
            val high = BAND_EDGES[band]
            val out = IntArray(w * h)
            for (i in pixels.indices) {
                // Il canale rosso: la profondita' e' in scala di grigi, quindi
                // i tre canali sono uguali e il rosso e' il piu' a buon mercato.
                val d = ((z[i] shr 16) and 0xFF) / 255f
                val weight = when {
                    d <= low -> smoothEdge((d - low + FEATHER) / FEATHER)
                    d >= high -> smoothEdge((high + FEATHER - d) / FEATHER)
                    else -> 1f
                }
                if (weight <= 0f) continue
                val src = pixels[i]
                val alpha = (((src ushr 24) and 0xFF) * weight).toInt().coerceIn(0, 255)
                out[i] = (alpha shl 24) or (src and 0x00FFFFFF)
            }
            Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        }
    }

    private fun smoothEdge(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /**
     * Dove finisce una fascia e comincia la successiva, in profondita'.
     *
     * Il cielo con le sue nuvole sta sotto 0,35 perche' e' li' che il generatore
     * dei segnaposto lo mette, ed e' anche dove lo mette un dipinto con
     * l'orizzonte a tre quinti: sopra la linea c'e' aria, e l'aria e' lontana.
     */
    private val BAND_EDGES = floatArrayOf(0.35f, 0.70f, 1.01f)

    /** Quanto e' morbido il bordo fra due fasce, in profondita'. */
    private const val FEATHER = 0.09f
}
