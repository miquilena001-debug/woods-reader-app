# ── Readium Kotlin Toolkit ────────────────────────────────────────────────────
-keep class org.readium.** { *; }
-dontwarn org.readium.**

# ── sherpa-onnx (Piper TTS + espeak-ng + ONNX Runtime) ───────────────────────
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

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

# ── Commons Compress (Sherpa ONNX archive extractor) ─────────────────────────
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

# ── PDFBox Android ────────────────────────────────────────────────────────────
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# ── Jsoup ─────────────────────────────────────────────────────────────────────
-dontwarn org.jsoup.**

# ── Room Database ─────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ── Vico Charts ───────────────────────────────────────────────────────────────
-dontwarn com.patrykandpatrick.vico.**

# ── Reglas generales de seguridad ─────────────────────────────────────────────
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile