package io.github.noximiliencoxen.caelum.widget

import io.github.noximiliencoxen.caelum.lingua.Lingua
import io.github.noximiliencoxen.caelum.lingua.tr
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver.PendingResult
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.state.PreferencesGlanceStateDefinition
import io.github.noximiliencoxen.caelum.MainActivity
import io.github.noximiliencoxen.caelum.data.DeviceLocation
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.prefs.SettingsPrefs
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
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
 *   Tenta il GPS. Se disponibile, usa quello e lo annota come ultima
 *   posizione nota dell'istanza.
 *   Se il GPS e' null - e da dietro le quinte lo e' quasi sempre, vedi
 *   [WidgetPrefs.lastFix] - usa l'ultima posizione nota, poi [place]
 *   dell'istanza, e solo se non c'e' nessuno dei due la citta' dell'app.
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
internal suspend fun WidgetConfig.resolvePlace(context: Context, appWidgetId: Int): Place? {
    val prefs = WidgetPrefs(context)
    suspend fun fromApp(): Place = SettingsPrefs(context).settings.first().place
    suspend fun fromGps(): Place? =
        DeviceLocation.current(context)?.also { prefs.rememberFix(appWidgetId, it) }

    return when {
        // REGOLA 1: GPS richiesto
        useLocation -> fromGps() ?: prefs.lastFix(appWidgetId) ?: place ?: fromApp()
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

/**
 * Apre l'app sulla sala che corrisponde al widget: la luna sulla luna, la
 * settimana sulla settimana. Il `data` distingue i widget come in
 * [configureIntent]; `SINGLE_TOP` fa arrivare la richiesta anche all'app gia'
 * aperta, tramite `onNewIntent`.
 */
internal fun apriSalaIntent(context: Context, appWidgetId: Int, kind: WidgetKind, place: Place?): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .putExtra(MainActivity.EXTRA_SALA_WIDGET, kind.sala.name)
        .apply {
            // La citta' del widget, che l'app mostra senza farla sua.
            if (place != null) {
                putExtra(MainActivity.EXTRA_WIDGET_NOME, place.name)
                putExtra(MainActivity.EXTRA_WIDGET_REGIONE, place.admin)
                putExtra(MainActivity.EXTRA_WIDGET_PAESE, place.country)
                putExtra(MainActivity.EXTRA_WIDGET_LAT, place.latitude)
                putExtra(MainActivity.EXTRA_WIDGET_LON, place.longitude)
            }
        }
        .setData("caelum://sala/${kind.sala.name.lowercase()}/$appWidgetId".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

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
    /** Come si chiama per esteso, nella schermata di configurazione: italiano e inglese. */
    private val nome: Pair<String, String>,
    /** Come si chiama sul suo riquadro, dove lo spazio e' due celle. */
    private val nomeBreve: Pair<String, String>,
    /** Se ha bisogno di sapere dove si trova chi lo guarda. */
    val needsPlace: Boolean,
    /** Il ricevitore che il sistema sveglia per questo widget. */
    val receiver: Class<out ConfigurableWidgetReceiver>,
    private val make: () -> GlanceAppWidget,
    /** La sala che il tocco apre. */
    val sala: SalaRoom,
) {
    METEO("METEO" to "WEATHER", "METEO" to "WEATHER", true, WeatherWidgetReceiver::class.java, ::WeatherWidget, SalaRoom.OGGI),

    // La luna e' la stessa da qualunque parte la si guardi: chiederle una
    // citta' sarebbe una domanda senza conseguenze.
    LUNA("LUNA" to "MOON", "LUNA" to "MOON", false, MoonWidgetReceiver::class.java, ::MoonWidget, SalaRoom.LUNA),

    ARIA("QUALITÀ DELL'ARIA" to "AIR QUALITY", "ARIA" to "AIR", true, AirQualityWidgetReceiver::class.java, ::AirQualityWidget, SalaRoom.ARIA),

    SETTIMANA("SETTIMANA" to "WEEK", "SETTIMANA" to "WEEK", true, WeekWidgetReceiver::class.java, ::WeekWidget, SalaRoom.SETTIMANA),

    // Una cella: niente nome, solo tempo e temperatura.
    MINI("TEMPERATURA" to "TEMPERATURE", "MINI" to "MINI", true, MiniWidgetReceiver::class.java, ::MiniWidget, SalaRoom.OGGI),

    ORE("PROSSIME ORE" to "NEXT HOURS", "ORE" to "HOURS", true, HoursWidgetReceiver::class.java, ::HoursWidget, SalaRoom.OGGI),
    ;

    val label: String get() = tr(nome.first, nome.second)

    val shortLabel: String get() = tr(nomeBreve.first, nomeBreve.second)

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

    /** Quel che finisce sulla Home: il disegno e dove porta toccarlo. */
    private class Face(val drawn: Drawn, val onClick: Action)

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

    /**
     * **Il disegno si rifa' dentro la composizione, non solo prima.**
     *
     * Prima la configurazione si leggeva una volta, qui sopra, e l'immagine
     * che ne usciva restava fissa per tutta la vita della sessione Glance. E
     * la sessione vive: 45 secondi dopo ogni disegno, di piu' se arrivano
     * altri eventi. Mentre vive, `update()` **non** rilancia `provideGlance` -
     * ricarica solo lo stato Glance e ricompone (`GlanceAppWidget.update` →
     * `session.updateGlance()`, glance-appwidget 1.2.0). Ricomporre un'immagine
     * gia' fatta ridà la stessa immagine.
     *
     * Il caso che si vedeva sempre: il lanciatore disegna il widget appena
     * posato - senza citta', quindi "TOCCA PER SCEGLIERE LA CITTÀ" - e subito
     * dopo apre la configurazione. Chi sceglie in meno di 45 secondi salva, il
     * ridisegno trova la sessione ancora aperta, e l'invito resta li'. Chi ci
     * mette di piu' trova la sessione chiusa e vede la citta'. Da qui il "a
     * volte". Lo stesso valeva per il tocco che aggiorna e per il cambio di
     * tema.
     *
     * Adesso la composizione osserva le due cose da cui dipende il disegno: la
     * configurazione dell'istanza ([WidgetPrefs.watch]) e un contatore di
     * ridisegni nello stato Glance ([RedrawKey]), che il tocco e il cambio di
     * tema fanno avanzare. Cambia una delle due, e l'immagine si rifa' - con la
     * sessione aperta o chiusa, allo stesso modo.
     */
    final override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Il widget scrive nella lingua dell'app anche quando l'app e' chiusa.
        Lingua.carica(context)
        val appWidgetId = appWidgetIdOf(context, id)
        val prefs = WidgetPrefs(context)

        // Il primo disegno prima di provideContent, come prima: la Home non
        // deve vedere un fotogramma vuoto mentre si scarica.
        val firstConfig = prefs.load(appWidgetId)
        val firstRedraw = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[RedrawKey] ?: 0L
        val first = draw(context, appWidgetId, firstConfig)

        provideContent {
            val config by remember { prefs.watch(appWidgetId) }.collectAsState(firstConfig)
            val redraw = currentState(RedrawKey) ?: 0L

            var face by remember { mutableStateOf(first) }
            var faceOf by remember { mutableStateOf(firstConfig to firstRedraw) }
            LaunchedEffect(config, redraw) {
                val wanted = config to redraw
                if (wanted == faceOf) return@LaunchedEffect
                face = draw(context, appWidgetId, config)
                faceOf = wanted
            }

            WidgetImage(face.drawn.bitmap, face.drawn.spoken, face.onClick)
        }
    }

    private suspend fun draw(context: Context, appWidgetId: Int, config: WidgetConfig): Face {
        val frame = WidgetCanvas.plan(context, appWidgetId)
        val ink = WidgetInk.of(context)
        val type = WidgetType(context)

        val place = if (kind.needsPlace) config.resolvePlace(context, appWidgetId) else null

        // Nullo con `needsPlace` acceso vuol dire che questa istanza non e' mai
        // stata configurata. Non si ripiega sulla citta' dell'app: si dice che
        // manca, e il tocco porta a sceglierla. Vedi resolvePlace().
        if (kind.needsPlace && place == null) {
            val bitmap = withContext(Dispatchers.Default) {
                WidgetCanvas.paint(frame, ink.background) { setupArt(kind.shortLabel, type, ink) }
            }
            val spoken = tr(
                "Widget ${kind.label.lowercase()} da configurare. Tocca per scegliere la città.",
                "${kind.label.lowercase()} widget to set up. Tap to choose the city.",
            )
            return Face(Drawn(bitmap, spoken), actionStartActivity(configureIntent(context, appWidgetId)))
        }

        // Il tocco apre l'app sulla sala del widget. Prima riscaricava e
        // basta: il widget si aggiorna gia' da solo, e chi lo tocca vuole
        // saperne di piu', non lo stesso numero ridisegnato.
        return Face(paint(context, frame, place, type, ink), actionStartActivity(apriSalaIntent(context, appWidgetId, kind, place)))
    }
}

/**
 * Il contatore di ridisegni, nello stato Glance di ogni istanza.
 *
 * Il valore non dice niente: conta che cambi. Lo stato Glance e' l'unica cosa
 * che `update()` fa arrivare a una sessione gia' aperta, quindi e' l'unico modo
 * di dirle "rifai il disegno" invece di "ricomponi quello che hai".
 */
private val RedrawKey = longPreferencesKey("caelum_ridisegno")

/**
 * Ridisegna davvero, riscaricando: fa avanzare [RedrawKey] e poi aggiorna.
 *
 * Con la sessione chiusa `update()` la riapre e `provideGlance` riparte da capo;
 * con la sessione aperta il contatore cambiato fa ripartire il disegno dentro
 * la composizione. In entrambi i casi il risultato e' lo stesso.
 */
internal suspend fun WidgetKind.redraw(context: Context, glanceId: GlanceId) {
    updateAppWidgetState(context, glanceId) { it[RedrawKey] = (it[RedrawKey] ?: 0L) + 1 }
    widget().update(context, glanceId)
}

/**
 * Ridisegna il widget appena configurato.
 *
 * **Qui c'era un `delay(800)`**, e prima ancora un commento che diceva che
 * dopo la chiusura della sessione Glance `update()` non ha effetto. Era al
 * rovescio: con la sessione **chiusa** `update()` la riapre e `provideGlance`
 * rilegge tutto; e' con la sessione **aperta** che si limitava a ricomporre
 * l'immagine vecchia. Nessun numero poteva sistemarlo, perche' il difetto non
 * era di tempo.
 *
 * Adesso la sessione aperta si accorge da sola della configurazione nuova -
 * la osserva, vedi `CaelumWidget.provideGlance` - e [redraw] copre anche il
 * caso in cui la scelta salvata sia la stessa di prima.
 *
 * Resta `getGlanceIdBy` per tradurre l'identificativo di sistema in quello di
 * Glance, e il broadcast solo come ripiego se quella traduzione fallisce -
 * succede quando il lanciatore non ha ancora agganciato l'istanza. Li' ha
 * senso: e' esattamente cio' che il sistema fa da solo ogni mezz'ora.
 */
internal suspend fun refreshWidget(context: Context, appWidgetId: Int, kind: WidgetKind?) {
    val resolved = kind ?: WidgetKind.of(context, appWidgetId) ?: return

    val redrawn = runCatching {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        resolved.redraw(context, glanceId)
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
 * Se un tipo non ha widget in giro, l'elenco e' vuoto e non costa.
 *
 * [redraw] e non `updateAll`: `updateAll` su una sessione ancora aperta
 * ricomponeva l'immagine vecchia, con i colori vecchi.
 */
suspend fun repaintWidgets(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    WidgetKind.entries.forEach { kind ->
        runCatching {
            manager.getGlanceIds(kind.widget().javaClass).forEach { kind.redraw(context, it) }
        }
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

    // L'aggiornamento con la rete garantita parte col primo widget e si
    // ferma quando non ce n'e' piu' nessuno: vedi `AggiornaWidgetWorker`.
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AggiornaWidgetWorker.pianifica(context)
    }

    // Anche a ogni aggiornamento di sistema: chi aveva gia' i widget prima di
    // questa versione non ricevera' piu' `onEnabled`. `KEEP` la rende innocua.
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        AggiornaWidgetWorker.pianifica(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Solo se non resta nessun widget di nessun tipo: `onDisabled` arriva
        // per tipo, e togliere l'ultimo meteo non deve fermare la settimana.
        val manager = AppWidgetManager.getInstance(context)
        val restano = WidgetKind.entries.any { kind ->
            manager.getAppWidgetIds(ComponentName(context, kind.receiver)).isNotEmpty()
        }
        if (!restano) AggiornaWidgetWorker.annulla(context)
    }

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
