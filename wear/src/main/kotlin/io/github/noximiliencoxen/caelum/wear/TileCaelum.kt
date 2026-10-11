package io.github.noximiliencoxen.caelum.wear

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.lingua.tr
import io.github.noximiliencoxen.caelum.ui.sala.GlifoMeteo
import io.github.noximiliencoxen.caelum.ui.sala.glifoDi
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * La Tile di Caelum: la pagina Adesso, da scorrere dal quadrante.
 *
 * ProtoLayout non sa disegnare liberamente e non ha i caratteri di Sala, quindi
 * il cielo arriva come **immagine di fondo**, disegnata dallo stesso codice
 * dell'app ([disegnaCielo]); sopra, testo di sistema con gli inchiostri gia'
 * misurati di [CieloOrologio]. Le figurine sono immagini anche loro.
 *
 * **La versione delle risorse e' il quarto d'ora.** Il fondo cambia con l'ora
 * del giorno: la versione porta l'istante per cui e' stato disegnato, e quando
 * il sistema chiede le risorse il cielo si ridisegna per quello stesso istante,
 * non per "adesso" - che fra le due richieste puo' essere passato.
 */
class TileCaelum : TileService() {

    override fun onTileRequest(richiesta: RequestBuilders.TileRequest) =
        SuspendToFutureAdapter.launchFuture {
            val risultato = PrevisioneOrologio.carica(this@TileCaelum, preferisciRete = false)
            val previsione = risultato?.previsione
            val istante = quartoDOra(Instant.now())
            val adesso = adessoLa(previsione, istante)
            val cielo = cieloOrologio(previsione, adesso)
            val schermo = richiesta.deviceConfiguration
            val contenuto = if (risultato == null) {
                senzaDati(cielo)
            } else {
                adesso(risultato.previsione, adesso, risultato.fahrenheit, cielo)
            }
            TileBuilders.Tile.Builder()
                .setResourcesVersion(VERSIONE + istante.epochSecond)
                .setFreshnessIntervalMillis(TimeUnit.MINUTES.toMillis(PrevisioneOrologio.FRESCA_MINUTI))
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(radice(schermo, contenuto)))
                .build()
        }

    override fun onTileResourcesRequest(richiesta: RequestBuilders.ResourcesRequest) =
        SuspendToFutureAdapter.launchFuture {
            val istante = richiesta.version.removePrefix(VERSIONE).toLongOrNull()
                ?.let(Instant::ofEpochSecond) ?: quartoDOra(Instant.now())
            val previsione = PrevisioneOrologio.carica(this@TileCaelum, preferisciRete = false)?.previsione
            val cielo = cieloOrologio(previsione, adessoLa(previsione, istante))
            val schermo = richiesta.deviceConfiguration
            val densita = schermo.screenDensity
            val lato = (maxOf(schermo.screenWidthDp, schermo.screenHeightDp) * densita).roundToInt().coerceAtLeast(1)
            val richieste = richiesta.resourceIds.toSet()
            fun serve(id: String) = richieste.isEmpty() || id in richieste

            val risorse = ResourceBuilders.Resources.Builder().setVersion(richiesta.version)
            if (serve(ID_FONDO)) risorse.addIdToImageMapping(ID_FONDO, png(fondoPng(cielo, lato), lato))
            val latoFigurina = (FIGURINA_DP * densita).roundToInt()
            for (glifo in GlifoMeteo.entries) {
                for (notte in listOf(false, true)) {
                    val id = idFigurina(glifo, notte)
                    if (serve(id)) risorse.addIdToImageMapping(id, png(figurina(glifo, notte, latoFigurina).png(), latoFigurina))
                }
            }
            risorse.build()
        }

    override fun onTileAddEvent(evento: EventBuilders.TileAddEvent) {
        AggiornaOrologio.pianifica(this)
    }

    // -- Il disegno -------------------------------------------------------------

    private fun radice(schermo: DeviceParameters, contenuto: LayoutElement): LayoutElement {
        val apri = ModifiersBuilders.Clickable.Builder()
            .setId("apri")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(WearActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(apri).build())
            .addContent(
                Image.Builder()
                    .setResourceId(ID_FONDO)
                    .setWidth(dp(schermo.screenWidthDp.toFloat()))
                    .setHeight(dp(schermo.screenHeightDp.toFloat()))
                    .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                    .build(),
            )
            .addContent(contenuto)
            .build()
    }

    private fun adesso(previsione: Forecast, adesso: LocalDateTime, fahrenheit: Boolean, cielo: CieloOrologio): LayoutElement {
        val ora = previsione.current
        val oggi = previsione.dayOf(adesso)
        val ore = oreDaAdesso(previsione, adesso)
        val pioggia = pioggiaInVista(ore)
        val bagnata = pioggia !is PioggiaInVista.Asciutto

        val numero = Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(immagine(idFigurina(glifoDi(ora.weatherCode, nuvolositaAdesso(previsione, adesso)), !ora.isDay), 36f))
            .addContent(Spacer.Builder().setWidth(dp(4f)).build())
            .addContent(testo(temperatura(ora.temperature, fahrenheit), 40f, cielo.ink, grassetto = true))
            .build()

        val giornata = buildList {
            add(condizioneInFrase(ora.weatherCode))
            if (oggi?.tempMax != null && oggi.tempMin != null) {
                add("↑${temperatura(oggi.tempMax, fahrenheit)} ↓${temperatura(oggi.tempMin, fahrenheit)}")
            }
        }.joinToString(" · ")

        // Tre ore dopo quella in corso: la domanda "e fra un po'?" senza aprire l'app.
        val striscia = Row.Builder().setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        ore.drop(1).take(3).forEachIndexed { i, h ->
            if (i > 0) striscia.addContent(Spacer.Builder().setWidth(dp(12f)).build())
            striscia.addContent(
                Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(testo(oraBreve(h.time), 11f, cielo.inkSoft))
                    .addContent(immagine(idFigurina(glifoDi(h.weatherCode, h.cloudCover), !h.isDay), 20f))
                    .addContent(testo(temperatura(h.temperature, fahrenheit), 13f, cielo.ink, grassetto = true))
                    .build(),
            )
        }

        return Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(Spacer.Builder().setHeight(dp(4f)).build())
            .addContent(testo(previsione.place.name, 13f, cielo.inkSoft, grassetto = true))
            .addContent(numero)
            .addContent(testo(giornata, 12f, cielo.ink))
            .addContent(Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(striscia.build())
            .addContent(Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(
                pastiglia(
                    testoPioggia(pioggia),
                    fondo = if (bagnata) cielo.palette.accent else cielo.palette.panelSolido,
                    inchiostro = if (bagnata) cielo.inkAccento else cielo.inkPanel,
                ),
            )
            .build()
    }

    private fun senzaDati(cielo: CieloOrologio): LayoutElement =
        testo(tr("Nessun dato. Tocca per riprovare.", "No data. Tap to retry."), 14f, cielo.ink, righe = 2)

    private fun testo(s: String, punti: Float, colore: Color, grassetto: Boolean = false, righe: Int = 1): LayoutElement =
        Text.Builder()
            .setText(s)
            .setMaxLines(righe)
            .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(punti))
                    .setColor(argb(colore.toArgb()))
                    .setWeight(if (grassetto) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build(),
            )
            .build()

    private fun immagine(id: String, lato: Float): LayoutElement =
        Image.Builder().setResourceId(id).setWidth(dp(lato)).setHeight(dp(lato)).build()

    private fun pastiglia(s: String, fondo: Color, inchiostro: Color): LayoutElement =
        Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(fondo.toArgb()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(14f)).build())
                            .build(),
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(12f)).setEnd(dp(12f)).setTop(dp(5f)).setBottom(dp(5f))
                            .build(),
                    )
                    .build(),
            )
            .addContent(testo(s, 12f, inchiostro, grassetto = true))
            .build()

    private fun png(dati: ByteArray, lato: Int): ResourceBuilders.ImageResource =
        ResourceBuilders.ImageResource.Builder()
            .setInlineResource(
                ResourceBuilders.InlineImageResource.Builder()
                    .setData(dati)
                    .setWidthPx(lato)
                    .setHeightPx(lato)
                    .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                    .build(),
            )
            .build()

    private companion object {
        const val VERSIONE = "cielo-"
        const val ID_FONDO = "fondo"
        const val FIGURINA_DP = 36f

        fun idFigurina(glifo: GlifoMeteo, notte: Boolean) = "figurina-${glifo.name.lowercase()}-${if (notte) "notte" else "giorno"}"

        /** L'istante arrotondato al quarto d'ora: il cielo non cambia piu' spesso di cosi'. */
        fun quartoDOra(istante: Instant): Instant = Instant.ofEpochSecond(istante.epochSecond / 900 * 900)

        /** L'ora del posto, per l'istante dato. */
        fun adessoLa(previsione: Forecast?, istante: Instant): LocalDateTime =
            LocalDateTime.ofInstant(istante, ZoneOffset.ofTotalSeconds(previsione?.utcOffsetSeconds ?: 0))
    }
}
