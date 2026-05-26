package at.hrechny.predictionsbot.controller.model.league

import io.micronaut.core.annotation.Introspected
import java.util.UUID

@Introspected
class LeagueUpdateRequestDto {
    var userId: Long? = null
    var name: String? = null
    var competitions: List<UUID> = emptyList()
}
