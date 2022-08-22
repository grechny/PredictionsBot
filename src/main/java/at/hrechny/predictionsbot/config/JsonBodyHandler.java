package at.hrechny.predictionsbot.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class JsonBodyHandler<W> implements HttpResponse.BodyHandler<W> {

  private final Class<W> wClass;

  public JsonBodyHandler(Class<W> wClass) {
    this.wClass = wClass;
  }

  @Override
  public HttpResponse.BodySubscriber<W> apply(HttpResponse.ResponseInfo responseInfo) {
    return asJSON(wClass);
  }

  public static <T> HttpResponse.BodySubscriber<T> asJSON(Class<T> targetType) {
    HttpResponse.BodySubscriber<String> upstream = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);

    return HttpResponse.BodySubscribers.mapping(upstream, (String body) -> {
      try {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new JavaTimeModule());

        return objectMapper.readValue(body, targetType);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }
}

