plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * L'app per l'orologio (Wear OS).
 *
 * **Stesso `applicationId` dell'app del telefono**, ed e' voluto: il Data Layer
 * fa parlare fra loro due app solo se hanno lo stesso pacchetto e la stessa
 * firma. Per questo la firma e' la stessa chiave di debug versionata di `app`.
 * Il codice in comune sta nel modulo `core` (dati, lingua, glifi del tempo).
 */

// Come in `app/build.gradle.kts`: `providers.exec` e non `ProcessBuilder`, perche'
// la configuration cache vieta un processo lanciato a mano in configurazione.
fun comando(vararg argomenti: String): String? = runCatching {
    providers.exec {
        commandLine(argomenti.toList())
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { null }
}.getOrNull()

val commitCount: Int = comando("git", "rev-list", "--count", "HEAD")?.toIntOrNull()
    ?: providers.gradleProperty("caelum.versionCode").orNull?.toIntOrNull()
    ?: 1
val commitSha: String = comando("git", "rev-parse", "--short", "HEAD") ?: "ignoto"

android {
    namespace = "io.github.noximiliencoxen.caelum.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.noximiliencoxen.caelum"
        // Wear OS 3: la prima versione con Compose per Wear stabile.
        minSdk = 30
        targetSdk = 36

        versionCode = commitCount
        versionName = "1.0-$commitSha"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
        }
        release {
            // Spento, per ora: la build che si installa e' quella di debug, e
            // R8 con kotlinx.serialization vuole le sue regole (vedi
            // `app/proguard-rules.pro`). Si accende quando l'orologio avra' un
            // percorso di rilascio che qualcuno ha davvero provato.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        textReport = true
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
    }

    testOptions {
        unitTests {
            all {
                it.systemProperty("user.language", "it")
                it.systemProperty("user.country", "IT")
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)

    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)

    // Il Data Layer: riceve dal telefono localita', unita' e lingua.
    implementation(libs.play.services.wearable)

    testImplementation(libs.junit)
}
