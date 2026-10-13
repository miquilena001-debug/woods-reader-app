package com.example.woodsreader

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject
import dagger.hilt.android.HiltAndroidApp

import timber.log.Timber
import com.example.woodsreader.BuildConfig

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.crossfade

/**
 * Clase Application del proyecto.
 * @HiltAndroidApp es obligatorio para que Hilt genere el grafo de dependencias.
 */
@HiltAndroidApp
class LectorApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.08)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(60L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Inicializar el subsistema local para PDF (PDFBox)
        PDFBoxResourceLoader.init(applicationContext)
    }
}
