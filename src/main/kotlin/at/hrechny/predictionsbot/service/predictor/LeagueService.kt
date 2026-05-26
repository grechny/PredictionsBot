package at.hrechny.predictionsbot.service.predictor

import at.hrechny.predictionsbot.database.entity.LeagueEntity
import at.hrechny.predictionsbot.database.entity.UserEntity
import at.hrechny.predictionsbot.database.repository.CompetitionRepository
import at.hrechny.predictionsbot.database.repository.LeagueRepository
import at.hrechny.predictionsbot.database.repository.UserRepository
import at.hrechny.predictionsbot.exception.InputValidationException
import at.hrechny.predictionsbot.controller.model.league.LeagueCreateRequestDto
import at.hrechny.predictionsbot.controller.model.league.LeagueResponseDto
import at.hrechny.predictionsbot.controller.model.league.LeagueUpdateRequestDto
import jakarta.inject.Singleton
import io.micronaut.transaction.annotation.Transactional
import java.util.UUID
import org.slf4j.LoggerFactory

@Singleton
open class LeagueService(
    private val leagueRepository: LeagueRepository,
    private val userRepository: UserRepository,
    private val competitionRepository: CompetitionRepository,
    private val leagueRules: LeagueRules,
) {
    @Transactional
    open fun create(userId: Long?, leagueRequest: LeagueCreateRequestDto?): LeagueResponseDto {
        log.info("Creating new league {} by user {}", leagueRequest!!.name, userId)

        leagueRules.validateName(leagueRequest.name)
        val adminUser = getUser(userId!!)
        val competitions = competitionRepository.findAll()
            .filter { competitionEntity -> leagueRequest.competitions.contains(competitionEntity.id) }
            .toMutableList()

        val leagueEntity = leagueRepository.save(
            LeagueEntity().apply {
                name = leagueRequest.name
                this.adminUser = adminUser
                users = mutableSetOf(adminUser)
                this.competitions = competitions
            },
        )
        log.info("Created new league {}", leagueEntity.id)
        return LeagueResponseDto(leagueEntity.id)
    }

    @Transactional
    open fun update(userId: Long?, leagueId: UUID, leagueRequest: LeagueUpdateRequestDto?): LeagueResponseDto? {
        val adminUser = getUser(userId!!)
        val league = leagueRepository.findById(leagueId).orElseThrow()
        leagueRules.ensureLeagueAdmin(league.adminUser == adminUser)
        return null
    }

    @Transactional
    open fun join(userId: Long?, leagueId: UUID): LeagueResponseDto? = null

    @Transactional
    open fun delete(userId: Long?, leagueId: UUID): LeagueResponseDto? = null

    @Transactional
    open fun joinLeague(leagueIdString: String, userId: Long?): LeagueEntity {
        val leagueId = try {
            UUID.fromString(leagueIdString)
        } catch (exception: IllegalArgumentException) {
            log.warn("Invalid league id {}", leagueIdString)
            throw InputValidationException("Invalid league id")
        }

        val league = leagueRepository.findById(leagueId)
        if (league.isEmpty) {
            throw InputValidationException("Invalid league id")
        }

        val leagueEntity = league.get()
        if (leagueEntity.users.any { user -> user.id == userId }) {
            throw InputValidationException("User is already a member of this league")
        }

        val user = userRepository.findById(userId!!).orElseThrow()
        leagueRules.ensureLeagueLimit(user.leagues.size)

        leagueEntity.users.add(user)
        leagueRepository.save(leagueEntity)
        log.info("User {} added to the league {}", userId, leagueId)
        return leagueEntity
    }

    private fun getUser(userId: Long): UserEntity {
        val user = userRepository.findById(userId).orElseThrow()
        leagueRules.ensureLeagueLimit(user.leagues.size)
        return user
    }

    private companion object {
        val log = LoggerFactory.getLogger(LeagueService::class.java)
    }
}
