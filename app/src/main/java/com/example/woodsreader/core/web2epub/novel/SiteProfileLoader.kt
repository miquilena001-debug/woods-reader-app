package com.example.woodsreader.core.web2epub.novel

import android.content.Context
import timber.log.Timber
import java.io.File

class SiteProfileLoader(
    private val context: Context,
    private val extraDirs: List<File> = emptyList(),
    private val codeAdapters: List<SiteAdapter> = SiteAdapterRegistry.DEFAULT_CODE_ADAPTERS
) {

    fun load(): SiteProfileRegistry {
        val registry = loadRegistry()
        val allProfiles = (registry.userAdapters + registry.assetAdapters).map { it.profile }
        return SiteProfileRegistry(allProfiles)
    }

    fun loadRegistry(): SiteAdapterRegistry {
        val userProfiles = mutableListOf<SiteProfile>()
        val candidateDirs = mutableListOf<File>()

        // 1. Directorios extra proporcionados
        candidateDirs.addAll(extraDirs)

        // 2. Directorio de la app (context.getExternalFilesDir("site_profiles"))
        try {
            context.getExternalFilesDir("site_profiles")?.let { candidateDirs.add(it) }
        } catch (e: Exception) {
            Timber.w(e, "Error al obtener getExternalFilesDir(site_profiles)")
        }

        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles { f -> f.extension.equals("json", ignoreCase = true) }?.forEach { file ->
                    try {
                        val json = file.readText(Charsets.UTF_8)
                        userProfiles.add(SiteProfileRegistry.parse(json))
                    } catch (e: Exception) {
                        Timber.w(e, "Error al parsear perfil de usuario: %s", file.name)
                    }
                }
            }
        }

        val assetProfiles = mutableListOf<SiteProfile>()
        try {
            val assetFiles = context.assets.list("site_profiles") ?: emptyArray()
            for (fileName in assetFiles) {
                if (fileName.endsWith(".json", ignoreCase = true)) {
                    try {
                        val json = context.assets.open("site_profiles/$fileName").bufferedReader().use { it.readText() }
                        val profile = SiteProfileRegistry.parse(json)
                        assetProfiles.add(profile)
                    } catch (e: Exception) {
                        Timber.w(e, "Error al parsear perfil de sitio en assets: %s", fileName)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error al listar perfiles en assets/site_profiles")
        }

        return SiteAdapterRegistry.fromProfiles(
            userProfiles = userProfiles,
            codeAdapters = codeAdapters,
            assetProfiles = assetProfiles
        )
    }
}
