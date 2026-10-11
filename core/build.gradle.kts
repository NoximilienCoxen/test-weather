plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Il codice che l'app del telefono e quella dell'orologio hanno in comune:
 * modello e lettura delle previsioni (`data/`), la lingua (`lingua/`), e la
 * faccia di Sala: cielo, inchiostri, caratteri (`ui/sala/SalaTheme.kt`, con
 * Caprasimo e Figtree in `res/font`) e il contrasto (`ui/theme/Contrast.kt`).
 *
 * **Niente Android qui dentro, a parte la libreria stessa**: nessun `Context`,
 * nessuna `SharedPreferences`. La scelta della lingua si salva nel modulo `app`
 * (`lingua/LinguaSalvata.kt`) e arriva all'orologio dal telefono. Il package
 * resta lo stesso di prima per non toccare gli import dell'app.
 */
android {
    namespace = "io.github.noximiliencoxen.caelum.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    lint {
        textReport = true
        warningsAsErrors = false
        abortOnError = false
    }
}

dependencies {
    // Il disegno (`DrawScope`, `Path`, `Color`) e i caratteri (`TextStyle`):
    // niente compilatore di Compose, perche' qui non ci sono funzioni
    // `@Composable`.
    api(platform(libs.compose.bom))
    api(libs.compose.ui.graphics)
    api(libs.compose.ui.text)
    // `lerp` sui numeri, e `@Immutable` sulle tinte della palette.
    api(libs.compose.ui.util)
    api(libs.compose.runtime)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
