package at.hrechny.predictionsbot.controller

import at.hrechny.predictionsbot.controller.model.connector.ApiConnectorIdResponseDto
import at.hrechny.predictionsbot.database.entity.ApiConnectorIdEntity
import at.hrechny.predictionsbot.database.model.ApiConnectorEntityType
import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport
import at.hrechny.predictionsbot.service.connector.ApiConnectorService
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import java.util.UUID

@Controller
@ExecuteOn(TaskExecutors.BLOCKING)
@EnableErrorReport
open class ApiConnectorController(
    private val apiConnectorService: ApiConnectorService,
) {
    @Get(
        value = "/\${secrets.adminKey:}/api-connectors/ids/{entityType}/{internalId}",
        produces = [MediaType.APPLICATION_JSON],
    )
    open fun getConnectorIds(
        @PathVariable("entityType") entityType: ApiConnectorEntityType,
        @PathVariable("internalId") internalId: UUID,
    ): HttpResponse<List<ApiConnectorIdResponseDto>> =
        HttpResponse.ok(
            apiConnectorService.findConnectorIdMappings(internalId, entityType)
                .map { entity -> entity.toApiConnectorIdResponse() },
        )

    private fun ApiConnectorIdEntity.toApiConnectorIdResponse(): ApiConnectorIdResponseDto =
        ApiConnectorIdResponseDto().apply {
            connectorCode = this@toApiConnectorIdResponse.connectorCode
            entityType = this@toApiConnectorIdResponse.entityType
            connectorEntityId = this@toApiConnectorIdResponse.connectorEntityId
            internalId = this@toApiConnectorIdResponse.internalId
        }
}
