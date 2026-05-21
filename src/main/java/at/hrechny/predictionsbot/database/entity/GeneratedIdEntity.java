package at.hrechny.predictionsbot.database.entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.GenericGenerator;

@Getter
@Setter
@MappedSuperclass
public abstract class GeneratedIdEntity {

  @Id
  @GeneratedValue(generator = "UUID")
  @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
  private UUID id;

  @Override
  public final boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || Hibernate.getClass(this) != Hibernate.getClass(object)) {
      return false;
    }
    var that = (GeneratedIdEntity) object;
    return id != null && id.equals(that.id);
  }

  @Override
  public final int hashCode() {
    return Hibernate.getClass(this).hashCode();
  }

}
