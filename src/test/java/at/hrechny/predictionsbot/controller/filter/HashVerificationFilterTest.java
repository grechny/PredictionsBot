package at.hrechny.predictionsbot.controller.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.util.HashUtils;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.filter.ServerFilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

@ExtendWith(MockitoExtension.class)
class HashVerificationFilterTest {

  private static final String USER_ID = "42";
  private static final String HASH = "valid-hash";

  @Mock
  private HashUtils hashUtils;

  @Mock
  private ServerFilterChain filterChain;

  private HashVerificationFilter filter;

  @BeforeEach
  void setUp() {
    filter = new HashVerificationFilter(hashUtils);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/webapp/%s/users/%s/predictions",
      "/webapp/%s/users/%s/results",
      "/webapp/%s/users/%s/leagues",
      "/webapp/%s/users/%s/leagues/747654fa-25d6-4e27-beb2-331ec865a803"
  })
  void doFilterContinuesWhenHashMatchesUserIdFromWebappPath(String uriTemplate) {
    var request = HttpRequest.GET(uriTemplate.formatted(HASH, USER_ID));
    var expectedResponse = HttpResponse.ok();
    when(hashUtils.getHash(USER_ID)).thenReturn(HASH);
    when(filterChain.proceed(request)).thenReturn(Publishers.just(expectedResponse));

    var response = response(filter.doFilter(request, filterChain));

    assertThat(response).isSameAs(expectedResponse);
    verify(filterChain).proceed(request);
  }

  @Test
  void doFilterRejectsRequestWhenHashDoesNotMatchUserIdFromWebappPath() {
    var request = HttpRequest.GET("/webapp/wrong-hash/users/%s/results".formatted(USER_ID));
    when(hashUtils.getHash(USER_ID)).thenReturn(HASH);

    var response = response(filter.doFilter(request, filterChain));

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    assertThat(response.body()).isEqualTo("User not found");
    verify(filterChain, never()).proceed(request);
  }

  @Test
  void doFilterRejectsRequestWhenHashBelongsToDifferentPathUserId() {
    var otherUserId = "43";
    var request = HttpRequest.GET("/webapp/%s/users/%s/predictions".formatted(HASH, otherUserId));
    when(hashUtils.getHash(otherUserId)).thenReturn("other-user-hash");

    var response = response(filter.doFilter(request, filterChain));

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    assertThat(response.body()).isEqualTo("User not found");
    verify(hashUtils).getHash(otherUserId);
    verify(filterChain, never()).proceed(request);
  }

  private MutableHttpResponse<?> response(Publisher<MutableHttpResponse<?>> publisher) {
    var response = new AtomicReference<MutableHttpResponse<?>>();
    publisher.subscribe(new Subscriber<>() {
      @Override
      public void onSubscribe(Subscription subscription) {
        subscription.request(1);
      }

      @Override
      public void onNext(MutableHttpResponse<?> mutableHttpResponse) {
        response.set(mutableHttpResponse);
      }

      @Override
      public void onError(Throwable throwable) {
        throw new AssertionError(throwable);
      }

      @Override
      public void onComplete() {
      }
    });
    return response.get();
  }
}
