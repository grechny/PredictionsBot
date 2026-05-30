package at.hrechny.predictionsbot.service.predictor

import at.hrechny.predictionsbot.connector.ApiConnector
import at.hrechny.predictionsbot.connector.model.FixtureSyncDto
import at.hrechny.predictionsbot.connector.model.FixtureSyncStatus
import at.hrechny.predictionsbot.connector.model.RoundSyncDto
import at.hrechny.predictionsbot.connector.model.RoundSyncType
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
import at.hrechny.predictionsbot.mapper.CompetitionMapper
import at.hrechny.predictionsbot.mapper.SeasonMapper
import at.hrechny.predictionsbot.service.connector.ApiConnectorService
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
        return toCompetitionResponse(entity)
    }

    open fun getCompetitions(): List<CompetitionResponseDto> =
        competitionRepository!!.findAll().map(::toCompetitionResponse)

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

    open fun refreshActiveFixtures(seasonId: UUID) {
        val seasonEntity = getSeason(seasonId)
        val activeMatches = matchRepository!!.findAllActive(seasonEntity)
        if (activeMatches.isEmpty()) {
            return
        }

        val connectorName = connectorName()
        val fixtureIds = activeMatches.map { match ->
            connectorService().requireConnectorEntityId(
                connectorName,
                ApiConnectorEntityType.MATCH,
                match.id!!,
            )
        }
        val fixtures = apiConnector!!.getFixturesByExternalIds(fixtureIds)
        syncFixtures(fixtures, seasonEntity)
    }

    open fun refreshFixtures(seasonEntity: SeasonEntity) {
        val managedSeasonEntity = getSeason(seasonEntity.id!!)
        log.info("Start refreshing fixtures data for the season {}", managedSeasonEntity.id)
        val competitionExternalId = connectorService().requireConnectorEntityId(
            connectorName(),
            ApiConnectorEntityType.COMPETITION,
            managedSeasonEntity.competition!!.id!!,
        )
        val fixtures = apiConnector!!.getSeasonFixtures(
            competitionExternalId,
            managedSeasonEntity.year!!,
        )
        syncFixtures(fixtures, managedSeasonEntity)
    }

    private fun syncFixtures(
        fixtures: List<FixtureSyncDto>,
        seasonEntity: SeasonEntity,
    ) {
        val rounds = resolveRoundsForFixtures(fixtures, seasonEntity)
        validateRequiredFixtureMappings(fixtures, rounds, seasonEntity)
        val matches = collectSeasonMatches(rounds).toMutableList()
        val matchConnectorIds = mutableMapOf<MatchEntity, String>()

        fixtures.forEach { fixture ->
            syncFixture(fixture, seasonEntity, rounds, matches, matchConnectorIds)
        }
        seasonRepository!!.save(seasonEntity)
        recordMatchConnectorIds(matchConnectorIds)
        log.info("Fixtures have been successfully updated for the season {}", seasonEntity.id)
    }

    private fun syncFixture(
        fixture: FixtureSyncDto,
        seasonEntity: SeasonEntity,
        rounds: List<RoundEntity>,
        matches: MutableList<MatchEntity>,
        matchConnectorIds: MutableMap<MatchEntity, String>,
    ) {
        val roundCandidates = findRoundsForConnectorRound(rounds, fixture.round)
        val matchEntity = resolveMatchEntity(fixture, seasonEntity, roundCandidates, matches)
        val targetRound = selectRoundForMatch(roundCandidates, matchEntity)

        moveMatchToRound(matchEntity, targetRound)
        updateMatch(matchEntity, fixture)
        trackResolvedMatch(matches, matchEntity)
        matchConnectorIds[matchEntity] = fixture.externalId
    }

    private fun resolveMatchEntity(
        fixture: FixtureSyncDto,
        seasonEntity: SeasonEntity,
        roundCandidates: List<RoundEntity>,
        matches: List<MatchEntity>,
    ): MatchEntity {
        val matchByConnectorId = findMatchByConnectorId(matches, seasonEntity, fixture.externalId)
        if (matchByConnectorId != null) {
            return matchByConnectorId
        }

        val homeTeam = getTeamEntity(fixture.homeTeam)
        val awayTeam = getTeamEntity(fixture.awayTeam)
        return findMatchByCanonicalIdentity(matches, roundCandidates, homeTeam, awayTeam)
            ?: createMatch(homeTeam, awayTeam)
    }

    private fun createMatch(homeTeam: TeamEntity, awayTeam: TeamEntity): MatchEntity =
        MatchEntity().apply {
            this.homeTeam = homeTeam
            this.awayTeam = awayTeam
        }

    private fun moveMatchToRound(matchEntity: MatchEntity, targetRound: RoundEntity) {
        val currentRound = matchEntity.round
        if (currentRound == null) {
            matchEntity.round = targetRound
            targetRound.matches.add(matchEntity)
            return
        }
        if (!sameRoundEntity(currentRound, targetRound)) {
            currentRound.matches.remove(matchEntity)
            matchEntity.round = targetRound
            targetRound.matches.add(matchEntity)
        }
    }

    private fun updateMatch(matchEntity: MatchEntity, fixture: FixtureSyncDto) {
        matchEntity.homeTeamScore = fixture.score.home
        matchEntity.awayTeamScore = fixture.score.away
        matchEntity.status = mapStatus(fixture.status)
        matchEntity.startTime = if (fixture.startTime != null && matchEntity.status != MatchStatus.NOT_DEFINED) {
            fixture.startTime
        } else {
            null
        }
    }

    private fun trackResolvedMatch(matches: MutableList<MatchEntity>, matchEntity: MatchEntity) {
        if (matches.none { existingMatch -> sameMatchEntity(existingMatch, matchEntity) }) {
            matches.add(matchEntity)
        }
    }

    private fun mapStatus(status: FixtureSyncStatus): MatchStatus =
        when (status) {
            FixtureSyncStatus.PLANNED -> MatchStatus.PLANNED
            FixtureSyncStatus.STARTED -> MatchStatus.STARTED
            FixtureSyncStatus.NOT_DEFINED -> MatchStatus.NOT_DEFINED
            FixtureSyncStatus.FINISHED -> MatchStatus.FINISHED
        }

    private fun resolveRoundsForFixtures(
        fixtures: List<FixtureSyncDto>,
        seasonEntity: SeasonEntity,
    ): List<RoundEntity> {
        var rounds = distinctRoundEntities(seasonEntity.rounds)
        if (findMissingRoundNames(fixtures, rounds).isNotEmpty()) {
            refreshRounds(seasonEntity)
            rounds = distinctRoundEntities(seasonEntity.rounds)
        }
        return rounds
    }

    private fun validateRequiredFixtureMappings(
        fixtures: List<FixtureSyncDto>,
        rounds: List<RoundEntity>,
        seasonEntity: SeasonEntity,
    ) {
        val missingMappings = MissingConnectorMappings()
        findMissingRoundNames(fixtures, rounds).forEach(missingMappings::addRound)
        findMissingTeamMappings(fixtures).forEach(missingMappings::addTeam)
        missingMappings.throwIfNotEmpty(connectorName(), seasonEntity)
    }

    private fun findMissingTeamMappings(fixtures: List<FixtureSyncDto>): List<TeamSyncDto> =
        fixtures
            .asSequence()
            .map(FixtureSyncDto::homeTeam)
            .plus(fixtures.asSequence().map(FixtureSyncDto::awayTeam))
            .distinctBy(TeamSyncDto::externalId)
            .filter { team ->
                connectorService()
                    .findInternalId(connectorName(), ApiConnectorEntityType.TEAM, team.externalId)
                    .isEmpty
            }
            .toList()

    private fun findMissingRoundNames(
        fixtures: List<FixtureSyncDto>,
        rounds: List<RoundEntity>,
    ): List<String> =
        fixtures
            .map(FixtureSyncDto::round)
            .filter { round -> findRoundsForConnectorRound(rounds, round).isEmpty() }
            .map(RoundSyncDto::name)
            .distinct()

    private fun getTeamEntity(team: TeamSyncDto): TeamEntity {
        val internalId = connectorService()
            .findInternalId(connectorName(), ApiConnectorEntityType.TEAM, team.externalId)
            .orElse(null)
        if (internalId != null) {
            var teamEntity = teamRepository!!.findById(internalId)
                .orElseThrow { NotFoundException("Team mapping $internalId points to a missing team") }
            if (teamEntity.name == team.name && teamEntity.logoUrl == team.logoUrl) {
                return teamEntity
            }
            teamEntity.name = team.name
            teamEntity.logoUrl = team.logoUrl
            teamEntity = teamRepository.save(teamEntity)
            log.info("Team {} has been updated: {}", teamEntity.name, teamEntity.id)
            return teamEntity
        }
        throw FixturesSynchronizationException(
            "No internal team found for connector ${connectorName()} team ${team.externalId}",
        )
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
        val competitionExternalId = connectorService().requireConnectorEntityId(
            connectorName(),
            ApiConnectorEntityType.COMPETITION,
            seasonEntity.competition!!.id!!,
        )
        val actualRounds = apiConnector!!.getRounds(
            competitionExternalId,
            seasonEntity.year!!,
        )
        val roundEntities = seasonEntity.rounds
        val lastRound = roundEntities.maxByOrNull(RoundEntity::orderNumber)
        val nextOrderNumber = AtomicInteger(if (lastRound != null) lastRound.orderNumber + 1 else 1)
        for (round in actualRounds) {
            round.types.map(::mapRoundType).forEach { roundType ->
                val orderNumber = round.orderNumber ?: getOrderNumber(round.name, roundType, nextOrderNumber)
                if (roundEntities.none { roundEntity -> sameRound(roundEntity, roundType, round.orderNumber, orderNumber) }) {
                    roundEntities.add(
                        RoundEntity().apply {
                            type = roundType
                            this.orderNumber = orderNumber
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

    private fun findRoundsForConnectorRound(rounds: List<RoundEntity>, round: RoundSyncDto): List<RoundEntity> {
        val roundTypes = round.types.map(::mapRoundType).toSet()
        return rounds.filter { roundEntity ->
            roundEntity.type in roundTypes && (round.orderNumber == null || roundEntity.orderNumber == round.orderNumber)
        }.distinctBy(::roundEntityKey)
    }

    private fun sameRound(
        roundEntity: RoundEntity,
        roundType: RoundType,
        sourceOrderNumber: Int?,
        resolvedOrderNumber: Int,
    ): Boolean =
        roundEntity.type == roundType && (sourceOrderNumber == null || roundEntity.orderNumber == resolvedOrderNumber)

    private fun mapRoundType(roundType: RoundSyncType): RoundType =
        RoundType.valueOf(roundType.name)

    private fun findMatchByConnectorId(
        matches: List<MatchEntity>,
        seasonEntity: SeasonEntity,
        externalId: String,
    ): MatchEntity? {
        val internalId = connectorService()
            .findInternalId(connectorName(), ApiConnectorEntityType.MATCH, externalId)
            .orElse(null)
            ?: return null
        val match = matches.firstOrNull { existingMatch -> existingMatch.id == internalId }
            ?: matchRepository!!.findById(internalId)
                .orElseThrow { NotFoundException("Match mapping $internalId points to a missing match") }
        if (match.round?.season?.id != seasonEntity.id) {
            throw FixturesSynchronizationException(
                "Connector match $externalId points to match ${match.id} outside season ${seasonEntity.id}",
            )
        }
        return match
    }

    private fun findMatchByCanonicalIdentity(
        matches: List<MatchEntity>,
        roundList: List<RoundEntity>,
        homeTeam: TeamEntity,
        awayTeam: TeamEntity,
    ): MatchEntity? {
        val matched = matches.filter { match ->
            match.round in roundList && match.homeTeam == homeTeam && match.awayTeam == awayTeam
        }
        if (matched.size > 1) {
            throw FixturesSynchronizationException(
                "Multiple matches found for ${homeTeam.name} vs ${awayTeam.name} in connector round $roundList",
            )
        }
        return matched.firstOrNull()
    }

    private fun recordMatchConnectorIds(matchConnectorIds: Map<MatchEntity, String>) {
        val connectorName = connectorName()
        matchConnectorIds.forEach { (match, externalId) ->
            if (match.id != null) {
                connectorService().upsertId(connectorName, ApiConnectorEntityType.MATCH, externalId, match.id!!)
            }
        }
    }

    private fun toCompetitionResponse(entity: CompetitionEntity): CompetitionResponseDto =
        competitionMapper!!.entityToModel(entity)

    private fun connectorService(): ApiConnectorService =
        apiConnectorService ?: throw IllegalStateException("ApiConnectorService is required")

    private fun connectorName(): String = apiConnector!!.name

    private fun selectRoundForMatch(roundList: List<RoundEntity>, match: MatchEntity): RoundEntity {
        val existingRound = match.round
        if (existingRound != null && roundList.any { round -> sameRoundEntity(round, existingRound) }) {
            return existingRound
        }
        return selectRoundForTeams(roundList, match.homeTeam!!, match.awayTeam!!)
    }

    private fun selectRoundForTeams(roundList: List<RoundEntity>, homeTeam: TeamEntity, awayTeam: TeamEntity): RoundEntity {
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

    private fun distinctRoundEntities(rounds: Collection<RoundEntity>): List<RoundEntity> =
        rounds.distinctBy(::roundEntityKey)

    private fun collectSeasonMatches(rounds: List<RoundEntity>): List<MatchEntity> =
        rounds.flatMap { round -> distinctMatchEntities(round.matches) }
            .distinctBy(::matchEntityKey)

    private fun distinctMatchEntities(matches: Collection<MatchEntity>): List<MatchEntity> =
        matches.distinctBy(::matchEntityKey)

    private fun sameRoundEntity(left: RoundEntity, right: RoundEntity): Boolean =
        if (left.id != null && right.id != null) {
            left.id == right.id
        } else {
            left === right
        }

    private fun sameMatchEntity(left: MatchEntity, right: MatchEntity): Boolean =
        if (left.id != null && right.id != null) {
            left.id == right.id
        } else {
            left === right
        }

    private fun roundEntityKey(round: RoundEntity): Any =
        round.id ?: System.identityHashCode(round)

    private fun matchEntityKey(match: MatchEntity): Any =
        match.id ?: System.identityHashCode(match)

    private class MissingConnectorMappings {
        private val rounds = linkedSetOf<String>()
        private val teams = linkedSetOf<String>()

        fun addRound(roundName: String) {
            rounds.add(roundName)
        }

        fun addTeam(team: TeamSyncDto) {
            teams.add("${team.name ?: "unknown"} (${team.externalId})")
        }

        fun throwIfNotEmpty(connectorName: String, seasonEntity: SeasonEntity) {
            if (rounds.isEmpty() && teams.isEmpty()) {
                return
            }

            throw FixturesSynchronizationException(
                buildString {
                    append("Missing connector mappings for ")
                    append(connectorName)
                    append(" season ")
                    append(seasonEntity.id)
                    append(": ")
                    appendSection("rounds", rounds)
                    appendSection("teams", teams)
                },
            )
        }

        private fun StringBuilder.appendSection(name: String, values: Set<String>) {
            if (values.isEmpty()) {
                return
            }
            if (!endsWith(": ")) {
                append("; ")
            }
            append(name)
            append("=")
            append(values.joinToString(", "))
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(CompetitionService::class.java)
    }
}
