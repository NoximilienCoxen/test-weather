plugins {
    alias(libs.plugins.android.test)
}

/**
 * Il generatore del baseline profile (CONTESTO §45).
 *
 * **Un modulo di test semplice, e non il plugin `androidx.baselineprofile`.**
 * Il plugin collega da se' generazione e consumo, ma e' legato alle versioni
 * di AGP che conosce, e questo progetto e' su AGP 9: da dentro il container
 * Maven non si raggiunge, e la sua compatibilita' si scoprirebbe solo in CI,
 * a tentativi. Qui servono soltanto AGP stesso (`com.android.test`) e due
 * librerie di prova, che di AGP non sanno niente.
 *
 * Il prezzo e' un passo a mano: la CI genera `baseline-prof.txt` e lo
 * pubblica, e il file si copia in `app/src/main/`, dove AGP lo prende senza
 * altra configurazione.
 */
android {
    namespace = "io.github.noximiliencoxen.caelum.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // 28: la soglia sotto cui il profilo non si raccoglie comunque.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Prova la build di debug dell'app, che qui **non e' debuggabile** (vedi
    // `app/build.gradle.kts`): e' la condizione che la raccolta chiede, e non
    // e' offuscata, quindi le regole escono coi nomi veri. Sulla release AGP le
    // riscrive da se' attraverso la mappa di R8.
    targetProjectPath = ":app"

    // La raccolta gira in un processo suo, separato dall'app che misura.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
