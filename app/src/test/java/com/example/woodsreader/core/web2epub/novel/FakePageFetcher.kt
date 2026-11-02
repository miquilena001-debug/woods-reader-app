package com.example.woodsreader.core.web2epub.novel

import java.util.concurrent.atomic.AtomicInteger

class FakePageFetcher(
    private val responses: MutableMap<String, Any> = mutableMapOf()
) : PageFetcher {

    private val _callCount = AtomicInteger(0)
    val callCount: Int get() = _callCount.get()

    private val _requestedUrls = mutableListOf<String>()
    val requestedUrls: List<String> get() = synchronized(_requestedUrls) { _requestedUrls.toList() }

    fun setResponse(url: String, page: FetchedPage) {
        responses[url] = page
    }

    fun setResult(url: String, result: FetchResult) {
        responses[url] = result
    }

    fun setFailure(url: String, failure: FetchFailure) {
        responses[url] = failure
    }

    override suspend fun fetch(url: String, referer: String?): FetchResult {
        _callCount.incrementAndGet()
        synchronized(_requestedUrls) {
            _requestedUrls.add(url)
        }

        return when (val response = responses[url]) {
            is FetchResult -> response
            is FetchedPage -> FetchResult.Success(response)
            is FetchFailure.Challenge -> FetchResult.Failure(FetchError.ChallengeRequired("Cloudflare", response.message ?: "Challenge"))
            is FetchFailure.Blocked -> FetchResult.Failure(FetchError.RateLimited(response.retryAfterMs))
            is FetchFailure.NotFound -> FetchResult.Failure(FetchError.NotFound(response.url))
            is FetchFailure.TooLarge -> FetchResult.Failure(FetchError.TooLarge(response.url))
            is FetchFailure.Network -> FetchResult.Failure(FetchError.NetworkError(response.message ?: "Network", response.cause))
            is FetchFailure.LoginRequired -> FetchResult.Failure(FetchError.LoginRequired(response.loginUrl))
            is FetchFailure.NeedsJavaScript -> FetchResult.Failure(FetchError.NeedsJavaScript(response.reason))
            is FetchFailure.UnsupportedSite -> FetchResult.Failure(FetchError.UnsupportedSite(response.siteName, response.reason))
            is FetchFailure -> FetchResult.Failure(FetchError.HttpError(500, response.message ?: "Failure"))
            else -> FetchResult.Failure(FetchError.NotFound(url))
        }
    }

    override suspend fun get(url: String, referer: String?): FetchedPage {
        val configured = responses[url]
        if (configured is FetchFailure) {
            _callCount.incrementAndGet()
            synchronized(_requestedUrls) {
                _requestedUrls.add(url)
            }
            throw configured
        }
        return fetch(url, referer).toFetchedPageOrThrow()
    }
}
