package at.hrechny.predictionsbot.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
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
