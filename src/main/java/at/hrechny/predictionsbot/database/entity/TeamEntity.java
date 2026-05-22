package at.hrechny.predictionsbot.database.entity;

import io.micronaut.core.annotation.Introspected;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Getter
@Setter
@Entity
@Table(name = "teams", indexes = {
  @Index(name = "idx_api_football_id", columnList = "apiFootballId", unique = true)
})
public class TeamEntity extends GeneratedIdEntity {

  @Column
  private String name;

  @Column
  private Long apiFootballId;

  @Column
  private String logoUrl;

}
