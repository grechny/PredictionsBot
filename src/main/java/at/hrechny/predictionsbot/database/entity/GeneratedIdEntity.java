package at.hrechny.predictionsbot.database.entity;

import io.micronaut.core.annotation.Introspected;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.GenericGenerator;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Getter
@Setter
@MappedSuperclass
public abstract class GeneratedIdEntity {

  @io.micronaut.data.annotation.Id
  @Id
  @GeneratedValue(generator = "UUID")
  @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
  private UUID id;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

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
