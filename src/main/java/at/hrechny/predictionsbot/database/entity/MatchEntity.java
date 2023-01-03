package at.hrechny.predictionsbot.database.entity;

import at.hrechny.predictionsbot.database.model.MatchStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
  @JoinColumn(name="round_id", nullable=false)
  private RoundEntity round;

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
