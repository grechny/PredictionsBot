package at.hrechny.predictionsbot.controller.filter;

import at.hrechny.predictionsbot.util.HashUtils;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

@Singleton
@Filter("/webapp/**")
public class HashVerificationFilter implements HttpServerFilter {

  private final HashUtils hashUtils;

  public HashVerificationFilter(HashUtils hashUtils) {
    this.hashUtils = hashUtils;
  }

  @Override
  public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
    var uri = request.getPath();
    var uriParts = uri.split("/");

    var hash = uriParts[2];
    var userId = uriParts[4];

    if (!hashUtils.getHash(userId).equals(hash)) {
      return Publishers.just(HttpResponse.badRequest("User not found"));
    }

    return chain.proceed(request);
  }
}
