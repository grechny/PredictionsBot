package at.hrechny.predictionsbot.mapper

import at.hrechny.predictionsbot.database.entity.CompetitionEntity
import at.hrechny.predictionsbot.database.entity.SeasonEntity
import at.hrechny.predictionsbot.controller.model.competition.SeasonCreateRequestDto
import at.hrechny.predictionsbot.controller.model.competition.SeasonResponseDto
import at.hrechny.predictionsbot.controller.model.competition.SeasonUpdateRequestDto
import jakarta.inject.Singleton

@Singleton
class SeasonMapper(
    private val yearMapper: YearMapper,
) {
    fun entityToModel(source: SeasonEntity): SeasonResponseDto =
        SeasonResponseDto().apply {
            id = source.id
            year = yearMapper.asYear(source.year)
            competition = source.competition?.name
            active = source.active
        }

    fun updateEntity(entity: SeasonEntity, model: SeasonUpdateRequestDto) {
        if (model.id != null) {
            entity.id = model.id
        }
        if (model.year != null) {
            entity.year = yearMapper.asString(model.year)
        }
        entity.active = model.active
    }

    fun modelToEntity(competition: CompetitionEntity, season: SeasonCreateRequestDto): SeasonEntity =
        SeasonEntity().apply {
            id = season.id
            this.competition = competition
            year = yearMapper.asString(season.year)
            active = season.active
        }
}
