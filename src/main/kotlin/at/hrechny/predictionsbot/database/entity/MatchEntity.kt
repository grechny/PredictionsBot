package at.hrechny.predictionsbot.database.entity

import at.hrechny.predictionsbot.database.model.MatchStatus
import io.micronaut.core.annotation.Introspected
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant
import java.util.Optional

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@Entity
@Table(name = "matches")
class MatchEntity : GeneratedIdEntity() {
    @field:ManyToOne
    @field:JoinColumn(name = "round_id", nullable = false)
    var round: RoundEntity? = null

    @field:Column
    @field:Enumerated(EnumType.STRING)
    var status: MatchStatus? = null

    @field:Column(columnDefinition = "TIMESTAMP")
    var startTime: Instant? = null

    @field:ManyToOne
    @field:JoinColumn(name = "home_team_id", nullable = false)
    var homeTeam: TeamEntity? = null

    @field:ManyToOne
    @field:JoinColumn(name = "away_team_id", nullable = false)
    var awayTeam: TeamEntity? = null

    @field:Column
    var homeTeamScore: Int? = null

    @field:Column
    var awayTeamScore: Int? = null

    @field:Column(unique = true)
    var apiFootballId: Long? = null

    @field:OneToMany(mappedBy = "match", cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    var predictions: MutableList<PredictionEntity> = mutableListOf()

    fun getPrediction(userId: Long): Optional<PredictionEntity> =
        predictions.stream().filter { prediction -> prediction.user?.id == userId }.findFirst()
}
