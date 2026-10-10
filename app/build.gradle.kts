
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
// Excluir librerías viejas support que Readium trae transitivamente
// y que duplican clases de Media3 / AndroidX
configurations.all {
    exclude(group = "com.android.support", module = "support-media-compat")
    exclude(group = "com.android.support", module = "support-compat")
    exclude(group = "com.android.support", module = "support-annotations")
    // Forzar kotlin-stdlib a la versión del proyecto.
    // kotlinllamacpp:0.2.0 trae kotlin-stdlib:2.2.20 que es incompatible
    // con KSP 2.0.21-x que usamos para Room y Hilt.
    resolutionStrategy.force(
        "org.jetbrains.kotlin:kotlin-stdlib:2.0.21",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21",
        "org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21"
    )
}
android {
    namespace = "com.example.woodsreader"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.woodsreader"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // arm64 para rendimiento, armeabi-v7a para compatibilidad total
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    androidResources {
        noCompress += listOf("onnx", "txt")
    }

    // NDK r27b obligatorio para Android 15 (alineación de páginas 16KB)
    ndkVersion = "27.2.12479018"
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        // Necesario para java.time.* usado por Readium
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }

    // KSP — Room schema location para AutoMigrations
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // ── Desugaring ────────────────────────────────────────────────────────────
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // ── AndroidX Core ─────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)         // ProcessLifecycleOwner para IA

    // ── Compose ───────────────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.viewbinding)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.ext)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.haze)

    // ── Hilt ──────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // ── Room + KSP ────────────────────────────────────────────────────────────
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // ── Fragment & Collections ───────────────────────────────────────────────────────
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation(libs.androidx.collection)
    implementation(libs.androidx.collection.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ── DataStore Proto ───────────────────────────────────────────────────────
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.kotlin.lite)

    // ── Serialización (TypeConverters de Room + Locator de Readium) ───────────
    implementation(libs.kotlinx.serialization.json)

    // ── Readium ───────────────────────────────────────────────────────────────
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.tts)              // PublicationSpeechSynthesizer — MÓDULO SEPARADO
    implementation(libs.readium.pdfium)
    // ── IA Local — kotlinllamacpp (Maven Central desde v0.2.0) ─────────────────
    // implementation(libs.kotlinllamacpp)  // TODO Fase 4: descomentar cuando se implemente IA. Requiere Kotlin 2.2.0+ (binario incompatible con 2.0.21)
    // ── Media3 — Audio + Notificación de controles ────────────────────────────
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    // ── Coil 3 — Portadas con disco cache LRU ────────────────────────────────
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)

    // ── Paging 3 — Carga paginada de la biblioteca ───────────────────────────
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // ── WorkManager — Escaneo periódico en background ────────────────────────
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // ── Parsing ───────────────────────────────────────────────────────────────
    implementation(libs.pdfbox.android)   // Apache PDFBox for extraction
    implementation(libs.junrar)           // CBR RAR4 (fallback ZIP para RAR5)
    implementation(libs.jsoup)            // HTML/XML de EPUBs en metadatos
    implementation(libs.commonmark)       // Renderizado de .md
    implementation(libs.commonmark.gfm.tables)

    // ── Charts — Estadísticas de lectura ─────────────────────────────────────
    implementation(libs.vico.compose.m3)

    // ── Logging ──────────────────────────────────────────────────────────────────
    implementation(libs.timber)

    // ── OkHttp — Descarga reanudable del modelo Gemma 3n ────────────────────
    implementation(libs.okhttp)

    // ── Sherpa ONNX para TTS Neuronal Offline ───────────────────────────────
    implementation(files("libs/sherpa-onnx.aar"))




    // ── Tests ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}


