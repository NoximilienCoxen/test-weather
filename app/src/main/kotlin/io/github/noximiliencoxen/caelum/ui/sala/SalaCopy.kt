package io.github.noximiliencoxen.caelum.ui.sala

/**
 * Le didascalie di Sala I: titolo e corpo per ognuna delle ventiquattro
 * combinazioni fase x tempo, portate dal prototipo. Sono testo, non dati —
 * i numeri che le accompagnano (temperatura, ora, percepiti) restano quelli
 * veri della previsione.
 */
private val SalaTitles: Map<SalaCondition, Map<SalaPhase, String>> = mapOf(
    SalaCondition.SERENO to mapOf(
        SalaPhase.ALBA to "Alba limpida sopra la pianura",
        SalaPhase.GIORNO to "Pieno sole, aria calda",
        SalaPhase.TRAMONTO to "Tramonto senza una nuvola",
        SalaPhase.NOTTE to "Notte serena, aria ferma",
    ),
    SalaCondition.NUVOLOSO to mapOf(
        SalaPhase.ALBA to "Luce filtrata dalle nubi alte",
        SalaPhase.GIORNO to "Nuvole di passaggio",
        SalaPhase.TRAMONTO to "Cielo coperto verso sera",
        // Diceva "niente stelle", e da questo giro le stelle si vedono
        // **attraverso** le nuvole - le nubi non le spengono, le coprono. Una
        // didascalia che smentisce cio' che si ha sotto gli occhi toglie
        // credito anche alle altre sei.
        SalaPhase.NOTTE to "Notte coperta, poche stelle",
    ),
    SalaCondition.PIOGGIA to mapOf(
        SalaPhase.ALBA to "Piove dalle prime luci",
        SalaPhase.GIORNO to "Rovescio di metà pomeriggio",
        SalaPhase.TRAMONTO to "Pioggia fino a notte",
        SalaPhase.NOTTE to "Pioggia nella notte",
    ),
    SalaCondition.GRANDINE to mapOf(
        SalaPhase.ALBA to "Grandine all'alba",
        SalaPhase.GIORNO to "Rovescio di grandine",
        SalaPhase.TRAMONTO to "Grandine sul tardi",
        SalaPhase.NOTTE to "Grandine notturna",
    ),
    SalaCondition.TEMPORALE to mapOf(
        SalaPhase.ALBA to "Temporale all'alba",
        SalaPhase.GIORNO to "Temporale sul pomeriggio",
        SalaPhase.TRAMONTO to "Temporale al tramonto",
        SalaPhase.NOTTE to "Temporale notturno",
    ),
    SalaCondition.TEMPORALE_GRANDINE to mapOf(
        SalaPhase.ALBA to "Temporale e grandine all'alba",
        SalaPhase.GIORNO to "Temporale con grandine",
        SalaPhase.TRAMONTO to "Temporale e grandine a sera",
        SalaPhase.NOTTE to "Temporale e grandine di notte",
    ),
)

private val SalaBodies: Map<SalaCondition, String> = mapOf(
    SalaCondition.SERENO to "Cielo aperto e visibilità ottima.",
    SalaCondition.NUVOLOSO to "Nubi medie che coprono il sole a intervalli. Non portano pioggia, ma tengono la temperatura ferma.",
    SalaCondition.PIOGGIA to "Pioggia in corso: i millimetri e la probabilità ora per ora sono in Sala III.",
    SalaCondition.GRANDINE to "Chicchi in caduta: copri le piante in vaso e sposta l'auto se puoi.",
    SalaCondition.TEMPORALE to "Fulminazione attiva. Meglio non stare all'aperto fino a mezz'ora dopo l'ultimo tuono.",
    SalaCondition.TEMPORALE_GRANDINE to "Cella temporalesca con grandine: raffiche improvvise e visibilità ridotta.",
)

fun salaTitle(condition: SalaCondition, phase: SalaPhase): String =
    SalaTitles.getValue(condition).getValue(phase)

fun salaBody(condition: SalaCondition): String = SalaBodies.getValue(condition)

fun SalaCondition.label(): String = when (this) {
    SalaCondition.SERENO -> "sereno"
    SalaCondition.NUVOLOSO -> "nuvoloso"
    SalaCondition.PIOGGIA -> "pioggia"
    SalaCondition.GRANDINE -> "grandine"
    SalaCondition.TEMPORALE -> "temporale"
    SalaCondition.TEMPORALE_GRANDINE -> "temporale con grandine"
}

fun SalaPhase.label(): String = when (this) {
    SalaPhase.ALBA -> "alba"
    SalaPhase.GIORNO -> "giorno"
    SalaPhase.TRAMONTO -> "tramonto"
    SalaPhase.NOTTE -> "notte"
}
