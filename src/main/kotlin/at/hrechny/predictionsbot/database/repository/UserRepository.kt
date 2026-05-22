package at.hrechny.predictionsbot.database.repository

import at.hrechny.predictionsbot.database.entity.UserEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.util.Optional
import java.util.UUID

@Singleton
class UserRepository(
    private val entityManager: EntityManager,
) {
    @Transactional
    fun save(entity: UserEntity): UserEntity = entityManager.merge(entity)

    fun findById(id: Long): Optional<UserEntity> = Optional.ofNullable(entityManager.find(UserEntity::class.java, id))

    fun findByIdAndActiveIsTrue(id: Long): Optional<UserEntity> = entityManager
        .createQuery("select u from UserEntity u where u.id = :id and u.active = true", UserEntity::class.java)
        .setParameter("id", id)
        .resultStream
        .findFirst()

    fun findAllByActiveIsTrue(): List<UserEntity> =
        entityManager.createQuery("select u from UserEntity u where u.active = true", UserEntity::class.java).resultList

    fun findAllActiveByCompetitionsId(competitionId: UUID): List<UserEntity> = entityManager
        .createQuery(
            "select u from UserEntity u join u.competitions c where u.active = true and c.id = :competitionId",
            UserEntity::class.java,
        )
        .setParameter("competitionId", competitionId)
        .resultList
}
