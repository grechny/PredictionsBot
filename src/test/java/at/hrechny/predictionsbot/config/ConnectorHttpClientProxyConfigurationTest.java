package at.hrechny.predictionsbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import at.hrechny.predictionsbot.connector.impl.apifootball.ApiFootballHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.annotation.Client;
import java.net.InetSocketAddress;
import java.net.Proxy;
import org.junit.jupiter.api.Test;

class ConnectorHttpClientProxyConfigurationTest {

  @Test
  void apiFootballClientUsesConfiguredBaseUrlAndVersionedBasePath() {
    var client = ApiFootballHttpClient.class.getAnnotation(Client.class);

    assertThat(client.value()).isEqualTo("${connectors.api-football.url}");
    assertThat(client.path()).isEqualTo("/v3");
  }

  @Test
  void leavesConnectorClientsDirectWhenProxyIsNotConfigured() {
    var configuration = new DefaultHttpClientConfiguration();
    var proxyConfiguration = new ConnectorHttpClientProxyConfiguration("", 0, "", "");

    proxyConfiguration.configure(configuration);

    assertThat(configuration.getProxyType()).isEqualTo(Proxy.Type.DIRECT);
    assertThat(configuration.getProxyAddress()).isEmpty();
  }

  @Test
  void appliesConfiguredProxyToConnectorHttpClients() {
    var configuration = new DefaultHttpClientConfiguration();
    var proxyConfiguration = new ConnectorHttpClientProxyConfiguration("proxy.local", 3128, "proxy-user", "proxy-pass");

    proxyConfiguration.configure(configuration);

    assertThat(configuration.getProxyType()).isEqualTo(Proxy.Type.HTTP);
    assertThat(configuration.getProxyAddress()).isPresent();
    var proxyAddress = (InetSocketAddress) configuration.getProxyAddress().orElseThrow();
    assertThat(proxyAddress.getHostString()).isEqualTo("proxy.local");
    assertThat(proxyAddress.getPort()).isEqualTo(3128);
    assertThat(configuration.getProxyUsername()).contains("proxy-user");
    assertThat(configuration.getProxyPassword()).contains("proxy-pass");
  }

  @Test
  void ignoresProxyHostWhenPortIsInvalid() {
    var configuration = new DefaultHttpClientConfiguration();
    var proxyConfiguration = new ConnectorHttpClientProxyConfiguration("proxy.local", 0, "proxy-user", "proxy-pass");

    proxyConfiguration.configure(configuration);

    assertThat(configuration.getProxyType()).isEqualTo(Proxy.Type.DIRECT);
    assertThat(configuration.getProxyAddress()).isEmpty();
  }
}
