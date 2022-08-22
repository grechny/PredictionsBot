package at.hrechny.predictionsbot.database.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "predictions")
public class PredictionEntity extends GeneratedIdEntity {

  @ManyToOne
  @JoinColumn(name="user_id", nullable=false)
  private UserEntity user;

  @ManyToOne
  @JoinColumn(name="match_id", nullable=false)
  private MatchEntity match;

  @Column(columnDefinition = "numeric(1)")
  private int predictionHome;

  @Column(columnDefinition = "numeric(1)")
  private int predictionAway;

  @Column(name = "double")
  private boolean doubleUp;

  @Column(columnDefinition = "TIMESTAMP")
  private Instant updatedAt;

}
