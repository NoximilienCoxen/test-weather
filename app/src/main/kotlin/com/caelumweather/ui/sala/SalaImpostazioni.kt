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
import com.caelumweather.notifiche.pioggiaDiSoglia
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
    onToggleColoriTelefono: (Boolean) -> Unit = {},
    onToggleWidgetColori: (Boolean) -> Unit,
    onToggleSchedeLarghe: (Boolean) -> Unit,
    onApriGuida: () -> Unit,
    onToggleNotifichePioggia: (Boolean) -> Unit,
    onToggleNotificheAllerte: (Boolean) -> Unit,
    onToggleRiepilogo: (Boolean) -> Unit,
    onChooseOraRiepilogo: (Int) -> Unit,
    onToggleOmbrello: (Boolean) -> Unit = {},
    onChooseOraOmbrello: (Int) -> Unit = {},
    onToggleCambioPrevisione: (Boolean) -> Unit = {},
    onToggleSoglia: (TipoSoglia, Boolean) -> Unit,
    onChooseSoglia: (TipoSoglia, Int) -> Unit,
    onToggleSoglieAltre: (Boolean) -> Unit,
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
                    // Era "Schede larghe", accesa toglieva la colonna. Ora la
                    // colonna e' spenta di serie e la voce dice cosa aggiunge.
                    titolo = tr("Colonna delle icone", "Icon column", "Symbolspalte", "Colonne d'icônes", "Columna de iconos"),
                    nota = tr("le schermate in una colonna a destra, invece dei puntini in basso", "the screens in a column on the right, instead of the dots at the bottom", "die Bildschirme in einer Spalte rechts statt der Punkte unten", "les écrans dans une colonne à droite, au lieu des points en bas", "las pantallas en una columna a la derecha, en lugar de los puntos abajo"),
                    acceso = !state.schedeLarghe,
                    palette = palette,
                    onCambia = { colonna -> onToggleSchedeLarghe(!colonna) },
                )
                // I colori dinamici esistono da Android 12: prima, la voce
                // sarebbe una levetta che non cambia niente.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    FiloGruppo(palette)
                    VoceInterruttore(
                        titolo = tr("Colori del telefono", "Phone colours", "Telefonfarben", "Couleurs du téléphone", "Colores del teléfono"),
                        nota = tr("schede, celle e accento prendono la tinta dello sfondo del telefono; il cielo resta quello vero", "cards, cells and accent take the tint of the phone's wallpaper; the sky stays the real one", "Karten, Zellen und Akzent übernehmen die Tönung des Hintergrundbilds; der Himmel bleibt echt", "fiches, cases et accent prennent la teinte du fond d'écran ; le ciel reste le vrai", "fichas, celdas y acento toman el tono del fondo de pantalla; el cielo sigue siendo el real"),
                        acceso = state.coloriTelefono,
                        palette = palette,
                        onCambia = onToggleColoriTelefono,
                    )
                    FiloGruppo(palette)
                    VoceInterruttore(
                        titolo = tr("Widget coi colori del telefono", "Widgets in phone colours", "Widgets in Telefonfarben", "Widgets aux couleurs du téléphone", "Widgets con los colores del teléfono"),
                        nota = tr("Material You: sfondo e testo prendono la tinta dello sfondo del telefono", "Material You: background and text take the tint of the phone's wallpaper", "Material You: Hintergrund und Text übernehmen die Tönung des Hintergrundbilds", "Material You : le fond et le texte prennent la teinte du fond d'écran", "Material You: el fondo y el texto toman el tono del fondo de pantalla"),
                        acceso = state.widgetColoriTelefono,
                        palette = palette,
                        onCambia = onToggleWidgetColori,
                    )
                }
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

            // ── Avvisi e notifiche, per argomento ────────────────────────────
            //
            // **Erano tre gruppi che si sovrapponevano** ("Avvisi nelle
            // schermate", "Notifiche", "Avvisi personali"): "Vento forte" due
            // volte, l'UV due volte, la pioggia quattro, e nessun titolo diceva
            // quale voce faceva comparire qualcosa nell'app e quale suonava. Ora
            // un riquadro solo, diviso per argomento; dentro, prima cio' che si
            // vede nell'app, poi le notifiche, segnate tutte allo stesso modo
            // (`segno`). **Le chiavi DataStore sono quelle di prima**: cambia
            // solo dove stanno le righe e cosa dicono (CONTESTO §83).
            //
            // Gli avvisi nell'app filtrano solo quelli calcolati: un'allerta di
            // un ente si vede sempre, e la riga "Arancioni e rosse" lo dice.
            //
            // Accendere una notifica senza il permesso non accenderebbe niente:
            // su Android 13 e oltre l'interruttore lo chiede, e se viene negato
            // la voce resta spenta, perche' un interruttore acceso che non fa
            // niente e' peggio di uno spento.
            val contesto = LocalContext.current
            val puoNotificare = PioggiaInArrivoWorker.puoNotificare(contesto)
            val chiediPermesso = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { concesso -> onToggleNotifichePioggia(concesso) }
            val chiediPermessoAllerte = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { concesso -> onToggleNotificheAllerte(concesso) }
            val chiediPermessoRiepilogo = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { concesso -> onToggleRiepilogo(concesso) }
            val chiediPermessoOmbrello = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { concesso -> onToggleOmbrello(concesso) }
            val chiediPermessoCambio = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { concesso -> onToggleCambioPrevisione(concesso) }
            // Un permesso solo per le cinque soglie: si ricorda quale levetta
            // l'ha chiesto, e se viene negato quella resta giu'.
            var sogliaInAttesa by remember { mutableStateOf<TipoSoglia?>(null) }
            val chiediPermessoSoglia = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { concesso ->
                sogliaInAttesa?.let { onToggleSoglia(it, concesso) }
                sogliaInAttesa = null
            }
            val serveIlPermesso = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !puoNotificare
            fun notifica(vuole: Boolean, chiedi: () -> Unit, cambia: (Boolean) -> Unit) = when {
                !vuole -> cambia(false)
                serveIlPermesso -> chiedi()
                else -> cambia(true)
            }

            // Le note dicono tutte di che cosa si tratta: "Nell'app ·" per cio'
            // che compare nelle schermate, la campanella per cio' che suona.
            // Si era provata anche la parola "Notifica ·": allungava le note
            // fino ad andare a capo, ed e' stata scelta la campanella.
            fun nellApp(testo: String) = tr("Nell'app", "In the app", "In der App", "Dans l'app", "En la app") + " · " + testo

            // Come si scrive il valore di una soglia, nell'unita' scelta.
            fun scritta(tipo: TipoSoglia, valore: Int): String = when (tipo) {
                TipoSoglia.VENTO -> ventoDiSoglia(valore, state.windUnit)
                TipoSoglia.PIOGGIA -> pioggiaDiSoglia(valore)
                TipoSoglia.UV -> "UV $valore"
                else -> gradiDiSoglia(valore, state.unit)
            }

            // **Il selettore di una soglia, uno per argomento** (§84): per vento,
            // UV e caldo lo stesso numero vale per l'avviso nell'app e per la
            // notifica, e si vede se almeno una delle due e' accesa.
            @Composable
            fun SelettoreSoglia(tipo: TipoSoglia, visibile: Boolean) {
                ComparsaMorbida(visibile = visibile) {
                    SceltaSegmentata(
                        voci = SogliePersonali.possibili(tipo).map { it to scritta(tipo, it) },
                        scelta = state.soglie.valore(tipo),
                        palette = palette,
                        onScegli = { onChooseSoglia(tipo, it) },
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                    )
                }
            }

            /** Per vento, UV e caldo il selettore sta sotto le due voci, non qui. */
            fun condivisa(tipo: TipoSoglia) = tipo == TipoSoglia.VENTO || tipo == TipoSoglia.UV || tipo == TipoSoglia.CALDO

            @Composable
            fun VoceSoglia(tipo: TipoSoglia) {
                val accesa = state.soglie.accesa(tipo) && puoNotificare
                val s = scritta(tipo, state.soglie.valore(tipo))
                VoceInterruttore(
                    titolo = when (tipo) {
                        TipoSoglia.GELO -> tr("Gelo", "Frost", "Frost", "Gel", "Helada")
                        TipoSoglia.CALDO -> tr("Caldo", "Heat", "Hitze", "Chaleur", "Calor")
                        TipoSoglia.VENTO -> tr("Raffiche", "Gusts", "Böen", "Rafales", "Rachas")
                        TipoSoglia.PIOGGIA -> tr("Pioggia forte", "Heavy rain", "Starkregen", "Pluie forte", "Lluvia fuerte")
                        TipoSoglia.UV -> tr("UV alto", "High UV", "Hoher UV-Index", "UV élevés", "UV alto")
                    },
                    nota = (
                        when (tipo) {
                            TipoSoglia.GELO ->
                                if (accesa) tr("minima a $s o sotto", "low of $s or below", "Tiefstwert $s oder darunter", "minimale à $s ou moins", "mínima de $s o menos")
                                else tr("quando la minima scende sotto una soglia tua", "when the low drops below your threshold", "wenn der Tiefstwert unter deine Schwelle fällt", "quand la minimale passe sous votre seuil", "cuando la mínima baja de tu umbral")
                            TipoSoglia.CALDO -> tr("percepiti da $s", "feels like $s or more", "gefühlt ab $s", "ressenti dès $s", "sensación desde $s")
                            TipoSoglia.VENTO -> tr("raffiche da $s", "gusts from $s", "Böen ab $s", "rafales dès $s", "rachas desde $s")
                            TipoSoglia.PIOGGIA ->
                                if (accesa) tr("da $s", "from $s", "ab $s", "dès $s", "desde $s")
                                else tr("quando la pioggia di un'ora passa una soglia tua", "when an hour's rain passes your threshold", "wenn der Regen einer Stunde deine Schwelle überschreitet", "quand la pluie d'une heure dépasse votre seuil", "cuando la lluvia de una hora supera tu umbral")
                            TipoSoglia.UV -> tr("da $s", "from $s", "ab $s", "dès $s", "desde $s")
                        }
                    ),
                    acceso = accesa,
                    palette = palette,
                    compatta = true,
                    campanella = true,
                    onCambia = { vuole ->
                        notifica(vuole, chiedi = {
                            sogliaInAttesa = tipo
                            chiediPermessoSoglia.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) { onToggleSoglia(tipo, it) }
                    },
                )
                // Gelo e pioggia forte hanno il valore solo per la notifica: si
                // sceglie a voce accesa (vedi la regola in testa).
                if (!condivisa(tipo)) SelettoreSoglia(tipo, visibile = accesa)
            }

            GruppoImpostazioni(
                titolo = tr("AVVISI E NOTIFICHE", "NOTICES AND NOTIFICATIONS", "HINWEISE UND BENACHRICHTIGUNGEN", "AVIS ET NOTIFICATIONS", "AVISOS Y NOTIFICACIONES"),
                palette = palette,
                // **Solo cio' che e' vero**: tacciono di notte le cinque soglie
                // (`SogliePersonaliWorker.oraDiParlare`), non la pioggia in
                // arrivo, le allerte o la previsione cambiata, che girano a ogni
                // ora; ombrello e riepilogo arrivano all'ora scelta.
                nota = tr(
                    "Le voci con la campanella arrivano come notifica. Per vento, UV e caldo il numero scelto vale anche nell'app. Pioggia forte, Raffiche, UV alto, Gelo e Caldo tacciono fra le 22 e le 7. Sono previsioni: allerte ufficiali sono solo «Arancioni e rosse».",
                    "Items with the bell arrive as notifications. For wind, UV and heat the number you choose applies in the app too. Heavy rain, Gusts, High UV, Frost and Heat stay quiet from 10 pm to 7 am. They are forecasts: only «Orange and red» are official warnings.",
                    "Einträge mit der Glocke kommen als Benachrichtigung. Für Wind, UV und Hitze gilt die gewählte Zahl auch in der App. Starkregen, Böen, Hoher UV-Index, Frost und Hitze schweigen von 22 bis 7 Uhr. Es sind Vorhersagen: amtliche Warnungen sind nur «Orange und rot».",
                    "Les éléments avec la cloche arrivent en notification. Pour le vent, les UV et la chaleur, le seuil choisi vaut aussi dans l'app. Pluie forte, Rafales, UV élevés, Gel et Chaleur se taisent de 22 h à 7 h. Ce sont des prévisions : seules « Orange et rouges » sont des alertes officielles.",
                    "Las opciones con la campana llegan como notificación. Para viento, UV y calor el número elegido vale también en la app. Lluvia fuerte, Rachas, UV alto, Helada y Calor callan de 22 a 7 h. Son previsiones: solo «Naranjas y rojas» son alertas oficiales.",
                ),
            ) {
                // ── Pioggia ──
                // La pioggia ha due misure e due nomi (§84): "abbondante" sono i
                // millimetri del giorno, nell'app; "forte" quelli di un'ora, in
                // notifica. Non si fingono un numero solo.
                SottotitoloGruppo(tr("PIOGGIA E NEVE", "RAIN AND SNOW", "REGEN UND SCHNEE", "PLUIE ET NEIGE", "LLUVIA Y NIEVE"), palette, primo = true)
                VoceInterruttore(
                    titolo = tr("Pioggia abbondante", "Lots of rain", "Ergiebiger Regen", "Pluie abondante", "Lluvia abundante"),
                    nota = nellApp(tr("da 40 mm nel giorno", "from 40 mm in a day", "ab 40 mm am Tag", "dès 40 mm dans la journée", "desde 40 mm en el día")),
                    acceso = state.alertToggles.pioggiaIntensa,
                    palette = palette,
                    compatta = true,
                    onCambia = { onToggleAlert(AlertToggleKind.PIOGGIA, it) },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Neve", "Snow", "Schnee", "Neige", "Nieve"),
                    nota = nellApp(tr("da 5 cm nel giorno", "from 5 cm in a day", "ab 5 cm am Tag", "dès 5 cm dans la journée", "desde 5 cm en el día")),
                    acceso = state.alertToggles.neve,
                    palette = palette,
                    compatta = true,
                    onCambia = { onToggleAlert(AlertToggleKind.NEVE, it) },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Temporali", "Thunderstorms", "Gewitter", "Orages", "Tormentas"),
                    nota = nellApp(tr("da 2 ore di temporale nel giorno", "from 2 hours of thunderstorms in a day", "ab 2 Gewitterstunden am Tag", "dès 2 heures d'orage dans la journée", "desde 2 horas de tormenta en el día")),
                    acceso = state.alertToggles.temporali,
                    palette = palette,
                    compatta = true,
                    onCambia = { onToggleAlert(AlertToggleKind.TEMPORALE, it) },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Pioggia in arrivo", "Rain on the way", "Regen im Anmarsch", "Pluie en approche", "Lluvia en camino"),
                    nota = (tr("nella prossima ora: pioggia, grandine o neve", "in the next hour: rain, hail or snow", "in der nächsten Stunde: Regen, Hagel oder Schnee", "dans l'heure : pluie, grêle ou neige", "en la próxima hora: lluvia, granizo o nieve")),
                    acceso = state.notifichePioggia && puoNotificare,
                    palette = palette,
                    compatta = true,
                    campanella = true,
                    onCambia = { notifica(it, { chiediPermesso.launch(Manifest.permission.POST_NOTIFICATIONS) }, onToggleNotifichePioggia) },
                )
                FiloGruppo(palette)
                val ombrelloAcceso = state.avvisoOmbrello && puoNotificare
                VoceInterruttore(
                    titolo = tr("Porta l'ombrello", "Take an umbrella", "Schirm mitnehmen", "Prends un parapluie", "Lleva paraguas"),
                    nota = (
                        if (ombrelloAcceso) {
                            val ora = oraRiepilogo(state.oraOmbrello)
                            tr("alle $ora, nei giorni di pioggia", "at $ora, on rainy days", "um $ora, an Regentagen", "à $ora, les jours de pluie", "a las $ora, los días de lluvia")
                        } else {
                            tr("al mattino, nei giorni di pioggia", "in the morning, on rainy days", "morgens, an Regentagen", "le matin, les jours de pluie", "por la mañana, los días de lluvia")
                        }
                    ),
                    acceso = ombrelloAcceso,
                    palette = palette,
                    compatta = true,
                    campanella = true,
                    onCambia = { notifica(it, { chiediPermessoOmbrello.launch(Manifest.permission.POST_NOTIFICATIONS) }, onToggleOmbrello) },
                )
                ComparsaMorbida(visibile = ombrelloAcceso) {
                    SceltaSegmentata(
                        voci = com.caelumweather.notifiche.OmbrelloWorker.ORE_POSSIBILI.map { it to oraRiepilogo(it) },
                        scelta = state.oraOmbrello,
                        palette = palette,
                        onScegli = onChooseOraOmbrello,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                    )
                }
                FiloGruppo(palette)
                VoceSoglia(TipoSoglia.PIOGGIA)

                // ── Vento ──
                SottotitoloGruppo(tr("VENTO", "WIND", "WIND", "VENT", "VIENTO"), palette)
                VoceInterruttore(
                    titolo = tr("Vento forte", "Strong wind", "Starker Wind", "Vent fort", "Viento fuerte"),
                    // La stessa soglia della notifica qui sotto (`SoglieAvvisi`).
                    nota = nellApp(scritta(TipoSoglia.VENTO, state.soglie.ventoKmh).let { v -> tr("raffiche da $v", "gusts from $v", "Böen ab $v", "rafales dès $v", "rachas desde $v") }),
                    acceso = state.alertToggles.ventoForte,
                    palette = palette,
                    compatta = true,
                    onCambia = { onToggleAlert(AlertToggleKind.VENTO, it) },
                )
                FiloGruppo(palette)
                VoceSoglia(TipoSoglia.VENTO)
                SelettoreSoglia(TipoSoglia.VENTO, state.alertToggles.ventoForte || (state.soglie.vento && puoNotificare))

                // ── Raggi UV ──
                SottotitoloGruppo(tr("RAGGI UV", "UV", "UV", "UV", "UV"), palette)
                VoceInterruttore(
                    titolo = tr("Raggi UV alti", "Strong UV", "Starke UV-Strahlung", "UV forts", "UV fuertes"),
                    nota = nellApp(scritta(TipoSoglia.UV, state.soglie.uvIndice).let { u -> tr("da $u", "from $u", "ab $u", "dès $u", "desde $u") }),
                    acceso = state.alertToggles.uvAlto,
                    palette = palette,
                    compatta = true,
                    onCambia = { onToggleAlert(AlertToggleKind.UV, it) },
                )
                FiloGruppo(palette)
                VoceSoglia(TipoSoglia.UV)
                SelettoreSoglia(TipoSoglia.UV, state.alertToggles.uvAlto || (state.soglie.uv && puoNotificare))

                // ── Temperatura ──
                SottotitoloGruppo(tr("TEMPERATURA", "TEMPERATURE", "TEMPERATUR", "TEMPÉRATURE", "TEMPERATURA"), palette)
                // Caldo: un numero solo, sulla percepita, per l'app e per la
                // notifica. Freddo intenso e gelo restano due cose: il freddo
                // percepito a -5° e la brina sotto 0° reali (§84).
                VoceInterruttore(
                    titolo = tr("Caldo intenso", "Intense heat", "Große Hitze", "Forte chaleur", "Calor intenso"),
                    nota = nellApp(scritta(TipoSoglia.CALDO, state.soglie.caldoGradi).let { g -> tr("percepiti da $g", "feels like $g or more", "gefühlt ab $g", "ressenti dès $g", "sensación desde $g") }),
                    acceso = state.alertToggles.caldo,
                    palette = palette,
                    compatta = true,
                    onCambia = { onToggleAlert(AlertToggleKind.CALDO, it) },
                )
                FiloGruppo(palette)
                VoceSoglia(TipoSoglia.CALDO)
                SelettoreSoglia(TipoSoglia.CALDO, state.alertToggles.caldo || (state.soglie.caldo && puoNotificare))
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Freddo intenso", "Intense cold", "Strenge Kälte", "Froid intense", "Frío intenso"),
                    nota = nellApp(gradiDiSoglia(-5, state.unit).let { g -> tr("percepiti da $g in giù", "feels like $g or less", "gefühlt ab $g abwärts", "ressenti dès $g et moins", "sensación desde $g hacia abajo") }),
                    acceso = state.alertToggles.freddo,
                    palette = palette,
                    compatta = true,
                    onCambia = { onToggleAlert(AlertToggleKind.FREDDO, it) },
                )
                FiloGruppo(palette)
                VoceSoglia(TipoSoglia.GELO)
                // Vale solo per le cinque soglie, e lo dice: compare quando
                // almeno una e' accesa, subito sotto l'ultima.
                ComparsaMorbida(visibile = state.soglie.qualcunaAccesa && puoNotificare) {
                    Column {
                        FiloGruppo(palette)
                        VoceInterruttore(
                            titolo = tr("Soglie anche sulle località salvate", "Thresholds for saved places too", "Schwellen auch für gespeicherte Orte", "Seuils aussi pour les lieux enregistrés", "Umbrales también para los lugares guardados"),
                            nota = tr(
                                "Pioggia forte, Raffiche, UV alto, Gelo e Caldo, fino a ${SogliePersonali.ALTRE_LOCALITA_MAX} località",
                                "Heavy rain, Gusts, High UV, Frost and Heat, up to ${SogliePersonali.ALTRE_LOCALITA_MAX} places",
                                "Starkregen, Böen, Hoher UV-Index, Frost und Hitze, bis zu ${SogliePersonali.ALTRE_LOCALITA_MAX} Orte",
                                "Pluie forte, Rafales, UV élevés, Gel et Chaleur, jusqu'à ${SogliePersonali.ALTRE_LOCALITA_MAX} lieux",
                                "Lluvia fuerte, Rachas, UV alto, Helada y Calor, hasta ${SogliePersonali.ALTRE_LOCALITA_MAX} lugares",
                            ),
                            acceso = state.soglie.altreLocalita,
                            palette = palette,
                            compatta = true,
                            onCambia = onToggleSoglieAltre,
                        )
                    }
                }

                // ── Allerte ufficiali ──
                SottotitoloGruppo(tr("ALLERTE UFFICIALI", "OFFICIAL WARNINGS", "AMTLICHE WARNUNGEN", "ALERTES OFFICIELLES", "ALERTAS OFICIALES"), palette)
                VoceInterruttore(
                    titolo = tr("Arancioni e rosse", "Orange and red", "Orange und rot", "Orange et rouges", "Naranjas y rojas"),
                    nota = (tr("degli enti; nell'app si vedono tutte, anche le gialle", "from the authorities; in the app you see them all, yellow too", "der Behörden; in der App siehst du alle, auch gelbe", "des organismes ; dans l'app on les voit toutes, même les jaunes", "de los organismos; en la app se ven todas, también las amarillas")),
                    acceso = state.notificheAllerte && puoNotificare,
                    palette = palette,
                    compatta = true,
                    campanella = true,
                    onCambia = { notifica(it, { chiediPermessoAllerte.launch(Manifest.permission.POST_NOTIFICATIONS) }, onToggleNotificheAllerte) },
                )

                // ── Riepiloghi ──
                SottotitoloGruppo(tr("RIEPILOGHI", "SUMMARIES", "ÜBERSICHTEN", "RÉSUMÉS", "RESÚMENES"), palette)
                val riepilogoAcceso = state.riepilogoMattino && puoNotificare
                VoceInterruttore(
                    titolo = tr("Riepilogo del mattino", "Morning summary", "Morgenüberblick", "Résumé du matin", "Resumen de la mañana"),
                    nota = (
                        if (riepilogoAcceso) {
                            val ora = oraRiepilogo(state.oraRiepilogo)
                            tr("ogni giorno alle $ora", "every day at $ora", "jeden Tag um $ora", "chaque jour à $ora", "cada día a las $ora")
                        } else {
                            tr("ogni mattina, la giornata in tre righe", "every morning, the day in three lines", "jeden Morgen, der Tag in drei Zeilen", "chaque matin, la journée en trois lignes", "cada mañana, el día en tres líneas")
                        }
                    ),
                    acceso = riepilogoAcceso,
                    palette = palette,
                    compatta = true,
                    campanella = true,
                    onCambia = { notifica(it, { chiediPermessoRiepilogo.launch(Manifest.permission.POST_NOTIFICATIONS) }, onToggleRiepilogo) },
                )
                ComparsaMorbida(visibile = riepilogoAcceso) {
                    SceltaSegmentata(
                        voci = RiepilogoMattinoWorker.ORE_POSSIBILI.map { it to oraRiepilogo(it) },
                        scelta = state.oraRiepilogo,
                        palette = palette,
                        onScegli = onChooseOraRiepilogo,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                    )
                }
                FiloGruppo(palette)
                // ±4 gradi Celsius (`CambioPrevisioneWorker`), detti nella scala di chi legge.
                val scarto = if (state.unit == TempUnit.FAHRENHEIT) 7 else 4
                VoceInterruttore(
                    titolo = tr("Previsione cambiata", "Forecast changed", "Vorhersage geändert", "Prévision modifiée", "Previsión cambiada"),
                    nota = (
                        tr(
                            "domani e dopodomani: pioggia che compare o sparisce, massima ±$scarto°",
                            "tomorrow and the day after: rain appearing or vanishing, high ±$scarto°",
                            "morgen und übermorgen: Regen kommt oder geht, Höchstwert ±$scarto°",
                            "demain et après-demain : pluie qui apparaît ou disparaît, max ±$scarto°",
                            "mañana y pasado: lluvia que aparece o desaparece, máxima ±$scarto°",
                        )
                    ),
                    acceso = state.avvisoCambioPrevisione && puoNotificare,
                    palette = palette,
                    compatta = true,
                    campanella = true,
                    onCambia = { notifica(it, { chiediPermessoCambio.launch(Manifest.permission.POST_NOTIFICATIONS) }, onToggleCambioPrevisione) },
                )
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

/** Il sottotitolo di un argomento dentro il riquadro "Avvisi e notifiche". */
@Composable
private fun SottotitoloGruppo(testo: String, palette: SalaPalette, primo: Boolean = false) {
    if (!primo) FiloGruppo(palette)
    Text(
        text = testo,
        style = SalaType.sectionLabel,
        color = palette.accent,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
    )
}
