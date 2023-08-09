package at.hrechny.predictionsbot.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "competitions")
public class CompetitionEntity extends GeneratedIdEntity {

  @Column
  private String name;

  @Column(unique=true)
  private Long apiFootballId;

  @OneToMany(mappedBy="competition")
  private List<SeasonEntity> seasons;

}
