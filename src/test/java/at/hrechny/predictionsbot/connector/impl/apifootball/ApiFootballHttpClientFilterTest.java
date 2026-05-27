package at.hrechny.predictionsbot.connector.impl.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.connector.ApiConnector;
import at.hrechny.predictionsbot.exception.ApiConnectorException;
import at.hrechny.predictionsbot.service.connector.ApiConnectorAuditService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiFootballHttpClientFilterTest {

  private static final String API_KEY = "secret-api-key";
  private static final String API_FOOTBALL_URL = "https://api-football-v1.p.rapidapi.com";

  @Mock
  private ApiConnectorAuditService auditService;

  @Test
  void tagsApiFootballRequestsWithRapidApiHeadersAndQuotaAttempt() {
    var filter = filterAt("2026-05-26T23:30:00Z", 100, 0);
    var request = taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures?league=39&season=2025");

    filter.beforeRequest(request);

    verify(auditService).countRequestsSince(ApiFootballConnector.NAME, Instant.parse("2026-05-26T22:00:00Z"));
    assertThat(request.getHeaders().get("X-RapidAPI-Key")).isEqualTo(API_KEY);
    assertThat(request.getHeaders().get("X-RapidAPI-Host")).isEqualTo("api-football-v1.p.rapidapi.com");
  }

  @Test
  void ignoresRequestsForOtherConnectors() {
    var filter = filterAt("2026-05-26T23:30:00Z", 100, 0);
    var request = HttpRequest.GET("https://example.test/v3/fixtures");

    filter.beforeRequest(request);
    filter.afterResponse(request, HttpResponse.ok());
    filter.afterFailure(request, new IllegalStateException("failure"));

    assertThat(request.getHeaders().contains("X-RapidAPI-Key")).isFalse();
    assertThat(request.getHeaders().contains("X-RapidAPI-Host")).isFalse();
  }

  @Test
  void startupHydrationAtDailyLimitBlocksNextRequest() {
    var filter = filterAt("2026-05-26T21:30:00Z", 100, 100);
    var request = taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures");

    assertThatThrownBy(() -> filter.beforeRequest(request))
        .isInstanceOfSatisfying(ApiConnectorException.class, exception ->
            assertThat(exception.getReason()).isEqualTo(ApiConnectorException.Reason.QUOTA_EXCEEDED));

    verify(auditService).countRequestsSince(ApiFootballConnector.NAME, Instant.parse("2026-05-25T22:00:00Z"));
  }

  @Test
  void startupHydrationBelowDailyLimitAllowsRequest() {
    var filter = filterAt("2026-05-26T23:30:00Z", 100, 99);

    assertThatCode(() -> filter.beforeRequest(taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures")))
        .doesNotThrowAnyException();
    verify(auditService).countRequestsSince(ApiFootballConnector.NAME, Instant.parse("2026-05-26T22:00:00Z"));
  }

  @Test
  void localCountIsMoreRestrictiveThanProviderHeaders() {
    var filter = filterAt("2026-05-26T23:30:00Z", 1, 0);
    var request = taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures");

    filter.beforeRequest(request);
    filter.afterResponse(request, HttpResponse.ok().header("x-ratelimit-requests-remaining", "100"));

    assertThatThrownBy(() -> filter.beforeRequest(taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures")))
        .isInstanceOfSatisfying(ApiConnectorException.class, exception ->
            assertThat(exception.getReason()).isEqualTo(ApiConnectorException.Reason.QUOTA_EXCEEDED));
  }

  @Test
  void providerRemainingZeroBlocksNextRequest() {
    var filter = filterAt("2026-05-26T23:30:00Z", 100, 0);
    var request = taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures");
    var response = HttpResponse.ok()
        .header("x-ratelimit-requests-remaining", "0")
        .header("x-ratelimit-requests-reset", "60")
        .header("authorization", "secret");

    filter.afterResponse(request, response);

    assertThatThrownBy(() -> filter.beforeRequest(taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures")))
        .isInstanceOfSatisfying(ApiConnectorException.class, exception ->
            assertThat(exception.getReason()).isEqualTo(ApiConnectorException.Reason.QUOTA_EXCEEDED));
  }

  @Test
  void retryAfterBlocksNextRequest() {
    var filter = filterAt("2026-05-26T23:30:00Z", 100, 0);
    var request = taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures");
    var exception = new HttpClientResponseException(
        "Too many requests",
        HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("retry-after", "60"));

    filter.afterFailure(request, exception);

    assertThatThrownBy(() -> filter.beforeRequest(taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures")))
        .isInstanceOfSatisfying(ApiConnectorException.class, exceptionThrown ->
            assertThat(exceptionThrown.getReason()).isEqualTo(ApiConnectorException.Reason.TOO_OFTEN_REQUESTS));
  }

  @Test
  void malformedHeadersDoNotCrashFilter() {
    var filter = filterAt("2026-05-26T23:30:00Z", 100, 0);
    var request = taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures");
    var response = HttpResponse.ok()
        .header("x-ratelimit-requests-remaining", "invalid")
        .header("x-ratelimit-requests-reset", "invalid");

    filter.afterResponse(request, response);

    assertThatCode(() -> filter.beforeRequest(taggedRequest("https://api-football-v1.p.rapidapi.com/v3/fixtures")))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsApiFootballUrlWithoutHost() {
    assertThatThrownBy(() -> new ApiFootballHttpClientFilter(
        auditService,
        Clock.systemUTC(),
        API_KEY,
        "not-a-url",
        100,
        "22:00"))
        .isInstanceOfSatisfying(ApiConnectorException.class, exception ->
            assertThat(exception.getReason()).isEqualTo(ApiConnectorException.Reason.REQUEST_ERROR));
  }

  private ApiFootballHttpClientFilter filterAt(String now, int maxAttempts, int hydratedCount) {
    when(auditService.countRequestsSince(eq(ApiFootballConnector.NAME), any(Instant.class))).thenReturn(hydratedCount);
    return new ApiFootballHttpClientFilter(
        auditService,
        Clock.fixed(Instant.parse(now), ZoneOffset.UTC),
        API_KEY,
        API_FOOTBALL_URL,
        maxAttempts,
        "22:00");
  }

  private MutableHttpRequest<?> taggedRequest(String uri) {
    var request = HttpRequest.GET(uri);
    request.setAttribute(ApiConnector.CONNECTOR_NAME_ATTRIBUTE, ApiFootballConnector.NAME);
    return request;
  }
}
