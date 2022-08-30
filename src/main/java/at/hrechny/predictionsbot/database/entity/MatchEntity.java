package at.hrechny.predictionsbot.database.entity;

import at.hrechny.predictionsbot.database.model.MatchStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "matches")
public class MatchEntity extends GeneratedIdEntity {

  @ManyToOne
  @JoinColumn(name="season_id", nullable=false)
  private SeasonEntity season;

  @Column
  private Integer round;

  @Column
  @Enumerated(EnumType.STRING)
  private MatchStatus status;

  @Column(columnDefinition = "TIMESTAMP")
  private Instant startTime;

  @ManyToOne
  @JoinColumn(name="home_team_id", nullable=false)
  private TeamEntity homeTeam;

  @ManyToOne
  @JoinColumn(name="away_team_id", nullable=false)
  private TeamEntity awayTeam;

  @Column
  private Integer homeTeamScore;

  @Column
  private Integer awayTeamScore;

  @Column(unique=true)
  private Long apiFootballId;

  @OneToMany(mappedBy="match", fetch = FetchType.EAGER, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private List<PredictionEntity> predictions = new ArrayList<>();

  public Optional<PredictionEntity> getPrediction(Long userId) {
    return predictions.stream().filter(p -> p.getUser().getId().equals(userId)).findFirst();
  }

}
