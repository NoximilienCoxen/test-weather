package com.caelumweather.ui.sala

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import com.caelumweather.lingua.SceltaLingua
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.scegli
import com.caelumweather.lingua.tr
import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.caelumweather.BuildConfig
import com.caelumweather.notifiche.PioggiaInArrivoWorker
import com.caelumweather.notifiche.RiepilogoMattinoWorker
import com.caelumweather.widget.AggiornaWidgetWorker
import com.caelumweather.prefs.AlertToggleKind
import com.caelumweather.prefs.CaptionStyle
import com.caelumweather.prefs.CardTheme
import com.caelumweather.prefs.SalaWindUnit
import com.caelumweather.prefs.TempUnit
import com.caelumweather.prefs.SogliePersonali
import com.caelumweather.prefs.TipoSoglia
import com.caelumweather.notifiche.gradiDiSoglia
import com.caelumweather.notifiche.ventoDiSoglia
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.caelumweather.ui.UiState
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * Le impostazioni.
 *
 * ## Per argomento, non per comando
 *
 * Erano quindici pastiglie identiche in fila, con "Le località" inchiodata in
 * fondo sopra la lista che le scorreva dietro: il tema pesava quanto "Vento
 * forte", e per capire cosa facesse cosa bisognava leggerle tutte. Adesso sono
 * sette gruppi col titolo fuori dal riquadro - località, aspetto, unità,
 * avvisi, notifiche, aiuto, dati - e ogni scelta ha sotto una nota che **cambia
 * con la scelta** e dice cosa succede, invece di ripetere il nome della voce.
 *
 * **Le località stanno in cima**, dentro la lista: sono la cosa che si cambia
 * piu' spesso, e inchiodate in fondo coprivano la voce che scorreva sotto.
 *
 * **Ogni interruttore qui dentro comanda qualcosa.** E' una regola e non una
 * constatazione: un comando che si accende, si spegne, si ricorda fra un avvio
 * e l'altro e non viene letto da nessuno e' peggio di un comando assente - chi
 * lo prova e non vede cambiare niente impara che i comandi di questa schermata
 * non contano, e da li' in poi non si fida nemmeno di quelli veri.
 */
@Composable
fun SalaImpostazioniScreen(
    state: UiState,
    palette: SalaPalette,
    onToggleAnimazioni: (Boolean) -> Unit,
    onToggleSchedeLarghe: (Boolean) -> Unit,
    onApriGuida: () -> Unit,
    onToggleNotifichePioggia: (Boolean) -> Unit,
    onToggleNotificheAllerte: (Boolean) -> Unit,
    onToggleRiepilogo: (Boolean) -> Unit,
    onChooseOraRiepilogo: (Int) -> Unit,
    onToggleSoglia: (TipoSoglia, Boolean) -> Unit,
    onChooseSoglia: (TipoSoglia, Int) -> Unit,
    onChooseTheme: (CardTheme) -> Unit,
    onChooseUnit: (TempUnit) -> Unit,
    onChooseWindUnit: (SalaWindUnit) -> Unit,
    onChooseCaptionStyle: (CaptionStyle) -> Unit,
    onToggleAlert: (AlertToggleKind, Boolean) -> Unit,
    onApriLocalita: () -> Unit,
    onAggiorna: () -> Unit,
    onApriLegali: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.schermoPieno)
            .systemBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 12.dp),
    ) {
        IntestazioneServizio(titolo = tr("Impostazioni", "Settings", "Einstellungen", "Réglages", "Ajustes"), palette = palette, onIndietro = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            GruppoImpostazioni(titolo = tr("LOCALITÀ", "PLACE", "ORT", "LIEU", "LUGAR"), palette = palette) {
                val salvate = state.favorites.size
                VoceImpostazione(
                    titolo = state.place.name,
                    nota = listOf(
                        if (state.followsLocation) tr("dalla posizione del telefono", "from the phone's location", "vom Standort des Telefons", "d'après la position du téléphone", "según la ubicación del teléfono") else tr("scelta a mano", "chosen by hand", "von Hand gewählt", "choisi à la main", "elegido a mano"),
                        if (salvate == 1) tr("1 salvata", "1 saved", "1 gespeichert", "1 enregistré", "1 guardado") else tr("$salvate salvate", "$salvate saved", "$salvate gespeichert", "$salvate enregistrés", "$salvate guardados"),
                    ).joinToString(" · "),
                    palette = palette,
                    onClick = onApriLocalita,
                    coda = { FrecciaAvanti(palette, testo = tr("Cambia", "Change", "Ändern", "Changer", "Cambiar")) },
                )
            }

            GruppoImpostazioni(titolo = tr("ASPETTO", "APPEARANCE", "DARSTELLUNG", "APPARENCE", "ASPECTO"), palette = palette) {
                VoceImpostazione(
                    titolo = tr("Tema", "Theme", "Design", "Thème", "Tema"),
                    nota = when (state.cardTheme) {
                        CardTheme.AUTO -> tr("chiaro col sole, scuro quando tramonta sulla località", "light while the sun is up, dark once it sets over the place", "hell, solange die Sonne scheint, dunkel, wenn sie am Ort untergeht", "clair tant que le soleil est levé, sombre quand il se couche sur le lieu", "claro con sol, oscuro cuando se pone en el lugar")
                        CardTheme.CHIARO -> tr("sempre chiaro, a qualunque ora", "always light, at any hour", "immer hell, zu jeder Stunde", "toujours clair, à toute heure", "siempre claro, a cualquier hora")
                        CardTheme.SCURO -> tr("sempre scuro, a qualunque ora", "always dark, at any hour", "immer dunkel, zu jeder Stunde", "toujours sombre, à toute heure", "siempre oscuro, a cualquier hora")
                    },
                    palette = palette,
                    sotto = {
                        SceltaSegmentata(
                            voci = listOf(
                                CardTheme.AUTO to tr("Segui il cielo", "Follow the sky", "Dem Himmel folgen", "Suivre le ciel", "Seguir el cielo"),
                                CardTheme.CHIARO to tr("Chiaro", "Light", "Hell", "Clair", "Claro"),
                                CardTheme.SCURO to tr("Scuro", "Dark", "Dunkel", "Sombre", "Oscuro"),
                            ),
                            scelta = state.cardTheme,
                            palette = palette,
                            onScegli = onChooseTheme,
                        )
                    },
                )
                FiloGruppo(palette)
                VoceImpostazione(
                    titolo = tr("Testi delle schermate", "Screen texts", "Texte der Bildschirme", "Textes des écrans", "Textos de las pantallas"),
                    nota = when (state.captionStyle) {
                        CaptionStyle.COMPLETE -> tr("titolo, numeri e il paragrafo che li spiega", "title, numbers and the paragraph that explains them", "Titel, Zahlen und der Absatz, der sie erklärt", "titre, chiffres et le paragraphe qui les explique", "título, números y el párrafo que los explica")
                        CaptionStyle.BREVI -> tr("solo titolo e numeri, senza il paragrafo", "only title and numbers, without the paragraph", "nur Titel und Zahlen, ohne den Absatz", "seulement le titre et les chiffres, sans le paragraphe", "solo título y números, sin el párrafo")
                    },
                    palette = palette,
                    sotto = {
                        SceltaSegmentata(
                            voci = listOf(CaptionStyle.COMPLETE to tr("Completi", "Full", "Vollständig", "Complets", "Completos"), CaptionStyle.BREVI to tr("Brevi", "Short", "Kurz", "Courts", "Breves")),
                            scelta = state.captionStyle,
                            palette = palette,
                            onScegli = onChooseCaptionStyle,
                        )
                    },
                )
                FiloGruppo(palette)
                // **La lingua non e' uno stato di Compose**: i testi la leggono
                // da `Lingue` mentre si compongono, anche fuori dalle schermate
                // (i widget, le notifiche). Cambiarla rifa' l'attivita', che
                // ricompone tutto da capo; il ViewModel resta, i dati pure.
                val attivita = LocalContext.current
                val scegliLingua: (SceltaLingua) -> Unit = { nuova ->
                    if (nuova != Lingue.scelta) {
                        Lingue.scegli(attivita, nuova)
                        AggiornaWidgetWorker.appenaPossibile(attivita)
                        (attivita as? Activity)?.recreate()
                    }
                }
                VoceImpostazione(
                    titolo = tr("Lingua", "Language", "Sprache", "Langue", "Idioma"),
                    nota = when (Lingue.scelta) {
                        SceltaLingua.AUTOMATICA -> tr("quella del telefono, se è una di queste cinque; se no l'inglese", "the phone's, if it is one of these five; otherwise English", "die des Telefons, wenn es eine dieser fünf ist; sonst Englisch", "celle du téléphone, si c'est l'une de ces cinq ; sinon l'anglais", "el del teléfono, si es uno de estos cinco; si no, inglés")
                        SceltaLingua.ITALIANO -> tr("sempre in italiano", "always Italian", "immer Italienisch", "toujours en italien", "siempre en italiano")
                        SceltaLingua.INGLESE -> tr("sempre in inglese", "always English", "immer Englisch", "toujours en anglais", "siempre en inglés")
                        SceltaLingua.TEDESCO -> tr("sempre in tedesco", "always German", "immer Deutsch", "toujours en allemand", "siempre en alemán")
                        SceltaLingua.FRANCESE -> tr("sempre in francese", "always French", "immer Französisch", "toujours en français", "siempre en francés")
                        SceltaLingua.SPAGNOLO -> tr("sempre in spagnolo", "always Spanish", "immer Spanisch", "toujours en espagnol", "siempre en español")
                    },
                    palette = palette,
                    sotto = {
                        // **Due binari, e la scelta e' una sola**: sei voci su
                        // una riga non ci stanno. Ogni lingua si chiama col
                        // nome che ha per chi la parla, e non si traduce.
                        SceltaSegmentata(
                            voci = listOf(
                                SceltaLingua.AUTOMATICA to tr("Automatica", "Automatic", "Automatisch", "Automatique", "Automático"),
                                SceltaLingua.ITALIANO to "Italiano",
                                SceltaLingua.INGLESE to "English",
                            ),
                            scelta = Lingue.scelta,
                            palette = palette,
                            onScegli = scegliLingua,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SceltaSegmentata(
                            voci = listOf(
                                SceltaLingua.TEDESCO to "Deutsch",
                                SceltaLingua.FRANCESE to "Français",
                                SceltaLingua.SPAGNOLO to "Español",
                            ),
                            scelta = Lingue.scelta,
                            palette = palette,
                            onScegli = scegliLingua,
                        )
                    },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Animazioni ridotte", "Reduced motion", "Weniger Bewegung", "Animations réduites", "Animaciones reducidas"),
                    nota = tr("il cielo sta fermo e il telefono non vibra con pioggia e neve", "the sky stays still and the phone does not buzz with rain and snow", "der Himmel steht still, und das Telefon vibriert nicht bei Regen und Schnee", "le ciel reste immobile et le téléphone ne vibre pas avec la pluie et la neige", "el cielo se queda quieto y el teléfono no vibra con la lluvia y la nieve"),
                    acceso = state.animazioniRidotte,
                    palette = palette,
                    onCambia = onToggleAnimazioni,
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Schede larghe", "Wide cards", "Breite Karten", "Cartes larges", "Tarjetas anchas"),
                    nota = tr("toglie la colonna delle icone: si passa da una schermata all'altra col dito", "hides the icon column: swipe from one screen to the next", "blendet die Symbolspalte aus: Du wechselst mit dem Finger von einem Bildschirm zum nächsten", "masque la colonne d'icônes : on passe d'un écran à l'autre avec le doigt", "oculta la columna de iconos: se pasa de una pantalla a otra con el dedo"),
                    acceso = state.schedeLarghe,
                    palette = palette,
                    onCambia = onToggleSchedeLarghe,
                )
            }

            // Le unita' stanno sulla riga del loro nome: due o tre voci corte
            // non hanno bisogno di una riga intera, e il gruppo resta basso.
            GruppoImpostazioni(titolo = tr("UNITÀ DI MISURA", "UNITS", "EINHEITEN", "UNITÉS", "UNIDADES"), palette = palette) {
                VoceImpostazione(
                    titolo = tr("Temperatura", "Temperature", "Temperatur", "Température", "Temperatura"),
                    palette = palette,
                    coda = {
                        SceltaSegmentata(
                            voci = listOf(TempUnit.CELSIUS to "°C", TempUnit.FAHRENHEIT to "°F"),
                            scelta = state.unit,
                            palette = palette,
                            onScegli = onChooseUnit,
                            modifier = Modifier.width(LarghezzaUnita),
                        )
                    },
                )
                FiloGruppo(palette)
                VoceImpostazione(
                    titolo = tr("Vento", "Wind", "Wind", "Vent", "Viento"),
                    palette = palette,
                    coda = {
                        SceltaSegmentata(
                            voci = SalaWindUnit.entries.map { it to it.label },
                            scelta = state.windUnit,
                            palette = palette,
                            onScegli = onChooseWindUnit,
                            modifier = Modifier.width(LarghezzaUnita),
                        )
                    },
                )
            }

            // **Gli avvisi filtrano solo quelli calcolati.** Un'allerta del
            // servizio meteorologico non la si nasconde perche' un interruttore e'
            // giu': la scelta e' su cio' che questa applicazione deduce dalle
            // soglie, non su cio' che un ente dichiara. La nota sotto il gruppo
            // lo dice, perche' prima lo sapeva solo questo commento.
            GruppoImpostazioni(
                titolo = tr("AVVISI NELLE SCHERMATE", "NOTICES ON THE SCREENS", "HINWEISE AUF DEN BILDSCHIRMEN", "AVIS SUR LES ÉCRANS", "AVISOS EN LAS PANTALLAS"),
                palette = palette,
                nota = tr(
                    "Le allerte ufficiali dei servizi meteorologici si vedono sempre: qui scegli solo " +
                        "gli avvisi che l'app ricava dalla previsione.",
                    "Official weather service warnings always show: here you only choose " +
                        "the notices the app works out from the forecast.",
                    "Amtliche Warnungen der Wetterdienste werden immer angezeigt: Hier wählst du nur die Hinweise, die die App aus der Vorhersage ableitet.",
                    "Les alertes officielles des services météorologiques s'affichent toujours : ici, vous choisissez seulement les avis que l'app déduit de la prévision.",
                    "Las alertas oficiales de los servicios meteorológicos se muestran siempre: aquí solo eliges los avisos que la app deduce de la previsión.",
                ),
            ) {
                VoceInterruttore(
                    titolo = tr("Pioggia intensa", "Heavy rain", "Starkregen", "Fortes pluies", "Lluvia intensa"),
                    nota = tr("quando sono attesi molti millimetri", "when a lot of millimetres are expected", "wenn viele Millimeter erwartet werden", "quand beaucoup de millimètres sont attendus", "cuando se esperan muchos milímetros"),
                    acceso = state.alertToggles.pioggiaIntensa,
                    palette = palette,
                    onCambia = { onToggleAlert(AlertToggleKind.PIOGGIA, it) },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Temporali", "Thunderstorms", "Gewitter", "Orages", "Tormentas"),
                    nota = tr("quando la previsione dà temporale", "when the forecast gives thunderstorms", "wenn die Vorhersage Gewitter meldet", "quand la prévision annonce de l'orage", "cuando la previsión da tormenta"),
                    acceso = state.alertToggles.temporali,
                    palette = palette,
                    onCambia = { onToggleAlert(AlertToggleKind.TEMPORALE, it) },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Raggi UV alti", "High UV", "Hohe UV-Strahlung", "UV élevés", "Rayos UV altos"),
                    nota = tr("indice sopra 6, dove la scala mondiale passa ad alto", "index above 6, where the world scale turns to high", "Index über 6, wo die weltweite Skala auf hoch wechselt", "indice supérieur à 6, là où l'échelle mondiale passe à élevé", "índice por encima de 6, donde la escala mundial pasa a alto"),
                    acceso = state.alertToggles.uvAlto,
                    palette = palette,
                    onCambia = { onToggleAlert(AlertToggleKind.UV, it) },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Vento forte", "Strong wind", "Starker Wind", "Vent fort", "Viento fuerte"),
                    nota = tr("quando sono attese raffiche forti", "when strong gusts are expected", "wenn starke Böen erwartet werden", "quand de fortes rafales sont attendues", "cuando se esperan rachas fuertes"),
                    acceso = state.alertToggles.ventoForte,
                    palette = palette,
                    onCambia = { onToggleAlert(AlertToggleKind.VENTO, it) },
                )
            }

            // Accenderle senza il permesso non accenderebbe niente: su Android
            // 13 e oltre l'interruttore lo chiede, e se viene negato le
            // notifiche restano spente, perche' un interruttore acceso che non
            // fa niente e' peggio di uno spento.
            val contesto = LocalContext.current
            val chiediPermesso = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { concesso -> onToggleNotifichePioggia(concesso) }
            val chiediPermessoAllerte = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { concesso -> onToggleNotificheAllerte(concesso) }
            val chiediPermessoRiepilogo = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { concesso -> onToggleRiepilogo(concesso) }
            GruppoImpostazioni(titolo = tr("NOTIFICHE", "NOTIFICATIONS", "BENACHRICHTIGUNGEN", "NOTIFICATIONS", "NOTIFICACIONES"), palette = palette) {
                VoceInterruttore(
                    titolo = tr("Pioggia e grandine in arrivo", "Rain and hail on the way", "Regen und Hagel im Anmarsch", "Pluie et grêle en approche", "Lluvia y granizo en camino"),
                    nota = tr("un avviso poco prima che cominci, sulla località dell'app", "a heads-up shortly before it starts, for the app's place", "ein Hinweis kurz bevor es losgeht, für den Ort der App", "un avis peu avant que ça commence, pour le lieu de l'app", "un aviso poco antes de que empiece, para el lugar de la app"),
                    acceso = state.notifichePioggia && PioggiaInArrivoWorker.puoNotificare(contesto),
                    palette = palette,
                    onCambia = { vuole ->
                        when {
                            !vuole -> onToggleNotifichePioggia(false)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !PioggiaInArrivoWorker.puoNotificare(contesto) ->
                                chiediPermesso.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> onToggleNotifichePioggia(true)
                        }
                    },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Allerte ufficiali", "Official warnings", "Amtliche Warnungen", "Alertes officielles", "Alertas oficiales"),
                    nota = tr("arancioni e rosse, diramate dagli enti; le gialle restano nel bollettino", "orange and red, issued by the authorities; yellow ones stay in the bulletin", "orange und rot, von den Behörden herausgegeben; gelbe bleiben im Warnbericht", "orange et rouges, émises par les organismes ; les jaunes restent dans le bulletin", "naranjas y rojas, emitidas por los organismos; las amarillas se quedan en el boletín"),
                    acceso = state.notificheAllerte && PioggiaInArrivoWorker.puoNotificare(contesto),
                    palette = palette,
                    onCambia = { vuole ->
                        when {
                            !vuole -> onToggleNotificheAllerte(false)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !PioggiaInArrivoWorker.puoNotificare(contesto) ->
                                chiediPermessoAllerte.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> onToggleNotificheAllerte(true)
                        }
                    },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Riepilogo del mattino", "Morning summary", "Morgenüberblick", "Résumé du matin", "Resumen de la mañana"),
                    nota = if (state.riepilogoMattino && PioggiaInArrivoWorker.puoNotificare(contesto)) {
                        tr(
                            "ogni giorno alle ${oraRiepilogo(state.oraRiepilogo)}: temperature, pioggia, UV e allerte",
                            "every day at ${oraRiepilogo(state.oraRiepilogo)}: temperatures, rain, UV and warnings",
                            "jeden Tag um ${oraRiepilogo(state.oraRiepilogo)}: Temperaturen, Regen, UV und Warnungen",
                            "chaque jour à ${oraRiepilogo(state.oraRiepilogo)} : températures, pluie, UV et alertes",
                            "cada día a las ${oraRiepilogo(state.oraRiepilogo)}: temperaturas, lluvia, UV y alertas",
                        )
                    } else {
                        tr("la giornata in tre righe, una volta al giorno", "the day in three lines, once a day", "der Tag in drei Zeilen, einmal täglich", "la journée en trois lignes, une fois par jour", "el día en tres líneas, una vez al día")
                    },
                    acceso = state.riepilogoMattino && PioggiaInArrivoWorker.puoNotificare(contesto),
                    palette = palette,
                    onCambia = { vuole ->
                        when {
                            !vuole -> onToggleRiepilogo(false)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !PioggiaInArrivoWorker.puoNotificare(contesto) ->
                                chiediPermessoRiepilogo.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> onToggleRiepilogo(true)
                        }
                    },
                )
                // L'ora si sceglie solo a riepilogo acceso: spento, quattro
                // bottoni che non comandano niente (vedi la regola in testa).
                ComparsaMorbida(visibile = state.riepilogoMattino && PioggiaInArrivoWorker.puoNotificare(contesto)) {
                    SceltaSegmentata(
                        voci = RiepilogoMattinoWorker.ORE_POSSIBILI.map { it to oraRiepilogo(it) },
                        scelta = state.oraRiepilogo,
                        palette = palette,
                        onScegli = onChooseOraRiepilogo,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                    )
                }
            }

            // ── Gli avvisi personali ─────────────────────────────────────
            //
            // **Un gruppo a parte, con la sua nota**: pioggia e allerte qui
            // sopra dicono una cosa che succede o che un ente ha dichiarato;
            // questi confrontano la previsione con un numero scelto da chi usa
            // l'app. La nota lo dice dove si accendono, e la notifica lo ripete.
            //
            // Un permesso solo per tutti e tre: si ricorda quale levetta l'ha
            // chiesto, e se viene negato quella resta giu'.
            var sogliaInAttesa by remember { mutableStateOf<TipoSoglia?>(null) }
            val chiediPermessoSoglia = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { concesso ->
                sogliaInAttesa?.let { onToggleSoglia(it, concesso) }
                sogliaInAttesa = null
            }
            val puoNotificare = PioggiaInArrivoWorker.puoNotificare(contesto)
            GruppoImpostazioni(
                titolo = tr("AVVISI PERSONALI", "PERSONAL NOTICES", "PERSÖNLICHE HINWEISE", "AVIS PERSONNELS", "AVISOS PERSONALES"),
                palette = palette,
                nota = tr(
                    "Una notifica quando la previsione delle prossime ore passa la soglia che scegli tu, " +
                        "sulla località dell'app. Sono previsioni, non allerte ufficiali, e di notte non suonano.",
                    "A notification when the forecast for the coming hours crosses the threshold you choose, " +
                        "for the app's place. They are forecasts, not official warnings, and they stay quiet at night.",
                    "Eine Benachrichtigung, wenn die Vorhersage der nächsten Stunden die von dir gewählte Schwelle überschreitet, für den Ort der App. Es sind Vorhersagen, keine amtlichen Warnungen, und nachts bleiben sie still.",
                    "Une notification quand la prévision des prochaines heures franchit le seuil que vous choisissez, pour le lieu de l'app. Ce sont des prévisions, pas des alertes officielles, et la nuit elles restent silencieuses.",
                    "Una notificación cuando la previsión de las próximas horas supera el umbral que tú eliges, para el lugar de la app. Son previsiones, no alertas oficiales, y de noche no suenan.",
                ),
            ) {
                TipoSoglia.entries.forEachIndexed { indice, tipo ->
                    if (indice > 0) FiloGruppo(palette)
                    val accesa = state.soglie.accesa(tipo) && puoNotificare
                    val scritta: (Int) -> String = { valore ->
                        if (tipo == TipoSoglia.VENTO) ventoDiSoglia(valore, state.windUnit) else gradiDiSoglia(valore, state.unit)
                    }
                    val scelta = scritta(state.soglie.valore(tipo))
                    VoceInterruttore(
                        titolo = when (tipo) {
                            TipoSoglia.GELO -> tr("Gelo", "Frost", "Frost", "Gel", "Helada")
                            TipoSoglia.CALDO -> tr("Caldo", "Heat", "Hitze", "Chaleur", "Calor")
                            TipoSoglia.VENTO -> tr("Vento forte", "Strong wind", "Starker Wind", "Vent fort", "Viento fuerte")
                        },
                        nota = when (tipo) {
                            TipoSoglia.GELO ->
                                if (accesa) tr("quando la minima scende a $scelta o sotto", "when the low drops to $scelta or below", "wenn das Minimum auf $scelta oder darunter fällt", "quand la minimale descend à $scelta ou en dessous", "cuando la mínima baja a $scelta o menos")
                                else tr("quando la minima scende sotto la tua soglia", "when the low drops below your threshold", "wenn das Minimum unter deine Schwelle fällt", "quand la minimale descend sous votre seuil", "cuando la mínima baja de tu umbral")
                            TipoSoglia.CALDO ->
                                if (accesa) tr("quando la massima arriva a $scelta o sopra", "when the high reaches $scelta or above", "wenn das Maximum $scelta oder mehr erreicht", "quand la maximale atteint $scelta ou plus", "cuando la máxima llega a $scelta o más")
                                else tr("quando la massima passa la tua soglia", "when the high passes your threshold", "wenn das Maximum deine Schwelle überschreitet", "quand la maximale dépasse votre seuil", "cuando la máxima supera tu umbral")
                            TipoSoglia.VENTO ->
                                if (accesa) tr("quando le raffiche arrivano a $scelta o sopra", "when gusts reach $scelta or above", "wenn die Böen $scelta oder mehr erreichen", "quand les rafales atteignent $scelta ou plus", "cuando las rachas llegan a $scelta o más")
                                else tr("quando le raffiche passano la tua soglia", "when gusts pass your threshold", "wenn die Böen deine Schwelle überschreiten", "quand les rafales dépassent votre seuil", "cuando las rachas superan tu umbral")
                        },
                        acceso = accesa,
                        palette = palette,
                        onCambia = { vuole ->
                            when {
                                !vuole -> onToggleSoglia(tipo, false)
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !puoNotificare -> {
                                    sogliaInAttesa = tipo
                                    chiediPermessoSoglia.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                else -> onToggleSoglia(tipo, true)
                            }
                        },
                    )
                    // La soglia si sceglie solo ad avviso acceso: spento, tre
                    // bottoni che non comandano niente (vedi la regola in testa).
                    ComparsaMorbida(visibile = accesa) {
                        SceltaSegmentata(
                            voci = SogliePersonali.possibili(tipo).map { it to scritta(it) },
                            scelta = state.soglie.valore(tipo),
                            palette = palette,
                            onScegli = { onChooseSoglia(tipo, it) },
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                        )
                    }
                }
            }

            GruppoImpostazioni(titolo = tr("AIUTO", "HELP", "HILFE", "AIDE", "AYUDA"), palette = palette) {
                VoceImpostazione(
                    titolo = tr("Guida all'uso", "How to use it", "Anleitung", "Mode d'emploi", "Guía de uso"),
                    nota = tr("come si sfogliano le schermate e si cambia ora", "how to move through the screens and change the hour", "wie man durch die Bildschirme blättert und die Stunde ändert", "comment parcourir les écrans et changer l'heure", "cómo pasar de una pantalla a otra y cambiar la hora"),
                    palette = palette,
                    onClick = onApriGuida,
                    coda = { FrecciaAvanti(palette) },
                )
                FiloGruppo(palette)
                VoceImpostazione(
                    titolo = tr("Note legali e privacy", "Legal notes and privacy", "Rechtliches und Datenschutz", "Mentions légales et confidentialité", "Notas legales y privacidad"),
                    nota = tr("fonti dei dati, cosa esce dal telefono, permessi", "data sources, what leaves the phone, permissions", "Datenquellen, was das Telefon verlässt, Berechtigungen", "sources des données, ce qui quitte le téléphone, autorisations", "fuentes de los datos, qué sale del teléfono, permisos"),
                    palette = palette,
                    onClick = onApriLegali,
                    coda = { FrecciaAvanti(palette) },
                )
            }

            // ── Le informazioni ──────────────────────────────────────────
            //
            // Tutto quello che c'e' qui dentro **esisteva gia' nel codice** e
            // non lo leggeva nessuna schermata (vedi CONTESTO 28.7). L'ultimo
            // aggiornamento sta sulla riga che lo rifa': "quando" e "rifallo"
            // sono la stessa domanda, e in due righe diverse sembravano due.
            //
            // **`refresh()` era pubblico e non lo chiamava nessuna schermata**,
            // e `state.error` - un messaggio gia' scritto per chi guarda - non
            // aveva un solo lettore in tutta l'app. Qui trovano tutti e due il
            // loro posto: si riprova a mano, e se va storto lo si legge.
            GruppoImpostazioni(titolo = tr("DA DOVE VENGONO I NUMERI", "WHERE THE NUMBERS COME FROM", "WOHER DIE ZAHLEN KOMMEN", "D'OÙ VIENNENT LES CHIFFRES", "DE DÓNDE VIENEN LOS NÚMEROS"), palette = palette) {
                VoceImpostazione(
                    titolo = tr("Aggiorna adesso", "Refresh now", "Jetzt aktualisieren", "Actualiser maintenant", "Actualizar ahora"),
                    nota = state.error ?: tr("ultimo aggiornamento ${quandoScaricata(state.fetchedAt)}", "last updated ${quandoScaricata(state.fetchedAt)}", "zuletzt aktualisiert ${quandoScaricata(state.fetchedAt)}", "dernière mise à jour ${quandoScaricata(state.fetchedAt)}", "última actualización ${quandoScaricata(state.fetchedAt)}"),
                    notaInRisalto = state.error != null,
                    palette = palette,
                    onClick = onAggiorna,
                    coda = { PastigliaAggiorna(palette) },
                )
                FiloGruppo(palette)
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    VoceInformativa(tr("Previsione", "Forecast", "Vorhersage", "Prévision", "Previsión"), tr("Open-Meteo · modello ${state.model.label}", "Open-Meteo · model ${state.model.label}", "Open-Meteo · Modell ${state.model.label}", "Open-Meteo · modèle ${state.model.label}", "Open-Meteo · modelo ${state.model.label}"), palette)
                    VoceInformativa(tr("Qualità dell'aria", "Air quality", "Luftqualität", "Qualité de l'air", "Calidad del aire"), "Open-Meteo", palette)
                    // Solo dove il mare c'e': altrove la riga direbbe di una
                    // fonte che per questa localita' non porta niente.
                    if (state.mare != null) VoceInformativa(tr("Mare", "Sea", "Meer", "Mer", "Mar"), tr("Open-Meteo · stima al largo", "Open-Meteo · open-water estimate", "Open-Meteo · Schätzung für die offene See", "Open-Meteo · estimation au large", "Open-Meteo · estimación mar adentro"), palette)
                    VoceInformativa(tr("Allerte", "Warnings", "Warnungen", "Alertes", "Alertas"), tr("Servizi meteorologici nazionali, più avvisi calcolati sui dati", "National weather services, plus notices worked out from the data", "Nationale Wetterdienste, dazu aus den Daten berechnete Hinweise", "Services météorologiques nationaux, plus des avis calculés à partir des données", "Servicios meteorológicos nacionales, más avisos calculados con los datos"), palette)
                    VoceInformativa(tr("Località", "Place", "Ort", "Lieu", "Lugar"), dettaglioLocalita(state), palette)
                    VoceInformativa(tr("Versione", "Version", "Version", "Version", "Versión"), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", palette)
                }
            }
        }
    }
}

/** Larga abbastanza per "km/h", "m/s" e "nodi" senza puntini, e uguale per le due righe. */
private val LarghezzaUnita = 176.dp

/** Il segno di "Aggiorna adesso": una pastiglia d'accento, perche' e' un'azione e non una porta. */
@Composable
private fun PastigliaAggiorna(palette: SalaPalette) {
    Text(
        text = tr("Aggiorna", "Refresh", "Aktualisieren", "Actualiser", "Actualizar"),
        style = SalaType.pill,
        color = palette.accentInk,
        modifier = Modifier
            .clip(CircleShape)
            .background(palette.accent)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/**
 * Una voce che **dice** invece di comandare: etichetta a sinistra, valore a
 * destra, sulla stessa riga.
 *
 * Non e' una [VoceImpostazione]: quella e' alta quanto un bersaglio del
 * pollice, e cinque fatti di una riga ciascuno non si toccano, si leggono.
 * Stanno fitti, sotto un filo, nello stesso riquadro della riga che li rifa'.
 */
@Composable
private fun VoceInformativa(etichetta: String, valore: String, palette: SalaPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = etichetta, style = SalaType.rowNote, color = palette.inkFaint)
        Text(
            text = valore,
            style = SalaType.rowNote,
            color = palette.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Da quanto e' vecchio il dato che si ha in mano, detto come lo direbbe una
 * persona.
 *
 * L'ora e' quella del **telefono** e non della localita', ed e' voluto: la
 * domanda e' *quando l'ho scaricata io*, non che ore fossero a Bergen quando e'
 * partita la richiesta.
 */
/** "07:00", dai minuti dalla mezzanotte. */
private fun oraRiepilogo(minuti: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minuti / 60, minuti % 60)

private fun quandoScaricata(quando: LocalDateTime?): String {
    if (quando == null) return tr("non ancora", "not yet", "noch nicht", "pas encore", "todavía no")
    val ora = String.format(Locale.ITALY, "%02d:%02d", quando.hour, quando.minute)
    return when (quando.toLocalDate()) {
        LocalDate.now() -> tr("oggi alle $ora", "today at $ora", "heute um $ora", "aujourd'hui à $ora", "hoy a las $ora")
        LocalDate.now().minusDays(1) -> tr("ieri alle $ora", "yesterday at $ora", "gestern um $ora", "hier à $ora", "ayer a las $ora")
        else -> tr("il ${quando.dayOfMonth}/${quando.monthValue} alle $ora", "on ${quando.dayOfMonth}/${quando.monthValue} at $ora", "am ${quando.dayOfMonth}.${quando.monthValue}. um $ora", "le ${quando.dayOfMonth}/${quando.monthValue} à $ora", "el ${quando.dayOfMonth}/${quando.monthValue} a las $ora")
    }
}

/**
 * La localita' per esteso: dove, con che coordinate, in che fuso, e **chi l'ha
 * scelta**.
 *
 * L'ultima parte non e' un dettaglio: un posto deciso dal telefono e un posto
 * scelto a mano si comportano diversamente quando ci si sposta, e finora non
 * c'era nessun punto dell'app che dicesse quale dei due fosse in corso.
 */
private fun dettaglioLocalita(state: UiState): String {
    val dove = listOfNotNull(
        state.place.name,
        state.place.detail.takeIf { it.isNotEmpty() },
    ).joinToString(", ")
    val punto = String.format(
        Lingue.locale,
        "%.2f / %.2f",
        state.place.latitude,
        state.place.longitude,
    )
    val chi = if (state.followsLocation) tr("dal telefono", "from the phone", "vom Telefon", "depuis le téléphone", "desde el teléfono") else tr("scelta a mano", "chosen by hand", "von Hand gewählt", "choisi à la main", "elegido a mano")
    val fuso = state.forecast?.utcOffsetSeconds?.let { fusoOrario(it) }
    return listOfNotNull(dove, punto, fuso, chi).joinToString(" · ")
}

/** Lo scarto dall'ora universale, scritto come lo scrive il mondo: `UTC+2`. */
private fun fusoOrario(secondi: Int): String {
    val segno = if (secondi < 0) "-" else "+"
    val minuti = kotlin.math.abs(secondi) / 60
    val ore = minuti / 60
    val resto = minuti % 60
    return if (resto == 0) "UTC$segno$ore" else "UTC$segno$ore:%02d".format(resto)
}
