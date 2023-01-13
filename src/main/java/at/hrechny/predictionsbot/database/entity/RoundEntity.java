package at.hrechny.predictionsbot.database.entity;

import at.hrechny.predictionsbot.database.model.RoundType;
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
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "rounds")
public class RoundEntity extends GeneratedIdEntity {

  @Column
  @Enumerated(EnumType.STRING)
  private RoundType type;

  @ManyToOne
  @JoinColumn(name="season_id", nullable=false)
  private SeasonEntity season;

  @Column
  private int orderNumber;

  @Column
  private String apiFootballId;

  @OneToMany(mappedBy="round",  fetch = FetchType.EAGER, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  private List<MatchEntity> matches = new ArrayList<>();

}
