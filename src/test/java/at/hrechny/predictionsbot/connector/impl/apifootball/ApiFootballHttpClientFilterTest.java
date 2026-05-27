package at.hrechny.predictionsbot.connector.impl.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import at.hrechny.predictionsbot.connector.ApiConnector;
import at.hrechny.predictionsbot.exception.ApiConnectorException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiFootballHttpClientFilterTest {

  private static final String API_KEY = "secret-api-key";
  private static final String API_FOOTBALL_URL = "https://api-football-v1.p.rapidapi.com";

  @Mock
  private ApiFootballQuotaGuard quotaGuard;

  @Test
  void tagsApiFootballRequestsWithRapidApiHeadersAndQuotaAttempt() {
    var filter = new ApiFootballHttpClientFilter(quotaGuard, API_KEY, API_FOOTBALL_URL);
    var request = taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures?league=39&season=2025");

    filter.beforeRequest(request);

    verify(quotaGuard).checkRequestAllowed("https://api-football-v1.p.rapidapi.com/v3/fixtures?league=39&season=2025");
    verify(quotaGuard).markRequestAttempted();
    assertThat(request.getHeaders().get("X-RapidAPI-Key")).isEqualTo(API_KEY);
    assertThat(request.getHeaders().get("X-RapidAPI-Host")).isEqualTo("api-football-v1.p.rapidapi.com");
  }

  @Test
  void ignoresRequestsForOtherConnectors() {
    var filter = new ApiFootballHttpClientFilter(quotaGuard, API_KEY, API_FOOTBALL_URL);
    var request = HttpRequest.GET("https://example.test/v3/fixtures");

    filter.beforeRequest(request);
    filter.afterResponse(request, HttpResponse.ok());
    filter.afterFailure(request, new IllegalStateException("failure"));

    verifyNoInteractions(quotaGuard);
    assertThat(request.getHeaders().contains("X-RapidAPI-Key")).isFalse();
    assertThat(request.getHeaders().contains("X-RapidAPI-Host")).isFalse();
  }

  @Test
  void updatesQuotaFromSafeResponseHeaders() {
    var filter = new ApiFootballHttpClientFilter(quotaGuard, API_KEY, API_FOOTBALL_URL);
    var request = taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures");
    var response = HttpResponse.ok()
        .header("x-ratelimit-requests-remaining", "98")
        .header("authorization", "secret");

    filter.afterResponse(request, response);

    verify(quotaGuard).updateFromHeaders(java.util.Map.of("x-ratelimit-requests-remaining", "98"));
  }

  @Test
  void updatesQuotaFromClientResponseExceptionHeaders() {
    var filter = new ApiFootballHttpClientFilter(quotaGuard, API_KEY, API_FOOTBALL_URL);
    var request = taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures");
    var exception = new HttpClientResponseException(
        "Too many requests",
        HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("retry-after", "60"));

    filter.afterFailure(request, exception);

    verify(quotaGuard).updateFromHeaders(java.util.Map.of("retry-after", "60"));
  }

  @Test
  void rejectsApiFootballUrlWithoutHost() {
    assertThatThrownBy(() -> new ApiFootballHttpClientFilter(quotaGuard, API_KEY, "not-a-url"))
        .isInstanceOfSatisfying(ApiConnectorException.class, exception ->
            assertThat(exception.getReason()).isEqualTo(ApiConnectorException.Reason.REQUEST_ERROR));
  }

  private MutableHttpRequest<?> taggedRequest(String uri) {
    var request = HttpRequest.GET(uri);
    request.setAttribute(ApiConnector.CONNECTOR_NAME_ATTRIBUTE, ApiFootballConnector.connectorName());
    return request;
  }
}
