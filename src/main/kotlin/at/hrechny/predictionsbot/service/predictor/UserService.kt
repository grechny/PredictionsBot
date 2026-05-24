package at.hrechny.predictionsbot.service.predictor

import at.hrechny.predictionsbot.database.entity.CompetitionEntity
import at.hrechny.predictionsbot.database.entity.UserEntity
import at.hrechny.predictionsbot.database.repository.UserRepository
import at.hrechny.predictionsbot.exception.NotFoundException
import at.hrechny.predictionsbot.exception.RequestValidationException
import at.hrechny.predictionsbot.util.NameUtils
import jakarta.inject.Singleton
import io.micronaut.transaction.annotation.Transactional
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

@Singleton
open class UserService(
    private val userRepository: UserRepository,
) {
    @Transactional
    open fun createUser(userId: Long, username: String?, language: String?) {
        log.info("Creating new user {} with id {}", username, userId)
        val userEntity = userRepository.findById(userId).orElse(UserEntity())
        userEntity.id = userId
        userEntity.initialLanguage = Locale.forLanguageTag(language)
        userEntity.username = userEntity.username ?: NameUtils.formatName(username)
        userEntity.timezone = userEntity.timezone ?: ZoneOffset.UTC
        userEntity.active = true
        userRepository.save(userEntity)
        log.info("Created user {} with id {}", userEntity.username, userEntity.id)
    }

    @Transactional
    open fun saveUser(userEntity: UserEntity) {
        userRepository.save(userEntity)
        log.info("Added/updated user {} with id {}", userEntity.username, userEntity.id)
    }

    open fun getUser(userId: Long): UserEntity =
        userRepository.findByIdAndActiveIsTrue(userId).orElseThrow { NotFoundException("User not found") }

    open fun getUsers(): List<UserEntity> = userRepository.findAllByActiveIsTrue()

    open fun getUsers(competitionId: UUID): List<UserEntity> = userRepository.findAllActiveByCompetitionsId(competitionId)

    @Transactional
    open fun updateUsername(userId: Long, username: String?) {
        log.info("Updating username to '{}' for the {}", username, userId)
        if (StringUtils.isBlank(username)) {
            throw RequestValidationException("Username can not be null")
        }

        val userEntity = getUser(userId)
        userEntity.username = username
        saveUser(userEntity)
    }

    @Transactional
    open fun updateTimeZone(userId: Long, zoneId: String) {
        log.info("Updating time zone to '{}' for the {}", zoneId, userId)
        val userEntity = getUser(userId)
        userEntity.timezone = ZoneId.of(zoneId)
        saveUser(userEntity)
    }

    @Transactional
    open fun updateLanguage(userId: Long, language: String?) {
        log.info("Updating language to '{}' for the {}", language, userId)
        val userEntity = getUser(userId)
        userEntity.language = language?.let(Locale::forLanguageTag)
        saveUser(userEntity)
    }

    @Transactional
    open fun updateCompetitions(userId: Long, competitionId: UUID) {
        log.info("Updating competitions for the {}", userId)
        val userEntity = getUser(userId)
        val competition = userEntity.competitions.firstOrNull { it.id == competitionId }
        if (competition != null) {
            userEntity.competitions.remove(competition)
        } else {
            userEntity.competitions.add(CompetitionEntity().apply { id = competitionId })
        }
        saveUser(userEntity)
    }

    @Transactional
    open fun deactivate(userId: Long) {
        log.info("Deactivating user {}", userId)
        val userEntity = getUser(userId)
        userEntity.active = false
        saveUser(userEntity)
    }

    private companion object {
        val log = LoggerFactory.getLogger(UserService::class.java)
    }
}
