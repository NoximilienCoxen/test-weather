package io.github.noximiliencoxen.caelum.ui.sala.rooms

/**
 * Da che parte arriva il vento **a chi guarda**, dato dove guarda.
 *
 * Il dato dice "da nord-est"; chi sta in mezzo a un campo vuole sapere se lo
 * prende in faccia o alle spalle. [provenienza] sono i gradi da cui arriva il
 * vento (0 = nord, in senso orario), [direzioneSguardo] i gradi verso cui e'
 * girato il telefono. Il risultato e' lo scarto fra i due, da -180 a 180:
 * negativo a sinistra, positivo a destra.
 */
internal fun ventoRispettoASguardo(provenienza: Float, direzioneSguardo: Float): Float {
    var d = (provenienza - direzioneSguardo) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

/** Lo scarto di [ventoRispettoASguardo], detto a parole. */
internal fun latoDelVento(scarto: Float): String = when {
    kotlin.math.abs(scarto) <= 45f -> "ti arriva in faccia"
    kotlin.math.abs(scarto) >= 135f -> "ti arriva alle spalle"
    scarto < 0f -> "ti arriva da sinistra"
    else -> "ti arriva da destra"
}
