package at.hrechny.predictionsbot.service.predictor

import at.hrechny.predictionsbot.connector.apifootball.ApiFootballConnector
import at.hrechny.predictionsbot.connector.apifootball.exception.ApiFootballConnectorException
import at.hrechny.predictionsbot.connector.apifootball.model.Fixture
import at.hrechny.predictionsbot.connector.apifootball.model.FixtureStatusEnum
import at.hrechny.predictionsbot.connector.apifootball.model.Status
import at.hrechny.predictionsbot.connector.apifootball.model.Team
import at.hrechny.predictionsbot.database.entity.CompetitionEntity
import at.hrechny.predictionsbot.database.entity.MatchEntity
import at.hrechny.predictionsbot.database.entity.RoundEntity
import at.hrechny.predictionsbot.database.entity.SeasonEntity
import at.hrechny.predictionsbot.database.entity.TeamEntity
import at.hrechny.predictionsbot.database.model.MatchStatus
import at.hrechny.predictionsbot.database.model.RoundType
import at.hrechny.predictionsbot.database.repository.CompetitionRepository
import at.hrechny.predictionsbot.database.repository.MatchRepository
import at.hrechny.predictionsbot.database.repository.SeasonRepository
import at.hrechny.predictionsbot.database.repository.TeamRepository
import at.hrechny.predictionsbot.exception.FixturesSynchronizationException
import at.hrechny.predictionsbot.exception.NotFoundException
import at.hrechny.predictionsbot.exception.RequestValidationException
import at.hrechny.predictionsbot.mapper.CompetitionMapper
import at.hrechny.predictionsbot.mapper.SeasonMapper
import at.hrechny.predictionsbot.model.Competition
import at.hrechny.predictionsbot.model.Season
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
    private val apiFootballConnector: ApiFootballConnector?,
) {
    open fun addCompetition(competition: Competition): UUID {
        log.info("Adding the new competition: {}", competition)
        val entity = competitionRepository!!.save(competitionMapper!!.modelToEntity(competition))
        log.info("The competition has been successfully stored: {}", entity.id)
        return entity.id!!
    }

    open fun getCompetition(competitionId: UUID): Competition {
        val entity: CompetitionEntity = competitionRepository!!.findById(competitionId)
            .orElseThrow { NotFoundException("Competition with the ID $competitionId not found") }
        return competitionMapper!!.entityToModel(entity)
    }

    open fun getCompetitions(): List<Competition> =
        competitionRepository!!.findAll().map(competitionMapper!!::entityToModel)

    open fun addSeason(competitionId: UUID, season: Season): UUID {
        log.info("Adding the new season for the competition {}: {}", competitionId, season)
        val competitionEntity = competitionRepository!!.findById(competitionId)
            .orElseThrow { NotFoundException("Competition with the ID $competitionId not found") }

        validateActiveSeasons(competitionId, season)
        val seasonEntity = seasonRepository!!.save(seasonMapper!!.modelToEntity(competitionEntity, season))
        log.info("The season has been successfully stored")
        return seasonEntity.id!!
    }

    open fun updateSeason(competitionId: UUID, season: Season) {
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

    open fun getSeasons(competitionId: UUID): List<Season> =
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

    open fun refreshActiveFixtures(seasonId: UUID): Boolean {
        val seasonEntity = getSeason(seasonId)
        val activeMatches = matchRepository!!.findAllActive(seasonEntity)
        if (activeMatches.isEmpty()) {
            return true
        }

        val fixtureIds = activeMatches.map { match -> match.apiFootballId!! }
        try {
            val fixtures = apiFootballConnector!!.getFixtures(fixtureIds)
            refreshFixtures(fixtures, seasonEntity)
            return true
        } catch (exception: ApiFootballConnectorException) {
            log.error("Failed to refresh fixtures: {}", exception.message)
            return false
        }
    }

    open fun refreshFixtures(seasonEntity: SeasonEntity): Boolean = refreshFixtures(seasonEntity, false)

    open fun refreshFixturesOrThrow(seasonEntity: SeasonEntity) {
        refreshFixtures(seasonEntity, true)
    }

    private fun refreshFixtures(seasonEntity: SeasonEntity, propagateProviderErrors: Boolean): Boolean {
        val managedSeasonEntity = getSeason(seasonEntity.id!!)
        log.info("Start refreshing fixtures data for the season {}", managedSeasonEntity.id)
        try {
            val fixtures = apiFootballConnector!!.getFixtures(
                managedSeasonEntity.competition!!.apiFootballId!!,
                managedSeasonEntity.year!!,
            )
            refreshFixtures(fixtures, managedSeasonEntity)
            return true
        } catch (exception: ApiFootballConnectorException) {
            log.error("Failed to refresh fixtures for {}: {}", managedSeasonEntity.competition!!.name, exception.message)
            if (propagateProviderErrors) {
                throw exception
            }
            return false
        }
    }

    private fun refreshFixtures(fixtures: List<Fixture>, seasonEntity: SeasonEntity) {
        val rounds = seasonEntity.rounds
        val matches = rounds.flatMap { round -> round.matches }

        fixtures.forEach { fixture ->
            val fixtureData = fixture.fixture!!
            val fulltimeScore = fixture.score!!.fulltime!!
            val score = if (fulltimeScore.home != null) fulltimeScore else fixture.goals!!
            var roundList = getRound(rounds, fixture.league!!.round!!)
            if (roundList.isEmpty()) {
                refreshRounds(seasonEntity)
                roundList = getRound(rounds, fixture.league!!.round!!)
            }

            val matchEntity = matches.firstOrNull { match -> match.apiFootballId == fixtureData.id }
                ?: MatchEntity().apply {
                    apiFootballId = fixtureData.id
                    homeTeam = getTeamEntity(fixture.teams!!.home!!)
                    awayTeam = getTeamEntity(fixture.teams!!.away!!)
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

            matchEntity.homeTeamScore = score.home
            matchEntity.awayTeamScore = score.away
            matchEntity.status = mapStatus(fixture.fixture!!.status)
            matchEntity.startTime = if (fixtureData.date != null && matchEntity.status != MatchStatus.NOT_DEFINED) {
                fixtureData.date!!.toInstant()
            } else {
                null
            }
        }
        seasonRepository!!.save(seasonEntity)
        log.info("Fixtures have been successfully updated for the season {}", seasonEntity.id)
    }

    private fun mapStatus(status: Status?): MatchStatus {
        val fixtureStatus = status?.status
        if (fixtureStatus == null) {
            return MatchStatus.NOT_DEFINED
        }

        return when (fixtureStatus) {
            FixtureStatusEnum.NS -> MatchStatus.PLANNED
            FixtureStatusEnum._1H,
            FixtureStatusEnum.HT,
            FixtureStatusEnum._2H,
            FixtureStatusEnum.LIVE,
            -> MatchStatus.STARTED
            FixtureStatusEnum.ABD,
            FixtureStatusEnum.CANC,
            FixtureStatusEnum.INT,
            FixtureStatusEnum.PST,
            FixtureStatusEnum.SUSP,
            FixtureStatusEnum.WO,
            FixtureStatusEnum.TBD,
            -> MatchStatus.NOT_DEFINED
            FixtureStatusEnum.AET,
            FixtureStatusEnum.P,
            FixtureStatusEnum.PEN,
            FixtureStatusEnum.ET,
            FixtureStatusEnum.AWD,
            FixtureStatusEnum.BT,
            FixtureStatusEnum.FT,
            -> MatchStatus.FINISHED
        }
    }

    private fun getTeamEntity(team: Team): TeamEntity {
        val teamEntityOptional = teamRepository!!.findFirstByApiFootballId(team.id!!)
        if (teamEntityOptional.isPresent) {
            var teamEntity = teamEntityOptional.get()
            if (teamEntity.name == team.name && teamEntity.logoUrl == team.logo) {
                return teamEntity
            }
            teamEntity.name = team.name
            teamEntity.logoUrl = team.logo
            teamEntity = teamRepository.save(teamEntity)
            log.info("Team {} has been updated: {}", teamEntity.name, teamEntity.id)
            return teamEntity
        }
        return createTeam(team)
    }

    private fun createTeam(team: Team): TeamEntity {
        var teamEntity = TeamEntity().apply {
            name = team.name
            apiFootballId = team.id
            logoUrl = team.logo
        }
        teamEntity = teamRepository!!.save(teamEntity)
        log.info("New team {} has been created: {}", teamEntity.name, teamEntity.id)
        return teamEntity
    }

    private fun validateActiveSeasons(competitionId: UUID, season: Season) {
        if (season.isActive()) {
            val activeSeasons = seasonRepository!!.countAllByActiveIsTrueAndCompetitionId(competitionId)
            if (activeSeasons > 0) {
                throw RequestValidationException("Not possible to add more than one active season")
            }
        }
    }

    private fun refreshRounds(seasonEntity: SeasonEntity) {
        val actualRounds = apiFootballConnector!!.getRounds(seasonEntity.competition!!.apiFootballId!!, seasonEntity.year!!)
        val roundEntities = seasonEntity.rounds
        val lastRound = roundEntities.maxByOrNull(RoundEntity::orderNumber)
        val nextOrderNumber = AtomicInteger(if (lastRound != null) lastRound.orderNumber + 1 else 1)
        for (round in actualRounds) {
            if (roundEntities.none { roundEntity -> roundEntity.apiFootballId == round }) {
                RoundType.getByAlias(round).forEach { roundType ->
                    roundEntities.add(
                        RoundEntity().apply {
                            type = roundType
                            orderNumber = getOrderNumber(round, roundType, nextOrderNumber)
                            apiFootballId = round
                            this.season = seasonEntity
                        },
                    )
                }
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
    }
}
