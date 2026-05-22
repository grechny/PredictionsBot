package at.hrechny.predictionsbot.database.entity;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.MappedEntity;

import at.hrechny.predictionsbot.database.converter.ZoneIdConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"leagues", "competitions"})
@MappedEntity("users")
@Entity
@Table(name = "users")
public class UserEntity {

  @io.micronaut.data.annotation.Id
  @Id
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column
  private boolean active = true;

  @Column
  private String username;

  @Column
  private Locale language;

  @Column
  private Locale initialLanguage;

  @Column
  @Convert(converter = ZoneIdConverter.class)
  private ZoneId timezone;

  @ManyToMany(mappedBy = "users")
  private List<LeagueEntity> leagues;

  @ManyToMany
  private List<CompetitionEntity> competitions;

}
