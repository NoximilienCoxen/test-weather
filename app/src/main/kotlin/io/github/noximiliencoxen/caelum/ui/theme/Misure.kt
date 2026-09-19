package io.github.noximiliencoxen.caelum.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le misure che valgono per tutta l'app, e che non sono colori.
 *
 * **E' quel che resta di `ui/common/`.** Quel pacchetto teneva settecentosessanta
 * righe - la cornice del feed: barra in cima, schede, pastiglie, divisori,
 * glifi del tempo, le misure dello schermo - e quando il feed e' stato
 * sostituito da Sala nessuno lo ha piu' chiamato. Di vivo, in tutte e
 * settecentosessanta, era rimasta **questa riga sola**, e per lei si portava
 * dietro tre file, un pacchetto e un arco da `ui.common` a `widget.paint`.
 *
 * Sta in `ui/theme/` e non in un `ui/common/` da tre righe perche' un pacchetto
 * che contiene un pacchetto vuoto e' peggio di nessun pacchetto: qui accanto ci
 * sono gia' la tavolozza e i corpi del testo, cioe' le altre cose che ogni
 * schermata chiede prima di disegnare.
 */

/**
 * L'area minima toccabile che Material pretende, e che qui mancava ovunque.
 *
 * Quarantotto punti e' misurato sul polpastrello, non sull'icona: un comando
 * piu' piccolo si sbaglia col pollice, e in questa app se n'e' gia' pagata una
 * (sezione 15.3, la colonna delle scorciatoie sul bordo destro).
 */
val MinTouchTarget: Dp = 48.dp
