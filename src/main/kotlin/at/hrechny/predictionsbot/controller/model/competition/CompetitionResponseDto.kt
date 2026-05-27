package at.hrechny.predictionsbot.controller.model.competition

import io.micronaut.core.annotation.Introspected
import java.util.UUID

@Introspected
class CompetitionResponseDto {
    var id: UUID? = null
    var name: String? = null
    var connectorIds: MutableMap<String, String> = mutableMapOf()
    var active: Boolean = false

    fun isActive(): Boolean = active
}
