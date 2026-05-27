package at.hrechny.predictionsbot.controller.model.connector

import at.hrechny.predictionsbot.database.model.ApiConnectorEntityType
import io.micronaut.core.annotation.Introspected
import java.util.UUID

@Introspected
class ApiConnectorIdResponseDto {
    var connectorCode: String? = null
    var entityType: ApiConnectorEntityType? = null
    var connectorEntityId: String? = null
    var internalId: UUID? = null
}
