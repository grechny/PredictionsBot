package at.hrechny.predictionsbot.database.entity;

import at.hrechny.predictionsbot.database.model.ApiProvider;
import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
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
