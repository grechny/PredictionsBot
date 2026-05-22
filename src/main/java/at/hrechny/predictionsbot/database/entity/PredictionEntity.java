package at.hrechny.predictionsbot.database.entity;

import io.micronaut.core.annotation.Introspected;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Getter
@Setter
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
