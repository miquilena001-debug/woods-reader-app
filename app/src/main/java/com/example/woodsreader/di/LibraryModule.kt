package com.example.woodsreader.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.services.content.DefaultContentService
import org.readium.r2.shared.publication.services.content.contentServiceFactory
import org.readium.r2.shared.publication.services.content.iterators.HtmlResourceContentIterator
import dagger.Lazy
import org.readium.r2.shared.util.http.DefaultHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {

    // << CHANGE: Provisión única y compartida del cliente HTTP (Módulo E-3)
    @Provides
    @Singleton
    fun provideHttpClient(): DefaultHttpClient = DefaultHttpClient()

    @Provides
    @Singleton
    fun providePdfiumDocumentFactory(
        @ApplicationContext context: Context
    ): PdfiumDocumentFactory = PdfiumDocumentFactory(context)

    @Provides
    @Singleton
    fun provideAssetRetriever(
        @ApplicationContext context: Context,
        // << CHANGE: Ahora recibe el cliente compartido
        httpClient: DefaultHttpClient
    ): AssetRetriever {
        return AssetRetriever(
            contentResolver = context.contentResolver,
            httpClient = httpClient
        )
    }

    @Provides
    @Singleton
    fun providePublicationOpener(
        @ApplicationContext context: Context,
        assetRetriever: AssetRetriever,
        // << CHANGE: Inyectar cliente HTTP compartido (Módulo E-3)
        httpClient: DefaultHttpClient,
        // << CHANGE: Inyectar factory de forma lazy para ahorrar RAM en el inicio (Módulo E-2)
        pdfFactory: Lazy<PdfiumDocumentFactory>
    ): PublicationOpener {
        return PublicationOpener(
            publicationParser = DefaultPublicationParser(
                context = context,
                assetRetriever = assetRetriever,
                httpClient = httpClient,
                // PDFium se inicializa en el primer uso: PublicationOpener es @Singleton,
                // por lo que Hilt solo invoca este bloque la primera vez que alguien lo inyecta.
                // La Lazy<PdfiumDocumentFactory> garantiza que PDFium no carga al arrancar la app.
                pdfFactory = pdfFactory.get()
            ),
            onCreatePublication = {
                @OptIn(ExperimentalReadiumApi::class)
                servicesBuilder.contentServiceFactory = DefaultContentService.createFactory(
                    resourceContentIteratorFactories = listOf(
                        HtmlResourceContentIterator.Factory()
                    )
                )
            }
        )
    }
}
