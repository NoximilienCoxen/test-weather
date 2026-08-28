package com.forli.meteo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import com.forli.meteo.data.DeviceLocation
import com.forli.meteo.data.Place
import com.forli.meteo.prefs.SettingsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Il widget, che ormai e' un'immagine sola.
 *
 * A tutto riquadro e **deformata**, non contenuta: il disegno e' gia' della
 * forma esatta del riquadro, e "contenere" lascerebbe due bande da cui si vede
 * la schermata sotto.
 */
@Composable
internal fun WidgetImage(bitmap: Bitmap, description: String, onClick: Action) {
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = description,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxSize().clickable(onClick),
    )
}

/**
 * La localita' da mostrare.
 *
 * Gerarchia di fallback:
 * 1. Se `useLocation` e' true: prova il GPS del dispositivo.
 * 2. Se il GPS non e' disponibile o i permessi mancano: usa `place` salvata
 *    nella configurazione dell'istanza (potrebbe essere non-null anche con
 *    useLocation=true se l'utente l'aveva impostata in precedenza).
 * 3. Solo se anche `place` e' null: ripiegare sulla localita' globale dell'app.
 *
 * Un widget mai configurato ripiega sulla localita' dell'app: mostrare niente
 * sarebbe peggio che mostrare il posto sbagliato.
 */
internal suspend fun WidgetConfig.resolvePlace(context: Context): Place {
    suspend fun fromApp(): Place = SettingsPrefs(context).settings.first().place

    // REGOLA 1: useLocation=true → tenta GPS
    // REGOLA 2: useLocation=false e place!=null → usa la città dell'istanza
    // REGOLA 3 (fallback): GPS fallisce E place==null → città globale app
    val resolved = when {
        useLocation -> {
            Log.d("WidgetResolve", "resolvePlace: useLocation=true, tento GPS...")
            val gps = DeviceLocation.current(context)
            if (gps != null) {
                Log.d("WidgetResolve", "resolvePlace: REGOLA 1 → GPS: ${gps.name}")
                gps
            } else {
                // GPS non disponibile: usa place dell'istanza se c'è,
                // altrimenti fallback globale app.
                val fallback = place
                if (fallback != null) {
                    Log.d("WidgetResolve", "resolvePlace: GPS null, REGOLA 2 (fallback) → place istanza: ${fallback.name}")
                    fallback
                } else {
                    val app = fromApp()
                    Log.d("WidgetResolve", "resolvePlace: GPS null, place null, REGOLA 3 → app: ${app.name}")
                    app
                }
            }
        }
        place != null -> {
            Log.d("WidgetResolve", "resolvePlace: REGOLA 2 → città manuale istanza: ${place.name}")
            place
        }
        else -> {
            val app = fromApp()
            Log.d("WidgetResolve", "resolvePlace: REGOLA 3 → nessuna config istanza, usa app: ${app.name}")
            app
        }
    }
    return resolved
}

internal suspend fun appWidgetIdOf(context: Context, glanceId: GlanceId): Int =
    GlanceAppWidgetManager(context).getAppWidgetId(glanceId)

/**
 * Quale dei tre widget e' quello con questo identificativo.
 *
 * Si chiede al sistema invece di farselo passare: la configurazione riceve solo
 * un identificativo, e tre Activity quasi identiche per distinguere tre widget
 * sarebbero tre volte lo stesso codice.
 */
enum class WidgetKind {
    METEO, LUNA, ARIA;

    fun widget(): GlanceAppWidget = when (this) {
        METEO -> WeatherWidget()
        LUNA -> MoonWidget()
        ARIA -> AirQualityWidget()
    }

    companion object {
        fun of(context: Context, appWidgetId: Int): WidgetKind? =
            when (
                AppWidgetManager.getInstance(context)
                    .getAppWidgetInfo(appWidgetId)?.provider?.className
            ) {
                MoonWidgetReceiver::class.java.name -> LUNA
                AirQualityWidgetReceiver::class.java.name -> ARIA
                WeatherWidgetReceiver::class.java.name -> METEO
                // Nessun tipo: il widget non risulta ancora agganciato. Non si
                // tira a indovinare, perche' ridisegnare il widget sbagliato
                // significa mettere la luna al posto della temperatura.
                else -> null
            }
    }
}

/**
 * Ridisegna il widget appena configurato e **attende** il completamento.
 *
 * Serve davvero, e non e' una cortesia: il lanciatore aggancia il widget
 * **prima** di aprire la configurazione, quindi a quel punto e' gia' stato
 * disegnato una volta con le preferenze ancora vuote. Senza questo ridisegno
 * la tinta appena scelta non comparirebbe fino al risveglio successivo.
 *
 * Garanzia anti-race: `WidgetPrefs.save()` e' una suspend function che
 * completa il flush su DataStore prima di tornare. `update()` viene chiamato
 * solo dopo quel completamento, cosi' `provideGlance` legge sempre le
 * preferenze gia' scritte. `withContext(Dispatchers.IO)` forza l'esecuzione
 * sul pool IO, lo stesso usato da DataStore internamente, eliminando qualsiasi
 * coda di scrittura pendente prima che Glance legga.
 */
internal suspend fun refreshWidget(context: Context, appWidgetId: Int, kind: WidgetKind?) {
    val resolved = kind ?: WidgetKind.of(context, appWidgetId) ?: return
    // Forza la lettura delle preferenze su IO per svuotare la coda DataStore
    // prima di schedulare il ridisegno: elimina la race condition tra save()
    // e il primo provideGlance().
    val confirmedConfig = withContext(Dispatchers.IO) {
        WidgetPrefs(context).load(appWidgetId)
    }
    Log.d("WidgetResolve", "refreshWidget: widget=$appWidgetId kind=${resolved.name} " +
        "| DataStore confermato → useLocation=${confirmedConfig.useLocation}, " +
        "place=${confirmedConfig.place?.name ?: "null"}")
    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    resolved.widget().update(context, glanceId)
}

/**
 * Ridisegna tutti i widget piazzati, di qualunque tipo.
 *
 * Serve al cambio di tema: le immagini gia' dipinte non si ricolorano da sole.
 * Se un tipo non ha widget in giro, `updateAll` non fa niente e non costa.
 */
suspend fun repaintWidgets(context: Context) {
    WidgetKind.entries.forEach { kind ->
        runCatching { kind.widget().updateAll(context) }
    }
}

/**
 * Il ricevitore di un widget configurabile.
 *
 * Esiste solo per buttare via le scelte di un widget che non c'e' piu': senza,
 * l'archivio crescerebbe a ogni widget aggiunto e tolto, e l'identificativo
 * riassegnato a un widget nuovo si porterebbe dietro i colori del precedente.
 */
abstract class ConfigurableWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // La cancellazione arriva come broadcast, che non aspetta le coroutine:
        // goAsync() tiene in vita il processo il tempo di scrivere.
        val pending = goAsync()
        val prefs = WidgetPrefs(context.applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { prefs.forget(it) }
            } finally {
                pending.finish()
            }
        }
    }
}
