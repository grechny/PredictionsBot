package at.hrechny.predictionsbot.connector.impl.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import at.hrechny.predictionsbot.connector.proxy.ConnectorProxy;
import at.hrechny.predictionsbot.connector.proxy.ConnectorProxyManager;
import io.micronaut.context.BeanContext;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import java.net.InetSocketAddress;
import java.net.Proxy;
import org.junit.jupiter.api.Test;

class ApiFootballHttpClientFactoryTest {

  @Test
  void createsDirectHttpClientConfigurationWhenProxyIsMissing() {
    var configuration = testFactory().createHttpClientConfiguration(null);

    assertThat(configuration.getProxyType()).isEqualTo(Proxy.Type.DIRECT);
    assertThat(configuration.getProxyAddress()).isEmpty();
  }

  @Test
  void createsHttpProxyConfigurationFromConnectorProxy() {
    var proxy = new ConnectorProxy("proxy-1", "proxy.example", 3128, "proxy-user", "proxy-pass", "DE");

    var configuration = testFactory().createHttpClientConfiguration(proxy);

    assertThat(configuration.getProxyType()).isEqualTo(Proxy.Type.HTTP);
    assertThat(configuration.getProxyAddress()).isPresent();
    var proxyAddress = (InetSocketAddress) configuration.getProxyAddress().orElseThrow();
    assertThat(proxyAddress.getHostString()).isEqualTo("proxy.example");
    assertThat(proxyAddress.getPort()).isEqualTo(3128);
    assertThat(configuration.getProxyUsername()).contains("proxy-user");
    assertThat(configuration.getProxyPassword()).contains("proxy-pass");
  }

  private ApiFootballHttpClientFactory testFactory() {
    return new ApiFootballHttpClientFactory(
        "https://api-football-v1.p.rapidapi.com/v3",
        mock(ConnectorProxyManager.class),
        httpClientRegistry(),
        mock(BeanContext.class));
  }

  @SuppressWarnings("unchecked")
  private HttpClientRegistry<HttpClient> httpClientRegistry() {
    return mock(HttpClientRegistry.class);
  }
}
