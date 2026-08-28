package com.forli.meteo.widget.paint

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.forli.meteo.R
import com.forli.meteo.data.AirBand

/**
 * I colori con cui si disegna un widget.
 *
 * Disegnando su un'immagine non si puo' piu' lasciare al sistema la scelta fra
 * chiaro e scuro: la si fa qui, leggendo le stesse risorse di prima, cosi' la
 * tavolozza resta in un posto solo invece di essere ricopiata in Kotlin.
 */
internal class WidgetInk(
    val background: Int,
    val primary: Color,
    val secondary: Color,
    /** Vero col tema scuro: i corpi celesti cambiano di conseguenza. */
    val night: Boolean,
) {
    // La nuvola bianca su fondo bianco non esiste: col tema chiaro le masse si
    // scuriscono, invece di sparire.
    val cloudCore: Color get() = if (night) Color(0xFFFFFFFF) else Color(0xFF9AA0AA)
    val cloudShade: Color get() = if (night) Color(0xFF9BA1AB) else Color(0xFF5A6068)
    val rainCloudCore: Color get() = if (night) Color(0xFF9BA1AB) else Color(0xFF6B717A)
    val rainCloudShade: Color get() = if (night) Color(0xFF474C56) else Color(0xFF3A3F47)

    val sunCore: Color get() = Color(0xFFFFDE59)
    val sunShade: Color get() = Color(0xFFE39A0C)
    val moonCore: Color get() = if (night) Color(0xFFF6F7F9) else Color(0xFFC9CDD4)
    val moonShade: Color get() = if (night) Color(0xFF9AA0AA) else Color(0xFF6E747E)
    val rain: Color get() = Color(0xFF3C8DF5)
    val snow: Color get() = if (night) Color(0xFFDCE6F2) else Color(0xFF8FB4DA)
    val bolt: Color get() = Color(0xFFFFC83D)

    companion object {
        fun of(context: Context): WidgetInk {
            val night = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return WidgetInk(
                background = ContextCompat.getColor(context, R.color.widget_background),
                primary = Color(ContextCompat.getColor(context, R.color.widget_primary)),
                secondary = Color(ContextCompat.getColor(context, R.color.widget_secondary)),
                night = night,
            )
        }
    }
}

/**
 * Il colore della banda, quello della scala europea.
 *
 * Il pallino accanto alla parola serve a chi la parola non la legge: il verde e
 * il rosso si riconoscono da lontano, "discreta" e "scarsa" no.
 */
internal fun AirBand?.tint(): Color = when (this) {
    AirBand.BUONA -> Color(0xFF4E9F52)
    AirBand.DISCRETA -> Color(0xFFA6C74A)
    AirBand.MEDIA -> Color(0xFFF0C51F)
    AirBand.SCARSA -> Color(0xFFF08A3C)
    AirBand.MOLTO_SCARSA -> Color(0xFFE05252)
    AirBand.ESTREMAMENTE_SCARSA -> Color(0xFF9C4BB0)
    null -> Color(0xFF8A8A8E)
}
