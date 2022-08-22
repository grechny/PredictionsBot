package at.hrechny.predictionsbot.database.entity;

import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "competitions")
public class CompetitionEntity extends GeneratedIdEntity {

  @Column
  private String name;

  @Column(unique=true)
  private Long apiFootballId;

  @OneToMany(mappedBy="competition", fetch = FetchType.EAGER)
  private List<SeasonEntity> seasons;

}
