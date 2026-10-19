# ── Sherpa-ONNX JNI (Crítico: No ofuscar clases llamadas desde C++) ───────────
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# ── Readium Kotlin Toolkit ────────────────────────────────────────────────────
-keep class org.readium.** { *; }
-dontwarn org.readium.**
-keep class org.readium.r2.** { *; }
-dontwarn org.readium.r2.**

# ── Room Database Entities & DAOs ─────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# ── Kotlinx Serialization / Org.JSON ──────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# ── Media3 ────────────────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Hilt ──────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

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

# ── Vico Charts ───────────────────────────────────────────────────────────────
-dontwarn com.patrykandpatrick.vico.**

# ── Reglas generales de seguridad ─────────────────────────────────────────────
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile