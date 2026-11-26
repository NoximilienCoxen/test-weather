package com.caelumweather.data

import android.content.Context
import com.caelumweather.lingua.Lingue
import java.io.InputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.cos

/**
 * **Il nome della posizione senza chiedere a nessuno.**
 *
 * Il geocoder della piattaforma non c'e' su ogni telefono: senza i servizi
 * Google non risponde mai, e in testa all'app restava "POSIZIONE" (visto in
 * mano il 26 novembre 2026, anche dopo aver riprovato). Qui c'e' un elenco
 * delle localita' del mondo con almeno mille abitanti - GeoNames, CC BY 4.0,
 * preparato da `scripts/comuni_geonames.py` - e si prende la piu' vicina.
 *
 * Niente rete: le coordinate non escono dal telefono piu' di quanto gia'
 * facciano per la previsione, e l'informativa resta vera.
 *
 * L'elenco si legge solo quando serve (il geocoder non ha risposto) e resta in
 * memoria finche' vive il processo: sono circa 170 mila righe, qualche
 * centinaio di millisecondi la prima volta, fuori dal thread principale.
 */
object LocalitaVicine {

    /** Oltre questa distanza la localita' piu' vicina non e' "dove sei". */
    const val RAGGIO_KM = 25.0

    @Volatile private var elenco: Elenco? = null

    fun piuVicina(context: Context, lat: Double, lon: Double): Place? {
        val e = elenco ?: synchronized(this) {
            elenco ?: runCatching { context.assets.open(FILE).use(::leggi) }.getOrNull()?.also { elenco = it }
        } ?: return null
        return e.piuVicina(lat, lon)
    }

    private const val FILE = "localita.tsv.gz"

    /** Legge il file (gzip, righe ordinate per latitudine). Pura, per i test. */
    internal fun leggi(flusso: InputStream): Elenco {
        val lat = IntArrayBuilder()
        val lon = IntArrayBuilder()
        val nomi = ArrayList<String>(180_000)
        val regioni = ArrayList<String>(180_000)
        val paesi = ArrayList<String>(180_000)
        // Le regioni e i paesi si ripetono migliaia di volte: una stringa sola.
        val uniche = HashMap<String, String>()
        GZIPInputStream(flusso).bufferedReader(Charsets.UTF_8).forEachLine { riga ->
            val c = riga.split('\t')
            if (c.size < 5) return@forEachLine
            lat.add(c[0].toIntOrNull() ?: return@forEachLine)
            lon.add(c[1].toIntOrNull() ?: 0)
            nomi.add(c[2])
            regioni.add(uniche.getOrPut(c[3]) { c[3] })
            paesi.add(uniche.getOrPut(c[4]) { c[4] })
        }
        return Elenco(lat.toArray(), lon.toArray(), nomi, regioni, paesi)
    }

    internal class Elenco(
        private val lat: IntArray,
        private val lon: IntArray,
        private val nomi: List<String>,
        private val regioni: List<String>,
        private val paesi: List<String>,
    ) {
        val size: Int get() = lat.size

        fun piuVicina(la: Double, lo: Double, raggioKm: Double = RAGGIO_KM): Place? {
            if (lat.isEmpty() || !la.isFinite() || !lo.isFinite()) return null
            // Una fascia di latitudine larga quanto il raggio, poi la distanza
            // vera solo li' dentro: un grado di latitudine sono 111 km.
            val fascia = (raggioKm / 111.0 * 1e4).toInt() + 1
            val centro = (la * 1e4).toInt()
            var i = primoNonMinore(centro - fascia)
            // Fuori dalla fascia di longitudine non si calcola niente: vicino ai
            // poli i gradi si stringono, quindi si allarga col coseno.
            val larghezzaLon = (fascia / cos(Math.toRadians(la)).coerceAtLeast(0.05)).toInt()
            val loE4 = (lo * 1e4).toInt()
            var meglio = -1
            var distanzaMeglio = Double.MAX_VALUE
            while (i < lat.size && lat[i] <= centro + fascia) {
                var dLo = abs(lon[i] - loE4)
                if (dLo > 1_800_000) dLo = 3_600_000 - dLo // oltre l'antimeridiano
                if (dLo <= larghezzaLon) {
                    val d = distanzaKm(la, lo, lat[i] / 1e4, lon[i] / 1e4)
                    if (d < distanzaMeglio) {
                        distanzaMeglio = d
                        meglio = i
                    }
                }
                i++
            }
            if (meglio < 0 || distanzaMeglio > raggioKm) return null
            return Place(
                name = nomi[meglio],
                admin = regioni[meglio].ifBlank { null },
                country = paesi[meglio].takeIf { it.isNotBlank() }
                    ?.let { Locale("", it).getDisplayCountry(Lingue.locale).ifBlank { null } },
                latitude = la,
                longitude = lo,
            )
        }

        private fun primoNonMinore(valore: Int): Int {
            var basso = 0
            var alto = lat.size
            while (basso < alto) {
                val m = (basso + alto) ushr 1
                if (lat[m] < valore) basso = m + 1 else alto = m
            }
            return basso
        }
    }

    private class IntArrayBuilder {
        private var a = IntArray(180_000)
        private var n = 0
        fun add(v: Int) {
            if (n == a.size) a = a.copyOf(a.size * 2)
            a[n++] = v
        }
        fun toArray(): IntArray = a.copyOf(n)
    }
}
