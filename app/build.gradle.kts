plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Numero di versione e istante della build.
 *
 * Erano fissi a 1 e "1.0", e questo aveva due conseguenze spiacevoli. La prima
 * e' che ogni build si presentava ad Android come *la stessa* versione della
 * precedente: un aggiornamento con lo stesso `versionCode` e' un'installazione
 * laterale, non un aggiornamento, e Play Protect guarda proprio quella storia
 * per decidere se un pacchetto e' una cosa che evolve o una che compare dal
 * nulla. La seconda e' che l'app non aveva alcun modo di sapere se quella
 * pubblicata fosse piu' recente di lei.
 *
 * In locale restano i valori di sviluppo; e' la CI a passarli, e sono derivati
 * dal numero della corsa e dall'istante del commit, quindi crescono davvero.
 */
val buildNumber = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val buildEpoch = System.getenv("BUILD_EPOCH")?.toLongOrNull() ?: 0L
val buildCommit = System.getenv("BUILD_COMMIT")?.takeIf { it.isNotBlank() } ?: "sviluppo"

android {
    namespace = "com.forli.meteo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.forli.meteo"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
            ?: "1.0.$buildNumber"

        // L'app deve poter rispondere a "sono aggiornata?" senza chiedere in
        // giro chi e'. L'istante della build e' il solo confronto che regge
        // contro un rilascio a tag fisso, dove il nome del tag non cambia mai.
        buildConfigField("long", "BUILD_EPOCH", "${buildEpoch}L")
        buildConfigField("String", "BUILD_COMMIT", "\"$buildCommit\"")
    }

    // Chiave di debug fissa e versionata. Senza, la CI ne genera una nuova a
    // ogni build e Android rifiuta di aggiornare l'app installata, costringendo
    // a disinstallarla prima. Non e' un segreto: sono le credenziali di debug
    // documentate da Android, valide solo per build non distribuibili.
    //
    // La chiave di **rilascio** e' l'altra meta' della storia, ed e' quella che
    // riguarda l'avviso di Play Protect. Un pacchetto firmato con la chiave di
    // debug si presenta al sistema per quello che e': roba non distribuibile,
    // firmata da "Android Debug", con la stessa identica identita' di migliaia
    // di altre build sperimentali in giro per il mondo. Play Protect quella
    // firma la conosce e la tratta di conseguenza.
    //
    // Questa chiave invece e' **una sola e sempre la stessa**, dedicata a questa
    // app, e le build che escono di qui formano una storia coerente: stesso
    // firmatario, `versionCode` che cresce, nessun flag di debug. Non e' un
    // segreto - sta nel repository come la chiave di debug, e chiunque puo'
    // leggerla - ma non deve esserlo: serve a dare **continuita'** all'identita'
    // del pacchetto, non a dimostrare che l'ha costruito qualcuno di fidato.
    // Chi volesse anche quella seconda garanzia mette la chiave vera nei
    // segreti del repository, e le tre variabili qui sotto la fanno vincere
    // senza toccare una riga.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("rilascio") {
            // `takeIf { isNotBlank() }` e non un semplice controllo di nullo:
            // la CI espone i segreti come variabili d'ambiente del job, e un
            // segreto **non impostato** arriva qui come stringa vuota, non
            // come assente. Con la sola verifica di nullo la password sarebbe
            // diventata "", la firma sarebbe fallita, e sarebbe fallita solo
            // sul repository che i segreti non li ha - cioe' su questo.
            fun env(name: String): String? =
                System.getenv(name)?.takeIf { it.isNotBlank() }

            val provided = env("RELEASE_KEYSTORE")?.takeIf { file(it).exists() }
            storeFile = provided?.let { file(it) } ?: file("release.keystore")
            storePassword = env("RELEASE_STORE_PASSWORD") ?: "forlimeteo"
            keyAlias = env("RELEASE_KEY_ALIAS") ?: "meteoforli"
            keyPassword = env("RELEASE_KEY_PASSWORD") ?: "forlimeteo"
            // Entrambi gli schemi: v2 e' quello che il sistema verifica
            // all'installazione dal Nougat in su, v1 serve solo ai dispositivi
            // piu' vecchi di minSdk e si puo' lasciar perdere.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            // Non marcata come debuggabile: una app debuggabile gira con
            // ottimizzazioni ridotte, e questa schermata fa geometria in tempo
            // reale a ogni fotogramma. Misurando la fluidita' su una build
            // debuggabile si misurerebbe un'app che non esiste.
            isDebuggable = false
        }
        release {
            // Niente offuscamento: questa e' un'app di poche migliaia di righe
            // che disegna geometria a mano, e R8 in modalita' completa
            // toglierebbe leggibilita' agli inciampi senza togliere peso
            // apprezzabile. Le risorse inutilizzate pero' vanno via.
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("rilascio")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Serve a BUILD_EPOCH e BUILD_COMMIT, che sono il modo in cui l'app
        // sa se quella pubblicata e' piu' recente di lei.
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}
