package at.hrechny.predictionsbot.controller

import at.hrechny.predictionsbot.controller.model.connector.ApiConnectorMappingCandidateDecisionRequestDto
import at.hrechny.predictionsbot.controller.model.connector.ApiConnectorMappingCandidateResponseDto
import at.hrechny.predictionsbot.database.entity.ApiConnectorMappingCandidateEntity
import at.hrechny.predictionsbot.database.model.ApiConnectorMappingCandidateStatus
import at.hrechny.predictionsbot.service.connector.ApiConnectorMappingCandidateService
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.validation.Valid
import java.util.UUID

@Controller
@ExecuteOn(TaskExecutors.BLOCKING)
open class ApiConnectorAdminController(
    private val apiConnectorMappingCandidateService: ApiConnectorMappingCandidateService,
) {
    @Get(
        value = "/\${secrets.adminKey:}/api-connectors/mapping-candidates",
        produces = [MediaType.APPLICATION_JSON],
    )
    open fun getMappingCandidates(
        @Nullable @QueryValue("status") status: ApiConnectorMappingCandidateStatus?,
    ): HttpResponse<List<ApiConnectorMappingCandidateResponseDto>> =
        HttpResponse.ok(
            apiConnectorMappingCandidateService.getCandidates(status)
                .map { candidate -> candidate.toResponse() },
        )

    @Post(
        value = "/\${secrets.adminKey:}/api-connectors/mapping-candidates/{candidateId}/decision",
        consumes = [MediaType.APPLICATION_JSON],
        produces = [MediaType.APPLICATION_JSON],
    )
    open fun decideMappingCandidate(
        @PathVariable("candidateId") candidateId: UUID,
        @Valid @Body request: ApiConnectorMappingCandidateDecisionRequestDto,
    ): HttpResponse<ApiConnectorMappingCandidateResponseDto> =
        HttpResponse.ok(
            apiConnectorMappingCandidateService.decideCandidate(
                candidateId,
                request.status!!,
                request.suggestedValue,
                request.decidedBy,
            ).toResponse(),
        )

    private fun ApiConnectorMappingCandidateEntity.toResponse() =
        ApiConnectorMappingCandidateResponseDto().apply {
            id = this@toResponse.id
            connectorCode = this@toResponse.connectorCode
            valueType = this@toResponse.valueType
            rawValue = this@toResponse.rawValue
            contextJson = this@toResponse.contextJson
            suggestedValue = this@toResponse.suggestedValue
            suggestionConfidence = this@toResponse.suggestionConfidence
            suggestionSource = this@toResponse.suggestionSource
            status = this@toResponse.status
            firstSeenAt = this@toResponse.firstSeenAt
            lastSeenAt = this@toResponse.lastSeenAt
            decidedAt = this@toResponse.decidedAt
            decidedBy = this@toResponse.decidedBy
        }
}
