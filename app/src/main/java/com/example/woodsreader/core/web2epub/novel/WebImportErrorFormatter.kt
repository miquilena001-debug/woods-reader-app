package com.example.woodsreader.core.web2epub.novel

object WebImportErrorFormatter {

    fun format(error: FetchError): String = when (error) {
        is FetchError.ChallengeRequired ->
            "El sitio requiere verificación anti-bot de navegador (Cloudflare)."
        is FetchError.LoginRequired ->
            "Este contenido requiere iniciar sesión en el sitio para ser leído."
        is FetchError.RateLimited -> {
            val sec = error.retryAfterMs?.let { it / 1000 }
            if (sec != null && sec > 0) {
                "Límite de peticiones alcanzado (HTTP 429). Espera $sec segundos antes de reintentar."
            } else {
                "Límite de peticiones alcanzado (HTTP 429). Por favor espera un momento antes de reintentar."
            }
        }
        is FetchError.NeedsJavaScript ->
            "El sitio requiere JavaScript para mostrar el contenido (página dinámica no soportada)."
        is FetchError.NotFound ->
            "Página no encontrada (HTTP 404). Verifica que el enlace sea correcto."
        is FetchError.HttpError ->
            "El servidor web devolvió un error (HTTP ${error.statusCode})."
        is FetchError.NetworkError ->
            "Error de conexión a internet o tiempo de espera agotado."
        is FetchError.TooLarge ->
            "La página web excede el tamaño máximo permitido (5 MB)."
        is FetchError.UnsupportedSite ->
            error.reason
    }

    fun format(failure: FetchFailure): String = when (failure) {
        is FetchFailure.Challenge ->
            "El sitio requiere verificación anti-bot de navegador (Cloudflare)."
        is FetchFailure.LoginRequired ->
            "Este contenido requiere iniciar sesión en el sitio para ser leído."
        is FetchFailure.Blocked -> {
            if (failure.status == 429) {
                val sec = failure.retryAfterMs?.let { it / 1000 }
                if (sec != null && sec > 0) {
                    "Límite de peticiones alcanzado (HTTP 429). Espera $sec segundos antes de reintentar."
                } else {
                    "Límite de peticiones alcanzado (HTTP 429). Por favor espera un momento antes de reintentar."
                }
            } else {
                "El servidor web devolvió un error (HTTP ${failure.status})."
            }
        }
        is FetchFailure.NeedsJavaScript ->
            "El sitio requiere JavaScript para mostrar el contenido (página dinámica no soportada)."
        is FetchFailure.NotFound ->
            "Página no encontrada (HTTP 404). Verifica que el enlace sea correcto."
        is FetchFailure.TooLarge ->
            "La página web excede el tamaño máximo permitido (5 MB)."
        is FetchFailure.Network ->
            "Error de conexión a internet o tiempo de espera agotado."
        is FetchFailure.UnsupportedSite ->
            failure.reason
    }

    fun format(throwable: Throwable): String = when (throwable) {
        is FetchFailure -> format(throwable)
        else -> throwable.message ?: "Ocurrió un error inesperado al procesar la novela."
    }
}
