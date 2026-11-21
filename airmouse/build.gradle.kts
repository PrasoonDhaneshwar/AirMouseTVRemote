plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// The air mouse: pointer math, the Bluetooth mouse link and the session that ties them together.
// It has no UI and no dependency injection, so it builds and runs its tests in seconds, without
// the app's annotation processing. The app's screens talk to it through AirMouseSession.
android {
    namespace = "com.prasoon.airmousetv.airmouse"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    testOptions {
        // android.util.Log calls become no-ops instead of throwing in JVM unit tests
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
