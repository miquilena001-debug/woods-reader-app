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

    fun setFailure(url: String, failure: FetchFailure) {
        responses[url] = failure
    }

    override suspend fun get(url: String, referer: String?): FetchedPage {
        _callCount.incrementAndGet()
        synchronized(_requestedUrls) {
            _requestedUrls.add(url)
        }

        return when (val response = responses[url]) {
            is FetchedPage -> response
            is FetchFailure -> throw response
            else -> throw FetchFailure.NotFound(url)
        }
    }
}
