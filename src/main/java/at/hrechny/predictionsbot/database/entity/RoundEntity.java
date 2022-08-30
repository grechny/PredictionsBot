package at.hrechny.predictionsbot.database.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "rounds")
public class RoundEntity extends GeneratedIdEntity {

  @Column
  private int orderNumber;

  @Column
  private String roundName;

  @Column
  private String apiFootballRoundName;

}
