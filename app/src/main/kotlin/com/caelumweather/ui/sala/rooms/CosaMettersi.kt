package com.caelumweather.ui.sala.rooms

import com.caelumweather.data.HourForecast
import com.caelumweather.data.Wmo
import com.caelumweather.lingua.tr
import com.caelumweather.notifiche.oraDaOmbrello

/**
 * **Cosa mettersi**, in una riga: "Giacca, a strati · ombrello · occhiali".
 *
 * Non si guarda l'ora sola ma **la giornata fuori casa**: dall'ora mostrata per
 * le dieci ore dopo, senza passare le 23. Chi esce alle otto con 6 gradi e
 * rientra alle sei con 15 vuole sapere che serve la giacca *e* che a mezzogiorno
 * la togliera'.
 *
 * Lo strato si sceglie dalla **temperatura percepita piu' bassa** della
 * finestra: e' quella per cui ci si veste uscendo. Il resto si aggiunge solo se
 * c'e' davvero, nello stesso spirito della notifica dell'ombrello:
 * - ombrello: un'ora con almeno 0,3 mm e il 40% (neve: scarpe che non bagnano);
 * - "a strati": fra la percepita minima e la massima corrono 8 gradi o piu';
 * - vento: raffiche da 40 km/h, che fanno sembrare la giacca piu' leggera;
 * - occhiali e crema: UV da 6 in su ("alto" per l'OMS).
 *
 * Torna nullo senza ore o senza temperature: una riga inventata e' peggio di
 * nessuna riga.
 */
internal fun cosaMettersi(ore: List<HourForecast>, da: java.time.LocalDateTime): String? {
    val fine = minOf(da.plusHours(10), da.toLocalDate().atTime(23, 0))
    val finestra = ore.filter { !it.time.isBefore(da) && !it.time.isAfter(fine) }
    val percepite = finestra.mapNotNull { it.apparent ?: it.temperature }
    if (percepite.isEmpty()) return null
    val minima = percepite.min()
    val massima = percepite.max()

    val strato = when {
        minima < 0 -> tr("Cappotto pesante, guanti e berretto", "Heavy coat, gloves and hat", "Dicker Mantel, Handschuhe und Mütze", "Manteau chaud, gants et bonnet", "Abrigo grueso, guantes y gorro")
        minima < 7 -> tr("Cappotto", "Coat", "Mantel", "Manteau", "Abrigo")
        minima < 13 -> tr("Giacca", "Jacket", "Jacke", "Veste", "Chaqueta")
        minima < 18 -> tr("Felpa o giacca leggera", "Sweater or light jacket", "Pulli oder leichte Jacke", "Pull ou veste légère", "Sudadera o chaqueta ligera")
        minima < 24 -> tr("Maglietta e qualcosa sopra", "T-shirt and a light layer", "T-Shirt und etwas darüber", "T-shirt et une petite couche", "Camiseta y algo encima")
        else -> tr("Abiti leggeri", "Light clothes", "Leichte Kleidung", "Vêtements légers", "Ropa ligera")
    }
    val pezzi = mutableListOf<String>()
    pezzi += if (massima - minima >= 8 && minima < 24) {
        strato + tr(", a strati", ", in layers", ", im Zwiebellook", ", en couches", ", por capas")
    } else strato

    val bagnate = finestra.filter(::oraDaOmbrello)
    if (bagnate.isNotEmpty()) {
        val neve = bagnate.count { o -> o.weatherCode?.let { Wmo.family(it) == Wmo.Family.NEVE } == true } * 2 > bagnate.size
        pezzi += if (neve) {
            tr("scarpe impermeabili", "waterproof shoes", "wasserdichte Schuhe", "chaussures imperméables", "calzado impermeable")
        } else {
            tr("ombrello", "umbrella", "Schirm", "parapluie", "paraguas")
        }
    }
    if (finestra.any { (it.windGusts ?: it.windSpeed ?: 0.0) >= 40.0 }) {
        pezzi += tr("qualcosa contro il vento", "something windproof", "etwas Winddichtes", "de quoi couper le vent", "algo cortavientos")
    }
    if (finestra.any { (it.uvIndex ?: 0.0) >= 6.0 }) {
        pezzi += tr("occhiali e crema", "sunglasses and sunscreen", "Sonnenbrille und Sonnencreme", "lunettes et crème", "gafas y crema")
    }
    return pezzi.joinToString(" · ")
}
