package at.hrechny.predictionsbot.database.entity

import io.micronaut.core.annotation.Introspected
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@Entity
@Table(name = "teams")
class TeamEntity : GeneratedIdEntity() {
    @field:Column
    var name: String? = null

    @field:Column
    var logoUrl: String? = null
}
