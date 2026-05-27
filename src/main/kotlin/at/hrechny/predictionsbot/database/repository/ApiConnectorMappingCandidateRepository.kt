package at.hrechny.predictionsbot.database.repository

import at.hrechny.predictionsbot.database.entity.ApiConnectorMappingCandidateEntity
import at.hrechny.predictionsbot.database.model.ApiConnectorMappingCandidateStatus
import at.hrechny.predictionsbot.database.model.ApiConnectorValueType
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.util.Optional
import java.util.UUID

@Singleton
@Transactional
class ApiConnectorMappingCandidateRepository(
    private val entityManager: EntityManager,
) {
    @Transactional
    fun save(entity: ApiConnectorMappingCandidateEntity): ApiConnectorMappingCandidateEntity =
        entityManager.merge(entity)

    fun findById(id: UUID): Optional<ApiConnectorMappingCandidateEntity> =
        Optional.ofNullable(entityManager.find(ApiConnectorMappingCandidateEntity::class.java, id))

    fun findAll(status: ApiConnectorMappingCandidateStatus?): List<ApiConnectorMappingCandidateEntity> {
        val statusFilter = status?.let { "where p.status = :status" } ?: ""
        val query = entityManager.createQuery(
            """
            select p
            from ApiConnectorMappingCandidateEntity p
            $statusFilter
            order by p.lastSeenAt desc
            """.trimIndent(),
            ApiConnectorMappingCandidateEntity::class.java,
        )
        if (status != null) {
            query.setParameter("status", status)
        }
        return query.resultList
    }

    fun findByConnectorCodeAndValueTypeAndRawValue(
        connectorCode: String,
        valueType: ApiConnectorValueType,
        rawValue: String,
    ): Optional<ApiConnectorMappingCandidateEntity> = entityManager
        .createQuery(
            """
            select p
            from ApiConnectorMappingCandidateEntity p
            where p.connectorCode = :connectorCode
              and p.valueType = :valueType
              and p.rawValue = :rawValue
            """.trimIndent(),
            ApiConnectorMappingCandidateEntity::class.java,
        )
        .setParameter("connectorCode", connectorCode)
        .setParameter("valueType", valueType)
        .setParameter("rawValue", rawValue)
        .setMaxResults(1)
        .resultStream
        .findFirst()
}
