plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.forli.meteo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.forli.meteo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("long", "BUILD_EPOCH", (System.getenv("BUILD_EPOCH") ?: "0") + "L")
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
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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
