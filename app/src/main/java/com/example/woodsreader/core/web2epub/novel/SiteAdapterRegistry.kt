package com.example.woodsreader.core.web2epub.novel

/**
 * Registro de adaptadores de sitio con orden de resolución estricto:
 * 1. Perfiles de usuario (archivos JSON en disco)
 * 2. Adaptadores en código (clases especializadas como Ao3SiteAdapter, etc.)
 * 3. Perfiles integrados (archivos JSON empaquetados en assets)
 * 4. Adaptador por defecto (SiteProfile.DEFAULT)
 */
class SiteAdapterRegistry(
    val userAdapters: List<SiteAdapter> = emptyList(),
    val codeAdapters: List<SiteAdapter> = emptyList(),
    val assetAdapters: List<SiteAdapter> = emptyList(),
    val defaultAdapter: SiteAdapter = ProfileSiteAdapter(SiteProfile.DEFAULT)
) {

    fun forUrl(url: String): SiteAdapter {
        // 1. Perfiles de usuario
        userAdapters.firstOrNull { it.matches(url) }?.let { return it }

        // 2. Adaptadores en código
        codeAdapters.firstOrNull { it.matches(url) }?.let { return it }

        // 3. Perfiles integrados (assets)
        assetAdapters.firstOrNull { it.matches(url) }?.let { return it }

        // 4. Default
        return defaultAdapter
    }

    /**
     * Retorna el [SiteProfile] subyacente para compatibilidad con componentes previos.
     */
    fun profileForUrl(url: String): SiteProfile = forUrl(url).profile

    companion object {
        fun fromProfiles(
            userProfiles: List<SiteProfile> = emptyList(),
            codeAdapters: List<SiteAdapter> = emptyList(),
            assetProfiles: List<SiteProfile> = emptyList(),
            defaultProfile: SiteProfile = SiteProfile.DEFAULT
        ): SiteAdapterRegistry {
            return SiteAdapterRegistry(
                userAdapters = userProfiles.map { ProfileSiteAdapter(it) },
                codeAdapters = codeAdapters,
                assetAdapters = assetProfiles.map { ProfileSiteAdapter(it) },
                defaultAdapter = ProfileSiteAdapter(defaultProfile)
            )
        }
    }
}
