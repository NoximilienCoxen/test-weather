package com.forli.meteo.widget

import com.forli.meteo.R
import com.forli.meteo.data.Wmo

/**
 * Icona da mostrare nel widget per una data famiglia meteo.
 *
 * Glance non puo' eseguire il Canvas 3D custom usato nell'app
 * ([com.forli.meteo.ui.home.WeatherSculpture]): serve un set separato di
 * VectorDrawable monocromatici, colorati a runtime col tint dell'accento
 * scelto nella configurazione.
 */
fun iconFor(family: Wmo.Family, isDay: Boolean): Int = when (family) {
    Wmo.Family.ASCIUTTO -> if (isDay) R.drawable.ic_widget_sun else R.drawable.ic_widget_moon
    Wmo.Family.NUVOLOSO -> R.drawable.ic_widget_cloud
    Wmo.Family.NEBBIA -> R.drawable.ic_widget_fog
    Wmo.Family.PIOGGIA -> R.drawable.ic_widget_rain
    Wmo.Family.NEVE -> R.drawable.ic_widget_snow
    Wmo.Family.TEMPORALE -> R.drawable.ic_widget_storm
}
