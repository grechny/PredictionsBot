package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.impl.apifootball.model.Fixture
import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixtureStatusEnum
import at.hrechny.predictionsbot.connector.impl.apifootball.model.Score
import at.hrechny.predictionsbot.connector.impl.apifootball.model.Status
import at.hrechny.predictionsbot.connector.impl.apifootball.model.Team
import at.hrechny.predictionsbot.connector.model.FixtureSyncDto
import at.hrechny.predictionsbot.connector.model.FixtureSyncStatus
import at.hrechny.predictionsbot.connector.model.RoundSyncDto
import at.hrechny.predictionsbot.connector.model.ScoreSyncDto
import at.hrechny.predictionsbot.connector.model.TeamSyncDto
import jakarta.inject.Singleton

@Singleton
open class ApiFootballFixtureMapper {
    open fun toFixtureSyncDto(fixture: Fixture): FixtureSyncDto {
        val fixtureData = requireNotNull(fixture.fixture) { "Fixture data is missing" }
        val fixtureId = requireNotNull(fixtureData.id) { "Fixture id is missing" }
        val connectorFixtureId = fixtureId.toString()
        val roundExternalId = requireNotNull(fixture.league?.round) { "Fixture round is missing" }
        val teamsData = requireNotNull(fixture.teams) { "Fixture teams are missing" }

        return FixtureSyncDto(
            externalId = connectorFixtureId,
            roundExternalId = roundExternalId,
            startTime = fixtureData.date?.toInstant(),
            status = mapStatus(fixtureData.status),
            homeTeam = toTeamSyncDto(requireNotNull(teamsData.home) { "Home team is missing" }),
            awayTeam = toTeamSyncDto(requireNotNull(teamsData.away) { "Away team is missing" }),
            score = toScoreSyncDto(resolveScore(fixture)),
        )
    }

    open fun toRoundSyncDto(round: String) =
        RoundSyncDto(
            externalId = round,
            name = round,
        )

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
