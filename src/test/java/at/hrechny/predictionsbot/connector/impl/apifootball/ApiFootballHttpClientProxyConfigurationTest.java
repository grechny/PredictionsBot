package at.hrechny.predictionsbot.connector.impl.apifootball;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.HttpClientConfiguration;
import java.net.InetSocketAddress;
import java.net.Proxy;
import org.junit.jupiter.api.Test;

class ApiFootballHttpClientProxyConfigurationTest {

  @Test
  void apiFootballClientUsesNamedServiceAndVersionedBasePath() {
    var client = ApiFootballHttpClient.class.getAnnotation(Client.class);

    assertThat(client.id()).isEqualTo("api-football");
    assertThat(client.path()).isEqualTo("/v3");
  }

  @Test
  void leavesApiFootballClientDirectWhenProxyIsNotConfigured() {
    var configuration = new DefaultHttpClientConfiguration();
    var proxyConfiguration = new ApiFootballHttpClientProxyConfiguration("", 0, "", "");

    proxyConfiguration.configure("api-football", configuration);

    assertThat(configuration.getProxyType()).isEqualTo(Proxy.Type.DIRECT);
    assertThat(configuration.getProxyAddress()).isEmpty();
  }

  @Test
  void appliesConfiguredProxyOnlyToApiFootballClient() {
    var apiFootballConfiguration = new DefaultHttpClientConfiguration();
    var otherConfiguration = new DefaultHttpClientConfiguration();
    var proxyConfiguration = new ApiFootballHttpClientProxyConfiguration("proxy.local", 3128, "proxy-user", "proxy-pass");

    proxyConfiguration.configure("api-football", apiFootballConfiguration);
    proxyConfiguration.configure("other-client", otherConfiguration);

    assertThat(apiFootballConfiguration.getProxyType()).isEqualTo(Proxy.Type.HTTP);
    assertThat(apiFootballConfiguration.getProxyAddress()).isPresent();
    var proxyAddress = (InetSocketAddress) apiFootballConfiguration.getProxyAddress().orElseThrow();
    assertThat(proxyAddress.getHostString()).isEqualTo("proxy.local");
    assertThat(proxyAddress.getPort()).isEqualTo(3128);
    assertThat(apiFootballConfiguration.getProxyUsername()).contains("proxy-user");
    assertThat(apiFootballConfiguration.getProxyPassword()).contains("proxy-pass");
    assertThat(otherConfiguration.getProxyType()).isEqualTo(Proxy.Type.DIRECT);
    assertThat(otherConfiguration.getProxyAddress()).isEmpty();
  }

  @Test
  void ignoresProxyHostWhenPortIsInvalid() {
    var configuration = new DefaultHttpClientConfiguration();
    var proxyConfiguration = new ApiFootballHttpClientProxyConfiguration("proxy.local", 0, "proxy-user", "proxy-pass");

    proxyConfiguration.configure("api-football", configuration);

    assertThat(configuration.getProxyType()).isEqualTo(Proxy.Type.DIRECT);
    assertThat(configuration.getProxyAddress()).isEmpty();
  }
}
