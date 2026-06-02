import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

// ─── Git-based versioning ─────────────────────────────────────────────────
fun String.runCommand(workingDir: File = rootDir): String? = try {
    ProcessBuilder(*trim().split("\\s+".toRegex()).toTypedArray())
        .directory(workingDir)
        .redirectErrorStream(true)
        .start()
        .inputStream
        .bufferedReader()
        .readText()
        .trim()
        .takeIf { it.isNotEmpty() }
} catch (_: Exception) { null }

val gitCommitCount: Int =
    "git rev-list --count HEAD".runCommand()?.toIntOrNull() ?: 1

val gitVersionName: String =
    "git describe --tags --always --dirty".runCommand() ?: "dev"

// ─── Keystore resolution ───────────────────────────────────────────────────
// Priority: env var (CI) → committed keystore (local dev)
val keystorePath: String? = System.getenv("KEYSTORE_FILE")
    ?.takeIf { file(it).exists() }
    ?: run {
        val committed = file("signing/ryzix-signing.p12")
        if (committed.exists()) committed.absolutePath else null
    }
val keystorePassword: String = System.getenv("KEYSTORE_PASSWORD") ?: "ryzix1234"
val keyAlias: String        = System.getenv("KEY_ALIAS")          ?: "ryzix-key"
val keyPassword: String     = System.getenv("KEY_PASSWORD")        ?: "ryzix1234"

android {
    namespace = "com.ryzix.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ryzix.player"
        minSdk = 24
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = gitVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    signingConfigs {
        // Single stable config used by both debug and release builds.
        // This prevents "App not installed — package conflict" when sideloading
        // because the same key is always used regardless of CI run.
        create("stable") {
            if (keystorePath != null) {
                storeFile     = file(keystorePath)
                storePassword = keystorePassword
                keyAlias      = keyAlias
                keyPassword   = keyPassword
                // ryzix-signing.p12 is PKCS12; AGP 8+ auto-detects the format.
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
            isDebuggable        = true
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        release {
            isMinifyEnabled    = true
            isShrinkResources  = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("stable")
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
        viewBinding  = true
        buildConfig  = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.viewpager2)

    // Media3 / ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.rtsp)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // Coil — image loading + video frame thumbnails
    implementation(libs.coil)
    implementation(libs.coil.video)

    // Coroutines
    implementation(libs.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
