package at.hrechny.predictionsbot.connector.impl.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import at.hrechny.predictionsbot.connector.ApiConnector;
import at.hrechny.predictionsbot.connector.proxy.ConnectorProxy;
import at.hrechny.predictionsbot.connector.proxy.ConnectorProxyManager;
import io.micronaut.context.BeanContext;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import java.net.InetSocketAddress;
import java.net.Proxy;
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

  @Test
  void createsDirectHttpClientConfigurationWhenProxyIsMissing() {
    var configuration = testClient().createHttpClientConfiguration(null);

    assertThat(configuration.getProxyType()).isEqualTo(Proxy.Type.DIRECT);
    assertThat(configuration.getProxyAddress()).isEmpty();
  }

  @Test
  void createsHttpProxyConfigurationFromConnectorProxy() {
    var proxy = new ConnectorProxy("proxy-1", "proxy.example", 3128, "proxy-user", "proxy-pass", "DE");

    var configuration = testClient().createHttpClientConfiguration(proxy);

    assertThat(configuration.getProxyType()).isEqualTo(Proxy.Type.HTTP);
    assertThat(configuration.getProxyAddress()).isPresent();
    var proxyAddress = (InetSocketAddress) configuration.getProxyAddress().orElseThrow();
    assertThat(proxyAddress.getHostString()).isEqualTo("proxy.example");
    assertThat(proxyAddress.getPort()).isEqualTo(3128);
    assertThat(configuration.getProxyUsername()).contains("proxy-user");
    assertThat(configuration.getProxyPassword()).contains("proxy-pass");
  }

  private ApiFootballHttpClient testClient() {
    return new ApiFootballHttpClient(
        "https://api-football-v1.p.rapidapi.com/v3",
        mock(ConnectorProxyManager.class),
        mock(HttpClientRegistry.class),
        mock(BeanContext.class));
  }
}
