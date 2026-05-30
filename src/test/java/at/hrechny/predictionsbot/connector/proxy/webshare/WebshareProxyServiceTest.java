package at.hrechny.predictionsbot.connector.proxy.webshare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.hrechny.predictionsbot.connector.proxy.ConnectorProxy;
import at.hrechny.predictionsbot.connector.proxy.webshare.model.WebshareProxyResponseDto;
import at.hrechny.predictionsbot.exception.ConnectorProxyException;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebshareProxyServiceTest {

  @Test
  void selectsValidEuropeanProxyBeforeNonEuropeanProxy() {
    var service = service(
        proxy("us", "203.0.113.10", 8000, "US", true),
        proxy("de", "203.0.113.11", 8001, "DE", true));

    var selected = service.selectProxy();

    assertThat(selected.getId()).isEqualTo("de");
    assertThat(selected.getCountryCode()).isEqualTo("DE");
  }

  @Test
  void fallsBackToValidNonEuropeanProxy() {
    var service = service(
        proxy("us", "203.0.113.10", 8000, "US", true),
        proxy("br", "203.0.113.11", 8001, "BR", true));

    var selected = service.selectProxy();

    assertThat(selected.getId()).isEqualTo("us");
  }

  @Test
  void ignoresInvalidProxies() {
    var service = service(
        proxy("invalid", "203.0.113.10", 8000, "DE", false),
        proxy("valid", "203.0.113.11", 8001, "FR", true));

    var selected = service.selectProxy();

    assertThat(selected.getId()).isEqualTo("valid");
  }

  @Test
  void doesNotSelectExcludedProxyWhenAnotherValidProxyExists() {
    var service = service(
        proxy("failed", "203.0.113.10", 8000, "DE", true),
        proxy("next", "203.0.113.11", 8001, "FR", true));
    var failedProxy = new ConnectorProxy("failed", "203.0.113.10", 8000, "user", "password", "DE");

    var selected = service.selectProxy(failedProxy);

    assertThat(selected.getId()).isEqualTo("next");
  }

  @Test
  void failsWhenNoValidProxyExistsWithoutLeakingCredentials() {
    var service = service(proxy("invalid", "203.0.113.10", 8000, "DE", false));

    assertThatThrownBy(service::selectProxy)
        .isInstanceOf(ConnectorProxyException.class)
        .hasMessageContaining("No valid Webshare proxies")
        .hasMessageNotContaining("secret-user")
        .hasMessageNotContaining("secret-password");
  }

  private WebshareProxyService service(WebshareProxyResponseDto... proxies) {
    WebshareProxyClient client = () -> List.of(proxies);
    return new WebshareProxyService(client);
  }

  private WebshareProxyResponseDto proxy(String id, String host, int port, String countryCode, boolean valid) {
    var proxy = new WebshareProxyResponseDto();
    proxy.setId(id);
    proxy.setProxyAddress(host);
    proxy.setPort(port);
    proxy.setUsername("secret-user");
    proxy.setPassword("secret-password");
    proxy.setCountryCode(countryCode);
    proxy.setValid(valid);
    return proxy;
  }
}
