package io.github.noximiliencoxen.caelum.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.noximiliencoxen.caelum.MainActivity
import io.github.noximiliencoxen.caelum.R
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.data.isWet
import io.github.noximiliencoxen.caelum.lingua.Lingue
import io.github.noximiliencoxen.caelum.prefs.SettingsPrefs
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.widget.WidgetForecast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Il riquadro delle Impostazioni rapide: la temperatura e il tempo di adesso
 * sulla localita' dell'app, e un tocco per aprirla.
 *
 * **Si aggiorna quando si guarda.** `onStartListening` arriva aprendo la
 * tendina: li' si legge la previsione con lo stesso giro dei widget
 * (`WidgetForecast.load`), che usa la scorta su disco se ha meno di venticinque
 * minuti e chiede alla rete solo se no. Nessun lavoro in sottofondo per lui:
 * un riquadro che nessuno guarda non ha bisogno di essere giusto.
 *
 * Finche' la risposta non arriva resta quel che diceva l'ultima volta - il
 * sistema conserva lo stato del riquadro fra un'apertura e l'altra - invece di
 * lampeggiare un "--" ogni volta che si abbassa la tendina.
 */
class RiquadroMeteo : TileService() {

    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lettura: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        lettura?.cancel()
        lettura = ambito.launch {
            val impostazioni = SettingsPrefs(applicationContext).settings.first()
            val previsione = WidgetForecast.load(applicationContext, impostazioni.place)
            val riquadro = qsTile ?: return@launch
            disegna(riquadro, previsione, impostazioni.place.name, impostazioni.unit)
        }
    }

    override fun onStopListening() {
        lettura?.cancel()
        super.onStopListening()
    }

    override fun onDestroy() {
        ambito.cancel()
        super.onDestroy()
    }

    private fun disegna(riquadro: Tile, previsione: Forecast?, citta: String, unita: TempUnit) {
        val adesso = previsione?.current
        val (titolo, sotto) = testiRiquadro(adesso?.temperature, adesso?.weatherCode, citta, unita)
        riquadro.label = titolo
        // Il sottotitolo c'e' da Android 10: prima il riquadro ha una riga
        // sola, e il posto finisce nella descrizione per TalkBack.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) riquadro.subtitle = sotto
        riquadro.contentDescription = "$titolo, $sotto"
        val piove = adesso?.weatherCode != null && Wmo.family(adesso.weatherCode).isWet()
        riquadro.icon = Icon.createWithResource(
            this,
            if (piove) R.drawable.ic_notifica_pioggia else R.drawable.ic_notifica_riepilogo,
        )
        // Attivo e non spento anche senza dati: grigio si legge come
        // "disattivato", e qui non c'e' niente da attivare.
        riquadro.state = Tile.STATE_ACTIVE
        riquadro.updateTile()
    }

    // `startActivityAndCollapse(Intent)` e' vietato da Android 14 in su, che
    // vuole il PendingIntent; prima il PendingIntent non c'era.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val apri = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, apri, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(apri)
        }
    }
}

/**
 * "21° · Sereno" sopra, la citta' sotto. Senza previsione, il nome del posto
 * e un trattino: il riquadro dice di dove parla anche quando non sa cosa dire.
 */
internal fun testiRiquadro(gradi: Double?, codice: Int?, citta: String, unita: TempUnit): Pair<String, String> {
    val temperatura = gradi?.let { "${unita.from(it).roundToInt()}°" } ?: "--°"
    val tempo = codice?.let {
        Wmo.condition(it).lowercase(Lingue.locale).replaceFirstChar { c -> c.titlecase(Lingue.locale) }
    }
    return listOfNotNull(temperatura, tempo).joinToString(" · ") to citta
}
