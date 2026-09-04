plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.noximiliencoxen.caelum"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.noximiliencoxen.caelum"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Chiave di debug fissa e versionata. Senza, la CI ne genera una nuova a
    // ogni build e Android rifiuta di aggiornare l'app installata, costringendo
    // a disinstallarla prima. Non e' un segreto: sono le credenziali di debug
    // documentate da Android, valide solo per build non distribuibili.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
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
            // R8 acceso, con le sue regole accanto in proguard-rules.pro.
            // Accenderlo senza dire cosa tenere, con kotlinx.serialization in
            // gioco, rompe la deserializzazione in silenzio: l'app compila, si
            // installa, e poi non legge piu' una previsione.
            //
            // La build che finisce sul telefono e' quella di debug, che non e'
            // minificata: qui il rischio e' zero e il guadagno e' avere un
            // percorso di release che qualcuno ha davvero provato. La CI
            // compila anche questa, se no il flag non lo verifica nessuno.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // I test leggono i colori del tema e le stringhe: senza questo, le
            // risorse di Android rispondono null e i fallimenti raccontano
            // tutt'altra storia rispetto a quella vera.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Per collectAsStateWithLifecycle: con il semplice collectAsState la
    // raccolta continua anche con l'app in sottofondo.
    implementation(libs.androidx.lifecycle.runtime.compose)

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

    // Solo per i test, e fuori dall'APK. Robolectric serve a un file solo -
    // il parser del feed passa da android.util.Xml, che su una JVM non c'e' -
    // e riscrivere quel parser su SAX per evitarlo avrebbe voluto dire rifare
    // da capo codice gia' pagato caro contro la risposta vera del feed.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
