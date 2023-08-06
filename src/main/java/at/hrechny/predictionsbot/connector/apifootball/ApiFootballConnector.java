package at.hrechny.predictionsbot.connector.apifootball;

import at.hrechny.predictionsbot.config.JsonBodyHandler;
import at.hrechny.predictionsbot.connector.apifootball.exception.ApiFootballConnectorException;
import at.hrechny.predictionsbot.connector.apifootball.exception.ApiFootballConnectorException.Reason;
import at.hrechny.predictionsbot.connector.apifootball.model.ApiFootballResponse;
import at.hrechny.predictionsbot.connector.apifootball.model.Fixture;
import at.hrechny.predictionsbot.connector.apifootball.model.FixturesResponse;
import at.hrechny.predictionsbot.connector.apifootball.model.RoundsResponse;
import at.hrechny.predictionsbot.database.entity.AuditEntity;
import at.hrechny.predictionsbot.database.model.ApiProvider;
import at.hrechny.predictionsbot.database.repository.AuditRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballConnector {

  private final AuditRepository auditRepository;

  @Value("${connectors.api-football.url}")
  private String baseUrl;

  @Value("${connectors.api-football.apiKey}")
  private String apiKey;

  @Value("${connectors.api-football.maxAttempts}")
  private int maxAttempts;

  @Value("${connectors.api-football.dayStarts}")
  private String dayStarts;

  public List<String> getRounds(Long competitionId, String seasonYear) {
    URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/fixtures/rounds")
        .queryParam("league", competitionId)
        .queryParam("season", seasonYear)
        .build().toUri();

    return sendRequest(uri, RoundsResponse.class).getResponse();
  }

  public List<Fixture> getFixtures(Long competitionId, String seasonYear) throws ApiFootballConnectorException {
    URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/fixtures")
        .queryParam("league", competitionId)
        .queryParam("season", seasonYear)
        .build().toUri();

    return sendRequest(uri, FixturesResponse.class).getResponse();
  }

  @Cacheable(value = "api-football")
  public List<Fixture> getFixtures(List<Long> fixtureIds) throws ApiFootballConnectorException {
    var fixtureIdsString = fixtureIds.stream().limit(20).map(Object::toString).toList();
    URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/fixtures")
        .queryParam("ids", String.join("-", fixtureIdsString))
        .build().toUri();

    return sendRequest(uri, FixturesResponse.class).getResponse();
  }

  private synchronized <T, G extends ApiFootballResponse<T>> G sendRequest(URI uri, Class<G> clazz) throws ApiFootballConnectorException {
    checkMaxAttempts();

    HttpRequest request = HttpRequest.newBuilder()
        .GET()
        .uri(uri)
        .header("X-RapidAPI-Key", apiKey)
        .header("X-RapidAPI-Host", "api-football-v1.p.rapidapi.com")
        .build();

    var auditEntity = new AuditEntity();
    auditEntity.setApiKey(apiKey);
    auditEntity.setApiProvider(ApiProvider.API_FOOTBALL);
    auditEntity.setRequestUri(uri.toString());
    auditEntity.setRequestDate(Instant.now());

    G response;
    try {
      response = HttpClient.newHttpClient().send(request, new JsonBodyHandler<>(clazz)).body();
      if (response == null || CollectionUtils.isNotEmpty(response.getErrors()) || response.getResponse() == null) {
        auditEntity.setSuccess(false);
        log.error("API-Football response is invalid: {}", response);
        throw new ApiFootballConnectorException(Reason.INVALID_RESPONSE);
      } else {
        auditEntity.setSuccess(true);
      }
    } catch (Exception e) {
      log.error("Request to API-Football failed", e);
      auditEntity.setSuccess(false);
      throw new ApiFootballConnectorException(Reason.REQUEST_ERROR);
    } finally {
      auditRepository.save(auditEntity);
    }

    return response;
  }

  private void checkMaxAttempts() {
    if (maxAttempts <= 0) {
      return;
    }

    LocalDate billingStartDate = LocalDate.now(ZoneOffset.UTC);
    LocalTime billingStartTime = LocalTime.parse(dayStarts);
    if (LocalTime.now(ZoneOffset.UTC).isBefore(billingStartTime)) {
      billingStartDate = billingStartDate.minusDays(1);
    }
    var billingStartDateTime = LocalDateTime.of(billingStartDate, billingStartTime).toInstant(ZoneOffset.UTC);
    int count = auditRepository.countAllByApiProviderAndApiKeyAndRequestDateAfter(ApiProvider.API_FOOTBALL, apiKey, billingStartDateTime);
    if (count >= maxAttempts) {
      throw new ApiFootballConnectorException(Reason.QUOTA_EXCEEDED);
    }
  }

}
