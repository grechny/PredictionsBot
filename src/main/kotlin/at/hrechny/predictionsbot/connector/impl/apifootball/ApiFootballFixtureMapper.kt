package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.impl.apifootball.model.Fixture
import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixtureStatusEnum
import at.hrechny.predictionsbot.connector.impl.apifootball.model.Score
import at.hrechny.predictionsbot.connector.impl.apifootball.model.Status
import at.hrechny.predictionsbot.connector.impl.apifootball.model.Team
import at.hrechny.predictionsbot.connector.model.FixtureSyncDto
import at.hrechny.predictionsbot.connector.model.FixtureSyncStatus
import at.hrechny.predictionsbot.connector.model.RoundSyncDto
import at.hrechny.predictionsbot.connector.model.RoundSyncType
import at.hrechny.predictionsbot.connector.model.ScoreSyncDto
import at.hrechny.predictionsbot.connector.model.TeamSyncDto
import at.hrechny.predictionsbot.database.model.ApiConnectorValueType
import at.hrechny.predictionsbot.service.connector.ApiConnectorMappingCandidateService
import jakarta.inject.Singleton
import java.util.regex.Pattern

@Singleton
class ApiFootballFixtureMapper(
    private val apiConnectorMappingCandidateService: ApiConnectorMappingCandidateService,
) {
    fun toFixtureSyncDto(fixture: Fixture): FixtureSyncDto {
        val fixtureData = requireNotNull(fixture.fixture) { "Fixture data is missing" }
        val fixtureId = requireNotNull(fixtureData.id) { "Fixture id is missing" }
        val connectorFixtureId = fixtureId.toString()
        val round = requireNotNull(fixture.league?.round) { "Fixture round is missing" }
        val teamsData = requireNotNull(fixture.teams) { "Fixture teams are missing" }

        return FixtureSyncDto(
            externalId = connectorFixtureId,
            round = toRoundSyncDto(round),
            startTime = fixtureData.date?.toInstant(),
            status = mapStatus(fixtureData.status),
            homeTeam = toTeamSyncDto(requireNotNull(teamsData.home) { "Home team is missing" }),
            awayTeam = toTeamSyncDto(requireNotNull(teamsData.away) { "Away team is missing" }),
            score = toScoreSyncDto(resolveScore(fixture)),
        )
    }

    fun toRoundSyncDto(round: String) =
        RoundSyncDto(
            name = round,
            orderNumber = parseOrderNumber(round),
            types = mapRoundTypes(round),
        )

    private fun mapRoundTypes(round: String): List<RoundSyncType> =
        when {
            round == "Knockout Round Play-offs" -> listOf(RoundSyncType.ROUND_OF_32)
            matches(round, "Preliminary Round", ".*Qualifying.*", "Relegation Round", ".*Play-offs.*") ->
                listOf(RoundSyncType.QUALIFYING)
            matches(round, "Regular Season.*") -> listOf(RoundSyncType.SEASON)
            matches(round, "Group.*", "League Stage.*") -> listOf(RoundSyncType.GROUP_STAGE)
            matches(round, "Round of 32") -> listOf(RoundSyncType.ROUND_OF_32, RoundSyncType.ROUND_OF_32_RETURN)
            matches(round, "Round of 16", "8th Finals") -> listOf(RoundSyncType.ROUND_OF_16, RoundSyncType.ROUND_OF_16_RETURN)
            matches(round, "Quarter-finals") -> listOf(RoundSyncType.QUARTER_FINAL, RoundSyncType.QUARTER_FINAL_RETURN)
            matches(round, "Semi-finals") -> listOf(RoundSyncType.SEMI_FINAL, RoundSyncType.SEMI_FINAL_RETURN)
            matches(round, "3rd Place Final") -> listOf(RoundSyncType.THIRD_PLACE_FINAL)
            matches(round, "Final") -> listOf(RoundSyncType.FINAL)
            else -> apiConnectorMappingCandidateService.findApprovedValue(
                ApiFootballConnector.NAME,
                ApiConnectorValueType.ROUND_LABEL,
                round,
            ).map(::parseApprovedRoundTypes).orElseGet {
                apiConnectorMappingCandidateService.recordCandidate(
                    ApiFootballConnector.NAME,
                    ApiConnectorValueType.ROUND_LABEL,
                    round,
                )
                throw IllegalArgumentException("Unsupported API-Football round: $round")
            }
        }

    private fun parseApprovedRoundTypes(value: String): List<RoundSyncType> =
        value.split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(RoundSyncType::valueOf)

    private fun matches(value: String, vararg patterns: String): Boolean =
        patterns.any { pattern -> Regex(pattern).matches(value) }

    private fun parseOrderNumber(round: String): Int? =
        Pattern.compile("^(.+) - (\\d+)$").matcher(round)
            .takeIf { matcher -> matcher.matches() }
            ?.group(2)
            ?.toInt()

    private fun toTeamSyncDto(team: Team): TeamSyncDto =
        TeamSyncDto(
            externalId = requireNotNull(team.id) { "Team id is missing" }.toString(),
            name = team.name,
            logoUrl = team.logo,
        )

    private fun resolveScore(fixture: Fixture): Score? {
        val fulltimeScore = fixture.score?.fulltime
        return if (fulltimeScore?.home != null) {
            fulltimeScore
        } else {
            fixture.goals ?: fulltimeScore
        }
    }

    private fun toScoreSyncDto(score: Score?): ScoreSyncDto =
        ScoreSyncDto(
            home = score?.home,
            away = score?.away,
        )

    private fun mapStatus(status: Status?): FixtureSyncStatus {
        val fixtureStatus = status?.status ?: return FixtureSyncStatus.NOT_DEFINED
        return when (fixtureStatus) {
            FixtureStatusEnum.NS -> FixtureSyncStatus.PLANNED
            FixtureStatusEnum._1H,
            FixtureStatusEnum.HT,
            FixtureStatusEnum._2H,
            FixtureStatusEnum.LIVE,
            -> FixtureSyncStatus.STARTED
            FixtureStatusEnum.ABD,
            FixtureStatusEnum.CANC,
            FixtureStatusEnum.INT,
            FixtureStatusEnum.PST,
            FixtureStatusEnum.SUSP,
            FixtureStatusEnum.WO,
            FixtureStatusEnum.TBD,
            -> FixtureSyncStatus.NOT_DEFINED
            FixtureStatusEnum.AET,
            FixtureStatusEnum.P,
            FixtureStatusEnum.PEN,
            FixtureStatusEnum.ET,
            FixtureStatusEnum.AWD,
            FixtureStatusEnum.BT,
            FixtureStatusEnum.FT,
            -> FixtureSyncStatus.FINISHED
        }
    }

}
