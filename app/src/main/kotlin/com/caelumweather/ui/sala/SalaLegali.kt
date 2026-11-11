package com.caelumweather.ui.sala

import com.caelumweather.data.FonteAllerte
import com.caelumweather.lingua.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Note legali e privacy: cosa fa l'app, detto dove si puo' leggere.
 *
 * **Ogni riga qui dentro e' un fatto verificabile nel codice**, non una formula
 * presa altrove. E' una scelta, e vale la pena dichiararla: queste non sono
 * condizioni d'uso. Un contratto lo scrive chi pubblica l'app e se ne assume la
 * responsabilita'; questa e' una dichiarazione di cosa il programma fa, e la si
 * puo' controllare aprendo i file che nomina.
 *
 * La pagina nasce da due cose diverse che chiedevano lo stesso posto.
 *
 * La prima e' **una decisione rimasta in sospeso da sezione 8-ter di CONTESTO**:
 * *"Restano da decidere: una riga nelle impostazioni che dichiari le due
 * fonti"*, con una motivazione che non e' di stile ma di legge. L'app mette in
 * scena due specie di avvisi - i bollettini di un ente e le soglie che calcola
 * da se' - e il codice gia' non li confonde: `SalaChrome` scrive "ALLERTA" solo
 * per i primi, perche' *"«Allerta» e' una parola che spetta a un ente"*. Quella
 * regola pero' viveva solo in una pastiglia larga due centimetri. Qui e'
 * scritta per esteso.
 *
 * La seconda e' che **nessuna schermata diceva cosa esce dal telefono**. Esce
 * poco: le coordinate della localita' scelta. Ma "poco" detto da chi scrive il
 * programma non vale niente se chi lo usa non ha modo di leggerlo.
 *
 * **L'attribuzione viene dalle pagine delle fonti, non dalla memoria.** Era
 * rimasta una riga generica finche' le frasi verbatim non si potevano leggere;
 * le ha scaricate la CI (`scripts/probe_licenze.py`, CONTESTO §47) e stanno in
 * `Fonti`, da dove le legge anche la riga dei crediti sotto la barra delle ore.
 */
@Composable
fun SalaLegaliScreen(
    palette: SalaPalette,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.schermoPieno)
            .systemBarsPadding()
            .padding(start = 26.dp, end = 26.dp, top = 12.dp, bottom = 30.dp),
    ) {
        IntestazioneServizio(titolo = tr("Note legali e privacy", "Legal notes and privacy", "Rechtliches und Datenschutz", "Mentions légales et confidentialité", "Notas legales y privacidad"), palette = palette, onIndietro = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BloccoImpostazioni(etichetta = tr("DA DOVE VENGONO I DATI", "WHERE THE DATA COME FROM", "WOHER DIE DATEN KOMMEN", "D'OÙ VIENNENT LES DONNÉES", "DE DÓNDE VIENEN LOS DATOS"), palette = palette) {
                Paragrafo(
                    tr(
                        "La previsione, la qualità dell'aria e, per le località di costa, onde e " +
                            "temperatura del mare arrivano da Open-Meteo. " +
                            "La ricerca delle località passa dal suo servizio di geocodifica. " +
                            "Le allerte ufficiali arrivano dai servizi meteorologici nazionali: " +
                            "${FonteAllerte.elenco}.",
                        "Forecasts, air quality and, for coastal places, waves and sea temperature come from Open-Meteo. Place search goes through its geocoding service. " +
                            "Official warnings come from the national weather services: ${FonteAllerte.elenco}.",
                        "Vorhersage, Luftqualität und – für Küstenorte – Wellen und Meerestemperatur kommen von Open-Meteo. Die Ortssuche läuft über dessen Geocoding-Dienst. Amtliche Warnungen kommen von den nationalen Wetterdiensten: ${FonteAllerte.elenco}.",
                        "La prévision, la qualité de l'air et, pour les lieux côtiers, les vagues et la température de la mer viennent d'Open-Meteo. La recherche de lieux passe par son service de géocodage. Les alertes officielles viennent des services météorologiques nationaux : ${FonteAllerte.elenco}.",
                        "La previsión, la calidad del aire y, en los lugares de costa, las olas y la temperatura del mar vienen de Open-Meteo. La búsqueda de lugares pasa por su servicio de geocodificación. Las alertas oficiales vienen de los servicios meteorológicos nacionales: ${FonteAllerte.elenco}.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Sono i soli indirizzi che questa applicazione interroga: quattro di Open-Meteo e " +
                            "uno per ciascun servizio di allerta. Non ce ne sono altri.",
                        "These are the only addresses this app contacts: four Open-Meteo ones and one " +
                            "for each warning service. There are no others.",
                        "Das sind die einzigen Adressen, die diese App kontaktiert: vier von Open-Meteo und eine für jeden Warndienst. Weitere gibt es nicht.",
                        "Ce sont les seules adresses que cette application contacte : quatre d'Open-Meteo et une pour chaque service d'alerte. Il n'y en a pas d'autres.",
                        "Son las únicas direcciones que esta aplicación consulta: cuatro de Open-Meteo y una por cada servicio de alertas. No hay otras.",
                    ),
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = tr("ALLERTE E AVVISI NON SONO LA STESSA COSA", "WARNINGS AND NOTICES ARE NOT THE SAME THING", "WARNUNGEN UND HINWEISE SIND NICHT DASSELBE", "ALERTES ET AVIS NE SONT PAS LA MÊME CHOSE", "ALERTAS Y AVISOS NO SON LO MISMO"), palette = palette) {
                Paragrafo(
                    tr(
                        "Dove c'è scritto ALLERTA, l'avviso viene da un servizio meteorologico nazionale " +
                            "(in Europa attraverso MeteoAlarm) ed è quel servizio a emetterlo. " +
                            "È un'allerta meteo, non un messaggio di protezione civile.",
                        "Where it says WARNING, it comes from a national weather service (in Europe through " +
                            "MeteoAlarm) and it is that service that issues it. It is a weather warning, not a civil protection alert.",
                        "Wo WARNUNG steht, kommt sie von einem nationalen Wetterdienst (in Europa über MeteoAlarm), und dieser Dienst gibt sie heraus. Es ist eine Wetterwarnung, keine Meldung des Katastrophenschutzes.",
                        "Là où il est écrit ALERTE, elle vient d'un service météorologique national (en Europe via MeteoAlarm) et c'est ce service qui l'émet. C'est une alerte météo, pas un message de la sécurité civile.",
                        "Donde pone ALERTA, viene de un servicio meteorológico nacional (en Europa a través de MeteoAlarm) y es ese servicio quien la emite. Es una alerta meteorológica, no un mensaje de protección civil.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Dove c'è scritto AVVISO, invece, è questa applicazione ad averlo " +
                            "calcolato da sola: confronta i numeri della previsione — millimetri " +
                            "di pioggia, raffiche, indice UV, codice del tempo — con delle soglie " +
                            "scritte nel programma. Non è un avviso di protezione civile e non " +
                            "sostituisce un bollettino ufficiale.",
                        "Where it says NOTICE, it is this app that worked it out on its own: it compares the forecast numbers — millimetres of rain, gusts, UV index, weather code — with thresholds written into the program. It is not a civil protection warning and does not replace an official bulletin.",
                        "Wo HINWEIS steht, hat ihn dagegen diese App selbst berechnet: Sie vergleicht die Zahlen der Vorhersage — Millimeter Regen, Böen, UV-Index, Wettercode — mit Schwellen, die im Programm stehen. Das ist keine Warnung des Katastrophenschutzes und ersetzt keinen amtlichen Warnbericht.",
                        "Là où il est écrit AVIS, c'est en revanche cette application qui l'a calculé seule : elle compare les chiffres de la prévision — millimètres de pluie, rafales, indice UV, code météo — à des seuils écrits dans le programme. Ce n'est pas un avis de la sécurité civile et cela ne remplace pas un bulletin officiel.",
                        "Donde pone AVISO, en cambio, lo ha calculado esta aplicación por su cuenta: compara los números de la previsión — milímetros de lluvia, rachas, índice UV, código del tiempo — con unos umbrales escritos en el programa. No es un aviso de protección civil y no sustituye a un boletín oficial.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Per la stessa ragione gli avvisi calcolati non arrivano mai al rosso: " +
                            "il rosso è una valutazione del rischio sul territorio, e un confronto " +
                            "fra un numero e una costante non può farla.",
                        "For the same reason calculated notices never reach red: red is an assessment of risk on the ground, and comparing a number with a constant cannot make it.",
                        "Aus demselben Grund erreichen berechnete Hinweise nie Rot: Rot ist eine Einschätzung des Risikos vor Ort, und der Vergleich einer Zahl mit einer Konstante kann sie nicht leisten.",
                        "Pour la même raison, les avis calculés n'atteignent jamais le rouge : le rouge est une évaluation du risque sur le terrain, et comparer un chiffre à une constante ne peut pas la faire.",
                        "Por la misma razón los avisos calculados nunca llegan al rojo: el rojo es una valoración del riesgo sobre el terreno, y comparar un número con una constante no puede hacerla.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Gli AVVISI PERSONALI (gelo, caldo, vento forte) sono un'altra cosa ancora: " +
                            "la soglia la scegli tu, e la notifica ti dice soltanto che la previsione " +
                            "la passa. Una previsione può sbagliare, e l'avviso può arrivare tardi o " +
                            "non arrivare: non affidargli la sicurezza di persone, animali o cose.",
                        "PERSONAL NOTICES (frost, heat, strong wind) are something else again: you choose the threshold, and the notification only tells you that the forecast crosses it. A forecast can be wrong, and the notice can arrive late or not at all: do not rely on it for the safety of people, animals or property.",
                        "PERSÖNLICHE HINWEISE (Frost, Hitze, starker Wind) sind noch einmal etwas anderes: Die Schwelle wählst du, und die Benachrichtigung sagt dir nur, dass die Vorhersage sie überschreitet. Eine Vorhersage kann falsch sein, und der Hinweis kann zu spät oder gar nicht kommen: Verlasse dich für die Sicherheit von Menschen, Tieren oder Sachen nicht darauf.",
                        "Les AVIS PERSONNELS (gel, chaleur, vent fort) sont encore autre chose : c'est vous qui choisissez le seuil, et la notification vous dit seulement que la prévision le franchit. Une prévision peut se tromper, et l'avis peut arriver tard ou ne pas arriver : ne lui confiez pas la sécurité de personnes, d'animaux ou de biens.",
                        "Los AVISOS PERSONALES (helada, calor, viento fuerte) son otra cosa distinta: el umbral lo eliges tú, y la notificación solo te dice que la previsión lo supera. Una previsión puede equivocarse, y el aviso puede llegar tarde o no llegar: no le confíes la seguridad de personas, animales o cosas.",
                    ),
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = tr("COSA ESCE DAL TELEFONO", "WHAT LEAVES THE PHONE", "WAS DAS TELEFON VERLÄSST", "CE QUI QUITTE LE TÉLÉPHONE", "QUÉ SALE DEL TELÉFONO"), palette = palette) {
                Paragrafo(
                    tr(
                        "Le coordinate della località mostrata, perché servono a chiedere la " +
                            "previsione per quel punto, e un'etichetta che dice quale programma " +
                            "sta chiedendo.",
                        "The coordinates of the place shown, because they are needed to ask for that spot's forecast, and a label saying which program is asking.",
                        "Die Koordinaten des angezeigten Ortes, weil sie nötig sind, um die Vorhersage für diesen Punkt abzufragen, und ein Etikett, das sagt, welches Programm fragt.",
                        "Les coordonnées du lieu affiché, parce qu'elles servent à demander la prévision pour ce point, et une étiquette indiquant quel programme fait la demande.",
                        "Las coordenadas del lugar mostrado, porque hacen falta para pedir la previsión de ese punto, y una etiqueta que dice qué programa la pide.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Nient'altro: nessun account, nessun identificativo del telefono, " +
                            "nessuna statistica d'uso, nessun invio automatico degli errori. " +
                            "Non è una promessa, è l'elenco di ciò che l'applicazione contiene: " +
                            "di quelle cose non c'è il codice per farle.",
                        "Nothing else: no account, no phone identifier, no usage statistics, no automatic error reports. It is not a promise, it is the list of what the app contains: the code to do those things is not there.",
                        "Sonst nichts: kein Konto, keine Gerätekennung, keine Nutzungsstatistik, keine automatischen Fehlerberichte. Das ist kein Versprechen, sondern die Liste dessen, was die App enthält: Den Code für diese Dinge gibt es nicht.",
                        "Rien d'autre : pas de compte, pas d'identifiant du téléphone, pas de statistiques d'usage, pas d'envoi automatique des erreurs. Ce n'est pas une promesse, c'est la liste de ce que contient l'application : le code pour faire ces choses n'existe pas.",
                        "Nada más: ni cuenta, ni identificador del teléfono, ni estadísticas de uso, ni envío automático de errores. No es una promesa, es la lista de lo que contiene la aplicación: el código para hacer esas cosas no está.",
                    ),
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = tr("COSA RESTA SUL TELEFONO", "WHAT STAYS ON THE PHONE", "WAS AUF DEM TELEFON BLEIBT", "CE QUI RESTE SUR LE TÉLÉPHONE", "QUÉ SE QUEDA EN EL TELÉFONO"), palette = palette) {
                Paragrafo(
                    tr(
                        "La località scelta con le sue coordinate, le località salvate, le unità " +
                            "di misura, il tema e gli interruttori di questa schermata. Stanno " +
                            "nell'archivio delle impostazioni dell'applicazione e non escono mai.",
                        "The chosen place with its coordinates, the saved places, the units, the theme and the switches on this screen. They live in the app's settings storage and never leave it.",
                        "Der gewählte Ort mit seinen Koordinaten, die gespeicherten Orte, die Einheiten, das Design und die Schalter dieses Bildschirms. Sie liegen im Einstellungsspeicher der App und verlassen ihn nie.",
                        "Le lieu choisi avec ses coordonnées, les lieux enregistrés, les unités, le thème et les interrupteurs de cet écran. Ils se trouvent dans le stockage des réglages de l'application et n'en sortent jamais.",
                        "El lugar elegido con sus coordenadas, los lugares guardados, las unidades, el tema y los interruptores de esta pantalla. Están en el almacenamiento de ajustes de la aplicación y nunca salen de él.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Disinstallando l'applicazione se ne vanno con lei.",
                        "Uninstalling the app removes them along with it.",
                        "Mit der Deinstallation der App verschwinden sie mit ihr.",
                        "En désinstallant l'application, ils disparaissent avec elle.",
                        "Al desinstalar la aplicación se van con ella.",
                    ),
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = tr("I PERMESSI, E A COSA SERVONO", "PERMISSIONS, AND WHAT THEY ARE FOR", "BERECHTIGUNGEN, UND WOZU SIE DIENEN", "LES AUTORISATIONS, ET À QUOI ELLES SERVENT", "LOS PERMISOS, Y PARA QUÉ SIRVEN"), palette = palette) {
                Paragrafo(
                    tr(
                        "Internet, per chiedere la previsione.",
                        "Internet, to ask for the forecast.",
                        "Internet, um die Vorhersage abzufragen.",
                        "Internet, pour demander la prévision.",
                        "Internet, para pedir la previsión.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Vibrazione, per il colpetto che segue la pioggia e gli scatti della " +
                            "barra delle ore. Si spegne da \"Animazioni ridotte\".",
                        "Vibration, for the tap that follows the rain and the clicks of the hour bar. It is turned off from \"Reduced motion\".",
                        "Vibration, für den leichten Impuls, der dem Regen folgt, und für die Rasten der Stundenleiste. Sie lässt sich über \"Weniger Bewegung\" abschalten.",
                        "Vibration, pour la petite impulsion qui accompagne la pluie et les crans de la barre des heures. Elle se coupe avec \"Animations réduites\".",
                        "Vibración, para el toque que acompaña a la lluvia y los pasos de la barra de horas. Se apaga con \"Animaciones reducidas\".",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Notifiche, per la pioggia o la grandine in arrivo, per le allerte " +
                            "ufficiali arancioni e rosse, per il riepilogo del mattino e per gli " +
                            "avvisi personali, sempre sulla località dell'app. Si spengono una " +
                            "per una dalle impostazioni.",
                        "Notifications, for rain or hail on the way, for orange and red official warnings, for the morning summary and for personal notices, always for the app's place. They are turned off one by one in settings.",
                        "Benachrichtigungen, für Regen oder Hagel im Anmarsch, für orange und rote amtliche Warnungen, für den Morgenüberblick und für persönliche Hinweise, immer für den Ort der App. Sie lassen sich einzeln in den Einstellungen abschalten.",
                        "Notifications, pour la pluie ou la grêle en approche, pour les alertes officielles orange et rouges, pour le résumé du matin et pour les avis personnels, toujours pour le lieu de l'app. Elles se désactivent une à une dans les réglages.",
                        "Notificaciones, para la lluvia o el granizo en camino, para las alertas oficiales naranjas y rojas, para el resumen de la mañana y para los avisos personales, siempre para el lugar de la app. Se apagan una a una en los ajustes.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Posizione approssimativa — approssimativa, non precisa: serve a capire " +
                            "in quale città sei, non dove sei dentro casa. Si usa solo quando " +
                            "chiedi tu di seguire la posizione, e la località resta quella scelta " +
                            "a mano finché non lo chiedi.",
                        "Approximate location — approximate, not precise: it tells which town you are in, not where you are in the house. It is used only when you ask to follow your location, and the place stays the one chosen by hand until you do.",
                        "Ungefährer Standort — ungefähr, nicht genau: Er sagt, in welcher Stadt du bist, nicht wo im Haus. Er wird nur verwendet, wenn du darum bittest, deinem Standort zu folgen, und der Ort bleibt der von Hand gewählte, bis du es tust.",
                        "Position approximative — approximative, pas précise : elle indique dans quelle ville vous êtes, pas où vous êtes dans la maison. Elle n'est utilisée que si vous demandez à suivre votre position, et le lieu reste celui choisi à la main tant que vous ne le demandez pas.",
                        "Ubicación aproximada — aproximada, no precisa: sirve para saber en qué ciudad estás, no en qué parte de la casa. Solo se usa cuando tú pides seguir tu ubicación, y el lugar sigue siendo el elegido a mano hasta que lo pidas.",
                    ),
                    palette,
                )
            }

            // Le fonti, con le parole che chiedono (vedi `Fonti`). Tre licenze,
            // tre paragrafi: chi fornisce cosa, sotto quale licenza, e cosa
            // l'app cambia - la CC BY chiede anche di dirlo.
            BloccoImpostazioni(etichetta = tr("ATTRIBUZIONE", "ATTRIBUTION", "QUELLENANGABEN", "ATTRIBUTION", "ATRIBUCIÓN"), palette = palette) {
                Paragrafo(
                    tr(
                        "Previsioni: Open-Meteo.com, che usa i dati aperti dei servizi meteorologici " +
                            "nazionali (fra cui DWD, ECMWF e, per l'Italia, ItaliaMeteo-ARPAE). " +
                            "I dati dell'API sono offerti sotto licenza Creative Commons " +
                            "Attribution 4.0 International (CC BY 4.0).",
                        "Forecasts: Open-Meteo.com, which uses open data from national weather services (including DWD, ECMWF and, for Italy, ItaliaMeteo-ARPAE). The API data are offered under the Creative Commons Attribution 4.0 International licence (CC BY 4.0).",
                        "Vorhersagen: Open-Meteo.com, das offene Daten nationaler Wetterdienste nutzt (darunter DWD, ECMWF und, für Italien, ItaliaMeteo-ARPAE). Die API-Daten werden unter der Lizenz Creative Commons Attribution 4.0 International (CC BY 4.0) angeboten.",
                        "Prévisions : Open-Meteo.com, qui utilise les données ouvertes des services météorologiques nationaux (dont DWD, ECMWF et, pour l'Italie, ItaliaMeteo-ARPAE). Les données de l'API sont proposées sous licence Creative Commons Attribution 4.0 International (CC BY 4.0).",
                        "Previsiones: Open-Meteo.com, que usa los datos abiertos de los servicios meteorológicos nacionales (entre ellos DWD, ECMWF y, para Italia, ItaliaMeteo-ARPAE). Los datos de la API se ofrecen bajo la licencia Creative Commons Attribution 4.0 International (CC BY 4.0).",
                    ),
                    palette,
                )
                Collegamento("Weather data by Open-Meteo.com", Fonti.OPEN_METEO, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))
                Collegamento(tr("Licenza e fonti di Open-Meteo", "Open-Meteo licence and sources", "Lizenz und Quellen von Open-Meteo", "Licence et sources d'Open-Meteo", "Licencia y fuentes de Open-Meteo"), Fonti.OPEN_METEO_LICENZA, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))
                Paragrafo(Fonti.CITAZIONE_OPEN_METEO, palette)

                Paragrafo(
                    tr(
                        "Qualità dell'aria e polline: CAMS European air quality forecasts, ENSEMBLE " +
                            "data, del Copernicus Atmosphere Monitoring Service, attraverso " +
                            "Open-Meteo.com. La citazione che chiedono:",
                        "Air quality and pollen: CAMS European air quality forecasts, ENSEMBLE data, from the Copernicus Atmosphere Monitoring Service, through Open-Meteo.com. The citation they ask for:",
                        "Luftqualität und Pollen: CAMS European air quality forecasts, ENSEMBLE data, des Copernicus Atmosphere Monitoring Service, über Open-Meteo.com. Die Zitierweise, um die sie bitten:",
                        "Qualité de l'air et pollen : CAMS European air quality forecasts, ENSEMBLE data, du Copernicus Atmosphere Monitoring Service, via Open-Meteo.com. La citation demandée :",
                        "Calidad del aire y polen: CAMS European air quality forecasts, ENSEMBLE data, del Copernicus Atmosphere Monitoring Service, a través de Open-Meteo.com. La cita que piden:",
                    ),
                    palette,
                )
                Paragrafo(Fonti.CITAZIONE_CAMS, palette)
                Collegamento(tr("I dati CAMS su Open-Meteo", "CAMS data on Open-Meteo", "Die CAMS-Daten bei Open-Meteo", "Les données CAMS sur Open-Meteo", "Los datos CAMS en Open-Meteo"), Fonti.CAMS_ARIA, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))

                Paragrafo(
                    tr(
                        "Onde e temperatura del mare: Open-Meteo.com Marine Weather API, sotto la " +
                            "stessa licenza CC BY 4.0, che usa i modelli d'onda dei servizi " +
                            "meteorologici (l'elenco è nella sua pagina). Sono stime per il mare " +
                            "aperto, poco precise sotto costa: non servono alla navigazione e non " +
                            "sostituiscono bollettini del mare, portolani o le bandiere in spiaggia.",
                        "Waves and sea temperature: Open-Meteo.com Marine Weather API, under the same CC BY 4.0 licence, which uses the wave models of the weather services (the list is on its page). They are estimates for open water, with limited accuracy near the coast: they are not suitable for navigation and do not replace marine bulletins, nautical almanacs or beach flags.",
                        "Wellen und Meerestemperatur: Open-Meteo.com Marine Weather API, unter derselben Lizenz CC BY 4.0, die die Wellenmodelle der Wetterdienste nutzt (die Liste steht auf ihrer Seite). Es sind Schätzungen für die offene See, in Küstennähe wenig genau: Sie eignen sich nicht für die Navigation und ersetzen weder Seewetterberichte noch nautische Handbücher noch die Flaggen am Strand.",
                        "Vagues et température de la mer : Open-Meteo.com Marine Weather API, sous la même licence CC BY 4.0, qui utilise les modèles de vagues des services météorologiques (la liste figure sur sa page). Ce sont des estimations pour le large, peu précises près des côtes : elles ne conviennent pas à la navigation et ne remplacent ni les bulletins marins, ni les ouvrages nautiques, ni les drapeaux de plage.",
                        "Olas y temperatura del mar: Open-Meteo.com Marine Weather API, bajo la misma licencia CC BY 4.0, que usa los modelos de oleaje de los servicios meteorológicos (la lista está en su página). Son estimaciones para mar abierto, poco precisas cerca de la costa: no sirven para la navegación y no sustituyen a los boletines marítimos, los derroteros ni las banderas de la playa.",
                    ),
                    palette,
                )
                Paragrafo(Fonti.CITAZIONE_MARE, palette)
                Collegamento(tr("I dati del mare su Open-Meteo", "Marine data on Open-Meteo", "Die Meeresdaten bei Open-Meteo", "Les données marines sur Open-Meteo", "Los datos del mar en Open-Meteo"), Fonti.MARE, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))

                Paragrafo(
                    tr(
                        "Allerte ufficiali: MeteoAlarm, con i dati forniti dai membri di EUMETNET, " +
                            "sotto licenza CC BY 4.0.",
                        "Official warnings: MeteoAlarm, with data provided by EUMETNET members, under the CC BY 4.0 licence.",
                        "Amtliche Warnungen: MeteoAlarm, mit Daten der EUMETNET-Mitglieder, unter der Lizenz CC BY 4.0.",
                        "Alertes officielles : MeteoAlarm, avec les données fournies par les membres d'EUMETNET, sous licence CC BY 4.0.",
                        "Alertas oficiales: MeteoAlarm, con los datos facilitados por los miembros de EUMETNET, bajo licencia CC BY 4.0.",
                    ),
                    palette,
                )
                Collegamento("meteoalarm.org", Fonti.METEOALARM, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))

                FonteAllerte.entries.filter { it != FonteAllerte.METEOALARM }.forEach { fonte ->
                    Paragrafo(fonte.credito, palette)
                    Collegamento(fonte.sito, fonte.indirizzo, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))
                }

                Paragrafo(
                    tr(
                        "Cosa cambia l'app: converte unità e ore nel fuso del posto, ricava da questi " +
                            "dati il cielo disegnato, i testi delle schermate e gli avvisi calcolati sulle " +
                            "soglie, e compone in italiano il titolo di ogni allerta; descrizione e " +
                            "istruzioni restano quelle pubblicate dall'ente. Nessuna delle fonti " +
                            "approva o sostiene quest'app.",
                        "What the app changes: it converts units and times to the local time zone, derives from these data the drawn sky, the screen texts and the threshold notices, and writes the title of each warning in English; description and instructions remain those published by the authority. None of the sources endorses or supports this app.",
                        "Was die App verändert: Sie rechnet Einheiten und Uhrzeiten in die Ortszeit um, leitet aus diesen Daten den gezeichneten Himmel, die Texte der Bildschirme und die Schwellen-Hinweise ab und formuliert den Titel jeder Warnung auf Deutsch; Beschreibung und Anweisungen bleiben die von der Behörde veröffentlichten. Keine der Quellen billigt oder unterstützt diese App.",
                        "Ce que l'app modifie : elle convertit les unités et les heures dans le fuseau du lieu, tire de ces données le ciel dessiné, les textes des écrans et les avis calculés sur les seuils, et rédige en français le titre de chaque alerte ; la description et les consignes restent celles publiées par l'organisme. Aucune des sources n'approuve ni ne soutient cette app.",
                        "Qué cambia la app: convierte unidades y horas a la zona horaria del lugar, obtiene de estos datos el cielo dibujado, los textos de las pantallas y los avisos calculados sobre los umbrales, y redacta en español el título de cada alerta; la descripción y las instrucciones siguen siendo las publicadas por el organismo. Ninguna de las fuentes aprueba ni respalda esta app.",
                    ),
                    palette,
                )
                Collegamento(tr("Il testo della licenza CC BY 4.0", "The text of the CC BY 4.0 licence", "Der Text der Lizenz CC BY 4.0", "Le texte de la licence CC BY 4.0", "El texto de la licencia CC BY 4.0"), Fonti.CC_BY_4, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

/** Un capoverso del testo legale: corpo leggibile, non una riga di elenco. */
@Composable
private fun Paragrafo(testo: String, palette: SalaPalette) {
    Text(
        text = testo,
        style = SalaType.body,
        color = palette.inkSoft,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
