package at.hrechny.predictionsbot.connector

import at.hrechny.predictionsbot.connector.model.FixtureSyncDto
import at.hrechny.predictionsbot.connector.model.RoundSyncDto

interface ApiConnector {
    val name: String

    fun getRounds(competitionExternalId: String, seasonYear: String): List<RoundSyncDto>

    fun getSeasonFixtures(competitionExternalId: String, seasonYear: String): List<FixtureSyncDto>

    fun getFixturesByExternalIds(fixtureExternalIds: List<String>): List<FixtureSyncDto>
}
