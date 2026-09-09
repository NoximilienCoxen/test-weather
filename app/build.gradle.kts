plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Quanti commit ha questo ramo, e a quale commit sta.
 *
 * **Servono perche' finora la build non sapeva dire chi era.** `versionCode` e'
 * rimasto a 1 per duecentosedici commit, e `versionName` a "1.0": chi ha l'app
 * installata da `apk-latest` - che e' un tag fisso, sempre lo stesso file - non
 * aveva nessun modo, ne' dentro l'app ne' dalle informazioni di sistema, di
 * sapere quale build stesse usando. Su un'app che si aggiorna sopra se' stessa
 * piu' volte al giorno, "che versione hai?" era una domanda senza risposta, e
 * senza risposta e' anche "il difetto che vedi c'e' ancora?".
 *
 * Il conto dei commit cresce da solo e non scende mai, che e' l'unica cosa che
 * Android chiede a un `versionCode`. Lo SHA breve dice a quale riga di storia
 * corrisponde quello che si ha in mano.
 *
 * Se git non c'e' - un sorgente scaricato come zip - si ripiega su 1 e su
 * "1.0-ignoto": la build deve funzionare lo stesso, ma non deve **fingere** di
 * sapere una cosa che non sa.
 */
fun comando(vararg argomenti: String): String? = runCatching {
    val processo = ProcessBuilder(*argomenti)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val uscita = processo.inputStream.bufferedReader().use { it.readText() }.trim()
    if (processo.waitFor() == 0 && uscita.isNotEmpty()) uscita else null
}.getOrNull()

val commitCount: Int = comando("git", "rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val commitSha: String = comando("git", "rev-parse", "--short", "HEAD") ?: "ignoto"

android {
    namespace = "io.github.noximiliencoxen.caelum"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.noximiliencoxen.caelum"
        minSdk = 26
        targetSdk = 36

        // Vedi la nota in cima: non piu' 1 e "1.0" fissi.
        versionCode = commitCount
        versionName = "1.0-$commitSha"
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

        // Serve a `BuildConfig.DEBUG`, che e' quello che tiene gli agganci di
        // verifica fuori dalla build di release: vedi `MainActivity.applyExtras`.
        // Da AGP 8 in poi va chiesto, non arriva piu' da se'.
        buildConfig = true
    }

    lint {
        // Android Lint sta gia' dentro AGP: niente plugin nuovo, niente versione
        // in piu' da tenere aggiornata per dire cose che qui direbbe comunque.
        //
        // **Referta, non blocca**, e non e' pigrizia: una baseline si puo'
        // scrivere solo dopo aver letto cosa segnala, e da questo container non
        // si compila. Il rapporto va nel log della CI, si legge, si sistema cio'
        // che va sistemato, e solo allora ha senso alzare `abortOnError`. Un
        // controllo acceso su un debito mai letto lo si spegne dopo due giri.
        textReport = true
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
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
    // `lifecycle-viewmodel-compose` tolta: dava `viewModel()`, che qui non si
    // chiama mai. Il ViewModel arriva da `by viewModels()` in `MainActivity` e
    // poi viaggia come parametro, e quello lo porta activity-ktx.

    // Per collectAsStateWithLifecycle: con il semplice collectAsState la
    // raccolta continua anche con l'app in sottofondo.
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    // **Qui c'erano `ui-tooling-preview` e `ui-tooling`, e non servivano a
    // niente.** Reggono le anteprime dell'editor, e in questo progetto non
    // esiste **una sola** `@Preview`: zero import di quel pacchetto in
    // ventimila righe. Non e' una svista, e' coerente con come si guarda questa
    // app - la scultura respira, la pioggia cade, le persone camminano dietro
    // il vetro, e un fotogramma fermo nell'editor non direbbe niente di utile.
    // Si guarda in mano, o negli scatti dell'emulatore in CI.
    //
    // La seconda era per giunta `debugImplementation`, cioe' finiva **proprio
    // nella variante che le persone installano** da `apk-latest`.

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.glance.appwidget)
    // `glance-material3` non c'e' piu': zero import, nessun `GlanceTheme`. I
    // widget non usano i componenti di Glance, dipingono una bitmap sola su un
    // Canvas loro (vedi `widget/paint/`), e i colori se li prendono da
    // `WidgetInk`. Un tema che nessuno interroga era peso e basta.

    // Solo per i test, e fuori dall'APK. Robolectric serve a un file solo -
    // il parser del feed passa da android.util.Xml, che su una JVM non c'e' -
    // e riscrivere quel parser su SAX per evitarlo avrebbe voluto dire rifare
    // da capo codice gia' pagato caro contro la risposta vera del feed.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
