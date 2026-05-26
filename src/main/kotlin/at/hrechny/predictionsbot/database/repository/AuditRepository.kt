package at.hrechny.predictionsbot.database.repository

import at.hrechny.predictionsbot.database.entity.AuditEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import io.micronaut.transaction.annotation.Transactional
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Singleton
@Transactional
class AuditRepository(
    private val entityManager: EntityManager,
) {
    @Transactional
    fun save(entity: AuditEntity): AuditEntity = entityManager.merge(entity)

    fun findById(id: UUID): Optional<AuditEntity> = Optional.ofNullable(entityManager.find(AuditEntity::class.java, id))

    fun countAllByConnectorNameAndRequestDateAfter(
        connectorName: String,
        date: Instant,
    ): Int = entityManager
        .createQuery(
            """
            select count(a)
            from AuditEntity a
            where a.connectorName = :connectorName
              and a.requestDate > :date
            """.trimIndent(),
            Long::class.javaObjectType,
        )
        .setParameter("connectorName", connectorName)
        .setParameter("date", date)
        .singleResult
        .toInt()
}
