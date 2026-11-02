package com.example.woodsreader.core.storage

import java.io.File

/**
 * Abstracción de resolución de raíz de almacenamiento de Woods Reader (Regla R-TEST).
 * Cero dependencias de android.* para permitir tests unitarios puros en JVM con carpetas temporales.
 */
interface StorageRootResolver {
    /** Raíz pública principal: Documents/WoodsReader si hay permiso */
    fun getPublicWoodsReaderRoot(): File

    /** Raíz de degradación privada: getExternalFilesDir o filesDir */
    fun getFallbackPrivateRoot(): File

    /** Indica si el permiso de acceso total o de almacenamiento está activo */
    fun isExternalAccessGranted(): Boolean

    /** Raíz efectiva actual según el estado del permiso */
    fun getEffectiveRoot(): File =
        if (isExternalAccessGranted()) getPublicWoodsReaderRoot() else getFallbackPrivateRoot()
}
