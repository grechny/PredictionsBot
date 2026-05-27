package at.hrechny.predictionsbot.database.entity

import at.hrechny.predictionsbot.database.model.RoundType
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

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@Entity
@Table(name = "rounds")
class RoundEntity : GeneratedIdEntity() {
    @field:Column
    @field:Enumerated(EnumType.STRING)
    var type: RoundType? = null

    @field:ManyToOne
    @field:JoinColumn(name = "season_id", nullable = false)
    var season: SeasonEntity? = null

    @field:Column
    var orderNumber: Int = 0

    @field:OneToMany(mappedBy = "round", cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    var matches: MutableList<MatchEntity> = mutableListOf()
}
