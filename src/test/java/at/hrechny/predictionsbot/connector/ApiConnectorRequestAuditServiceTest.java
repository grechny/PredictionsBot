package at.hrechny.predictionsbot.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.database.entity.AuditEntity;
import at.hrechny.predictionsbot.database.repository.AuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiConnectorRequestAuditServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-26T17:00:00Z");

  @Mock
  private AuditRepository auditRepository;

  @Test
  void recordRequestSavesConnectorAuditWithoutApiKey() {
    var service = new ApiConnectorRequestAuditService(
        auditRepository,
        Clock.fixed(NOW, ZoneOffset.UTC));
    var captor = ArgumentCaptor.forClass(AuditEntity.class);

    service.recordRequest(
        "api-football",
        "/fixtures?ids=1-2-3",
        false,
        "HTTP 429",
        "remaining=0");

    verify(auditRepository).save(captor.capture());
    var entity = captor.getValue();
    assertThat(entity.getConnectorCode()).isEqualTo("api-football");
    assertThat(entity.getRequestUri()).isEqualTo("/fixtures?ids=1-2-3");
    assertThat(entity.getRequestDate()).isEqualTo(NOW);
    assertThat(entity.isSuccess()).isFalse();
    assertThat(entity.getFailureReason()).isEqualTo("HTTP 429");
    assertThat(entity.getQuotaSnapshot()).isEqualTo("remaining=0");
  }

  @Test
  void countRequestsSinceDelegatesToRepository() {
    var service = new ApiConnectorRequestAuditService(
        auditRepository,
        Clock.fixed(NOW, ZoneOffset.UTC));
    var since = Instant.parse("2026-05-25T22:00:00Z");
    when(auditRepository.countAllByConnectorCodeAndRequestDateAfter("api-football", since))
        .thenReturn(42);

    assertThat(service.countRequestsSince("api-football", since)).isEqualTo(42);
  }
}
