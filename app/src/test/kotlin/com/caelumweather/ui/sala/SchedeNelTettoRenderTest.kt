package com.caelumweather.ui.sala

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.caelumweather.data.AirQuality
import com.caelumweather.data.AirScale
import com.caelumweather.data.CurrentWeather
import com.caelumweather.data.DayForecast
import com.caelumweather.data.Forecast
import com.caelumweather.data.GiornoPolline
import com.caelumweather.data.HourForecast
import com.caelumweather.data.OraAria
import com.caelumweather.data.TipoPolline
import com.caelumweather.ui.UiState
import com.caelumweather.ui.sala.rooms.SalaAriaScreen
import com.caelumweather.ui.sala.rooms.SalaOggiScreen
import com.caelumweather.ui.WeatherViewModel
import com.caelumweather.data.SkyState
import com.caelumweather.ui.sala.rooms.SalaLunaScreen
import com.caelumweather.ui.sala.rooms.SalaPioggiaScreen
import com.caelumweather.ui.sala.rooms.SalaUvScreen
import com.caelumweather.ui.sala.rooms.SalaVentoScreen
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * **Le schede stanno sotto il tetto, senza scorrere dentro di se'.**
 *
 * Su un telefono da 411 x 891 punti la Shell lascia al pannello circa 400
 * punti (`LocalAltezzaPannello`: il 30 per cento resta cielo). Oltre, il
 * pannello scorre dentro di se', e col carosello verticale vuol dire due
 * gesti per passare alla sala dopo: uno porta in fondo la scheda, il secondo
 * cambia sala. Tutto resta visibile: si sta nel tetto con schede piu' dense,
 * non nascondendo pezzi.
 *
 * Disegna ogni sala a larghezza di telefono in `build/widget-renders`
 * (`tetto-*.png`) con una giornata inventata ma piena - pioggia, vento, UV,
 * aria, polline - che e' il caso in cui le schede sono piu' alte.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SchedeNelTettoRenderTest {

    private val oggi = LocalDate.of(2026, 11, 24)

    private fun stato(): UiState {
        val ore = (0 until 8 * 24).map { h ->
            val t = oggi.atStartOfDay().plusHours(h.toLong())
            val ora = h % 24
            val pioggia = if (h in 14..30) 0.2 + 0.9 * sin((h - 14) / 16.0 * Math.PI) else 0.0
            HourForecast(
                time = t,
                temperature = 8.0 - 3.5 * cos((ora - 3) / 24.0 * 2 * Math.PI),
                apparent = 6.5 - 3.5 * cos((ora - 3) / 24.0 * 2 * Math.PI),
                weatherCode = if (pioggia > 0) 61 else 3,
                precipitation = pioggia,
                precipProbability = if (pioggia > 0) 80 else 10,
                isDay = ora in 7..16,
                humidity = 88.0,
                dewPoint = 6.0,
                windSpeed = 3.0 + 2 * sin(h / 5.0),
                windGusts = 7.0 + 3 * sin(h / 5.0),
                windDirection = 200.0 + 40 * sin(h / 9.0),
                uvIndex = max(0.0, 2.4 * sin((ora - 7) / 10.0 * Math.PI)),
                cloudCover = 90,
                pressure = 1012.0 - h * 0.05,
                visibility = 12000.0,
                rain = pioggia,
                snowfall = 0.0,
                freezingLevel = 2200.0,
            )
        }
        val etichette = listOf("OGGI", "MER", "GIO", "VEN", "SAB", "DOM", "LUN", "MAR")
        val giorni = (0 until 8).map { d ->
            val data = oggi.plusDays(d.toLong())
            DayForecast(
                date = data, label = etichette[d], weatherCode = 61,
                tempMax = 9.0 + d % 3, tempMin = 4.0 - d % 4, precipitationSum = listOf(0.4, 6.0, 3.0, 0.8, 0.6, 0.0, 0.0, 0.0)[d],
                precipProbability = 70, windMax = 6.0, gustMax = 12.0, windDirection = 210.0, uvMax = 2.4,
                sunrise = data.atTime(7, 18), sunset = data.atTime(16, 37), sunshineSeconds = 3600.0, humidityMean = 88.0, dewPointMean = 6.0,
            )
        }
        val previsione = Forecast(
            current = CurrentWeather(temperature = 9.0, apparent = 8.0, humidity = 90.0, weatherCode = 61, windSpeed = 2.0, windDirection = 210.0, windGusts = 6.0),
            days = giorni,
            hours = ore.take(24),
            allHours = ore,
            elevation = 55.0,
        )
        val aria = AirQuality(
            index = 43, scale = AirScale.EUROPEA, pm25 = 17.0, pm10 = 22.0, nitrogenDioxide = 18.0, ozone = 40.0,
            oreOggi = (0 until 24).map { OraAria(oggi.atStartOfDay().plusHours(it.toLong()), 25 + (it * 7) % 30) },
            polline = (0 until 3).map { d -> GiornoPolline(oggi.plusDays(d.toLong()), TipoPolline.entries.associateWith { (it.ordinal + d) % 4 }) },
        )
        return UiState(forecast = previsione, air = aria, selectedHour = 13)
    }

    /** Disegna [contenuto] largo 411 dp, con il tetto [tetto], e ne restituisce l'altezza in dp. */
    private fun disegna(nome: String, contenuto: @Composable () -> Unit): Float {
        val attivita = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        attivita.setContent {
            Column(modifier = Modifier.background(Color(0xFF8A939E)).width(411.dp).padding(horizontal = 16.dp)) {
                contenuto()
            }
        }
        ShadowLooper.idleMainLooper()
        // Le colonne in ritardo e le molle hanno bisogno di qualche giro.
        repeat(40) { ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS) }
        val radice = attivita.window.decorView
        val vista: View = (radice.findViewById<android.view.ViewGroup>(android.R.id.content)).getChildAt(0)
        val bitmap = Bitmap.createBitmap(vista.width.coerceAtLeast(1), vista.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(bitmap))
        File("build/widget-renders").apply { mkdirs() }.resolve("tetto-$nome.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return vista.height / attivita.resources.displayMetrics.density
    }

    private val palette = salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0f)

    /** L'altezza del solo pannello di ogni sala misurata, in punti. */
    private val pannelli = mutableMapOf<String, Float>()

    /** L'altezza naturale di ogni sala, senza tetto: quella che servirebbe per non scorrere. */
    private fun misura(nome: String, sala: @Composable () -> Unit): Float {
        var pannello = 0f
        val alta = disegna(nome) {
            androidx.compose.runtime.CompositionLocalProvider(LocalMisuraPannello provides { pannello = it }) { sala() }
        }
        pannelli[nome] = pannello
        println("SCHEDA $nome: ${alta.toInt()} dp")
        File("build/widget-renders").resolve("tetto-misure.txt").appendText("$nome ${alta.toInt()} (pannello ${pannello.toInt()})\n")
        return alta
    }

    @Test
    fun `le sale stanno sotto il tetto`() {
        val s = stato()
        File("build/widget-renders").apply { mkdirs() }.resolve("tetto-misure.txt").writeText("")
        val attivita = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val vm = WeatherViewModel(attivita.application)
        val cielo = SkyState(altitude = -10f, dayness = 0f, sunPresence = 0f, moonPresence = 1f, redness = 0f)
        val altezze = mutableMapOf(
            "oggi" to misura("oggi") { SalaOggiScreen(state = s, sky = cielo, palette = palette, viewModel = vm, faseLunare = 0.5f, onVai = {}) },
            "oggi-giornata" to misura("oggi-giornata") { SalaOggiScreen(state = s, sky = cielo, palette = palette, viewModel = vm, faseLunare = 0.5f, onVai = {}, giornataApertaIniziale = true) },
            // Il caso dello screenshot: un giorno futuro (la pastiglia del giorno
            // e "torna a oggi" in cima) con la giornata aperta.
            "oggi-domani-giornata" to misura("oggi-domani-giornata") { SalaOggiScreen(state = s.copy(selectedDay = 1), sky = cielo, palette = palette, viewModel = vm, faseLunare = 0.5f, onVai = {}, giornataApertaIniziale = true) },
            "pioggia" to misura("pioggia") { SalaPioggiaScreen(state = s, palette = palette, onSelectHour = {}, onSelectDay = {}, movimento = false) },
            "luna" to misura("luna") { SalaLunaScreen(palette = palette, giorno = oggi, movimento = false) },
            "aria" to misura("aria") { SalaAriaScreen(state = s, palette = palette) },
            "vento" to misura("vento") { SalaVentoScreen(state = s, palette = palette, movimento = false) },
            "mare" to misura("mare") { com.caelumweather.ui.sala.rooms.SalaMareScreen(state = s.copy(forcedMare = 110), palette = palette, movimento = false) },
            "uv" to misura("uv") { SalaUvScreen(state = s, palette = palette, onSelectHour = {}, movimento = false) },
        )
        // **La riga della pioggia nei prossimi minuti** compare solo per oggi
        // e all'ora di adesso: qui la si misura da sola e la si somma al caso
        // peggiore di Oggi, la giornata aperta (sul giorno futuro la riga non c'e').
        val adesso = oggi.atTime(12, 50)
        val quarti = (0 until 10).map { q ->
            com.caelumweather.data.QuartoDOra(oggi.atTime(12, 45).plusMinutes(15L * q), if (q in 2..6) 0.8 else 0.0, if (q in 2..6) 61 else 3)
        }
        val riga = misura("prossima-ora") {
            com.caelumweather.ui.sala.rooms.ProssimaOraVista(com.caelumweather.ui.sala.rooms.raccontoProssimaOra(quarti, adesso)!!, palette)
        }
        altezze["oggi-giornata-pioggia"] = (altezze["oggi-giornata"] ?: 0f) + riga + 8f
        pannelli["oggi-giornata-pioggia"] = (pannelli["oggi-giornata"] ?: 0f) + riga + 8f

        // Oggi e la luna hanno sopra la scheda il numero grande e la sfera: per
        // loro conta l'altezza della pagina intera, per le altre il tetto.
        // Due conti: la pagina intera non deve uscire dal carosello, e il
        // pannello non deve superare il tetto (oltre, scorrerebbe dentro di se').
        val fuori = altezze.filter { (nome, alta) ->
            alta > PAGINA_TELEFONO || (pannelli[nome] ?: 0f) > TETTO_TELEFONO
        }
        assertTrue("schede che non stanno: $fuori", fuori.isEmpty())
    }

    @Test
    fun `l'anteprima di un giorno a tenere premuto`() {
        val s = stato()
        val settimana = settimanaDi(s.forecast)
        misura("anteprima") {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .width(236.dp)
                    .background(palette.panelSolido, androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            ) {
                com.caelumweather.ui.sala.rooms.ContenutoAnteprima(settimana[2], s, palette)
            }
        }
    }

    @Test
    fun `i colori del telefono tingono tutta la scheda`() {
        val s = stato()
        val viola = salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0f).conTelefono(
            TintaTelefono(Color(0xFF65558F), Color.White, Color(0xFFF7F2FA), Color(0xFFFEF7FF), Color(0xFFE6E0E9), Color(0xFFCAC4D0), Color(0xFF1D1B20), Color(0xFF49454F)),
            TintaTelefono(Color(0xFFD0BCFE), Color(0xFF381E72), Color(0xFF1D1B20), Color(0xFF141218), Color(0xFF36343B), Color(0xFF49454F), Color(0xFFE6E0E9), Color(0xFFCAC4D0)),
        )
        misura("telefono-pioggia") { com.caelumweather.ui.sala.rooms.SalaPioggiaScreen(state = s, palette = viola, onSelectHour = {}, onSelectDay = {}, movimento = false) }
    }

    /** Le sale con i caratteri ingranditi dalle impostazioni del telefono. */
    private fun misuraIngrandita(scala: Float, nome: String, sala: @Composable () -> Unit): Float = misura("$nome-x$scala") {
        val d = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, scala),
        ) { sala() }
    }

    @Test
    fun `con i caratteri ingranditi`() {
        val s = stato()
        val attivita = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val vm = WeatherViewModel(attivita.application)
        val cielo = SkyState(altitude = -10f, dayness = 0f, sunPresence = 0f, moonPresence = 1f, redness = 0f)
        val fuori = mutableListOf<String>()
        listOf(1.3f, 1.6f).forEach { scala ->
            // Il tetto e la pagina crescono quanto cede il cielo (`quotaCielo`).
            val guadagno = (quotaCielo(1f) - quotaCielo(scala)) * ALTEZZA_TELEFONO
            val tetto = TETTO_TELEFONO + guadagno
            val pagina = PAGINA_TELEFONO
            fun conta(nome: String, sala: @Composable () -> Unit) {
                val alta = misuraIngrandita(scala, nome, sala)
                val pannello = pannelli["$nome-x$scala"] ?: 0f
                // A 1,3 tutto deve stare; a 1,6 si tollera lo scorrimento
                // interno del pannello, ma non la pagina che esce.
                val pannelloFuori = scala <= 1.3f && pannello > tetto
                // La pagina del carosello non cresce col cielo che cede: a 1,3
                // anche lei deve contenere tutto.
                val paginaFuori = scala <= 1.3f && alta - pannello + minOf(pannello, tetto) > pagina
                if (pannelloFuori || paginaFuori) fuori += "$nome x$scala: ${alta.toInt()} (pannello ${pannello.toInt()}, tetto ${tetto.toInt()})"
            }
            conta("oggi") { SalaOggiScreen(state = s, sky = cielo, palette = palette, viewModel = vm, faseLunare = 0.5f, onVai = {}) }
            conta("pioggia") { SalaPioggiaScreen(state = s, palette = palette, onSelectHour = {}, onSelectDay = {}, movimento = false) }
            conta("luna") { SalaLunaScreen(palette = palette, giorno = oggi, movimento = false) }
            conta("aria") { SalaAriaScreen(state = s, palette = palette) }
            conta("vento") { SalaVentoScreen(state = s, palette = palette, movimento = false) }
            conta("uv") { SalaUvScreen(state = s, palette = palette, onSelectHour = {}, movimento = false) }
        }
        assertTrue("col testo grande non stanno: $fuori", fuori.isEmpty())
    }

    private companion object {
        /**
         * Il tetto della Shell su un telefono da 891 punti, con il 30 per cento
         * dello schermo lasciato al cielo (`QUOTA_CIELO`), meno un margine e
         * meno la riga del bottone "torna all'ora attuale", alta 38 punti.
         */
        const val TETTO_TELEFONO = 462f

        /** L'altezza del telefono delle misure, in punti. */
        const val ALTEZZA_TELEFONO = 891f

        /** L'altezza della pagina del carosello, fra l'intestazione e i puntini. */
        const val PAGINA_TELEFONO = 602f

    }
}
