package at.hrechny.predictionsbot.database.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "teams")
public class TeamEntity extends GeneratedIdEntity {

  @Column
  private String name;

  @Column
  private Long apiFootballId;

}
