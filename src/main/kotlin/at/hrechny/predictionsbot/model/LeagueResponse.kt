package at.hrechny.predictionsbot.model

import io.micronaut.core.annotation.Introspected
import java.util.UUID

@Introspected
class LeagueResponse(
    var id: UUID? = null,
)
