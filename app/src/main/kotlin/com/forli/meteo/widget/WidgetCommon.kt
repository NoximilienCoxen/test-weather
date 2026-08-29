package com.forli.meteo.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
 * La localita' da mostrare per questo widget.
 *
 * La configurazione viene letta dai campi primitivi del DataStore (lat, lon,
 * nome, admin, paese) — nessun parsing JSON, nessun runCatching silenzioso.
 * Se i campi primitivi sono presenti, [place] e' non-null con certezza.
 *
 * Gerarchia di risoluzione:
 *
 * REGOLA 1 — useLocation=true:
 *   Tenta il GPS. Se disponibile, usa quello.
 *   Se il GPS e' null (permessi revocati, antenna spenta), usa [place]
 *   dell'istanza come fallback immediato — NON la citta' globale dell'app.
 *   Solo se anche [place] e' null si usa il fallback globale.
 *
 * REGOLA 2 — useLocation=false e place!=null:
 *   Usa tassativamente la citta' scelta per questa istanza.
 *   NON controlla SettingsPrefs.
 *
 * REGOLA 3 — widget mai configurato (useLocation=false, place=null):
 *   Il widget non e' mai stato configurato. Unico caso in cui si usa
 *   la citta' globale dell'app come placeholder.
 */
internal suspend fun WidgetConfig.resolvePlace(context: Context): Place {
    suspend fun fromApp(): Place = SettingsPrefs(context).settings.first().place

    return when {
        // REGOLA 1: GPS richiesto
        useLocation -> DeviceLocation.current(context) ?: place ?: fromApp()
        // REGOLA 2: citta' manuale impostata per questa istanza
        place != null -> place
        // REGOLA 3: widget mai configurato — placeholder globale app
        else -> fromApp()
    }
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
 * Ridisegna il widget appena configurato forzando una nuova sessione Glance.
 *
 * Il problema: Glance con SizeMode.Exact apre una sessione corta — esegue
 * provideGlance, emette il contenuto, e chiude la sessione (SessionWorker).
 * Dopo la chiusura, chiamate a update() o a updateAppWidgetState() non hanno
 * effetto perche' non c'e' nessun Flow attivo che le osservi.
 *
 * La soluzione: inviare ACTION_APPWIDGET_UPDATE direttamente al receiver
 * del widget. Questo e' esattamente cio' che il sistema fa ogni 30 minuti
 * tramite updatePeriodMillis. Il receiver (GlanceAppWidgetReceiver) risponde
 * aprendo una nuova sessione Glance da zero, che esegue provideGlance e legge
 * le preferenze aggiornate dal DataStore.
 *
 * Il DataStore (widget_config) e' gia' stato scritto da save() prima che
 * questa funzione venga chiamata: il nuovo provideGlance trovera' i dati
 * corretti al primo accesso.
 */
internal suspend fun refreshWidget(context: Context, appWidgetId: Int, kind: WidgetKind?) {
    val resolved = kind ?: WidgetKind.of(context, appWidgetId) ?: return

    // Determina la classe receiver corretta per questo tipo di widget.
    val receiverClass = when (resolved) {
        WidgetKind.METEO -> WeatherWidgetReceiver::class.java
        WidgetKind.LUNA -> MoonWidgetReceiver::class.java
        WidgetKind.ARIA -> AirQualityWidgetReceiver::class.java
    }

    // Prima di inviare il broadcast attendiamo che la sessione Glance
    // corrente si chiuda. Se il widget e' appena stato renderizzato per
    // la prima volta (provideGlance iniziale), la sessione e' ancora aperta
    // e il broadcast verrebbe ignorato. Il SessionWorker di Glance impiega
    // tipicamente meno di un secondo a chiudersi dopo aver emesso il contenuto.
    // 800ms e' sufficiente nella pratica e non rallenta percettibilmente l'UX
    // perche' avviene mentre l'animazione di chiusura della config Activity
    // e' ancora in corso.
    delay(800)

    // Invia ACTION_APPWIDGET_UPDATE al receiver specifico con l'id del widget.
    // Questo e' il meccanismo nativo del sistema: apre una nuova sessione
    // Glance da zero e garantisce l'esecuzione di provideGlance con le
    // preferenze aggiornate gia' presenti nel DataStore.
    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
        component = ComponentName(context, receiverClass)
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
    }
    context.sendBroadcast(intent)
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
