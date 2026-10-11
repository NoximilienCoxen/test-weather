package io.github.noximiliencoxen.caelum.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import io.github.noximiliencoxen.caelum.data.CurrentWeather
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.lingua.tr
import io.github.noximiliencoxen.caelum.ui.sala.glifoDi
import java.time.LocalDate

/**
 * La complicazione di Caelum, in tre forme:
 *
 * - **testo corto**: i gradi, la figurina in sagoma, e sopra la cosa da sapere
 *   ("☂ 21" se piove o pioverà, altrimenti la massima);
 * - **testo lungo**: gradi e condizione, e sotto il posto con la pioggia;
 * - **valore in un intervallo**: i gradi di adesso fra minima e massima di
 *   oggi, per i quadranti che la disegnano come un arco.
 *
 * Il dato e' quello della Tile e dell'app ([PrevisioneOrologio]); il sistema la
 * chiede ogni mezz'ora (`UPDATE_PERIOD_SECONDS` nel manifest) e
 * [AggiornaOrologio] la sollecita quando arriva una previsione nuova.
 */
class ComplicazioneCaelum : SuspendingComplicationDataSourceService() {

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        AggiornaOrologio.pianifica(this)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val risultato = PrevisioneOrologio.carica(this, preferisciRete = false)
            ?: return NoDataComplicationData()
        return dati(request.complicationType, risultato.previsione, risultato.fahrenheit, conTocco = true)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        dati(type, esempio(), fahrenheit = false, conTocco = false)

    private fun dati(tipo: ComplicationType, previsione: Forecast, fahrenheit: Boolean, conTocco: Boolean): ComplicationData? {
        val adesso = previsione.nowThere()
        val ora = previsione.current
        val oggi = previsione.dayOf(adesso)
        val pioggia = pioggiaInVista(oreDaAdesso(previsione, adesso))
        val gradi = temperatura(ora.temperature, fahrenheit)
        val condizione = condizioneInFrase(ora.weatherCode)
        val descrizione = testo("${previsione.place.name}, $gradi, $condizione. ${testoPioggia(pioggia)}")
        val immagine = MonochromaticImage.Builder(
            Icon.createWithBitmap(sagoma(glifoDi(ora.weatherCode, nuvolositaAdesso(previsione, adesso)), !ora.isDay, LATO_SAGOMA)),
        ).build()
        val tocco = if (conTocco) apriApp() else null

        return when (tipo) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(testo(gradi), descrizione)
                .setTitle(testo(titoloCorto(pioggia, oggi, fahrenheit)))
                .setMonochromaticImage(immagine)
                .setTapAction(tocco)
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(testo("$gradi $condizione"), descrizione)
                .setTitle(testo("${previsione.place.name} · ${testoPioggia(pioggia)}"))
                .setMonochromaticImage(immagine)
                .setTapAction(tocco)
                .build()

            ComplicationType.RANGED_VALUE -> {
                val valore = (ora.temperature ?: return NoDataComplicationData()).toFloat()
                // L'arco va dalla minima alla massima di oggi; se adesso e' gia'
                // fuori (la notte piu' fredda della minima prevista), l'arco si
                // allarga invece di tagliare il valore.
                val minimo = minOf(oggi?.tempMin?.toFloat() ?: valore, valore)
                val massimo = maxOf(oggi?.tempMax?.toFloat() ?: valore, valore).let { if (it <= minimo) minimo + 1f else it }
                RangedValueComplicationData.Builder(valore, minimo, massimo, descrizione)
                    .setText(testo(gradi))
                    .setMonochromaticImage(immagine)
                    .setTapAction(tocco)
                    .build()
            }

            else -> null
        }
    }

    private fun apriApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, WearActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun testo(s: String): ComplicationText = PlainComplicationText.Builder(s).build()

    private companion object {
        const val LATO_SAGOMA = 64

        /**
         * Il titolo del testo corto: sette caratteri al massimo, quindi una
         * cosa sola. La pioggia vince sulla massima, perche' e' quella che
         * cambia cosa si fa uscendo.
         */
        fun titoloCorto(pioggia: PioggiaInVista, oggi: DayForecast?, fahrenheit: Boolean): String = when (pioggia) {
            is PioggiaInVista.Adesso -> "☂ " + tr("ora", "now")
            is PioggiaInVista.Arriva -> "☂ ${oraBreve(pioggia.alle)}"
            is PioggiaInVista.Asciutto -> "↑${temperatura(oggi?.tempMax, fahrenheit)}"
        }

        /** Il campione che il quadrante mostra mentre si sceglie la complicazione. */
        fun esempio(): Forecast {
            val oggi = LocalDate.now()
            val ore = (0..23).map { HourForecast(time = oggi.atTime(it, 0), temperature = 18.0, weatherCode = 2, cloudCover = 40) }
            return Forecast(
                current = CurrentWeather(temperature = 18.0, weatherCode = 2),
                days = listOf(DayForecast(date = oggi, label = "OGGI", weatherCode = 2, tempMin = 11.0, tempMax = 22.0)),
                hours = ore,
                allHours = ore,
                place = Place.FORLI,
            )
        }
    }
}
