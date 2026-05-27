package at.hrechny.predictionsbot.connector.impl.apifootball

import io.micronaut.http.HttpResponse

internal object ApiFootballResponseHeaders {
    fun safeRateLimitHeaders(httpResponse: HttpResponse<*>): Map<String, String> =
        httpResponse.headers.names()
            .map { name -> name.lowercase() to (httpResponse.headers.get(name) ?: "") }
            .filter { (name, _) -> SAFE_RESPONSE_HEADERS.contains(name) || name.startsWith("x-ratelimit-") }
            .toMap()

    private const val RETRY_AFTER_HEADER = "retry-after"
    private const val REQUESTS_REMAINING_HEADER = "x-ratelimit-requests-remaining"
    private const val REQUESTS_RESET_HEADER = "x-ratelimit-requests-reset"
    private val SAFE_RESPONSE_HEADERS = setOf(
        RETRY_AFTER_HEADER,
        "x-ratelimit-requests-limit",
        REQUESTS_REMAINING_HEADER,
        REQUESTS_RESET_HEADER,
        "x-ratelimit-requests-used",
        "x-ratelimit-subscription-limit",
        "x-ratelimit-subscription-remaining",
    )
}
