package at.hrechny.predictionsbot.database.entity;

import at.hrechny.predictionsbot.database.model.ApiProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "audit")
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
