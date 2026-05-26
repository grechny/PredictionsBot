package at.hrechny.predictionsbot.mapper

import at.hrechny.predictionsbot.database.entity.CompetitionEntity
import at.hrechny.predictionsbot.database.entity.SeasonEntity
import at.hrechny.predictionsbot.controller.model.competition.CompetitionCreateRequestDto
import at.hrechny.predictionsbot.controller.model.competition.CompetitionResponseDto
import jakarta.inject.Singleton
import org.apache.commons.collections4.CollectionUtils

@Singleton
class CompetitionMapper {
    fun entityToModel(source: CompetitionEntity): CompetitionResponseDto =
        CompetitionResponseDto().apply {
            id = source.id
            name = source.name
            apiFootballId = source.apiFootballId
            active = isActive(source)
        }

    fun modelToEntity(source: CompetitionCreateRequestDto): CompetitionEntity =
        CompetitionEntity().apply {
            id = source.id
            name = source.name
            apiFootballId = source.apiFootballId
        }

    fun isActive(entity: CompetitionEntity): Boolean =
        CollectionUtils.isNotEmpty(entity.seasons) && entity.seasons.any(SeasonEntity::isActive)
}
