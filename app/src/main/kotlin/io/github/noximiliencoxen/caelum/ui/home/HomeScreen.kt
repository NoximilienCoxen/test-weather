package io.github.noximiliencoxen.caelum.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.data.isWet
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.alerts.AlertBanner
import io.github.noximiliencoxen.caelum.ui.alerts.AlertPill
import io.github.noximiliencoxen.caelum.ui.asBigDegrees
import io.github.noximiliencoxen.caelum.ui.asPlainDegrees
import io.github.noximiliencoxen.caelum.ui.common.MeteoIconButton
import io.github.noximiliencoxen.caelum.ui.common.MinTouchTarget
import io.github.noximiliencoxen.caelum.ui.motion.PhysicalNumber
import io.github.noximiliencoxen.caelum.ui.motion.SceneRotation
import io.github.noximiliencoxen.caelum.ui.motion.rememberSceneRotation
import io.github.noximiliencoxen.caelum.ui.motion.rotatesScene
import io.github.noximiliencoxen.caelum.ui.render3d.SceneContact
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.MeteoType
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Quello che serve sapere aprendo l'app: che tempo fa adesso, quanti gradi, e
 * come sara' nelle prossime ore. Tutto il resto sta un trascinamento piu' su.
 */
/**
 * I 48dp in cima a destra: vuoti, o col pallino dell'allerta.
 *
 * **Sta in una funzione sua per una ragione di compilazione, non di stile.**
 * Dentro il `Box`, in mezzo alla `Row` della barra, ci sono due riceventi
 * impliciti - `BoxScope` da vicino e `RowScope` da fuori - e Kotlin risolve
 * `AnimatedVisibility` sulla versione di `RowScope`, che li' non puo' chiamare:
 * *cannot be called in this context with an implicit receiver*. In una funzione
 * a se' `RowScope` non c'e', e resta una chiamata sola possibile.
 */
@Composable
private fun AlertPillSlot(
    alerts: List<WeatherAlert>,
    collapsed: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        // Larghezza fissa **anche da vuoto**: e' cio' che tiene il nome della
        // localita' al centro dello schermo invece che al centro di quel che
        // avanza, e che impedisce al nome di spostarsi quando il pallino
        // compare o sparisce.
        modifier = modifier.width(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = collapsed,
            enter = scaleIn(ALERT_SPRING) + fadeIn(ALERT_SPRING),
            exit = scaleOut(ALERT_SPRING) + fadeOut(ALERT_SPRING),
        ) {
            AlertPill(alerts = alerts, onOpen = onOpen)
        }
    }
}

/**
 * La molla del passaggio fra la fascia dell'allerta e il pallino.
 *
 * Una sola, dichiarata qui, perche' i due si muovono **insieme**: con due
 * animazioni tarate a parte il pallino comparirebbe prima che la fascia abbia
 * finito di chiudersi, e per un istante ci sarebbero due avvisi in scena.
 * Stessi valori dei quattro strati di `MeteoApp`.
 */
private val ALERT_SPRING = spring<Float>(dampingRatio = 0.9f, stiffness = 420f)

private val ALERT_SPRING_SIZE = spring<IntSize>(dampingRatio = 0.9f, stiffness = 420f)

@Composable
fun HomeScreen(
    state: UiState,
    sky: SkyState,
    tilt: State<Offset>,
    onSelectHour: (Int) -> Unit,
    onBackToNow: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTemperatureDetail: () -> Unit = {},
    /** Un giorno della striscia della settimana, toccato: apre il suo dettaglio. */
    onOpenDay: (Int) -> Unit = {},
    onOpenAlerts: () -> Unit = {},
    /** Riduce la fascia dell'allerta al pallino. */
    onDismissAlerts: () -> Unit = {},
    /** Riapre la fascia **e** il bollettino: e' il tocco sul pallino. */
    onReopenAlerts: () -> Unit = {},
    onRefresh: () -> Unit = {},
    /** Vero quando il tiro verso il basso basta gia' a chiedere una ricarica. */
    pullArmed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val hours = state.hours
    val hour = state.hour

    // Un solo orientamento per la scultura e la cifra: sono un oggetto solo
    // visto da un punto solo, e il gesto che li gira e' lo stesso.
    val rotation: SceneRotation = rememberSceneRotation()

    // L'aggancio di verifica, se c'e'. Fuori dalla composizione: scrivere lo
    // stato del gesto mentre si compone significherebbe comporre due volte per
    // qualcosa che in uso normale non succede mai.
    LaunchedEffect(state.forcedYawDeg) { rotation.pin(state.forcedYawDeg) }

    // E un solo mondo: la pioggia esce dalla nuvola e finisce sulla cifra, che
    // sta in un'altra tela. Qui passano la sagoma e le due origini.
    val contact = remember { SceneContact() }

    // Letti dentro il gesto e non catturati alla composizione: `pointerInput`
    // parte una volta sola (chiave `Unit`) e altrimenti continuerebbe a
    // chiamare la lambda di quel primo giro, con dentro un `rotation` o un
    // `onOpenTemperatureDetail` ormai vecchi.
    val liveRotation by rememberUpdatedState(rotation)
    val liveOnOpenDetail by rememberUpdatedState(onOpenTemperatureDetail)

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 24.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsButton(onClick = onOpenSettings)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.place.name.uppercase(),
                    style = MeteoType.caption,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                // La riga sotto il nome dice sempre qualcosa, e non e' un
                // riempitivo: di norma il giorno - la barra copre oggi e basta,
                // quindi vale la pena dire quale oggi - e quando serve prende
                // il posto per dire che il dato e' vecchio, che si sta
                // ricaricando, o che basta lasciare il dito.
                //
                // Sempre presente e non a comparsa: apparendo e sparendo
                // sposterebbe in su e in giu' tutto quello che ha sotto.
                val stale = rememberFreshness(state.fetchedAt)
                Text(
                    text = when {
                        pullArmed -> "RILASCIA"
                        state.refreshing -> "AGGIORNO"
                        stale != null -> stale
                        else -> dayLabel(hour)
                    },
                    style = MeteoType.caption,
                    color = if (stale != null || pullArmed || state.refreshing) {
                        colors.text
                    } else {
                        colors.line
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRefresh,
                        ),
                )
            }
            // Occupa quanto il pulsante a sinistra, cosi' il nome resta al
            // centro dello schermo e non al centro di quel che avanza.
            //
            // Quando la fascia dell'allerta e' ridotta, questi 48dp non restano
            // vuoti: ci sta il pallino. E' la stessa misura - `MinTouchTarget`,
            // cioe' quella di `MeteoIconButton` - quindi il nome della localita'
            // non si sposta di un pixel fra i due stati, e il pallino non ruba
            // altezza a niente. Che e' esattamente cio' che si cerca chiudendo
            // la fascia.
            AlertPillSlot(
                alerts = state.shownAlerts,
                collapsed = state.alertsCollapsed,
                onOpen = onReopenAlerts,
            )
        }

        // L'allerta sta qui, in cima alla schermata che si apre per prima.
        //
        // Dipinge un fondo proprio e prende i colori dal tema dei pannelli, non
        // dal cielo: e' l'unico riquadro che deve leggersi uguale a mezzanotte
        // e a mezzogiorno, mentre tutto il resto di questa schermata cambia con
        // l'ora. Se non ci sono allerte non occupa spazio.
        // Ridotta, la fascia lascia il posto al pallino nella riga qui sopra.
        // I due **non si scambiano, passano**: sono lo stesso avviso in due
        // misure, e uno scambio secco li farebbe leggere come due oggetti
        // diversi che si danno il cambio. La fascia si accartoccia in altezza
        // mentre il pallino cresce, con la stessa molla dei quattro strati di
        // MeteoApp.
        AnimatedVisibility(
            visible = !state.alertsCollapsed && state.shownAlerts.isNotEmpty(),
            enter = expandVertically(ALERT_SPRING_SIZE) + fadeIn(ALERT_SPRING),
            exit = shrinkVertically(ALERT_SPRING_SIZE) + fadeOut(ALERT_SPRING),
        ) {
            AlertBanner(
                alerts = state.shownAlerts,
                onOpen = onOpenAlerts,
                onDismiss = onDismissAlerts,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        // Scultura e cifra dentro lo stesso riquadro sensibile: il dito li
        // gira insieme dovunque lo si appoggi, invece di dover indovinare
        // quale dei due accetta il gesto.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .rotatesScene(rotation),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WeatherSculpture(
                weatherCode = state.forcedWeatherCode ?: hour?.weatherCode,
                // L'aggancio di verifica deve restare fedele: imporre pioggia a
                // qualunque codice faceva piovere anche su "coperto", che e'
                // asciutto. Solo i codici bagnati portano gocce.
                precipitationMm = state.forcedWeatherCode
                    ?.let { if (Wmo.family(it).isWet()) 2.5 else 0.0 }
                    ?: hour?.precipitation,
                probability = state.forcedWeatherCode
                    ?.let { if (Wmo.family(it).isWet()) 80 else 0 }
                    ?: hour?.precipProbability,
                sky = sky,
                date = hour?.time?.toLocalDate() ?: LocalDate.now(),
                rotation = rotation,
                tilt = tilt,
                // Dietro le impostazioni la schermata resta viva: senza questo
                // il telefono continuerebbe a vibrare di pioggia mentre si
                // sceglie una citta'.
                feelsIt = !state.settingsOpen,
                contact = contact,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.62f),
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    // Un tocco fermo apre il dettaglio, un trascinamento gira
                    // la scena: sono lo stesso dito sullo stesso numero, e la
                    // differenza si vede solo a gesto finito. Questo
                    // `pointerInput` sta sul figlio e non lascia salire
                    // l'evento al genitore - che ha il suo `.rotatesScene()` -
                    // altrimenti i due gestori risponderebbero insieme allo
                    // stesso trascinamento.
                    .pointerInput(Unit) {
                        detectTapOrRotate(liveRotation) { liveOnOpenDetail() }
                    },
            ) {
                // Finche' non c'e' un numero non si disegna niente. Un "--"
                // alto mezzo schermo, con tanto di spessore e di ombra, non
                // dice "sto aspettando": dice che l'app e' rotta.
                val degrees = hour?.temperature
                if (degrees != null) PhysicalNumber(
                    text = degrees.asBigDegrees(state.unit),
                    // Il grado e' l'ultimo carattere e non e' una cifra: va in
                    // corpo ridotto, a filo della cima delle altre.
                    smallTail = 1,
                    fontSize = maxHeight * 0.86f,
                    rotation = rotation,
                    tilt = tilt,
                    // Un filo verso l'alto: la cifra e la scultura devono
                    // leggersi come un oggetto solo, e fra loro non ci deve
                    // stare il vuoto che ci starebbe centrandole entrambe.
                    verticalBias = -0.04f,
                    contact = contact,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Crossfade e non sostituzione secca: scorrendo le ore la condizione
        // cambia spesso, e uno scatto di testo si nota piu' del testo stesso.
        // Al posto della condizione, finche' non c'e', si dice cosa sta
        // succedendo. Uno schermo fermo sui trattini lascia credere che
        // l'attesa sia il risultato.
        Crossfade(
            targetState = when {
                // L'errore prende la parola solo se non c'e' altro da dire. Una
                // ricarica fallita mentre si ha in mano una giornata intera di
                // dati validi non deve cancellare la condizione per annunciare
                // che la rete non risponde: il dato vecchio resta, e a dire che
                // e' vecchio ci pensa la riga in alto.
                hour == null && state.error != null -> state.error.uppercase()
                hour == null -> "IN ATTESA DEI DATI"
                else -> conditionLabel(hour, state.forcedWeatherCode)
            },
            label = "condizione",
            modifier = Modifier.fillMaxWidth(),
        ) { label ->
            Text(
                text = label,
                style = MeteoType.label,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(6.dp))

        // Minima, massima e percepita: sono gia' nella stessa risposta che
        // porta la temperatura, e finora non le leggeva nessuno.
        val today = hour?.time?.let { state.forecast?.dayOf(it) }
        Text(
            text = rangeLabel(
                min = today?.tempMin,
                max = today?.tempMax,
                apparent = hour?.apparent,
                real = hour?.temperature,
                unit = state.unit,
            ),
            style = MeteoType.caption,
            color = colors.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        // ── Ore e settimana, nello stesso posto ────────────────────────────────
        //
        // Sono due domande diverse sulla stessa previsione - "quando, dentro
        // oggi" e "quale giorno" - e messe una sotto l'altra costringerebbero la
        // scultura a stringersi per far posto a entrambe. Qui si danno il cambio.
        //
        // La scelta sopravvive alla rotazione dello schermo (`rememberSaveable`)
        // ma non alla chiusura dell'app: e' un modo di guardare, non una
        // preferenza, e riaprendo l'app la domanda e' di nuovo "che tempo fa
        // adesso".
        var settimana by rememberSaveable { mutableStateOf(false) }

        BarSwitch(
            settimana = settimana,
            onChoose = { settimana = it },
        )

        // L'altezza cambia - la settimana e' alta quattro righe, le ore una - e
        // il riquadro la insegue invece di saltarci. Il salto qui e' voluto da
        // chi tocca, non subito come quello che il commento sotto evita, ma
        // resta uno strappo di ottanta punti in mezzo allo schermo: animarlo
        // costa una riga e lo rende un movimento invece che uno scatto.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (settimana) {
                WeekBar(
                    days = state.forecast?.days.orEmpty(),
                    unit = state.unit,
                    onOpenDay = onOpenDay,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            } else {
                HourBar(
                    hours = hours,
                    selected = state.selectedHour,
                    nowIndex = state.nowIndex,
                    sunrise = today?.sunrise,
                    sunset = today?.sunset,
                    onSelect = onSelectHour,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }

        // Tornare all'ora vera deve costare un tocco. Scorrendo la barra si
        // finisce facilmente lontani, e ritrovare la posizione a mano annulla
        // il senso di aver aperto l'app per sapere che tempo fa adesso.
        //
        // **Adesso e' un tasto e basta.** Prima era l'etichetta dell'ora che
        // faceva anche da tasto: un bersaglio con due mestieri, e per giunta
        // spento proprio quando diceva la cosa piu' utile. L'ora se n'e' andata
        // nella bolla sopra il cursore, dove il pollice non la copre, e qui
        // resta il solo comando.
        val onNow = state.selectedHour == state.nowIndex
        Box(
            // L'altezza si riserva anche quando il tasto non c'e'. Comparendo e
            // sparendo a ogni scorrimento farebbe saltare in su e in giu' la
            // scultura che sta sopra, e un sussulto a ogni ora scelta e' peggio
            // dei pochi punti che si risparmierebbero.
            //
            // Il margine di sotto e' largo apposta: tutta la colonna vive dello
            // spazio che avanza alla scultura, quindi allontanarla dal bordo la
            // fa salire tutta insieme invece di lasciarla appiccicata in fondo.
            modifier = Modifier
                .padding(top = 2.dp, bottom = 26.dp)
                .height(MinTouchTarget),
            contentAlignment = Alignment.Center,
        ) {
            // Non con la settimana in scena: li' non c'e' un'ora scelta da cui
            // tornare, e un tasto che rimanda a "adesso" mentre si guardano i
            // prossimi otto giorni promette di riportare da qualche parte dove
            // non si e' andati.
            if (!onNow && !settimana) {
                MeteoIconButton(
                    onClick = onBackToNow,
                    contentDescription = "Torna all'ora attuale",
                    icon = {
                        NowIcon(
                            color = colors.text.copy(alpha = 0.62f),
                            minutesOfDay = rememberMinutesThere(state.forecast),
                        )
                    },
                )
            }
        }
    }
}

/**
 * Il segno che riporta all'ora vera: un orologio, disegnato.
 *
 * **Era la pillola "TORNA AD ADESSO".** Una parola in un fondo pieno, larga un
 * terzo di schermo, che compariva e spariva a ogni scorrimento: pesava come un
 * comando primario per una cosa che si fa di rado, e in mezzo a una schermata
 * fatta di cielo era l'unico rettangolo opaco.
 *
 * Adesso e' un cerchio con due lancette, senza fondo, al 62% di opacita'. Un
 * orologio dice *tempo*, e sotto una barra di ore in cui si e' andati altrove
 * dice l'unica cosa che li' si puo' volere: tornare. Il bersaglio resta pero'
 * quello pieno di [MinTouchTarget] - il disegno e' piccolo, la zona che lo
 * riceve no, che e' la differenza fra un'icona discreta e una da centrare.
 *
 * **Le lancette segnano l'ora vera**, non una posa fissa. Costa un seno e un
 * coseno, e cambia cosa dice il segno: non piu' "torna indietro" in astratto ma
 * *torna a quest'ora*, che e' proprio l'ora che l'utente ha lasciato scorrendo
 * la barra. La lancetta delle ore avanza anche dentro l'ora - mezzo grado al
 * minuto - come su un orologio vero: alle sette e mezza sta a meta' strada fra
 * il sette e l'otto, e non ferma sul sette.
 *
 * L'ora e' quella **della localita'**, presa da [Forecast.nowThere]: gli orari
 * della barra sono nel fuso del posto, e col telefono a Los Angeles e la
 * previsione su Forli' un orologio sul fuso del telefono segnerebbe nove ore
 * diverse da quelle che sta indicando il pallino sulla pista.
 *
 * Disegnata e non importata, come la freccia di `MeteoSurfaces`: il progetto non
 * ha `material-icons-extended` e non vale mezzo megabyte per un cerchio e due
 * segmenti.
 */
@Composable
private fun NowIcon(color: Color, minutesOfDay: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.size(19.dp)) {
        val stroke = 1.6.dp.toPx()
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - stroke / 2f
        drawCircle(
            color = color,
            radius = radius,
            center = centre,
            style = Stroke(width = stroke),
        )

        // Gradi in senso orario a partire dal mezzogiorno, come si legge un
        // quadrante: lo zero e' in alto, quindi la x segue il seno e la y il
        // coseno cambiato di segno, perche' sullo schermo si scende crescendo.
        fun lancetta(gradi: Float, lunghezza: Float) {
            val radianti = gradi * PI / 180.0
            drawLine(
                color = color,
                start = centre,
                end = Offset(
                    x = centre.x + (sin(radianti) * radius * lunghezza).toFloat(),
                    y = centre.y - (cos(radianti) * radius * lunghezza).toFloat(),
                ),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }

        // Dodici ore sono 720 minuti su 360 gradi: mezzo grado al minuto.
        lancetta(gradi = (minutesOfDay % 720) * 0.5f, lunghezza = 0.46f)
        lancetta(gradi = (minutesOfDay % 60) * 6f, lunghezza = 0.68f)

        // Il perno, e non e' un vezzo. Con l'ora vera le due lancette finiscono
        // spesso nello stesso quadrante - alle nove e trentacinque escono
        // entrambe a sinistra - e senza un centro dichiarato il disegno si legge
        // come una spezzata qualunque invece che come un quadrante. Il puntino
        // dice dove sono attaccate, ed e' quello che lo rende un orologio anche
        // quando le lancette sono vicine.
        drawCircle(color = color, radius = stroke * 0.7f, center = centre)
    }
}

/**
 * Che ore sono nella localita', in minuti dalla mezzanotte.
 *
 * Batte ogni venti secondi ma **scrive solo al cambio di minuto**, che e' la
 * stessa regola di [rememberFreshness] e per la stessa ragione: un valore nuovo
 * a ogni battito terrebbe la schermata a ricomporsi per sempre a schermo
 * immobile. Qui il risparmio e' anche piu' netto, perche' cio' che dipende da
 * questo numero e' un disegno di diciannove punti.
 */
@Composable
private fun rememberMinutesThere(forecast: Forecast?): Int {
    fun leggi(): Int = forecast?.nowThere()?.let { it.hour * 60 + it.minute } ?: 0
    var minuti by remember(forecast) { mutableIntStateOf(leggi()) }
    LaunchedEffect(forecast) {
        while (true) {
            val prossimo = leggi()
            if (prossimo != minuti) minuti = prossimo
            delay(CLOCK_TICK_MS)
        }
    }
    return minuti
}

/**
 * Distingue un tocco da un trascinamento sulla stessa cifra.
 *
 * Sotto la soglia di scorrimento resta un tocco possibile; superata, diventa
 * un giro di scena e non torna piu' indietro - un dito che parte fermo e poi
 * scivola non deve aprire il dettaglio **e** girare la scena insieme.
 */
private suspend fun PointerInputScope.detectTapOrRotate(
    rotation: SceneRotation,
    onTap: () -> Unit,
) {
    val slopPx = TAP_SLOP_DP.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var dragging = false
        while (true) {
            val event = awaitPointerEvent()
            val change: PointerInputChange = event.changes.firstOrNull { it.id == down.id }
                ?: break
            tracker.addPosition(change.uptimeMillis, change.position)
            if (!dragging && (change.position - down.position).getDistance() > slopPx) {
                dragging = true
                rotation.begin()
            }
            if (dragging) {
                rotation.drag(change.positionChange().x)
                change.consume()
            }
            if (!change.pressed) {
                if (dragging) {
                    rotation.release(tracker.calculateVelocity().x)
                } else {
                    change.consume()
                    onTap()
                }
                break
            }
        }
    }
}

private const val TAP_SLOP_DP = 5f

/**
 * Tre righe: e' il segno universale, e non serve una libreria di icone.
 *
 * Da 48dp e non da 34: e' il minimo che Material dichiara, e sotto quella
 * misura un dito manca il bersaglio. Il disegno resta piccolo - a crescere e'
 * l'area sensibile, non l'icona.
 */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val colors = LocalMeteoColors.current
    MeteoIconButton(onClick = onClick, contentDescription = "Apri le impostazioni") {
        Canvas(Modifier.size(16.dp)) {
            val gap = size.height / 3f
            for (i in 0 until 3) {
                val y = gap * (i + 0.5f)
                drawLine(
                    color = colors.label,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = size.height * 0.10f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * Quanto e' vecchio il dato, in parole, e nullo finche' e' fresco.
 *
 * L'orologio batte ogni mezzo minuto ma **scrive solo quando la frase cambia**,
 * e la frase cambia poche volte in un'ora. Scrivere a ogni battito terrebbe la
 * schermata a ricomporsi per sempre a schermo immobile, che e' esattamente la
 * trappola gia' pagata con l'accelerometro.
 */
@Composable
private fun rememberFreshness(fetchedAt: LocalDateTime?): String? {
    var label by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(fetchedAt) {
        while (true) {
            val next = freshnessOf(fetchedAt, LocalDateTime.now())
            if (next != label) label = next
            delay(FRESHNESS_TICK_MS)
        }
    }
    return label
}

private fun freshnessOf(fetchedAt: LocalDateTime?, now: LocalDateTime): String? {
    if (fetchedAt == null) return null
    val minutes = Duration.between(fetchedAt, now).toMinutes()
    return when {
        minutes < STALE_MINUTES -> null
        minutes < 120L -> "$minutes MIN FA"
        else -> "${minutes / 60L} H FA"
    }
}

/** Il giorno dell'ora mostrata: la barra copre oggi, e conviene dire quale. */
private fun dayLabel(hour: HourForecast?): String =
    hour?.time?.format(DAY_FORMAT)?.uppercase(Locale.ITALIAN).orEmpty()

private val DAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

/**
 * Minima e massima del giorno, e la percepita quando ha qualcosa da aggiungere.
 *
 * La percepita compare solo se stacca di almeno un grado e mezzo dalla reale:
 * scritta accanto a un numero uguale al suo non e' un'informazione, e' la
 * stessa riga stampata due volte. Il confronto si fa in gradi Celsius, prima
 * della conversione, perche' in Fahrenheit la stessa differenza vale quasi il
 * doppio e la soglia cambierebbe senso a seconda dell'unita' scelta.
 */
private fun rangeLabel(
    min: Double?,
    max: Double?,
    apparent: Double?,
    real: Double?,
    unit: TempUnit,
): String {
    val span = if (min != null && max != null) {
        "${min.asPlainDegrees(unit)} / ${max.asPlainDegrees(unit)}"
    } else {
        null
    }
    val felt = if (apparent != null && real != null && abs(apparent - real) >= FELT_THRESHOLD) {
        "PERCEPITI ${apparent.asPlainDegrees(unit)}"
    } else {
        null
    }
    return listOfNotNull(span, felt).joinToString("   \u00B7   ")
}

/**
 * La probabilita' compare solo quando c'e' davvero qualcosa da prevedere:
 * "sereno 0%" sarebbe rumore.
 */
private fun conditionLabel(
    hour: io.github.noximiliencoxen.caelum.data.HourForecast?,
    forcedCode: Int?,
): String {
    if (hour == null && forcedCode == null) return "--"
    val code = forcedCode ?: hour?.weatherCode
    val condition = Wmo.condition(code)
    val wet = Wmo.family(code).isWet()
    val probability = if (forcedCode != null) 80 else hour?.precipProbability ?: 0
    return if (wet && probability > 0) "$condition $probability%" else condition
}

/** Sotto questa eta' il dato si considera fresco e non lo si dichiara. */
private const val STALE_MINUTES = 30L

private const val FRESHNESS_TICK_MS = 30_000L

/** Ogni quanto si guarda l'orologio della localita' per le lancette dell'icona. */
private const val CLOCK_TICK_MS = 20_000L

/** In gradi Celsius: sotto, percepita e reale sono la stessa notizia. */
private const val FELT_THRESHOLD = 1.5
