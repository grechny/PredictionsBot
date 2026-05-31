package at.hrechny.predictionsbot.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.connector.proxy.ConnectorProxy;
import at.hrechny.predictionsbot.connector.proxy.ConnectorProxyManager;
import io.micronaut.context.BeanContext;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.LoadBalancer;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ConnectorHttpClientFactoryTest {

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

  @Test
  void cachesCurrentClientPerConnector() {
    var connectorProxyManager = mock(ConnectorProxyManager.class);
    var httpClientRegistry = httpClientRegistry();
    var beanContext = mock(BeanContext.class);
    var httpClient = mock(HttpClient.class);
    when(connectorProxyManager.currentProxy("api-football")).thenReturn(null);
    when(httpClientRegistry.resolveClient(
        eq(null),
        any(LoadBalancer.class),
        any(DefaultHttpClientConfiguration.class),
        eq(beanContext)))
        .thenReturn(httpClient);
    var factory = new ConnectorHttpClientFactory(connectorProxyManager, httpClientRegistry, beanContext);

    assertThat(factory.currentClient("api-football", URI.create("https://api.example/v3"))).isSameAs(httpClient);
    assertThat(factory.currentClient("api-football", URI.create("https://api.example/v3"))).isSameAs(httpClient);

    verify(httpClientRegistry, times(1)).resolveClient(
        eq(null),
        any(LoadBalancer.class),
        any(DefaultHttpClientConfiguration.class),
        eq(beanContext));
  }

  @Test
  void recreatesCurrentClientWhenConnectorProxyChanges() {
    var connectorProxyManager = mock(ConnectorProxyManager.class);
    var httpClientRegistry = httpClientRegistry();
    var beanContext = mock(BeanContext.class);
    var firstClient = mock(HttpClient.class);
    var secondClient = mock(HttpClient.class);
    when(connectorProxyManager.currentProxy("api-football"))
        .thenReturn(new ConnectorProxy("proxy-1", "proxy-1.example", 3128, "user", "pass", "DE"))
        .thenReturn(new ConnectorProxy("proxy-2", "proxy-2.example", 3128, "user", "pass", "DE"));
    when(httpClientRegistry.resolveClient(
        eq(null),
        any(LoadBalancer.class),
        any(DefaultHttpClientConfiguration.class),
        eq(beanContext)))
        .thenReturn(firstClient)
        .thenReturn(secondClient);
    var factory = new ConnectorHttpClientFactory(connectorProxyManager, httpClientRegistry, beanContext);

    assertThat(factory.currentClient("api-football", URI.create("https://api.example/v3"))).isSameAs(firstClient);
    assertThat(factory.currentClient("api-football", URI.create("https://api.example/v3"))).isSameAs(secondClient);

    verify(firstClient).close();
  }

  private ConnectorHttpClientFactory testFactory() {
    return new ConnectorHttpClientFactory(
        mock(ConnectorProxyManager.class),
        httpClientRegistry(),
        mock(BeanContext.class));
  }

  @SuppressWarnings("unchecked")
  private HttpClientRegistry<HttpClient> httpClientRegistry() {
    return mock(HttpClientRegistry.class);
  }
}
