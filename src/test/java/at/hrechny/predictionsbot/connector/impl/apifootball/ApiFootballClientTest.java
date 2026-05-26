package at.hrechny.predictionsbot.connector.impl.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.connector.ApiConnectorRequestAuditService;
import at.hrechny.predictionsbot.exception.ApiConnectorException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import java.util.List;
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
  private ApiConnectorRequestAuditService auditService;

  private ApiFootballClient client;

  @BeforeEach
  void setUp() {
    client = new ApiFootballClient(
        httpClient,
        new ApiFootballResponseParser(API_KEY),
        auditService,
        API_KEY);
  }

  @Test
  void getFixturesRecordsSuccessfulAudit() {
    when(httpClient.getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025"))
        .thenReturn(ok("{\"errors\":[],\"response\":[]}")
            .header("x-ratelimit-requests-remaining", "98"));

    assertThat(client.getFixtures(39L, "2025")).isEmpty();

    verify(auditService).recordRequest(
        eq("api-football"),
        eq("/fixtures?league=39&season=2025"),
        eq(true),
        isNull(),
        contains("x-ratelimit-requests-remaining=98"));
  }

  @Test
  void getFixturesIncludesSafeRateLimitHeadersWhenConnectorReturnsNonSuccessResponse() {
    when(httpClient.getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025"))
        .thenReturn(response(HttpStatus.TOO_MANY_REQUESTS, "{\"message\":\"too many secret-api-key requests\"}")
            .header("x-ratelimit-requests-remaining", "98")
            .header("x-ratelimit-requests-limit", "100"));

    assertThatThrownBy(() -> client.getFixtures(39L, "2025"))
        .isInstanceOf(ApiConnectorException.class)
        .hasMessageContaining("REQUEST_ERROR")
        .hasMessageContaining("HTTP 429")
        .hasMessageContaining("x-ratelimit-requests-remaining=98")
        .hasMessageNotContaining(API_KEY);

    verify(auditService).recordRequest(
        eq("api-football"),
        eq("/fixtures?league=39&season=2025"),
        eq(false),
        contains("HTTP 429"),
        contains("x-ratelimit-requests-remaining=98"));
  }

  @Test
  void getFixturesExtractsNonSuccessResponseFromMicronautException() {
    var exception = new HttpClientResponseException(
        "Too many requests",
        response(HttpStatus.TOO_MANY_REQUESTS, "{\"message\":\"too many requests\"}")
            .header("retry-after", "60"));
    when(httpClient.getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025")).thenThrow(exception);

    assertThatThrownBy(() -> client.getFixtures(39L, "2025"))
        .isInstanceOfSatisfying(ApiConnectorException.class, connectorException -> {
          assertThat(connectorException.getReason()).isEqualTo(ApiConnectorException.Reason.TOO_OFTEN_REQUESTS);
          assertThat(connectorException.getMessage()).contains("retry-after=60");
        });

    verify(auditService).recordRequest(
        eq("api-football"),
        eq("/fixtures?league=39&season=2025"),
        eq(false),
        contains("HTTP 429"),
        contains("retry-after=60"));
  }

  @Test
  void parserRejectsInvalidJsonWithoutLeakingApiKey() {
    when(httpClient.getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025"))
        .thenReturn(ok("{\"broken\":\"secret-api-key\""));

    assertThatThrownBy(() -> client.getFixtures(39L, "2025"))
        .isInstanceOf(ApiConnectorException.class)
        .hasMessageContaining("INVALID_RESPONSE")
        .hasMessageNotContaining(API_KEY);

    verify(auditService).recordRequest(
        eq("api-football"),
        eq("/fixtures?league=39&season=2025"),
        eq(false),
        contains("Failed to parse API-Football response"),
        isNull());
  }

  @Test
  void parserRejectsApiFootballErrors() {
    when(httpClient.getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025"))
        .thenReturn(ok("{\"errors\":[\"bad league\"],\"response\":[]}"));

    assertThatThrownBy(() -> client.getFixtures(39L, "2025"))
        .isInstanceOf(ApiConnectorException.class)
        .hasMessageContaining("INVALID_RESPONSE")
        .hasMessageContaining("bad league");
  }

  @Test
  void parserRejectsNullResponseField() {
    when(httpClient.getSeasonFixtures(API_KEY, RAPID_API_HOST, 39L, "2025"))
        .thenReturn(ok("{\"errors\":[]}"));

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

  private MutableHttpResponse<String> ok(String body) {
    return HttpResponse.ok(body);
  }

  private MutableHttpResponse<String> response(HttpStatus status, String body) {
    return HttpResponse.<String>status(status).body(body);
  }
}
