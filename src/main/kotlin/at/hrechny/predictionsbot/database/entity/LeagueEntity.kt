package at.hrechny.predictionsbot.database.entity

import io.micronaut.core.annotation.Introspected
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@Entity
@Table(name = "leagues")
class LeagueEntity : GeneratedIdEntity() {
    @field:Column(nullable = false, unique = true)
    var name: String? = null

    @field:ManyToOne
    @field:JoinColumn(name = "admin_user_id", nullable = false)
    var adminUser: UserEntity? = null

    @field:ManyToMany
    @field:JoinTable(
        name = "leagues_users",
        joinColumns = [JoinColumn(name = "league_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")],
    )
    var users: MutableSet<UserEntity> = mutableSetOf()

    @field:ManyToMany
    var competitions: MutableList<CompetitionEntity> = mutableListOf()
}
