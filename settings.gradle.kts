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

// Vendor: Termux terminal-emulator and terminal-view were used in phases 2-8
// but replaced by org.connectbot:termlib in the phase 9 terminal rewrite.
// The vendor source remains in vendor/termux-app/ for reference but is no
// longer compiled into the build.
