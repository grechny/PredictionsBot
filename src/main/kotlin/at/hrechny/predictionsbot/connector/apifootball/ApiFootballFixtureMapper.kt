package at.hrechny.predictionsbot.connector.apifootball

import at.hrechny.predictionsbot.connector.apifootball.model.Fixture
import at.hrechny.predictionsbot.connector.apifootball.model.FixtureStatusEnum
import at.hrechny.predictionsbot.connector.apifootball.model.Score
import at.hrechny.predictionsbot.connector.apifootball.model.Status
import at.hrechny.predictionsbot.connector.apifootball.model.Team
import at.hrechny.predictionsbot.football.provider.model.FootballFixtureStatus
import at.hrechny.predictionsbot.football.provider.model.FootballFixtureSyncDto
import at.hrechny.predictionsbot.football.provider.model.FootballRoundSyncDto
import at.hrechny.predictionsbot.football.provider.model.FootballScoreSyncDto
import at.hrechny.predictionsbot.football.provider.model.FootballTeamSyncDto
import jakarta.inject.Singleton

@Singleton
open class ApiFootballFixtureMapper {
    open fun toFixtureSyncDto(fixture: Fixture): FootballFixtureSyncDto {
        val fixtureData = requireNotNull(fixture.fixture) { "Fixture data is missing" }
        val fixtureId = requireNotNull(fixtureData.id) { "Fixture id is missing" }
        val providerFixtureId = fixtureId.toString()
        val roundExternalId = requireNotNull(fixture.league?.round) { "Fixture round is missing" }
        val teamsData = requireNotNull(fixture.teams) { "Fixture teams are missing" }

        return FootballFixtureSyncDto(
            externalId = providerFixtureId,
            roundExternalId = roundExternalId,
            startTime = fixtureData.date?.toInstant(),
            status = mapStatus(fixtureData.status),
            homeTeam = toTeamSyncDto(requireNotNull(teamsData.home) { "Home team is missing" }),
            awayTeam = toTeamSyncDto(requireNotNull(teamsData.away) { "Away team is missing" }),
            score = toScoreSyncDto(resolveScore(fixture)),
        )
    }

    open fun toRoundSyncDto(round: String) =
        FootballRoundSyncDto(
            externalId = round,
            name = round,
        )

    private fun toTeamSyncDto(team: Team): FootballTeamSyncDto =
        FootballTeamSyncDto(
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

    private fun toScoreSyncDto(score: Score?): FootballScoreSyncDto =
        FootballScoreSyncDto(
            home = score?.home,
            away = score?.away,
        )

    private fun mapStatus(status: Status?): FootballFixtureStatus {
        val fixtureStatus = status?.status ?: return FootballFixtureStatus.NOT_DEFINED
        return when (fixtureStatus) {
            FixtureStatusEnum.NS -> FootballFixtureStatus.PLANNED
            FixtureStatusEnum._1H,
            FixtureStatusEnum.HT,
            FixtureStatusEnum._2H,
            FixtureStatusEnum.LIVE,
            -> FootballFixtureStatus.STARTED
            FixtureStatusEnum.ABD,
            FixtureStatusEnum.CANC,
            FixtureStatusEnum.INT,
            FixtureStatusEnum.PST,
            FixtureStatusEnum.SUSP,
            FixtureStatusEnum.WO,
            FixtureStatusEnum.TBD,
            -> FootballFixtureStatus.NOT_DEFINED
            FixtureStatusEnum.AET,
            FixtureStatusEnum.P,
            FixtureStatusEnum.PEN,
            FixtureStatusEnum.ET,
            FixtureStatusEnum.AWD,
            FixtureStatusEnum.BT,
            FixtureStatusEnum.FT,
            -> FootballFixtureStatus.FINISHED
        }
    }
}
