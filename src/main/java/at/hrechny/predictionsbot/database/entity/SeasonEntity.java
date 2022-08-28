package at.hrechny.predictionsbot.database.entity;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

@Getter
@Setter
@Entity
@Table(name = "seasons", uniqueConstraints = { @UniqueConstraint(columnNames = { "competition_id", "year" }) })
public class SeasonEntity extends GeneratedIdEntity  {

  @ManyToOne
  @JoinColumn(name="competition_id", nullable=false)
  private CompetitionEntity competition;

  @Column
  private String year;

  @LazyCollection(LazyCollectionOption.FALSE)
  @OneToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private List<RoundEntity> apiFootballRounds;

  @OneToMany(mappedBy="season", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private List<MatchEntity> matches = new ArrayList<>();

  @Column
  private boolean active;

}
