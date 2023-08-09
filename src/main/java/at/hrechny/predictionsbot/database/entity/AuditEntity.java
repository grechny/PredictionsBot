package at.hrechny.predictionsbot.database.entity;

import at.hrechny.predictionsbot.database.model.ApiProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@EqualsAndHashCode(callSuper = true)
@Table(name = "audit", indexes = {
    @Index(name = "idx_api_key_provider", columnList = "apiKey, apiProvider", unique = true)
})
public class AuditEntity extends GeneratedIdEntity {

  @Column
  private String apiKey;

  @Column
  @Enumerated(EnumType.STRING)
  private ApiProvider apiProvider;

  @Column
  private String requestUri;

  @Column(columnDefinition = "TIMESTAMP")
  private Instant requestDate;

  @Column
  private boolean success;

}
