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

/**
 * La chiave con cui si firma cio' che va sullo store, **letta da fuori**.
 *
 * Quattro variabili d'ambiente (o proprieta' Gradle con lo stesso nome):
 * `CAELUM_KEYSTORE` (il percorso del file), `CAELUM_KEYSTORE_PASSWORD`,
 * `CAELUM_KEY_ALIAS`, `CAELUM_KEY_PASSWORD`. In CI arrivano dai segreti del
 * repository; il file della chiave **non sta nel repository** e non deve
 * entrarci mai, al contrario di `debug.keystore` che e' pubblico per
 * costruzione.
 *
 * Senza quelle variabili la build funziona lo stesso, e la release resta
 * com'era prima. E' la stessa di `app`: il Data Layer vuole stessa firma. Vedi `PUBBLICAZIONE.md`.
 */
fun segreto(nome: String): String? =
    providers.environmentVariable(nome).orElse(providers.gradleProperty(nome)).orNull?.ifBlank { null }

val chiaveStore: String? = segreto("CAELUM_KEYSTORE")
val chiaveStorePassword: String? = segreto("CAELUM_KEYSTORE_PASSWORD")
val chiaveAlias: String? = segreto("CAELUM_KEY_ALIAS")
val chiavePassword: String? = segreto("CAELUM_KEY_PASSWORD")
val firmaDiRilascio: Boolean =
    chiaveStore != null && chiaveStorePassword != null && chiaveAlias != null && chiavePassword != null

android {
    namespace = "io.github.noximiliencoxen.caelumweather.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.noximiliencoxen.caelumweather"
        // Wear OS 3: la prima versione con Compose per Wear stabile.
        minSdk = 30
        targetSdk = 36

        // **Non lo stesso numero del telefono.** Lo store tiene telefono e
        // orologio sotto lo stesso pacchetto, e rifiuta due file con lo stesso
        // `versionCode`: il secondo caricato risulta "gia' usato". Un milione
        // di scarto basta per sempre (il conto dei commit e' a qualche
        // centinaio) e lascia leggibile il numero vero nelle ultime cifre.
        versionCode = 1_000_000 + commitCount
        versionName = "1.0-$commitSha"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (firmaDiRilascio) {
            create("release") {
                storeFile = rootProject.file(chiaveStore!!)
                storePassword = chiaveStorePassword
                keyAlias = chiaveAlias
                keyPassword = chiavePassword
            }
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
            // Con la chiave di caricamento, quella; senza, la chiave di debug
            // come prima. Sempre la stessa di `app`.
            signingConfig = signingConfigs.getByName(if (firmaDiRilascio) "release" else "debug")
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

    // La Tile (pagina da scorrere dal quadrante) e la complicazione per il
    // quadrante, aggiornate da un lavoro periodico.
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.expression)
    implementation(libs.wear.complications.data.source.ktx)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
}
