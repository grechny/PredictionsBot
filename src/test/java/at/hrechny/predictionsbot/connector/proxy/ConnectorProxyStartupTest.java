package at.hrechny.predictionsbot.connector.proxy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.connector.impl.apifootball.ApiFootballConnector;
import at.hrechny.predictionsbot.exception.ConnectorProxyException;
import at.hrechny.predictionsbot.service.telegram.TelegramService;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectorProxyStartupTest {

  @Mock
  private ConnectorProxyManager connectorProxyManager;

  @Mock
  private TelegramService telegramService;

  @Test
  void skipsInitializationWhenApiFootballDoesNotUseWebshare() {
    when(connectorProxyManager.usesWebshare(ApiFootballConnector.NAME)).thenReturn(false);
    var startup = new ConnectorProxyStartup(connectorProxyManager, telegramService);

    startup.onApplicationEvent(mock(ApplicationStartupEvent.class));

    verify(connectorProxyManager, never()).initialize(ApiFootballConnector.NAME);
  }

  @Test
  void initializesApiFootballWhenItUsesWebshare() {
    when(connectorProxyManager.usesWebshare(ApiFootballConnector.NAME)).thenReturn(true);
    var startup = new ConnectorProxyStartup(connectorProxyManager, telegramService);

    startup.onApplicationEvent(mock(ApplicationStartupEvent.class));

    verify(connectorProxyManager).initialize(ApiFootballConnector.NAME);
  }

  @Test
  void reportsAndRethrowsStartupInitializationFailure() {
    var exception = new ConnectorProxyException("No valid Webshare proxies are available");
    when(connectorProxyManager.usesWebshare(ApiFootballConnector.NAME)).thenReturn(true);
    doThrow(exception).when(connectorProxyManager).initialize(ApiFootballConnector.NAME);
    var startup = new ConnectorProxyStartup(connectorProxyManager, telegramService);

    assertThatThrownBy(() -> startup.onApplicationEvent(mock(ApplicationStartupEvent.class)))
        .isSameAs(exception);
    verify(telegramService).sendErrorReport(exception);
  }
}
