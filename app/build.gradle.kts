plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.shelldroid"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.shelldroid"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-alpha"
        ndk {
            // Align with :core:ssh-native. We only ship 64-bit.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // libtermux.so is Termux's createSubprocess JNI; we override
            // initializeEmulator and never spawn a local process, so the
            // native lib is dead weight — and it's not 16 KB aligned.
            excludes += "**/libtermux.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:ssh"))
    implementation(project(":core:ssh-native"))
    implementation(project(":core:db"))
    implementation(project(":core:security"))
    implementation(project(":core:ui"))
    implementation(project(":feature:hosts"))
    implementation(project(":feature:identities"))
    implementation(project(":feature:terminal"))
    implementation(project(":feature:snippets"))
    implementation(project(":feature:portforward"))
    implementation(project(":service:session"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.hilt.android)
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    ksp(libs.hilt.compiler)
}
