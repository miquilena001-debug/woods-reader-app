package com.example.woodsreader

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject
import dagger.hilt.android.HiltAndroidApp

/**
 * Clase Application del proyecto.
 * @HiltAndroidApp es obligatorio para que Hilt genere el grafo de dependencias.
 */
@HiltAndroidApp
class LectorApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Inicializar el subsistema local para PDF (PDFBox)
        PDFBoxResourceLoader.init(applicationContext)
    }
}
