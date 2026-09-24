package io.github.noximiliencoxen.caelum.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import io.github.noximiliencoxen.caelum.data.DeviceLocation
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.ui.theme.MeteoTheme
import io.github.noximiliencoxen.caelum.ui.theme.skyColors
import io.github.noximiliencoxen.caelum.ui.widgetconfig.WidgetConfigScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Aperta dal sistema subito dopo che un widget e' stato trascinato sulla Home
 * (attributo `configure` dei provider): sceglie la localita' e i colori di
 * quella singola istanza, prima che compaia.
 *
 * Una sola per tutti e tre i widget: quale sia lo si chiede al sistema al
 * momento di ridisegnarlo.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var kind: WidgetKind? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Contratto standard dei widget: se l'utente esce senza salvare, il
        // sistema deve considerare il posizionamento annullato.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Il tipo si legge qui e si tiene: adesso il lanciatore ha gia'
        // agganciato il widget, mentre al momento del salvataggio la stessa
        // domanda potrebbe non avere piu' risposta.
        kind = WidgetKind.of(this, appWidgetId)

        // La scelta gia' fatta, se c'e'. Adesso questa schermata si riapre - i
        // widget sono `reconfigurable` - e riaprirla in bianco vorrebbe dire
        // che chi voleva solo controllare quale citta' aveva impostato e'
        // costretto a ripeterla alla cieca, con il rischio di salvarne una
        // diversa per sbaglio.
        //
        // Si legge prima di comporre, non dopo: montare la schermata vuota e
        // poi riempirla sovrascriverebbe la scelta di chi ha gia' iniziato a
        // toccare. La lettura e' un file locale, dura un attimo, e nel
        // frattempo non c'e' niente da mostrare.
        lifecycleScope.launch {
            val initial = withContext(Dispatchers.IO) { WidgetPrefs(this@WidgetConfigActivity).load(appWidgetId) }
            setContent {
                // Palette neutra, non quella dell'ora del giorno: e' una
                // schermata di sistema, non una schermata dell'app.
                MeteoTheme(colors = skyColors(SkyState.Giorno)) {
                    WidgetConfigScreen(
                        kind = kind,
                        initial = initial,
                        onSave = { place, useLocation ->
                            lifecycleScope.launch { saveAndFinish(place, useLocation) }
                        },
                    )
                }
            }
        }
    }

    private suspend fun saveAndFinish(chosen: Place?, useLocation: Boolean) {
        // **Chi segue la posizione la rileva adesso, finche' e' in primo
        // piano.** Da dietro le quinte il widget non la ottiene quasi mai (vedi
        // WidgetPrefs.lastFix), e senza un punto di partenza ripiegava su
        // quel che trovava: una citta' scelta mesi prima in un'altra scheda, o
        // quella aperta nell'app. Con un tempo massimo: e' un tocco su SALVA,
        // non un'attesa. Se non arriva niente resta la scelta di prima.
        val place = if (useLocation) {
            withTimeoutOrNull(FIX_TIMEOUT_MS) { DeviceLocation.current(this@WidgetConfigActivity) } ?: chosen
        } else {
            chosen
        }

        // Salvataggio su Dispatchers.IO: DataStore usa gia' IO internamente,
        // ma forzare il dispatcher garantisce che la scrittura sia completata
        // e visibile a qualsiasi lettura successiva prima di procedere.
        withContext(Dispatchers.IO) {
            WidgetPrefs(this@WidgetConfigActivity).save(
                appWidgetId = appWidgetId,
                config = WidgetConfig(useLocation = useLocation, place = place),
            )
        }

        // **L'esito si cede appena la scrittura e' a terra, prima del
        // ridisegno.** Prima veniva dopo, e in mezzo c'era un'attesa: se
        // `lifecycleScope` moriva in quella finestra restava il
        // RESULT_CANCELED impostato in onCreate, il lanciatore cancellava
        // l'identificativo, e onDeleted buttava via le preferenze appena
        // salvate. Il contratto col sistema riguarda la configurazione, e la
        // configurazione a questo punto c'e'.
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )

        // Il ridisegno non e' una cortesia: il widget e' gia' stato disegnato
        // una volta, prima che questa schermata si aprisse, con le preferenze
        // ancora vuote. E' un miglioramento dell'esperienza, non parte del
        // contratto: se fallisce si perde un ridisegno, non la scelta.
        // **La previsione si scarica adesso, finche' questa schermata e' in
        // primo piano.** Il ridisegno gira dopo, quando l'app e' gia' dietro le
        // quinte, e li' Android la rete spesso non la da': con una citta' mai
        // aperta prima non c'era nemmeno una previsione salvata, e il widget
        // restava vuoto finche' non si apriva l'app. Con un tempo massimo,
        // perche' e' un tocco su SALVA; se non basta, ci pensa il lavoro in
        // background appena c'e' rete.
        val tipo = kind
        if (tipo != null && tipo.needsPlace && place != null && tipo != WidgetKind.ARIA) {
            val scaricata = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
                WidgetForecast.scarica(this@WidgetConfigActivity, place)
            } ?: false
            if (!scaricata) runCatching { AggiornaWidgetWorker.appenaPossibile(this) }
        }
        runCatching { AggiornaWidgetWorker.pianifica(this) }

        runCatching { refreshWidget(this, appWidgetId, kind) }
            .onFailure { Log.w(TAG, "il widget $appWidgetId non si è ridisegnato", it) }

        finish()
    }

    private companion object {
        const val TAG = "WidgetConfig"
        const val DOWNLOAD_TIMEOUT_MS = 6_000L
        const val FIX_TIMEOUT_MS = 3_000L
    }
}
