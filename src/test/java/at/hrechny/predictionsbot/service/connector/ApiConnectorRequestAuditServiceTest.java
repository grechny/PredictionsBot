package at.hrechny.predictionsbot.service.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.database.entity.AuditEntity;
import at.hrechny.predictionsbot.database.repository.AuditRepository;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import java.lang.reflect.Method;
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
  void recordRequestUsesNewTransactionSoFailuresSurviveCallerRollback() throws Exception {
    Method method = ApiConnectorRequestAuditService.class.getDeclaredMethod(
        "recordRequest",
        String.class,
        String.class,
        boolean.class,
        String.class);

    assertThat(method.getAnnotation(Transactional.class).propagation())
        .isEqualTo(TransactionDefinition.Propagation.REQUIRES_NEW);
  }

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
        "HTTP 429");

    verify(auditRepository).save(captor.capture());
    var entity = captor.getValue();
    assertThat(entity.getConnectorName()).isEqualTo("api-football");
    assertThat(entity.getRequestUri()).isEqualTo("/fixtures?ids=1-2-3");
    assertThat(entity.getRequestDate()).isEqualTo(NOW);
    assertThat(entity.isSuccess()).isFalse();
    assertThat(entity.getFailureReason()).isEqualTo("HTTP 429");
  }

  @Test
  void countRequestsSinceDelegatesToRepository() {
    var service = new ApiConnectorRequestAuditService(
        auditRepository,
        Clock.fixed(NOW, ZoneOffset.UTC));
    var since = Instant.parse("2026-05-25T22:00:00Z");
    when(auditRepository.countAllByConnectorNameAndRequestDateAfter("api-football", since))
        .thenReturn(42);

    assertThat(service.countRequestsSince("api-football", since)).isEqualTo(42);
  }
}
