pluginManagement {
    repositories {
        google()
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
rootProject.name = "ShellDroid"

// App
include(":app")

// Core modules
include(":core:ssh")
include(":core:ssh-native")
include(":core:db")
include(":core:security")
include(":core:ui")

// Feature modules
include(":feature:hosts")
include(":feature:identities")
include(":feature:terminal")
include(":feature:snippets")
include(":feature:portforward")

// Service modules
include(":service:session")

// Vendor modules (Termux) — comentados hasta que agreguemos el submodule en fase 1.5
// include(":terminal-emulator")
// include(":terminal-view")
// project(":terminal-emulator").projectDir = file("vendor/termux-app/terminal-emulator")
// project(":terminal-view").projectDir   = file("vendor/termux-app/terminal-view")
