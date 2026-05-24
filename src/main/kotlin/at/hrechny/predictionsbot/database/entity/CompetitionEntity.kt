package at.hrechny.predictionsbot.database.entity

import io.micronaut.core.annotation.Introspected
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@Entity
@Table(name = "competitions")
class CompetitionEntity : GeneratedIdEntity() {
    @field:Column
    var name: String? = null

    @field:Column(unique = true)
    var apiFootballId: Long? = null

    @field:OneToMany(mappedBy = "competition")
    var seasons: MutableList<SeasonEntity> = mutableListOf()
}
