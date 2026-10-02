package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Chi fornisce i dati, e le parole con cui chiede di essere citato.
 *
 * **Non sono frasi scritte a memoria.** Vengono dalle pagine delle fonti,
 * scaricate in CI da `scripts/probe_licenze.py` il 2 ottobre 2026 e lette in
 * `ci-artifacts/api/licenze/` (CONTESTO §47):
 *
 * - **Open-Meteo** (`/en/licence`): "API data are offered under Attribution
 *   4.0 International (CC BY 4.0)" e "You must include a link next to any
 *   location Open-Meteo data are displayed". Per questo il credito sta sotto
 *   la barra delle ore, che e' sotto ogni sala, e non solo nelle note legali.
 * - **CAMS**, per aria e polline (`/en/docs/air-quality-api`): "All users of
 *   Open-Meteo data must provide a clear attribution to CAMS ENSEMBLE data
 *   provider as well as a reference to Open-Meteo", con la citazione in
 *   [CITAZIONE_CAMS].
 * - **MeteoAlarm** (`feeds.meteoalarm.org`): "License (CC BY 4.0)" e "Data
 *   provided by EUMETNET members".
 *
 * Tenute qui, in un posto solo: la riga sotto la barra, il bollettino e le
 * note legali le leggono da qui, e non possono dire tre cose diverse.
 */
object Fonti {
    const val OPEN_METEO = "https://open-meteo.com/"
    const val OPEN_METEO_LICENZA = "https://open-meteo.com/en/licence"
    const val CC_BY_4 = "https://creativecommons.org/licenses/by/4.0/"
    const val CAMS_ARIA = "https://open-meteo.com/en/docs/air-quality-api"
    const val METEOALARM = "https://meteoalarm.org/"

    /** La citazione che Open-Meteo chiede per i dati CAMS, parola per parola. */
    const val CITAZIONE_CAMS =
        "METEO FRANCE, Institut national de l'environnement industriel et des risques (Ineris), " +
            "Aarhus University, Norwegian Meteorological Institute (MET Norway), Jülich Institut für " +
            "Energie- und Klimaforschung (IEK), Institute of Environmental Protection – National " +
            "Research Institute (IEP-NRI), Koninklijk Nederlands Meteorologisch Instituut (KNMI), " +
            "Nederlandse Organisatie voor toegepast-natuurwetenschappelijk onderzoek (TNO), Swedish " +
            "Meteorological and Hydrological Institute (SMHI), Finnish Meteorological Institute (FMI), " +
            "Italian National Agency for New Technologies, Energy and Sustainable Economic Development " +
            "(ENEA) and Barcelona Supercomputing Center (BSC) (2022): CAMS European air quality " +
            "forecasts, ENSEMBLE data. Copernicus Atmosphere Monitoring Service (CAMS) Atmosphere " +
            "Data Store (ADS)."

    /** La citazione di Open-Meteo, come la propone la sua pagina della licenza. */
    const val CITAZIONE_OPEN_METEO =
        "Zippenfenig, P. (2023). Open-Meteo.com Weather API [Computer software]. Zenodo. " +
            "https://doi.org/10.5281/ZENODO.7970649"
}

/**
 * Un pezzo di testo che apre un indirizzo: sottolineato, perche' si capisca che
 * si tocca, e annunciato come collegamento da TalkBack.
 */
@Composable
internal fun Collegamento(
    testo: String,
    indirizzo: String,
    stile: TextStyle,
    colore: Color,
    modifier: Modifier = Modifier,
) {
    val apri = LocalUriHandler.current
    Text(
        text = testo,
        style = stile.copy(textDecoration = TextDecoration.Underline),
        color = colore,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .semantics { role = Role.Button }
            .clickable(onClickLabel = "apri $indirizzo") { runCatching { apri.openUri(indirizzo) } },
    )
}

/**
 * La riga dei crediti sotto la barra delle ore: in ogni sala, accanto ai dati.
 *
 * "Open-Meteo.com" apre il sito, come chiede la sua licenza; "aria e polline
 * CAMS" apre le note legali, dove sta la citazione intera, che in una riga non
 * entra. Corta e smorzata: e' un obbligo, non un titolo.
 */
@Composable
internal fun RigaCrediti(
    palette: SalaPalette,
    onLicenze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stile = SalaType.rowNote
    val colore = palette.inkSuCielo.copy(alpha = 0.85f)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = "Dati", style = stile, color = colore, maxLines = 1)
        Collegamento("Open-Meteo.com", Fonti.OPEN_METEO, stile, colore)
        Text(text = "·", style = stile, color = colore, maxLines = 1)
        Text(
            text = "aria e polline CAMS Copernicus",
            style = stile.copy(textDecoration = TextDecoration.Underline),
            color = colore,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .semantics { role = Role.Button }
                .clickable(onClickLabel = "apri le note legali con le fonti", onClick = onLicenze),
        )
    }
}
