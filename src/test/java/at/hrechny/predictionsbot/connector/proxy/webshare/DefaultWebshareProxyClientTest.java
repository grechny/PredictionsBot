package at.hrechny.predictionsbot.connector.proxy.webshare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.hrechny.predictionsbot.config.WebshareProxyConfiguration;
import at.hrechny.predictionsbot.exception.ConnectorProxyException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultWebshareProxyClientTest {

  private static final String API_KEY = "secret-webshare-key";

  private HttpServer server;
  private final List<String> requestUris = new ArrayList<>();
  private final List<String> authorizationHeaders = new ArrayList<>();

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void baseUrlAddsProxyListPathAndDefaultQueryParameters() {
    respond(200, """
        {
          "results": [
            {
              "id": "proxy-1",
              "proxy_address": "203.0.113.10",
              "port": 8000,
              "username": "proxy-user",
              "password": "proxy-password",
              "valid": true,
              "country_code": "DE"
            }
          ]
        }
        """);

    var proxies = client(serverUrl()).listDirectProxies();

    assertThat(proxies).hasSize(1);
    assertThat(proxies.get(0).getProxyAddress()).isEqualTo("203.0.113.10");
    assertThat(requestUris).containsExactly("/api/v2/proxy/list/?mode=direct&page=1&page_size=100");
    assertThat(authorizationHeaders).containsExactly("Token " + API_KEY);
  }

  @Test
  void fullListUrlPreservesExistingQueryAndDoesNotDuplicatePath() {
    respond(200, """
        {
          "results": []
        }
        """);

    client(serverUrl() + "/api/v2/proxy/list/?plan_id=plan-1&page_size=25").listDirectProxies();

    assertThat(requestUris).hasSize(1);
    assertThat(requestUris.get(0)).startsWith("/api/v2/proxy/list/?");
    assertThat(requestUris.get(0)).contains("plan_id=plan-1");
    assertThat(requestUris.get(0)).contains("page_size=25");
    assertThat(requestUris.get(0)).contains("mode=direct");
    assertThat(requestUris.get(0)).contains("page=1");
    assertThat(requestUris.get(0)).doesNotContain("/api/v2/proxy/list/api/v2/proxy/list");
  }

  @Test
  void nonSuccessResponseDoesNotParseOrExposeResponseBody() {
    respond(404, "<html>secret-webshare-key</html>");

    assertThatThrownBy(() -> client(serverUrl()).listDirectProxies())
        .isInstanceOf(ConnectorProxyException.class)
        .hasMessageContaining("HTTP 404")
        .hasMessageNotContaining("<html>")
        .hasMessageNotContaining(API_KEY);
  }

  @Test
  void invalidJsonResponseDoesNotExposeResponseBody() {
    respond(200, "<html>secret-webshare-key</html>");

    assertThatThrownBy(() -> client(serverUrl()).listDirectProxies())
        .isInstanceOf(ConnectorProxyException.class)
        .hasMessageContaining("Failed to parse Webshare proxy list response")
        .hasMessageNotContaining("<html>")
        .hasMessageNotContaining(API_KEY);
  }

  private DefaultWebshareProxyClient client(String apiUrl) {
    var configuration = new WebshareProxyConfiguration();
    configuration.setApiKey(API_KEY);
    configuration.setApiUrl(apiUrl);
    return new DefaultWebshareProxyClient(configuration);
  }

  private String serverUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private void respond(int status, String body) {
    server.createContext("/", exchange -> {
      requestUris.add(exchange.getRequestURI().toString());
      authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
      var response = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
  }
}
