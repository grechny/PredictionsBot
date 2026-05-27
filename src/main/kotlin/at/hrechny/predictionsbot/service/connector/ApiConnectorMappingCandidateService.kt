package at.hrechny.predictionsbot.service.connector

import at.hrechny.predictionsbot.database.entity.ApiConnectorMappingCandidateEntity
import at.hrechny.predictionsbot.database.model.ApiConnectorMappingCandidateStatus
import at.hrechny.predictionsbot.database.model.ApiConnectorValueType
import at.hrechny.predictionsbot.database.repository.ApiConnectorMappingCandidateRepository
import at.hrechny.predictionsbot.exception.NotFoundException
import at.hrechny.predictionsbot.exception.RequestValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Singleton
@Transactional
open class ApiConnectorMappingCandidateService(
    private val apiConnectorMappingCandidateRepository: ApiConnectorMappingCandidateRepository,
) {
    @JvmOverloads
    open fun recordCandidate(
        connectorCode: String,
        valueType: ApiConnectorValueType,
        rawValue: String,
        contextJson: String? = null,
        suggestedValue: String? = null,
        suggestionConfidence: Int? = null,
        suggestionSource: String? = null,
    ): ApiConnectorMappingCandidateEntity {
        val now = Instant.now()
        val candidate = apiConnectorMappingCandidateRepository
            .findByConnectorCodeAndValueTypeAndRawValue(connectorCode, valueType, rawValue)
            .orElseGet {
                ApiConnectorMappingCandidateEntity().apply {
                    this.connectorCode = connectorCode
                    this.valueType = valueType
                    this.rawValue = rawValue
                    this.status = ApiConnectorMappingCandidateStatus.PENDING
                    this.firstSeenAt = now
                }
            }
        candidate.contextJson = contextJson ?: candidate.contextJson
        candidate.suggestedValue = suggestedValue ?: candidate.suggestedValue
        candidate.suggestionConfidence = suggestionConfidence ?: candidate.suggestionConfidence
        candidate.suggestionSource = suggestionSource ?: candidate.suggestionSource
        candidate.lastSeenAt = now
        return apiConnectorMappingCandidateRepository.save(candidate)
    }

    open fun findApprovedValue(
        connectorCode: String,
        valueType: ApiConnectorValueType,
        rawValue: String,
    ): Optional<String> =
        apiConnectorMappingCandidateRepository
            .findByConnectorCodeAndValueTypeAndRawValue(connectorCode, valueType, rawValue)
            .filter { candidate -> candidate.status == ApiConnectorMappingCandidateStatus.APPROVED }
            .map(ApiConnectorMappingCandidateEntity::suggestedValue)

    open fun getCandidates(status: ApiConnectorMappingCandidateStatus?): List<ApiConnectorMappingCandidateEntity> =
        apiConnectorMappingCandidateRepository.findAll(status ?: ApiConnectorMappingCandidateStatus.PENDING)

    open fun decideCandidate(
        candidateId: UUID,
        status: ApiConnectorMappingCandidateStatus,
        suggestedValue: String?,
        decidedBy: String?,
    ): ApiConnectorMappingCandidateEntity {
        val candidate = apiConnectorMappingCandidateRepository.findById(candidateId)
            .orElseThrow { NotFoundException("Mapping candidate $candidateId not found") }

        if (status == ApiConnectorMappingCandidateStatus.APPROVED && suggestedValue.isNullOrBlank()) {
            throw RequestValidationException("Approved mapping candidate requires suggested value")
        }

        candidate.status = status
        if (!suggestedValue.isNullOrBlank()) {
            candidate.suggestedValue = suggestedValue
        }
        candidate.decidedAt = Instant.now()
        candidate.decidedBy = decidedBy
        return apiConnectorMappingCandidateRepository.save(candidate)
    }
}
