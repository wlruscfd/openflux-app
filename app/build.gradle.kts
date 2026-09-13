import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// keystore/keystore.properties is git-ignored (see .gitignore) - it and the
// .jks it points at hold the release signing identity. Absent for anyone
// without that file (fine: only assembleRelease needs it, debug builds
// don't), so this is loaded lazily rather than failing the whole build.
val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "org.openflux.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.openflux.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    lint {
        // lintVitalAnalyzeRelease downloads its own detached copy of the
        // Kotlin compiler on first use - a large (~47MB) one-off fetch from
        // dl.google.com that's prone to truncating on a flaky connection,
        // failing assembleRelease for a reason that has nothing to do with
        // this app's code. Regular `./gradlew lint` still runs the same
        // checks on demand.
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // The Go core, built by ../../build_android_aar.sh into libs/openflux.aar
    // (Mobile.startTunnel/stopTunnel/resolveKey from the `mobile` Go package).
    implementation(files("libs/openflux.aar"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // For ControlPlaneAdminClient - plain REST+JSON calls against
    // controlplane's admin API (key/node/ingest-token management).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // QR code generation for sharing a profile's deep link (encode-only,
    // see ui/profiles/QrCode.kt) plus zxing's barcode model used by
    // ui/profiles/QrScanScreen.kt.
    implementation("com.google.zxing:core:3.5.3")

    // QR code scanning (ui/profiles/QrScanScreen.kt): CameraX preview +
    // ML Kit's bundled, fully offline barcode reader.
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Extra Material icons (clipboard, QR scanner) for the profile add menu
    // - the Material 3 icon set ships a small core subset by default.
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
