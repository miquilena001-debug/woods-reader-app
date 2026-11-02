package com.example.woodsreader.data.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import com.example.woodsreader.core.storage.StorageRootResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidStorageRootResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : StorageRootResolver {

    override fun getPublicWoodsReaderRoot(): File {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(docsDir, "WoodsReader")
    }

    override fun getFallbackPrivateRoot(): File {
        val externalAppDir = context.getExternalFilesDir(null)
        return File(externalAppDir ?: context.filesDir, "WoodsReader")
    }

    override fun isExternalAccessGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}
