# ── Readium Kotlin Toolkit ────────────────────────────────────────────────────
-keep class org.readium.** { *; }
-dontwarn org.readium.**

# ── junrar (CBR RAR4) ─────────────────────────────────────────────────────────
-keep class com.github.junrar.** { *; }
-dontwarn com.github.junrar.**

# ── sherpa-onnx (Piper TTS + espeak-ng + ONNX Runtime) ───────────────────────
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# ── kotlinllamacpp (llama.cpp para Gemma 3n) ──────────────────────────────────
-keep class com.ljcamargo.kotlinllamacpp.** { *; }
-dontwarn com.ljcamargo.kotlinllamacpp.**

# ── Media3 ────────────────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Hilt ──────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# ── kotlinx.serialization + TypeConverters de Room ───────────────────────────
-keepattributes *Annotation*, InnerClasses, Signature
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# ── DataStore Proto ───────────────────────────────────────────────────────────
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# ── OkHttp ────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Coil 3 ────────────────────────────────────────────────────────────────────
-dontwarn coil3.**

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Logging (SLF4J) ───────────────────────────────────────────────────────────
-dontwarn org.slf4j.**

# ── Reglas generales de seguridad ─────────────────────────────────────────────
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile