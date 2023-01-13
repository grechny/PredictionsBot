package at.hrechny.predictionsbot.database.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "seasons", uniqueConstraints = { @UniqueConstraint(columnNames = { "competition_id", "year" }) })
public class SeasonEntity extends GeneratedIdEntity  {

  @ManyToOne
  @JoinColumn(name="competition_id", nullable=false)
  private CompetitionEntity competition;

  @Column
  private String year;

  @Column
  private boolean active;

  @OneToMany(mappedBy = "season", fetch = FetchType.EAGER, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private List<RoundEntity> rounds;

}
