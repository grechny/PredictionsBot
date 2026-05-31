package at.hrechny.predictionsbot.connector.impl.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import at.hrechny.predictionsbot.connector.ApiConnector;
import at.hrechny.predictionsbot.connector.ConnectorHttpClientFactory;
import org.junit.jupiter.api.Test;

class ApiFootballHttpClientTest {

  @Test
  void buildsTaggedApiFootballRequest() {
    var client = testClient();

    var request = client.buildRequest("/fixtures", java.util.Map.of("league", 39L, "season", "2025"));

    assertThat(request.getUri().toString()).contains("/fixtures");
    assertThat(request.getUri().toString()).contains("league=39");
    assertThat(request.getUri().toString()).contains("season=2025");
    assertThat(request.getAttribute(ApiConnector.CONNECTOR_NAME_ATTRIBUTE, String.class))
        .contains(ApiFootballConnector.NAME);
  }

  private ApiFootballHttpClient testClient() {
    return new ApiFootballHttpClient(
        "https://api-football-v1.p.rapidapi.com/v3",
        mock(ConnectorHttpClientFactory.class));
  }
}
