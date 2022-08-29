package at.hrechny.predictionsbot.database.entity;

import at.hrechny.predictionsbot.database.converter.ZoneIdConverter;
import java.time.ZoneId;
import java.util.Locale;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
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
