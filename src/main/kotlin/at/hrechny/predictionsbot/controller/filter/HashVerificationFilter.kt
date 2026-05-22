package at.hrechny.predictionsbot.controller.filter

import at.hrechny.predictionsbot.util.HashUtils
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

@Singleton
@Filter("/webapp/**")
class HashVerificationFilter(
    private val hashUtils: HashUtils,
) : HttpServerFilter {
    override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        val uriParts = request.path.split("/")
        val hash = uriParts[2]
        val userId = uriParts[4]

        if (hashUtils.getHash(userId) != hash) {
            return Publishers.just(HttpResponse.badRequest("User not found"))
        }

        return chain.proceed(request)
    }
}
