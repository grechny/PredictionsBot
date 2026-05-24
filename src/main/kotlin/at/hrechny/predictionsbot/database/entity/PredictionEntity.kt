package at.hrechny.predictionsbot.database.entity

import io.micronaut.core.annotation.Introspected
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@Entity
@Table(name = "predictions")
class PredictionEntity : GeneratedIdEntity() {
    @field:ManyToOne
    @field:JoinColumn(name = "user_id", nullable = false)
    var user: UserEntity? = null

    @field:ManyToOne
    @field:JoinColumn(name = "match_id", nullable = false)
    var match: MatchEntity? = null

    @field:Column(columnDefinition = "numeric(1)")
    var predictionHome: Int = 0

    @field:Column(columnDefinition = "numeric(1)")
    var predictionAway: Int = 0

    @field:Column(name = "double")
    var doubleUp: Boolean = false

    @field:Column(columnDefinition = "TIMESTAMP")
    var updatedAt: Instant? = null

    fun isDoubleUp(): Boolean = doubleUp
}
