package at.hrechny.predictionsbot.model

import io.micronaut.core.annotation.Introspected
import java.util.UUID

@Introspected
class LeagueRequest {
    var userId: Long? = null
    var name: String? = null
    var competitions: List<UUID> = emptyList()
}
