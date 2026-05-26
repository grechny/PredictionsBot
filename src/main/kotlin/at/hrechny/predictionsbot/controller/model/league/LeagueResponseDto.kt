package at.hrechny.predictionsbot.controller.model.league

import io.micronaut.core.annotation.Introspected
import java.util.UUID

@Introspected
class LeagueResponseDto(
    var id: UUID? = null,
)
