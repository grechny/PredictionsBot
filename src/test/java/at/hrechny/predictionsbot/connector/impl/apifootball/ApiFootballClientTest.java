package at.hrechny.predictionsbot.connector.impl.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.connector.impl.apifootball.model.Fixture;
import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixtureData;
import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixturesResponse;
import at.hrechny.predictionsbot.exception.ApiConnectorException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
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

  @Mock
  private ApiFootballHttpClient httpClient;

  private ApiFootballClient client;

  @BeforeEach
  void setUp() {
    client = clientWithBatchSize(20);
  }

  @Test
  void getFixturesReturnsSuccessfulResponse() {
    when(httpClient.getSeasonFixtures(39L, "2025"))
        .thenReturn(ok(fixtures())
            .header("x-ratelimit-requests-remaining", "98"));

    assertThat(client.getFixtures(39L, "2025")).isEmpty();
  }

  @Test
  void getFixturesIncludesSafeRateLimitHeadersWhenConnectorReturnsNonSuccessResponse() {
    when(httpClient.getSeasonFixtures(39L, "2025"))
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
    when(httpClient.getSeasonFixtures(39L, "2025")).thenThrow(exception);

    assertThatThrownBy(() -> client.getFixtures(39L, "2025"))
        .isInstanceOfSatisfying(ApiConnectorException.class, connectorException -> {
          assertThat(connectorException.getReason()).isEqualTo(ApiConnectorException.Reason.TOO_OFTEN_REQUESTS);
          assertThat(connectorException.getMessage()).contains("retry-after=60");
        });
  }

  @Test
  void requestFailureDoesNotLeakApiKey() {
    when(httpClient.getSeasonFixtures(39L, "2025"))
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
    when(httpClient.getSeasonFixtures(39L, "2025"))
        .thenReturn(ok(response));

    assertThatThrownBy(() -> client.getFixtures(39L, "2025"))
        .isInstanceOf(ApiConnectorException.class)
        .hasMessageContaining("INVALID_RESPONSE")
        .hasMessageContaining("bad league");
  }

  @Test
  void parserRejectsNullResponseField() {
    when(httpClient.getSeasonFixtures(39L, "2025"))
        .thenReturn(ok(new FixturesResponse()));

    assertThatThrownBy(() -> client.getFixtures(39L, "2025"))
        .isInstanceOf(ApiConnectorException.class)
        .hasMessageContaining("INVALID_RESPONSE")
        .hasMessageContaining("null response field");
  }

  @Test
  void fixturesResponseIgnoresApiFootballMetadataFields() throws Exception {
    var objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());

    var response = objectMapper.readValue("""
        {
          "get": "fixtures",
          "parameters": {
            "league": "39",
            "season": "2025"
          },
          "errors": [],
          "results": 1,
          "paging": {
            "current": 1,
            "total": 1
          },
          "response": [
            {
              "fixture": {
                "id": 1379342,
                "date": "2026-05-28T19:00:00+00:00",
                "timezone": "UTC",
                "venue": {
                  "id": 1,
                  "name": "Example Stadium"
                }
              },
              "league": {
                "id": 39,
                "name": "Premier League",
                "country": "England",
                "round": "Regular Season - 1"
              },
              "teams": {
                "home": {
                  "id": 33,
                  "name": "Manchester United",
                  "logo": "home.png",
                  "winner": null
                },
                "away": {
                  "id": 40,
                  "name": "Liverpool",
                  "logo": "away.png",
                  "winner": null
                }
              },
              "goals": {
                "home": null,
                "away": null
              },
              "score": {
                "halftime": {
                  "home": null,
                  "away": null
                },
                "fulltime": {
                  "home": null,
                  "away": null
                }
              }
            }
          ]
        }
        """, FixturesResponse.class);

    assertThat(response.getResponse()).hasSize(1);
    assertThat(response.getResponse().get(0).getFixture().getId()).isEqualTo(1379342L);
    assertThat(response.getResponse().get(0).getLeague().getRound()).isEqualTo("Regular Season - 1");
    assertThat(response.getResponse().get(0).getTeams().getHome().getName()).isEqualTo("Manchester United");
  }

  @Test
  void getFixturesByIdsReturnsEmptyWithoutCallingProviderForEmptyInput() {
    assertThat(client.getFixtures(List.of())).isEmpty();

    verifyNoInteractions(httpClient);
  }

  @Test
  void getFixturesByIdsBatchesMoreThanTwentyFixtureIds() {
    var fixtureIds = LongStream.rangeClosed(1, 41).boxed().toList();
    when(httpClient.getFixturesByIds(fixtureIdsString(1, 20)))
        .thenReturn(ok(fixtures(fixture(1))));
    when(httpClient.getFixturesByIds(fixtureIdsString(21, 40)))
        .thenReturn(ok(fixtures(fixture(21))));
    when(httpClient.getFixturesByIds("41"))
        .thenReturn(ok(fixtures(fixture(41))));

    var fixtures = client.getFixtures(fixtureIds);

    assertThat(fixtures).hasSize(3);
    assertThat(fixtures)
        .extracting(fixture -> fixture.getFixture().getId())
        .containsExactly(1L, 21L, 41L);
    verify(httpClient).getFixturesByIds(fixtureIdsString(1, 20));
    verify(httpClient).getFixturesByIds(fixtureIdsString(21, 40));
    verify(httpClient).getFixturesByIds("41");
  }

  @Test
  void getFixturesByIdsKeepsCompletedBatchesWhenLaterQuotaBatchStops() {
    var fixtureIds = LongStream.rangeClosed(1, 41).boxed().toList();
    when(httpClient.getFixturesByIds(fixtureIdsString(1, 20)))
        .thenReturn(ok(fixtures(fixture(1))));
    when(httpClient.getFixturesByIds(fixtureIdsString(21, 40)))
        .thenReturn(response(HttpStatus.TOO_MANY_REQUESTS, fixtures())
            .header("x-ratelimit-requests-remaining", "0")
            .header("x-ratelimit-requests-reset", "60"));

    var fixtures = client.getFixtures(fixtureIds);

    assertThat(fixtures).hasSize(1);
    assertThat(fixtures.get(0).getFixture().getId()).isEqualTo(1L);
    verify(httpClient, never()).getFixturesByIds("41");
  }

  private ApiFootballClient clientWithBatchSize(int fixtureBatchSize) {
    return new ApiFootballClient(
        httpClient,
        new ApiFootballResponseParser(API_KEY),
        fixtureBatchSize);
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
