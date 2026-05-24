package at.hrechny.predictionsbot.database.entity

import io.micronaut.core.annotation.Introspected
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@Entity
@Table(name = "seasons", uniqueConstraints = [UniqueConstraint(columnNames = ["competition_id", "year"])])
class SeasonEntity : GeneratedIdEntity() {
    @field:ManyToOne
    @field:JoinColumn(name = "competition_id", nullable = false)
    var competition: CompetitionEntity? = null

    @field:Column
    var year: String? = null

    @field:Column
    var active: Boolean = false

    @field:OneToMany(mappedBy = "season", cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    var rounds: MutableList<RoundEntity> = mutableListOf()

    fun isActive(): Boolean = active
}
