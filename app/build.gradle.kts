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

// versionCode = total commit count (always increments)
val gitCommitCount: Int =
    "git rev-list --count HEAD".runCommand()?.toIntOrNull() ?: 1

// versionName = clean "1.0.<count>" — no alpha/beta/dirty/SHA noise
val appVersionName: String = "1.0.$gitCommitCount"

// ─── Keystore resolution ───────────────────────────────────────────────────
// Variable names deliberately differ from the Gradle DSL property names
// (storePassword / keyAlias / keyPassword) to avoid silent self-assignment.
val ksFile: String? = System.getenv("KEYSTORE_FILE")
    ?.takeIf { file(it).exists() }
    ?: run {
        val local = file("signing/ryzix-signing.p12")
        if (local.exists()) local.absolutePath else null
    }
val ksStorePass: String = System.getenv("KEYSTORE_PASSWORD") ?: "ryzix1234"
val ksAlias: String     = System.getenv("KEY_ALIAS")         ?: "ryzix-key"
val ksKeyPass: String   = System.getenv("KEY_PASSWORD")       ?: "ryzix1234"

android {
    namespace = "com.ryzix.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ryzix.player"
        minSdk = 24
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    signingConfigs {
        // Single stable config used for both debug and release — same key
        // every build so sideloaded updates never show "package conflict".
        create("stable") {
            if (ksFile != null) {
                storeFile     = file(ksFile)
                storePassword = ksStorePass
                keyAlias      = ksAlias
                keyPassword   = ksKeyPass   // ksKeyPass ≠ "keyPassword" → no self-assignment
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
            isDebuggable        = true
            if (ksFile != null) signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (ksFile != null) signingConfig = signingConfigs.getByName("stable")
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
        viewBinding = true
        buildConfig = true
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
    implementation(libs.fragment.ktx)

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
