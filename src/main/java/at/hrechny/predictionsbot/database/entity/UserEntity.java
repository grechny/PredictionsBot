package at.hrechny.predictionsbot.database.entity;

import at.hrechny.predictionsbot.database.converter.ZoneIdConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZoneId;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Entity
@Table(name = "users")
public class UserEntity {

  @Id
  private Long id;

  @Column
  private String username;

  @Column
  private Locale language;

  @Column
  @Convert(converter = ZoneIdConverter.class)
  private ZoneId timezone;

  @Column
  private boolean active = true;

}
