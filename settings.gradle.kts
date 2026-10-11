pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Caelum"
include(":app")
// Il codice che telefono e orologio hanno in comune: dati, modello, lingua.
include(":core")
// L'app per l'orologio (Wear OS), con lo stesso applicationId di quella del telefono.
include(":wear")
// Il generatore del baseline profile: gira solo in CI, su richiesta (§45).
include(":baselineprofile")
