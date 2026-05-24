package at.hrechny.predictionsbot.database.entity

import io.micronaut.core.annotation.Introspected
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@Entity
@Table(
    name = "teams",
    indexes = [Index(name = "idx_api_football_id", columnList = "apiFootballId", unique = true)],
)
class TeamEntity : GeneratedIdEntity() {
    @field:Column
    var name: String? = null

    @field:Column
    var apiFootballId: Long? = null

    @field:Column
    var logoUrl: String? = null
}
