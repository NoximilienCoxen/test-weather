package io.github.noximiliencoxen.caelum.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver.PendingResult
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import io.github.noximiliencoxen.caelum.data.DeviceLocation
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.prefs.SettingsPrefs
import io.github.noximiliencoxen.caelum.widget.paint.Frame
import io.github.noximiliencoxen.caelum.widget.paint.WidgetCanvas
import io.github.noximiliencoxen.caelum.widget.paint.WidgetInk
import io.github.noximiliencoxen.caelum.widget.paint.WidgetType
import io.github.noximiliencoxen.caelum.widget.paint.setupArt
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
 *   Restituisce `null`. **Non** ripiega sulla citta' globale dell'app.
 *
 * Il `null` della REGOLA 3 e' la correzione di un difetto vero, non una
 * pignoleria di tipi. Prima quel ramo restituiva la citta' dell'app, e un
 * widget senza configurazione disegnava la localita' aperta nell'app: da fuori
 * era **indistinguibile** da un widget che funziona. Chi sceglieva Palermo e si
 * vedeva la propria citta' non aveva modo di sapere se la scelta non fosse
 * stata salvata o non fosse stata riletta, e la stessa immagine plausibile
 * copriva le due cose. Adesso l'assenza di configurazione ha una faccia sua -
 * i widget disegnano "TOCCA PER CONFIGURARE" - e il difetto, se torna, si
 * legge dallo schermo invece che dal logcat.
 *
 * Dentro la REGOLA 1 il ripiego sulla citta' dell'app **resta**: li' e' una
 * rete per il GPS che non risponde a un widget configurato apposta per
 * seguirlo, non il mascheramento di una configurazione che non c'e'.
 */
internal suspend fun WidgetConfig.resolvePlace(context: Context): Place? {
    suspend fun fromApp(): Place = SettingsPrefs(context).settings.first().place

    return when {
        // REGOLA 1: GPS richiesto
        useLocation -> DeviceLocation.current(context) ?: place ?: fromApp()
        // REGOLA 2: citta' manuale impostata per questa istanza
        place != null -> place
        // REGOLA 3: widget mai configurato — nessuna localita' da mostrare
        else -> null
    }
}

/**
 * Apre la configurazione di **questa** istanza del widget.
 *
 * Serve al widget non configurato: il disegno che dice di toccare per
 * configurare deve poi portarci davvero, se no e' un cartello che indica un
 * muro. L'`appWidgetId` va messo nell'intento perche' e' l'unica cosa che
 * `WidgetConfigActivity` legge per sapere di chi sta parlando.
 */
internal fun configureIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, WidgetConfigActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // **Il `data` non lo legge nessuno: serve a farli diversi.** Il tocco
        // diventa un PendingIntent, e due PendingIntent si considerano lo
        // stesso quando gli intenti sono uguali secondo `filterEquals`, che
        // guarda azione, componente e dati - **non** gli extra. Con due widget
        // sulla Home il secondo riuserebbe il permesso del primo e aprirebbe la
        // configurazione del widget sbagliato. L'identificativo nei dati rompe
        // il pareggio.
        .setData("caelum://widget/$appWidgetId".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal suspend fun appWidgetIdOf(context: Context, glanceId: GlanceId): Int =
    GlanceAppWidgetManager(context).getAppWidgetId(glanceId)

/**
 * Quale widget e' quello con questo identificativo, e cosa si sa di lui.
 *
 * **Questo elenco e' l'unico posto in cui un widget si dichiara.** Prima le
 * stesse tre risposte erano sparse: il tipo si deduceva con un `when` qui, il
 * nome da mostrare con un altro `when` nella schermata di configurazione, il
 * fatto che la luna non voglia una citta' con un `kind != LUNA` scritto a mano,
 * e la classe del ricevitore con un terzo `when` in `refreshWidget`. Quattro
 * elenchi da tenere allineati, e un widget nuovo che ne dimenticasse uno
 * sarebbe rotto in un modo diverso a seconda di quale.
 *
 * Adesso aggiungere un widget e' una riga qui - piu' la sua classe, il suo
 * ricevitore, il suo XML e la voce nel manifesto, che sono richiesti da Android
 * e non da noi. Tutto il resto - la configurazione, il ripiego quando la citta'
 * non c'e', il ridisegno al tocco - arriva da [CaelumWidget] senza copiare
 * niente.
 *
 * Il tipo si chiede al sistema invece di farselo passare: la configurazione
 * riceve solo un identificativo, e tante Activity quasi identiche per
 * distinguere tanti widget sarebbero tante volte lo stesso codice.
 */
enum class WidgetKind(
    /** Come si chiama per esteso, nella schermata di configurazione. */
    val label: String,
    /** Come si chiama sul suo riquadro, dove lo spazio e' due celle. */
    val shortLabel: String,
    /** Se ha bisogno di sapere dove si trova chi lo guarda. */
    val needsPlace: Boolean,
    /** Il ricevitore che il sistema sveglia per questo widget. */
    val receiver: Class<out ConfigurableWidgetReceiver>,
    private val make: () -> GlanceAppWidget,
) {
    METEO("METEO", "METEO", true, WeatherWidgetReceiver::class.java, ::WeatherWidget),

    // La luna e' la stessa da qualunque parte la si guardi: chiederle una
    // citta' sarebbe una domanda senza conseguenze.
    LUNA("LUNA", "LUNA", false, MoonWidgetReceiver::class.java, ::MoonWidget),

    ARIA("QUALITÀ DELL'ARIA", "ARIA", true, AirQualityWidgetReceiver::class.java, ::AirQualityWidget),
    ;

    fun widget(): GlanceAppWidget = make()

    companion object {
        fun of(context: Context, appWidgetId: Int): WidgetKind? {
            val provider = AppWidgetManager.getInstance(context)
                .getAppWidgetInfo(appWidgetId)?.provider?.className
            // Nessun tipo: il widget non risulta ancora agganciato. Non si
            // tira a indovinare, perche' ridisegnare il widget sbagliato
            // significa mettere la luna al posto della temperatura.
            return entries.firstOrNull { it.receiver.name == provider }
        }
    }
}

/**
 * Il tronco comune dei widget di Caelum.
 *
 * **Esiste perche' la cosa giusta da fare era ricopiata a mano.** Leggere
 * l'identificativo, caricare la configurazione di quell'istanza, accorgersi che
 * non c'e' una citta', disegnare l'invito a sceglierla e agganciarci sopra il
 * tocco che porta alla configurazione: sette passi che stavano scritti per
 * intero dentro ogni widget, e che un widget nuovo doveva ricopiare sapendo
 * quali. Chi ne dimenticava uno otteneva un widget che *sembrava* funzionare -
 * disegnava una citta' qualsiasi - ed e' esattamente il difetto da cui e'
 * partita tutta questa storia.
 *
 * Qui i sette passi stanno una volta sola e `provideGlance` e' `final`: un
 * widget nuovo non puo' sbagliarli perche' non li scrive. Quel che scrive e'
 * [paint], cioe' il suo disegno, che e' l'unica cosa che lo distingue davvero.
 */
internal abstract class CaelumWidget(private val kind: WidgetKind) : GlanceAppWidget() {

    // Esatto e non a scaglioni: con i tagli predefiniti Glance disegnerebbe
    // un'immagine per ciascuno e il sistema ne sommerebbe il peso.
    final override val sizeMode = SizeMode.Exact

    /** Un'immagine gia' dipinta e cosa dirne a chi non la vede. */
    data class Drawn(val bitmap: Bitmap, val spoken: String)

    /**
     * Il disegno di questo widget.
     *
     * [place] e' non-null per tutti i widget che dichiarano `needsPlace`: il
     * caso "non configurato" e' gia' stato intercettato prima di arrivare qui,
     * quindi qui non c'e' nessun ripiego da inventare.
     */
    protected abstract suspend fun paint(
        context: Context,
        frame: Frame,
        place: Place?,
        type: WidgetType,
        ink: WidgetInk,
    ): Drawn

    final override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = appWidgetIdOf(context, id)
        val frame = WidgetCanvas.plan(context, appWidgetId)
        val ink = WidgetInk.of(context)
        val type = WidgetType(context)

        val place = if (kind.needsPlace) {
            WidgetPrefs(context).load(appWidgetId).resolvePlace(context)
        } else {
            null
        }

        // Nullo con `needsPlace` acceso vuol dire che questa istanza non e' mai
        // stata configurata. Non si ripiega sulla citta' dell'app: si dice che
        // manca, e il tocco porta a sceglierla. Vedi resolvePlace().
        if (kind.needsPlace && place == null) {
            val bitmap = withContext(Dispatchers.Default) {
                WidgetCanvas.paint(frame, ink.background) { setupArt(kind.shortLabel, type, ink) }
            }
            provideContent {
                WidgetImage(
                    bitmap = bitmap,
                    description = "Widget ${kind.label.lowercase()} da configurare. " +
                        "Tocca per scegliere la città.",
                    onClick = actionStartActivity(configureIntent(context, appWidgetId)),
                )
            }
            return
        }

        val drawn = paint(context, frame, place, type, ink)
        provideContent {
            WidgetImage(drawn.bitmap, drawn.spoken, actionRunCallback<RefreshWidgetAction>())
        }
    }
}

/**
 * Un tocco sul widget forza un nuovo scaricamento.
 *
 * Una sola per tutti: quale widget ridisegnare lo dice l'identificativo, non la
 * classe della callback. Prima ce n'erano tre identiche, e un widget nuovo ne
 * voleva una quarta.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = appWidgetIdOf(context, glanceId)
        WidgetKind.of(context, appWidgetId)?.widget()?.update(context, glanceId)
    }
}

/**
 * Ridisegna il widget appena configurato.
 *
 * **Qui c'era un `delay(800)`, e la sua storia vale il commento** - perche' non
 * era un numero tarato male, era una premessa sbagliata.
 *
 * Il commento che se n'e' andato diceva che dopo la chiusura della sessione
 * Glance le chiamate a `update()` non hanno effetto, "perché non c'è nessun
 * Flow attivo che le osservi". Da li' veniva tutto il resto: se `update()` non
 * serve serve un broadcast, e se serve un broadcast bisogna aspettare che la
 * sessione si chiuda, e per aspettare serve un numero. Tre commit di fila
 * hanno spostato quel numero.
 *
 * **La premessa non regge.** `GlanceAppWidget.update()` passa per
 * `getOrCreateAppWidgetSession`: se una sessione non c'e', la **crea**, e
 * `provideGlance` riparte da capo rileggendo il DataStore (verificato nel
 * bytecode di glance-appwidget 1.2.0). Non c'era niente da attendere, e gli
 * ottocento millisecondi erano per giunta una scommessa persa in partenza -
 * quella sessione dentro `provideGlance` fa una richiesta **di rete**, che su
 * rete lenta dura molto di piu'.
 *
 * Resta `getGlanceIdBy` per tradurre l'identificativo di sistema in quello di
 * Glance, e il broadcast solo come ripiego se quella traduzione fallisce -
 * succede quando il lanciatore non ha ancora agganciato l'istanza. Li' ha
 * senso: e' esattamente cio' che il sistema fa da solo ogni mezz'ora.
 */
internal suspend fun refreshWidget(context: Context, appWidgetId: Int, kind: WidgetKind?) {
    val resolved = kind ?: WidgetKind.of(context, appWidgetId) ?: return

    // Rilegge le preferenze prima di procedere, e il valore non serve a
    // nessuno: serve la **lettura**, che sospende finche' il DataStore non
    // consegna, cioe' finche' la scrittura appena fatta non e' visibile. Da
    // qui in poi il nuovo provideGlance trovera' i dati aggiornati.
    withContext(Dispatchers.IO) { WidgetPrefs(context).load(appWidgetId) }

    val redrawn = runCatching {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        resolved.widget().update(context, glanceId)
    }.isSuccess
    if (redrawn) return

    // Ripiego: ACTION_APPWIDGET_UPDATE al receiver di questo tipo, con l'id.
    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
        component = ComponentName(context, resolved.receiver)
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
        // **`goAsync()` si consegna una volta sola, e qui lo chiedono in due.**
        // Azzera il proprio campo interno subito dopo averlo dato: il secondo
        // che lo chiede riceve `null`. `GlanceAppWidgetReceiver.onDeleted` lo
        // chiede gia' per conto suo, per cancellare il proprio stato, quindi
        // uno dei due resta sempre a mani vuote.
        //
        // Prima restavamo a mani vuote noi, e siccome `PendingResult!` e' un
        // tipo di piattaforma Kotlin lasciava passare l'assegnazione: il conto
        // arrivava nel `finally`, come NullPointerException, e il processo
        // moriva a ogni widget tolto dalla Home.
        //
        // **Invertire l'ordine non risolve, sposta.** Provato sul telefono:
        // chiedendolo noi per primi schianta Glance, in
        // `CoroutineBroadcastReceiver.kt:70`, e il processo muore lo stesso.
        // Quel `finish()` non e' protetto - una lettura frettolosa del bytecode
        // diceva di si', il logcat dice di no, e il logcat ha ragione.
        //
        // Quindi `super` per primo, che e' l'unico ad avere bisogno per forza
        // del permesso, e il nostro `pending` nullo per quasi tutte le volte.
        // Non e' un peccato: la ripulita e' comunque **al meglio possibile**, e
        // il caso in cui salta - processo ucciso a meta' - lascia qualche
        // chiave orfana, che la configurazione riscrive per intero appena
        // quell'identificativo viene riusato. Un archivio leggermente sporco,
        // non un'app che termina.
        //
        // Il permesso arriva davvero quando Glance passa dal suo
        // AsyncRequestWorker invece che da goAsync: in quel caso lo prendiamo
        // noi, e allora la scrittura e' garantita. Da qui il `?.`.
        super.onDeleted(context, appWidgetIds)
        val pending: PendingResult? = goAsync()
        val prefs = WidgetPrefs(context.applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { prefs.forget(it) }
            } finally {
                pending?.finish()
            }
        }
    }
}
