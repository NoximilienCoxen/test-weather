package io.github.noximiliencoxen.caelum.ui.render3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/**
 * Riempire un solido fatto di facce proiettate, senza buchi.
 *
 * **E' una lezione pagata con un difetto spedito.** La colonna d'acqua della
 * vasca graduata era un `Path` con sei quadrilateri separati, riempito con una
 * `drawPath` sola. Sullo schermo era vuota: tre facce risultano avvolte in un
 * verso e tre nell'altro - le lontane stanno **piu' in alto** per via
 * dell'inclinazione e sono **piu' strette** per via della prospettiva, quindi
 * coprono la stessa fascia centrale delle vicine con segno opposto - e la regola
 * di riempimento *non-zero* le annulla. Misurato campionando il numero di
 * avvolgimento: **il cinquantotto per cento della colonna era un buco**, e cio'
 * che restava erano due fettine, una in cima che si confondeva con la superficie
 * e una in fondo che si confondeva col fondo.
 *
 * Le due correzioni che vengono in mente per prime **non funzionano**, e vanno
 * lasciate scritte perche' nessuno le riprovi:
 *
 * - `PathFillType.EvenOdd` lascia lo stesso identico buco. Due sotto-percorsi
 *   sovrapposti danno parita' pari, cioe' di nuovo "fuori".
 * - Una `drawPath` per faccia non va bene quando il riempimento e' traslucido:
 *   a opacita' 0,42 le sovrapposizioni si compongono a 0,66, e il buco diventa
 *   tre bande scure che **si muovono girando**. Un difetto peggiore, e piu'
 *   lento - sei disegni invece di uno.
 *
 * Qui stanno le due strade che funzionano. Il file vive in `render3d` e non
 * accanto a chi l'ha scoperto, perche' **qualunque solido fatto di quadrilateri
 * proiettati incontra la stessa cosa**: un vano di finestra, una pozza, una
 * cassa. E' la stessa ragione per cui l'acqua che cade e' uscita dalla scultura.
 */

/**
 * Aggiunge un quadrilatero al percorso **con l'avvolgimento normalizzato**.
 *
 * Se ne calcola l'area con segno e, quando e' negativa, si percorrono i vertici
 * al contrario. Cosi' tutte le facce girano nello stesso verso, il *non-zero*
 * riempie l'unione, e una `drawPath` sola la copre con un'opacita' uniforme.
 *
 * Costa un prodotto vettoriale per faccia: e' la correzione col diff piu'
 * piccolo, ed e' quella da usare quando le facce le si hanno gia' in mano.
 */
fun Path.addFacet(a: Offset, b: Offset, c: Offset, d: Offset) {
    moveTo(a.x, a.y)
    if (facetTwiceArea(a, b, c, d) >= 0f) {
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        lineTo(d.x, d.y)
    } else {
        lineTo(d.x, d.y)
        lineTo(c.x, c.y)
        lineTo(b.x, b.y)
    }
    close()
}

/**
 * Il doppio dell'area con segno di un quadrilatero, che e' il suo verso.
 *
 * Sta fuori da [addFacet] per una ragione sola: `Path` e' la grafica di Android
 * e vuole un emulatore, questa e' aritmetica e si prova con JUnit. La stessa
 * separazione che il progetto fa gia' fra cosa si disegna e cosa si calcola.
 *
 * Formula di Gauss su quattro punti; della grandezza non se ne fa niente
 * nessuno, serve il segno.
 */
internal fun facetTwiceArea(a: Offset, b: Offset, c: Offset, d: Offset): Float =
    (b.x - a.x) * (b.y + a.y) +
        (c.x - b.x) * (c.y + b.y) +
        (d.x - c.x) * (d.y + c.y) +
        (a.x - d.x) * (a.y + d.y)

/**
 * La sagoma di un solido **convesso**, come inviluppo dei suoi punti proiettati.
 *
 * Un prisma convesso proiettato ha per sagoma esattamente l'inviluppo convesso
 * dei suoi vertici sullo schermo: un percorso solo, un contorno solo, nessuna
 * sovrapposizione da riconciliare. E' la descrizione piu' onesta di cio' che si
 * sta disegnando, ed e' immune a qualunque svista futura nell'ordine delle
 * facce - mentre [addFacet] si fida che le facce siano quelle giuste.
 *
 * Catena monotona di Andrew: si ordina, si costruisce lo scafo di sotto e quello
 * di sopra. Su una dozzina di punti e' niente.
 *
 * **Solo per solidi convessi.** Su una forma concava taglia gli incavi senza
 * dire niente, ed e' il modo peggiore di sbagliare - va bene per prismi, casse e
 * vani, non per una sagoma qualunque.
 */
// `convexHullPath` stava qui e non la chiamava nessuno, nemmeno il suo test:
// `FacetsTest` prova `convexHull`, che e' quella che fa il lavoro. Chi
// volesse di nuovo un Path invece di una lista di punti lo costruisce da
// `convexHull` in cinque righe, sapendo per cosa gli serve.

/**
 * L'inviluppo come **elenco di punti**, che e' la parte che si puo' provare.
 *
 * Come [facetTwiceArea]: la geometria sta di qua e si prova con JUnit, il `Path`
 * sta di la' e vuole un emulatore.
 */
internal fun convexHull(points: List<Offset>): List<Offset> {
    if (points.size < 3) return emptyList()

    val sorted = points.sortedWith(compareBy({ it.x }, { it.y }))
    val hull = ArrayList<Offset>(sorted.size * 2)

    // Girato a destra o allineato: il punto di mezzo non sta sull'inviluppo.
    fun turnsRight(o: Offset, a: Offset, b: Offset): Boolean =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x) <= 0f

    for (p in sorted) {
        while (hull.size >= 2 && turnsRight(hull[hull.size - 2], hull[hull.size - 1], p)) {
            hull.removeAt(hull.size - 1)
        }
        hull.add(p)
    }
    val lower = hull.size + 1
    for (i in sorted.indices.reversed()) {
        val p = sorted[i]
        while (hull.size >= lower && turnsRight(hull[hull.size - 2], hull[hull.size - 1], p)) {
            hull.removeAt(hull.size - 1)
        }
        hull.add(p)
    }
    // L'ultimo e' il primo ripetuto: a chiuderlo pensa `close()`.
    hull.removeAt(hull.size - 1)
    return if (hull.size < 3) emptyList() else hull
}
