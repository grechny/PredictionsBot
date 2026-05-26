package at.hrechny.predictionsbot.connector.football

import at.hrechny.predictionsbot.connector.football.model.FootballFixtureSyncDto
import at.hrechny.predictionsbot.connector.football.model.FootballProviderCapabilities
import at.hrechny.predictionsbot.connector.football.model.FootballRoundSyncDto
import at.hrechny.predictionsbot.model.ExternalApiProviderId

interface FootballDataProvider {
    val providerId: ExternalApiProviderId
    val capabilities: FootballProviderCapabilities

    fun getRounds(competitionExternalId: String, seasonYear: String): List<FootballRoundSyncDto>

    fun getSeasonFixtures(competitionExternalId: String, seasonYear: String): List<FootballFixtureSyncDto>

    fun getFixturesByExternalIds(fixtureExternalIds: List<String>): List<FootballFixtureSyncDto>
}
