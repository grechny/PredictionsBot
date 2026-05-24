package at.hrechny.predictionsbot.database.entity

import at.hrechny.predictionsbot.database.converter.ZoneIdConverter
import io.micronaut.core.annotation.Introspected
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import java.time.ZoneId
import java.util.Locale

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@Entity
@Table(name = "users")
class UserEntity {
    @field:io.micronaut.data.annotation.Id
    @field:Id
    var id: Long? = null

    @field:Column
    var active: Boolean = true

    @field:Column
    var username: String? = null

    @field:Column
    var language: Locale? = null

    @field:Column
    var initialLanguage: Locale? = null

    @field:Column
    @field:Convert(converter = ZoneIdConverter::class)
    var timezone: ZoneId? = null

    @field:ManyToMany(mappedBy = "users")
    var leagues: MutableList<LeagueEntity> = mutableListOf()

    @field:ManyToMany
    var competitions: MutableList<CompetitionEntity> = mutableListOf()

    fun isActive(): Boolean = active

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is UserEntity) {
            return false
        }

        return id == other.id &&
            active == other.active &&
            username == other.username &&
            language == other.language &&
            initialLanguage == other.initialLanguage &&
            timezone == other.timezone
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + active.hashCode()
        result = 31 * result + (username?.hashCode() ?: 0)
        result = 31 * result + (language?.hashCode() ?: 0)
        result = 31 * result + (initialLanguage?.hashCode() ?: 0)
        result = 31 * result + (timezone?.hashCode() ?: 0)
        return result
    }
}
