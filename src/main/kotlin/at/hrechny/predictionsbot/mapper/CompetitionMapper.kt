package at.hrechny.predictionsbot.mapper

import at.hrechny.predictionsbot.database.entity.CompetitionEntity
import at.hrechny.predictionsbot.database.entity.SeasonEntity
import at.hrechny.predictionsbot.model.Competition
import jakarta.inject.Singleton
import org.apache.commons.collections4.CollectionUtils

@Singleton
class CompetitionMapper {
    fun entityToModel(source: CompetitionEntity): Competition =
        Competition().apply {
            id = source.id
            name = source.name
            apiFootballId = source.apiFootballId
            active = isActive(source)
        }

    fun modelToEntity(source: Competition): CompetitionEntity =
        CompetitionEntity().apply {
            id = source.id
            name = source.name
            apiFootballId = source.apiFootballId
        }

    fun isActive(entity: CompetitionEntity): Boolean =
        CollectionUtils.isNotEmpty(entity.seasons) && entity.seasons.any(SeasonEntity::isActive)
}
