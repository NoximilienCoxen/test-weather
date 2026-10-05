package io.github.noximiliencoxen.caelum.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.Normalizer

/**
 * Le allerte ufficiali del Giappone, dall'Agenzia meteorologica (気象庁, JMA).
 *
 * ## Com'e' fatta davvero la fonte
 *
 * Letta da risposte vere della sonda `probe_allerte_mondo.py` del 5 ottobre
 * 2026, non dalla documentazione:
 *
 * - **Il JSON del sito JMA non serve**: quello degli avvisi di Tokyo
 *   (`bosai/warning/data/warning/130000.json`) era fermo al 28 maggio 2026.
 * - Vivo e' il servizio XML ufficiale. Il **feed lungo**
 *   (`developer/xml/feed/extra_l.xml`, circa 2 MB) copre sette giorni,
 *   le voci piu' recenti in cima, e conteneva 1362 bollettini "気象特別警報・
 *   警報・注意報" (codice `VPWW53`) per tutte le 58 aree. Il codice d'area sta
 *   nel nome del file: `..._VPWW53_340000.xml` e' Hiroshima.
 * - Il bollettino elenca gli avvisi a piu' livelli (prefettura, sotto-aree,
 *   comuni). Si legge quello di **prefettura** (`府県予報区等`): e' la grana di
 *   MeteoAlarm per l'Italia, e l'unica a cui si arriva da una regione.
 * - Ogni avviso ha un nome giapponese che dice anche il grado: 特別警報
 *   (emergenza) rossa, 警報 (avviso) arancione, 注意報 (avvertenza) gialla.
 *   `Status` dice se e' 発表 (emesso), 継続 (in corso), 解除 (revocato) o
 *   altro: tutto cio' che non e' revoca o assenza vale.
 * - **Niente orari**: un avviso JMA vale finche' non lo si revoca. Il
 *   bollettino dell'app lo dice ("la fonte non dice fino a quando vale").
 *
 * Il feed lungo e' pesante, ma e' l'unico che porta l'ultimo bollettino di
 * un'area anche se non e' cambiato negli ultimi dieci minuti; si chiede solo
 * per posti in Giappone.
 */
internal object JmaAlerts {

    const val FEED = "https://www.data.jma.go.jp/developer/xml/feed/extra_l.xml"

    fun load(place: Place): List<WeatherAlert> {
        val area = areaDi(place)
        val feed = httpGet(FEED, fonte = "JMA", accept = "application/atom+xml, application/xml;q=0.9, */*;q=0.8")
        val url = ultimoBollettino(feed, area.codice) ?: return emptyList()
        return parse(httpGet(url, fonte = "JMA", accept = "application/xml, */*;q=0.8"), area)
    }

    /** L'indirizzo del bollettino piu' recente dell'area: il feed ha in cima le voci nuove. */
    fun ultimoBollettino(feed: String, codice: String): String? =
        Regex("""href="([^"]+_VPWW53_$codice\.xml)"""").find(feed)?.groupValues?.get(1)

    /**
     * Gli avvisi di prefettura di un bollettino `VPWW53`.
     *
     * Si percorre solo il `Body`: nella `Head` gli stessi nomi tornano senza
     * `Status`, e leggerli due volte darebbe avvisi doppi o revocati.
     */
    fun parse(xml: String, area: AreaJma): List<WeatherAlert> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))
        var inBody = false
        var inPrefettura = false
        var inKind = false
        var nome: String? = null
        var stato: String? = null
        var titolo: String? = null
        var nomeArea: String? = null
        var ufficio: String? = null
        var inHeadline = false
        val avvisi = mutableListOf<Pair<String, String?>>()
        var evento = parser.eventType
        while (evento != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(':').orEmpty()
            when (evento) {
                XmlPullParser.START_TAG -> when {
                    tag == "Body" -> inBody = true
                    tag == "Headline" -> inHeadline = true
                    tag == "Text" && inHeadline && titolo == null -> titolo = parser.nextText().trim()
                    tag == "PublishingOffice" && ufficio == null -> ufficio = parser.nextText().trim()
                    tag == "Warning" && inBody ->
                        inPrefettura = parser.getAttributeValue(null, "type")?.contains("府県予報区") == true
                    tag == "Kind" && inPrefettura -> { inKind = true; nome = null; stato = null }
                    tag == "Name" && inKind -> nome = parser.nextText().trim()
                    tag == "Status" && inKind -> stato = parser.nextText().trim()
                    tag == "Name" && inPrefettura && !inKind && nomeArea == null -> nomeArea = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> when (tag) {
                    "Headline" -> inHeadline = false
                    "Warning" -> inPrefettura = false
                    "Kind" -> if (inKind) {
                        nome?.let { avvisi += it to stato }
                        inKind = false
                    }
                }
            }
            evento = parser.next()
        }
        return avvisi
            .filter { (_, s) -> s != null && s != "解除" && !s.contains("なし") }
            .map { it.first }
            .distinct()
            .map { avviso(it, titolo, nomeArea, ufficio, area) }
            .sortedByDescending { it.level.weight }
    }

    private fun avviso(nome: String, titolo: String?, nomeArea: String?, ufficio: String?, area: AreaJma): WeatherAlert {
        val livello = livelloDi(nome)
        val tipo = tipoDi(nome)
        return WeatherAlert(
            id = "jma|${area.codice}|$nome",
            level = livello,
            kind = tipo,
            headline = "${livello.label}: ${tipo.label.lowercase()}",
            // Il nome giapponese in testa: e' cio' che si ritrova sui canali
            // della JMA. Il testo e' quello dell'ente, in giapponese.
            description = listOfNotNull(nome, titolo?.takeIf { it.isNotBlank() }).joinToString("\n\n"),
            areaDesc = listOfNotNull(area.nome, nomeArea).joinToString(" · "),
            source = ufficio?.takeIf { it.isNotBlank() }?.let { "Japan Meteorological Agency - $it" }
                ?: "Japan Meteorological Agency",
            official = true,
            fonte = FonteAllerte.JMA,
        )
    }

    /** Il grado sta in fondo al nome: 特別警報, 警報, 注意報. */
    fun livelloDi(nome: String): AlertLevel = when {
        nome.endsWith("特別警報") -> AlertLevel.ROSSA
        nome.endsWith("警報") -> AlertLevel.ARANCIONE
        else -> AlertLevel.GIALLA
    }

    /** Il fenomeno, dai caratteri del nome. L'ordine conta: 暴風雪 e' neve prima che vento. */
    fun tipoDi(nome: String): AlertKind = when {
        nome.contains("雷") -> AlertKind.TEMPORALI
        nome.contains("暴風雪") || nome.contains("風雪") || nome.contains("大雪") ||
            nome.contains("着氷") || nome.contains("着雪") -> AlertKind.NEVE_GHIACCIO
        nome.contains("暴風") || nome.contains("強風") -> AlertKind.VENTO
        nome.contains("大雨") || nome.contains("洪水") || nome.contains("融雪") -> AlertKind.PIOGGIA
        nome.contains("波浪") || nome.contains("高潮") -> AlertKind.COSTIERO
        nome.contains("濃霧") -> AlertKind.NEBBIA
        nome.contains("乾燥") -> AlertKind.INCENDI
        nome.contains("なだれ") -> AlertKind.VALANGHE
        nome.contains("低温") || nome.contains("霜") -> AlertKind.FREDDO
        nome.contains("高温") -> AlertKind.CALDO
        else -> AlertKind.ALTRO
    }

    /**
     * L'area JMA del posto.
     *
     * Prima la regione, come la scrive la geocodifica ("Tokyo", "prefettura di
     * Osaka", "Hokkaido"): se nomina una prefettura con un'area sola, e' quella.
     * Hokkaido, Okinawa e Kagoshima ne hanno piu' d'una, e senza la regione non
     * si sa niente: li' decide la sede piu' vicina. E' una scelta di grana
     * grossa vicino ai confini, come il confronto di nomi per MeteoAlarm.
     */
    fun areaDi(place: Place): AreaJma {
        val regione = normalizza(place.admin)
        val candidate = AREE.filter { regione != null && (it.prefettura == regione || normalizza(it.nome) == regione) }
        return (candidate.ifEmpty { AREE }).minBy { it.distanza2(place.latitude, place.longitude) }
    }

    private fun normalizza(testo: String?): String? {
        val t = testo ?: return null
        val senzaSegni = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return senzaSegni.lowercase()
            .replace("prefettura di ", "").replace("prefecture", "")
            .replace(Regex("-(ken|fu|to)$"), "")
            .trim().ifEmpty { null }
    }

    /** Le 58 aree, con la sede dell'osservatorio come punto di riferimento. */
    private val AREE = listOf(
        AreaJma("011000", "Soya", 45.415, 141.673, "hokkaido"),
        AreaJma("012000", "Kamikawa Rumoi", 43.771, 142.365, "hokkaido"),
        AreaJma("013000", "Abashiri Kitami Mombetsu", 44.021, 144.273, "hokkaido"),
        AreaJma("014030", "Tokachi", 42.924, 143.196, "hokkaido"),
        AreaJma("014100", "Kushiro Nemuro", 42.985, 144.381, "hokkaido"),
        AreaJma("015000", "Iburi Hidaka", 42.315, 140.974, "hokkaido"),
        AreaJma("016000", "Ishikari Sorachi Shiribeshi", 43.062, 141.354, "hokkaido"),
        AreaJma("017000", "Oshima Hiyama", 41.769, 140.729, "hokkaido"),
        AreaJma("020000", "Aomori", 40.824, 140.740),
        AreaJma("030000", "Iwate", 39.702, 141.154),
        AreaJma("040000", "Miyagi", 38.269, 140.872),
        AreaJma("050000", "Akita", 39.720, 140.103),
        AreaJma("060000", "Yamagata", 38.240, 140.364),
        AreaJma("070000", "Fukushima", 37.750, 140.468),
        AreaJma("080000", "Ibaraki", 36.366, 140.471),
        AreaJma("090000", "Tochigi", 36.566, 139.884),
        AreaJma("100000", "Gunma", 36.391, 139.061),
        AreaJma("110000", "Saitama", 35.857, 139.649),
        AreaJma("120000", "Chiba", 35.605, 140.123),
        AreaJma("130000", "Tokyo", 35.690, 139.692),
        AreaJma("140000", "Kanagawa", 35.448, 139.642),
        AreaJma("150000", "Niigata", 37.902, 139.023),
        AreaJma("160000", "Toyama", 36.695, 137.211),
        AreaJma("170000", "Ishikawa", 36.594, 136.626),
        AreaJma("180000", "Fukui", 36.065, 136.222),
        AreaJma("190000", "Yamanashi", 35.664, 138.568),
        AreaJma("200000", "Nagano", 36.651, 138.181),
        AreaJma("210000", "Gifu", 35.391, 136.722),
        AreaJma("220000", "Shizuoka", 34.977, 138.383),
        AreaJma("230000", "Aichi", 35.180, 136.907),
        AreaJma("240000", "Mie", 34.730, 136.509),
        AreaJma("250000", "Shiga", 35.005, 135.869),
        AreaJma("260000", "Kyoto", 35.021, 135.756),
        AreaJma("270000", "Osaka", 34.686, 135.520),
        AreaJma("280000", "Hyogo", 34.691, 135.183),
        AreaJma("290000", "Nara", 34.685, 135.833),
        AreaJma("300000", "Wakayama", 34.226, 135.168),
        AreaJma("310000", "Tottori", 35.504, 134.238),
        AreaJma("320000", "Shimane", 35.472, 133.051),
        AreaJma("330000", "Okayama", 34.662, 133.935),
        AreaJma("340000", "Hiroshima", 34.396, 132.459),
        AreaJma("350000", "Yamaguchi", 34.186, 131.471),
        AreaJma("360000", "Tokushima", 34.066, 134.559),
        AreaJma("370000", "Kagawa", 34.340, 134.043),
        AreaJma("380000", "Ehime", 33.842, 132.766),
        AreaJma("390000", "Kochi", 33.560, 133.531),
        AreaJma("400000", "Fukuoka", 33.607, 130.418),
        AreaJma("410000", "Saga", 33.249, 130.299),
        AreaJma("420000", "Nagasaki", 32.745, 129.874),
        AreaJma("430000", "Kumamoto", 32.790, 130.742),
        AreaJma("440000", "Oita", 33.238, 131.613),
        AreaJma("450000", "Miyazaki", 31.911, 131.424),
        AreaJma("460040", "Amami", 28.377, 129.494, "kagoshima"),
        AreaJma("460100", "Kagoshima", 31.597, 130.557, "kagoshima"),
        AreaJma("471000", "Okinawa Main Island", 26.212, 127.681, "okinawa"),
        AreaJma("472000", "Daitojima", 25.829, 131.232, "okinawa"),
        AreaJma("473000", "Miyakojima", 24.805, 125.281, "okinawa"),
        AreaJma("474000", "Yaeyama", 24.341, 124.156, "okinawa"),
    )
}

/** Un'area di previsione della JMA: codice, nome inglese, sede, prefettura se ne ha piu' d'una. */
internal data class AreaJma(
    val codice: String,
    val nome: String,
    val lat: Double,
    val lon: Double,
    val prefettura: String = nome.lowercase(),
) {
    fun distanza2(la: Double, lo: Double): Double = (la - lat) * (la - lat) + (lo - lon) * (lo - lon)
}
