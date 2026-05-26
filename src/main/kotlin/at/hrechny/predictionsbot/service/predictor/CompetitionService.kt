package at.hrechny.predictionsbot.service.predictor

import at.hrechny.predictionsbot.connector.ApiConnector
import at.hrechny.predictionsbot.connector.model.FixtureSyncDto
import at.hrechny.predictionsbot.connector.model.FixtureSyncStatus
import at.hrechny.predictionsbot.connector.model.TeamSyncDto
import at.hrechny.predictionsbot.controller.model.competition.CompetitionCreateRequestDto
import at.hrechny.predictionsbot.controller.model.competition.CompetitionResponseDto
import at.hrechny.predictionsbot.controller.model.competition.SeasonCreateRequestDto
import at.hrechny.predictionsbot.controller.model.competition.SeasonResponseDto
import at.hrechny.predictionsbot.controller.model.competition.SeasonUpdateRequestDto
import at.hrechny.predictionsbot.database.entity.CompetitionEntity
import at.hrechny.predictionsbot.database.entity.MatchEntity
import at.hrechny.predictionsbot.database.entity.RoundEntity
import at.hrechny.predictionsbot.database.entity.SeasonEntity
import at.hrechny.predictionsbot.database.entity.TeamEntity
import at.hrechny.predictionsbot.database.model.ApiConnectorEntityType
import at.hrechny.predictionsbot.database.model.MatchStatus
import at.hrechny.predictionsbot.database.model.RoundType
import at.hrechny.predictionsbot.database.repository.CompetitionRepository
import at.hrechny.predictionsbot.database.repository.MatchRepository
import at.hrechny.predictionsbot.database.repository.SeasonRepository
import at.hrechny.predictionsbot.database.repository.TeamRepository
import at.hrechny.predictionsbot.exception.FixturesSynchronizationException
import at.hrechny.predictionsbot.exception.NotFoundException
import at.hrechny.predictionsbot.exception.RequestValidationException
import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport
import at.hrechny.predictionsbot.mapper.CompetitionMapper
import at.hrechny.predictionsbot.mapper.SeasonMapper
import jakarta.inject.Singleton
import io.micronaut.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import org.slf4j.LoggerFactory

@Singleton
@Transactional
open class CompetitionService(
    private val seasonMapper: SeasonMapper?,
    private val seasonRepository: SeasonRepository?,
    private val competitionMapper: CompetitionMapper?,
    private val competitionRepository: CompetitionRepository?,
    private val teamRepository: TeamRepository?,
    private val matchRepository: MatchRepository?,
    private val apiConnector: ApiConnector?,
    private val apiConnectorService: ApiConnectorService?,
) {
    open fun addCompetition(competition: CompetitionCreateRequestDto): UUID {
        log.info("Adding the new competition: {}", competition)
        val entity = competitionRepository!!.save(competitionMapper!!.modelToEntity(competition))
        log.info("The competition has been successfully stored: {}", entity.id)
        return entity.id!!
    }

    open fun getCompetition(competitionId: UUID): CompetitionResponseDto {
        val entity: CompetitionEntity = competitionRepository!!.findById(competitionId)
            .orElseThrow { NotFoundException("Competition with the ID $competitionId not found") }
        return competitionMapper!!.entityToModel(entity)
    }

    open fun getCompetitions(): List<CompetitionResponseDto> =
        competitionRepository!!.findAll().map(competitionMapper!!::entityToModel)

    open fun addSeason(competitionId: UUID, season: SeasonCreateRequestDto): UUID {
        log.info("Adding the new season for the competition {}: {}", competitionId, season)
        val competitionEntity = competitionRepository!!.findById(competitionId)
            .orElseThrow { NotFoundException("Competition with the ID $competitionId not found") }

        validateActiveSeasons(competitionId, season)
        val seasonEntity = seasonRepository!!.save(seasonMapper!!.modelToEntity(competitionEntity, season))
        log.info("The season has been successfully stored")
        return seasonEntity.id!!
    }

    open fun updateSeason(competitionId: UUID, season: SeasonUpdateRequestDto) {
        log.info("Updating the season {} for the competition {}", season.id, competitionId)
        val seasonEntity = seasonRepository!!.findById(season.id!!).orElse(null)
        if (seasonEntity == null || seasonEntity.competition!!.id != competitionId) {
            throw NotFoundException("Season with the ID ${season.id} not found")
        }

        validateActiveSeasons(competitionId, season)
        seasonMapper!!.updateEntity(seasonEntity, season)
        seasonRepository.save(seasonEntity)
        log.info("The season {} has been successfully updated", seasonEntity.id)
    }

    open fun getSeason(seasonId: UUID): SeasonEntity =
        seasonRepository!!.findById(seasonId).orElseThrow { NotFoundException("Season $seasonId not found") }

    open fun getSeasons(competitionId: UUID): List<SeasonResponseDto> =
        seasonRepository!!.findAllByCompetitionId(competitionId).map(seasonMapper!!::entityToModel)

    open fun getCurrentSeason(competitionId: UUID): SeasonEntity =
        seasonRepository!!.findFirstByCompetitionIdAndActiveIsTrue(competitionId)
            .orElseThrow { NotFoundException("No active season found for the competition $competitionId") }

    open fun getUpcomingRound(competitionId: UUID): RoundEntity? =
        matchRepository!!.findUpcoming(getCurrentSeason(competitionId)).map(MatchEntity::round).orElse(null)

    open fun getFixtures(from: Instant, to: Instant): List<MatchEntity> =
        matchRepository!!.findAllByStartTimeAfterAndStartTimeBeforeOrderByStartTimeAsc(from, to)

    open fun getRound(competitionId: UUID, orderNumber: Int): RoundEntity? {
        val season = getCurrentSeason(competitionId)
        return season.rounds.firstOrNull { roundEntity -> orderNumber == roundEntity.orderNumber }
    }

    open fun getActiveSeasons(): List<SeasonEntity> {
        log.info("Starting to refresh fixtures data for the all active competitions")
        return seasonRepository!!.findAllByActiveIsTrue()
    }

    @EnableErrorReport
    open fun refreshActiveFixtures(seasonId: UUID): Boolean {
        val seasonEntity = getSeason(seasonId)
        val activeMatches = matchRepository!!.findAllActive(seasonEntity)
        if (activeMatches.isEmpty()) {
            return true
        }

        val fixtureIds = activeMatches.map { match -> match.apiFootballId!!.toString() }
        val fixtures = apiConnector!!.getFixturesByExternalIds(fixtureIds)
        refreshFixtures(fixtures, seasonEntity)
        return true
    }

    open fun hasNonFinishedMatches(seasonEntity: SeasonEntity): Boolean =
        seasonEntity.rounds
            .flatMap { round -> round.matches }
            .any { match -> match.status in ACTIVE_REFRESH_STATUSES }

    open fun refreshFixtures(seasonEntity: SeasonEntity) {
        val managedSeasonEntity = getSeason(seasonEntity.id!!)
        log.info("Start refreshing fixtures data for the season {}", managedSeasonEntity.id)
        val fixtures = apiConnector!!.getSeasonFixtures(
            managedSeasonEntity.competition!!.apiFootballId!!.toString(),
            managedSeasonEntity.year!!,
        )
        refreshFixtures(fixtures, managedSeasonEntity)
    }

    private fun refreshFixtures(fixtures: List<FixtureSyncDto>, seasonEntity: SeasonEntity) {
        val rounds = seasonEntity.rounds
        val matches = rounds.flatMap { round -> round.matches }

        fixtures.forEach { fixture ->
            var roundList = getRound(rounds, fixture.roundExternalId)
            if (roundList.isEmpty()) {
                refreshRounds(seasonEntity)
                roundList = getRound(rounds, fixture.roundExternalId)
            }

            val matchEntity = findMatchByLegacyApiFootballId(matches, fixture.externalId)
                ?: MatchEntity().apply {
                    apiFootballId = fixture.externalId.toLongOrNull()
                    homeTeam = getTeamEntity(fixture.homeTeam)
                    awayTeam = getTeamEntity(fixture.awayTeam)
                }

            val round = getRound(roundList, matchEntity.homeTeam!!, matchEntity.awayTeam!!)
            if (matchEntity.round == null) {
                matchEntity.round = round
                round.matches.add(matchEntity)
            } else if (matchEntity.round != round) {
                matchEntity.round!!.matches.remove(matchEntity)
                matchEntity.round = round
                round.matches.add(matchEntity)
            }

            matchEntity.homeTeamScore = fixture.score.home
            matchEntity.awayTeamScore = fixture.score.away
            matchEntity.status = mapStatus(fixture.status)
            matchEntity.startTime = if (fixture.startTime != null && matchEntity.status != MatchStatus.NOT_DEFINED) {
                fixture.startTime
            } else {
                null
            }
        }
        val savedSeasonEntity: SeasonEntity? = seasonRepository!!.save(seasonEntity)
        recordApiConnectorIds(savedSeasonEntity ?: seasonEntity)
        log.info("Fixtures have been successfully updated for the season {}", seasonEntity.id)
    }

    private fun mapStatus(status: FixtureSyncStatus): MatchStatus =
        when (status) {
            FixtureSyncStatus.PLANNED -> MatchStatus.PLANNED
            FixtureSyncStatus.STARTED -> MatchStatus.STARTED
            FixtureSyncStatus.NOT_DEFINED -> MatchStatus.NOT_DEFINED
            FixtureSyncStatus.FINISHED -> MatchStatus.FINISHED
        }

    private fun getTeamEntity(team: TeamSyncDto): TeamEntity {
        val legacyApiFootballId = team.externalId.toLongOrNull()
        if (legacyApiFootballId != null) {
            val teamEntityOptional = teamRepository!!.findFirstByApiFootballId(legacyApiFootballId)
            if (teamEntityOptional.isPresent) {
                var teamEntity = teamEntityOptional.get()
                if (teamEntity.name == team.name && teamEntity.logoUrl == team.logoUrl) {
                    return teamEntity
                }
                teamEntity.name = team.name
                teamEntity.logoUrl = team.logoUrl
                teamEntity = teamRepository.save(teamEntity)
                log.info("Team {} has been updated: {}", teamEntity.name, teamEntity.id)
                return teamEntity
            }
        }
        return createTeam(team, legacyApiFootballId)
    }

    private fun createTeam(team: TeamSyncDto, legacyApiFootballId: Long?): TeamEntity {
        var teamEntity = TeamEntity().apply {
            name = team.name
            apiFootballId = legacyApiFootballId
            logoUrl = team.logoUrl
        }
        teamEntity = teamRepository!!.save(teamEntity)
        log.info("New team {} has been created: {}", teamEntity.name, teamEntity.id)
        return teamEntity
    }

    private fun validateActiveSeasons(competitionId: UUID, season: SeasonCreateRequestDto) {
        if (season.isActive()) {
            val activeSeasons = seasonRepository!!.countAllByActiveIsTrueAndCompetitionId(competitionId)
            if (activeSeasons > 0) {
                throw RequestValidationException("Not possible to add more than one active season")
            }
        }
    }

    private fun refreshRounds(seasonEntity: SeasonEntity) {
        val actualRounds = apiConnector!!.getRounds(
            seasonEntity.competition!!.apiFootballId!!.toString(),
            seasonEntity.year!!,
        )
        val roundEntities = seasonEntity.rounds
        val lastRound = roundEntities.maxByOrNull(RoundEntity::orderNumber)
        val nextOrderNumber = AtomicInteger(if (lastRound != null) lastRound.orderNumber + 1 else 1)
        for (round in actualRounds) {
            if (roundEntities.none { roundEntity -> roundEntity.apiFootballId == round.externalId }) {
                RoundType.getByAlias(round.name).forEach { roundType ->
                    roundEntities.add(
                        RoundEntity().apply {
                            type = roundType
                            orderNumber = round.orderNumber ?: getOrderNumber(round.name, roundType, nextOrderNumber)
                            apiFootballId = round.externalId
                            this.season = seasonEntity
                        },
                    )
                }
            }
        }
    }

    private fun validateActiveSeasons(competitionId: UUID, season: SeasonUpdateRequestDto) {
        if (season.isActive()) {
            val activeSeasons = seasonRepository!!.countAllByActiveIsTrueAndCompetitionId(competitionId)
            if (activeSeasons > 0) {
                throw RequestValidationException("Not possible to add more than one active season")
            }
        }
    }

    private fun getOrderNumber(roundName: String, roundType: RoundType, nextOrderNumber: AtomicInteger): Int {
        val pattern = Pattern.compile("^(.+) - (\\d+)$").matcher(roundName)
        if (pattern.matches()) {
            return pattern.group(2).toInt()
        }
        return if (roundType == RoundType.QUALIFYING) 0 else nextOrderNumber.getAndIncrement()
    }

    private fun getRound(rounds: List<RoundEntity>, apiFootballId: String): List<RoundEntity> =
        rounds.filter { roundEntity -> roundEntity.apiFootballId == apiFootballId }

    // Phase 1 still merges existing records through legacy API-Football columns while connector IDs are backfilled.
    private fun findMatchByLegacyApiFootballId(
        matches: List<MatchEntity>,
        externalId: String,
    ): MatchEntity? =
        matches.firstOrNull { match -> match.apiFootballId?.toString() == externalId }

    private fun recordApiConnectorIds(seasonEntity: SeasonEntity) {
        val connectorService = apiConnectorService ?: return
        val connectorName = connectorName()
        val competition = seasonEntity.competition
        if (competition?.id != null && competition.apiFootballId != null) {
            connectorService.upsertId(
                connectorName,
                ApiConnectorEntityType.COMPETITION,
                competition.apiFootballId!!.toString(),
                scopeGlobal(connectorService),
                competition.id!!,
            )
        }
        if (competition?.id != null && seasonEntity.id != null && seasonEntity.year != null) {
            connectorService.upsertId(
                connectorName,
                ApiConnectorEntityType.SEASON,
                seasonEntity.year!!,
                scopeCompetition(connectorService, competition.id!!),
                seasonEntity.id!!,
            )
        }
        seasonEntity.rounds.forEach { round ->
            if (round.id != null && round.apiFootballId != null && seasonEntity.id != null) {
                connectorService.upsertId(
                    connectorName,
                    ApiConnectorEntityType.ROUND,
                    round.apiFootballId!!,
                    scopeSeason(connectorService, seasonEntity.id!!),
                    round.id!!,
                )
            }
            round.matches.forEach { match ->
                if (match.id != null && match.apiFootballId != null && seasonEntity.id != null) {
                    connectorService.upsertId(
                        connectorName,
                        ApiConnectorEntityType.MATCH,
                        match.apiFootballId!!.toString(),
                        scopeSeason(connectorService, seasonEntity.id!!),
                        match.id!!,
                    )
                }
                recordTeamMapping(connectorService, connectorName, match.homeTeam)
                recordTeamMapping(connectorService, connectorName, match.awayTeam)
            }
        }
    }

    private fun recordTeamMapping(
        connectorService: ApiConnectorService,
        connectorName: String,
        team: TeamEntity?,
    ) {
        if (team?.id != null && team.apiFootballId != null) {
            connectorService.upsertId(
                connectorName,
                ApiConnectorEntityType.TEAM,
                team.apiFootballId!!.toString(),
                scopeGlobal(connectorService),
                team.id!!,
            )
        }
    }

    private fun connectorName(): String {
        val connectorName: String? = apiConnector!!.name
        return connectorName?.takeIf(String::isNotBlank) ?: API_FOOTBALL_CONNECTOR_NAME
    }

    private fun scopeGlobal(connectorService: ApiConnectorService): String {
        val scope: String? = connectorService.scopeGlobal()
        return scope ?: GLOBAL_SCOPE
    }

    private fun scopeCompetition(connectorService: ApiConnectorService, competitionId: UUID): String {
        val scope: String? = connectorService.scopeCompetition(competitionId)
        return scope ?: "competition:$competitionId"
    }

    private fun scopeSeason(connectorService: ApiConnectorService, seasonId: UUID): String {
        val scope: String? = connectorService.scopeSeason(seasonId)
        return scope ?: "season:$seasonId"
    }

    private fun getRound(roundList: List<RoundEntity>, homeTeam: TeamEntity, awayTeam: TeamEntity): RoundEntity {
        if (roundList.isEmpty()) {
            throw FixturesSynchronizationException("Round could not be find for the match between ${homeTeam.name} and ${awayTeam.name}")
        }

        if (roundList.size == 1) {
            return roundList[0]
        }

        if (roundList.size == 2) {
            val firstRound: RoundEntity
            val returnRound: RoundEntity
            if (roundList[0].type!!.name.endsWith("RETURN")) {
                firstRound = roundList[1]
                returnRound = roundList[0]
            } else {
                firstRound = roundList[0]
                returnRound = roundList[1]
            }

            val returnMatch = firstRound.matches.any { matchEntity ->
                matchEntity.homeTeam == awayTeam && matchEntity.awayTeam == homeTeam
            }
            return if (returnMatch) returnRound else firstRound
        }

        throw FixturesSynchronizationException(
            "Unexpected number of rounds found for the match between ${homeTeam.name} and ${awayTeam.name}: $roundList",
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(CompetitionService::class.java)
        const val GLOBAL_SCOPE = "global"
        const val API_FOOTBALL_CONNECTOR_NAME = "api-football"
        val ACTIVE_REFRESH_STATUSES = setOf(MatchStatus.PLANNED, MatchStatus.STARTED)
    }
}
