package at.hrechny.predictionsbot.database.entity

import io.micronaut.core.annotation.Introspected
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.util.UUID
import org.hibernate.Hibernate
import org.hibernate.annotations.UuidGenerator

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@MappedSuperclass
abstract class GeneratedIdEntity {
    @field:io.micronaut.data.annotation.Id
    @field:Id
    @field:GeneratedValue
    @field:UuidGenerator
    var id: UUID? = null

    final override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false
        }

        other as GeneratedIdEntity
        return id != null && id == other.id
    }

    final override fun hashCode(): Int = Hibernate.getClass(this).hashCode()
}
