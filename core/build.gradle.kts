plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Il codice che l'app del telefono e quella dell'orologio hanno in comune:
 * modello e lettura delle previsioni (`data/`) e la lingua (`lingua/`).
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
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
