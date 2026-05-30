package at.hrechny.predictionsbot.connector.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.config.ApiFootballConnectorConfiguration;
import at.hrechny.predictionsbot.config.ConnectorProxyMode;
import at.hrechny.predictionsbot.connector.impl.apifootball.ApiFootballConnector;
import at.hrechny.predictionsbot.connector.proxy.webshare.WebshareProxyService;
import at.hrechny.predictionsbot.exception.ConnectorProxyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectorProxyManagerTest {

  @Mock
  private WebshareProxyService webshareProxyService;

  @Test
  void directModeReturnsNullProxyWithoutCallingWebshare() {
    var manager = manager(ConnectorProxyMode.NONE);

    manager.initialize(ApiFootballConnector.NAME);

    assertThat(manager.currentProxy(ApiFootballConnector.NAME)).isNull();
    verifyNoInteractions(webshareProxyService);
  }

  @Test
  void webshareModeInitializesActiveProxy() {
    var proxy = proxy("eu");
    when(webshareProxyService.selectProxy(null)).thenReturn(proxy);
    var manager = manager(ConnectorProxyMode.WEBSHARE_IO);

    manager.initialize(ApiFootballConnector.NAME);

    assertThat(manager.currentProxy(ApiFootballConnector.NAME)).isEqualTo(proxy);
  }

  @Test
  void webshareModeRotatesAndExcludesFailedProxy() {
    var failedProxy = proxy("failed");
    var nextProxy = proxy("next");
    when(webshareProxyService.selectProxy(failedProxy)).thenReturn(nextProxy);
    var manager = manager(ConnectorProxyMode.WEBSHARE_IO);

    var selected = manager.rotate(ApiFootballConnector.NAME, failedProxy, new IllegalStateException("failure"));

    assertThat(selected).isEqualTo(nextProxy);
    assertThat(manager.currentProxy(ApiFootballConnector.NAME)).isEqualTo(nextProxy);
    verify(webshareProxyService).selectProxy(failedProxy);
  }

  @Test
  void rejectsUnknownConnector() {
    var manager = manager(ConnectorProxyMode.NONE);

    assertThatThrownBy(() -> manager.currentProxy("unknown"))
        .isInstanceOf(ConnectorProxyException.class)
        .hasMessageContaining("Unknown connector unknown");
  }

  private ConnectorProxyManager manager(ConnectorProxyMode proxyMode) {
    var configuration = new ApiFootballConnectorConfiguration();
    configuration.setProxy(proxyMode);
    return new ConnectorProxyManager(configuration, webshareProxyService);
  }

  private ConnectorProxy proxy(String id) {
    return new ConnectorProxy(id, "203.0.113.10", 8000, "user", "password", "DE");
  }
}
