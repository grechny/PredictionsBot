package at.hrechny.predictionsbot.connector.impl.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.connector.impl.apifootball.model.Fixture;
import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixtureData;
import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixturesResponse;
import at.hrechny.predictionsbot.exception.ApiConnectorException;
import at.hrechny.predictionsbot.service.connector.ApiConnectorAuditService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiFootballClientTest {

  private static final String API_KEY = "secret-api-key";
  private static final String RAPID_API_HOST = "api-football-v1.p.rapidapi.com";

  @Mock
  private ApiFootballHttpClient httpClient;

  @Mock
  private ApiConnectorAuditService auditService;

  private ApiFootballClient client;

  @BeforeEach
  void setUp() {
    when(auditService.countRequestsSince(eq("api-football"), org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(0);
    client = clientWithDailyLimit(100);
    org.mockito.Mockito.clearInvocations(auditService);
  }

  @Test
  void getFixturesReturnsSuccessfulResponse() {
    when(httpClient.getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025"))
        .thenReturn(ok(fixtures())
            .header("x-ratelimit-requests-remaining", "98"));

    assertThat(client.getFixtures(39L, "2025")).isEmpty();
  }

  @Test
  void getFixturesIncludesSafeRateLimitHeadersWhenConnectorReturnsNonSuccessResponse() {
    when(httpClient.getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025"))
        .thenReturn(response(HttpStatus.TOO_MANY_REQUESTS, fixtures())
            .header("x-ratelimit-requests-remaining", "98")
            .header("x-ratelimit-requests-limit", "100"));

    assertThatThrownBy(() -> client.getFixtures(39L, "2025"))
        .isInstanceOf(ApiConnectorException.class)
        .hasMessageContaining("REQUEST_ERROR")
        .hasMessageContaining("HTTP 429")
        .hasMessageContaining("x-ratelimit-requests-remaining=98")
        .hasMessageNotContaining(API_KEY);
  }

  @Test
  void getFixturesExtractsNonSuccessResponseFromMicronautException() {
    var exception = new HttpClientResponseException(
        "Too many requests",
        response(HttpStatus.TOO_MANY_REQUESTS, fixtures())
            .header("retry-after", "60"));
    when(httpClient.getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025")).thenThrow(exception);

    assertThatThrownBy(() -> client.getFixtures(39L, "2025"))
        .isInstanceOfSatisfying(ApiConnectorException.class, connectorException -> {
          assertThat(connectorException.getReason()).isEqualTo(ApiConnectorException.Reason.TOO_OFTEN_REQUESTS);
          assertThat(connectorException.getMessage()).contains("retry-after=60");
        });
  }

  @Test
  void requestFailureDoesNotLeakApiKey() {
    when(httpClient.getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025"))
        .thenThrow(new RuntimeException("failed with " + API_KEY));

    assertThatThrownBy(() -> client.getFixtures(39L, "2025"))
        .isInstanceOf(ApiConnectorException.class)
        .hasMessageContaining("REQUEST_ERROR")
        .hasMessageNotContaining(API_KEY);
  }

  @Test
  void parserRejectsApiFootballErrors() {
    var response = fixtures();
    response.setErrors(List.of("bad league"));
    when(httpClient.getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025"))
        .thenReturn(ok(response));

    assertThatThrownBy(() -> client.getFixtures(39L, "2025"))
        .isInstanceOf(ApiConnectorException.class)
        .hasMessageContaining("INVALID_RESPONSE")
        .hasMessageContaining("bad league");
  }

  @Test
  void parserRejectsNullResponseField() {
    when(httpClient.getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025"))
        .thenReturn(ok(new FixturesResponse()));

    assertThatThrownBy(() -> client.getFixtures(39L, "2025"))
        .isInstanceOf(ApiConnectorException.class)
        .hasMessageContaining("INVALID_RESPONSE")
        .hasMessageContaining("null response field");
  }

  @Test
  void getFixturesByIdsReturnsEmptyWithoutCallingProviderForEmptyInput() {
    assertThat(client.getFixtures(List.of())).isEmpty();

    verifyNoInteractions(httpClient, auditService);
  }

  @Test
  void getFixturesByIdsBatchesMoreThanTwentyFixtureIds() {
    var fixtureIds = LongStream.rangeClosed(1, 41).boxed().toList();
    when(httpClient.getFixturesByIds(API_KEY, RAPID_API_HOST, fixtureIdsString(1, 20)))
        .thenReturn(ok(fixtures(fixture(1))));
    when(httpClient.getFixturesByIds(API_KEY, RAPID_API_HOST, fixtureIdsString(21, 40)))
        .thenReturn(ok(fixtures(fixture(21))));
    when(httpClient.getFixturesByIds(API_KEY, RAPID_API_HOST, "41"))
        .thenReturn(ok(fixtures(fixture(41))));

    var fixtures = client.getFixtures(fixtureIds);

    assertThat(fixtures).hasSize(3);
    assertThat(fixtures)
        .extracting(fixture -> fixture.getFixture().getId())
        .containsExactly(1L, 21L, 41L);
    verify(httpClient).getFixturesByIds(API_KEY, RAPID_API_HOST, fixtureIdsString(1, 20));
    verify(httpClient).getFixturesByIds(API_KEY, RAPID_API_HOST, fixtureIdsString(21, 40));
    verify(httpClient).getFixturesByIds(API_KEY, RAPID_API_HOST, "41");
  }

  @Test
  void getFixturesByIdsKeepsCompletedBatchesWhenLaterQuotaBatchStops() {
    var fixtureIds = LongStream.rangeClosed(1, 41).boxed().toList();
    when(httpClient.getFixturesByIds(API_KEY, RAPID_API_HOST, fixtureIdsString(1, 20)))
        .thenReturn(ok(fixtures(fixture(1))));
    when(httpClient.getFixturesByIds(API_KEY, RAPID_API_HOST, fixtureIdsString(21, 40)))
        .thenReturn(response(HttpStatus.TOO_MANY_REQUESTS, fixtures())
            .header("x-ratelimit-requests-remaining", "0")
            .header("x-ratelimit-requests-reset", "60"));

    var fixtures = client.getFixtures(fixtureIds);

    assertThat(fixtures).hasSize(1);
    assertThat(fixtures.get(0).getFixture().getId()).isEqualTo(1L);
    verify(httpClient, never()).getFixturesByIds(API_KEY, RAPID_API_HOST, "41");
  }

  @Test
  void quotaBlockedRequestDoesNotCallProvider() {
    when(auditService.countRequestsSince(eq("api-football"), org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(100);
    var quotaBlockedClient = clientWithDailyLimit(100);
    org.mockito.Mockito.clearInvocations(auditService);

    assertThatThrownBy(() -> quotaBlockedClient.getFixtures(39L, "2025"))
        .isInstanceOfSatisfying(ApiConnectorException.class, exception ->
            assertThat(exception.getReason()).isEqualTo(ApiConnectorException.Reason.QUOTA_EXCEEDED));

    verifyNoInteractions(httpClient);
    verifyNoInteractions(auditService);
  }

  @Test
  void headerDrivenExhaustionBlocksFollowingRequest() {
    when(httpClient.getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025"))
        .thenReturn(ok(fixtures())
            .header("x-ratelimit-requests-remaining", "0")
            .header("x-ratelimit-requests-reset", "60"));

    assertThat(client.getFixtures(39L, "2025")).isEmpty();

    assertThatThrownBy(() -> client.getFixtures(39L, "2025"))
        .isInstanceOfSatisfying(ApiConnectorException.class, exception ->
            assertThat(exception.getReason()).isEqualTo(ApiConnectorException.Reason.QUOTA_EXCEEDED));
    verify(httpClient).getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025");
  }

  private ApiFootballClient clientWithDailyLimit(int dailyLimit) {
    return new ApiFootballClient(
        httpClient,
        new ApiFootballResponseParser(API_KEY),
        new ApiFootballQuotaGuard(
            auditService,
            Clock.fixed(Instant.parse("2026-05-26T23:30:00Z"), ZoneOffset.UTC),
            dailyLimit,
            "22:00"),
        API_KEY,
        RAPID_API_HOST,
        20);
  }

  private MutableHttpResponse<FixturesResponse> ok(FixturesResponse body) {
    return HttpResponse.ok(body);
  }

  private MutableHttpResponse<FixturesResponse> response(HttpStatus status, FixturesResponse body) {
    return HttpResponse.<FixturesResponse>status(status).body(body);
  }

  private FixturesResponse fixtures(Fixture... fixtures) {
    var response = new FixturesResponse();
    response.setResponse(Arrays.asList(fixtures));
    return response;
  }

  private Fixture fixture(long fixtureId) {
    var fixture = new Fixture();
    var fixtureData = new FixtureData();
    fixtureData.setId(fixtureId);
    fixture.setFixture(fixtureData);
    return fixture;
  }

  private String fixtureIdsString(long from, long to) {
    return LongStream.rangeClosed(from, to)
        .mapToObj(Long::toString)
        .reduce((left, right) -> left + "-" + right)
        .orElseThrow();
  }
}
