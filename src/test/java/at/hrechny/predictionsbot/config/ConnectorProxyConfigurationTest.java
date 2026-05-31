package at.hrechny.predictionsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micronaut.context.ApplicationContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectorProxyConfigurationTest {

  @Test
  void parsesSupportedProxyModes() {
    assertThat(ConnectorProxyMode.Companion.fromConfiguration(null)).isEqualTo(ConnectorProxyMode.NONE);
    assertThat(ConnectorProxyMode.Companion.fromConfiguration("none")).isEqualTo(ConnectorProxyMode.NONE);
    assertThat(ConnectorProxyMode.Companion.fromConfiguration("webshare.io")).isEqualTo(ConnectorProxyMode.WEBSHARE_IO);
  }

  @Test
  void rejectsUnsupportedProxyMode() {
    assertThatThrownBy(() -> ConnectorProxyMode.Companion.fromConfiguration("custom"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported connector proxy mode")
        .hasMessageContaining("none")
        .hasMessageContaining("webshare.io");
  }

  @Test
  void bindsApiFootballProxyModeFromConfiguration() {
    try (var context = ApplicationContext.run(testProperties("webshare.io"), "test")) {
      var configuration = context.getBean(ApiFootballConnectorConfiguration.class);

      assertThat(configuration.getProxy()).isEqualTo(ConnectorProxyMode.WEBSHARE_IO);
    }
  }

  @Test
  void defaultsApiFootballProxyModeToNone() {
    try (var context = ApplicationContext.run(testProperties(null), "test")) {
      var configuration = context.getBean(ApiFootballConnectorConfiguration.class);

      assertThat(configuration.getProxy()).isEqualTo(ConnectorProxyMode.NONE);
    }
  }

  private Map<String, Object> testProperties(String proxyMode) {
    var properties = new java.util.HashMap<String, Object>();
    properties.put("application.url", "http://localhost");
    properties.put("connectors.api-football.apiKey", "api-football-key");
    properties.put("connectors.api-football.dayStarts", "00:00");
    properties.put("connectors.api-football.maxAttempts", 100);
    properties.put("connectors.api-football.url", "http://localhost:18080/v3");
    properties.put("jpa.default.properties.hibernate.dialect", "org.hibernate.dialect.H2Dialect");
    properties.put("jpa.default.properties.hibernate.hbm2ddl.auto", "create-drop");
    properties.put("secrets.telegramKey", "telegram-secret");
    properties.put("schedulers.fixtures.enabled", false);
    properties.put("telegram.polling.enabled", false);
    properties.put("telegram.reportTo", "1");
    properties.put("telegram.token", "telegram-token");
    if (proxyMode != null) {
      properties.put("connectors.api-football.proxy", proxyMode);
    }
    return properties;
  }
}
