package at.hrechny.predictionsbot.football.provider

import at.hrechny.predictionsbot.football.provider.model.FootballFixtureSyncDto
import at.hrechny.predictionsbot.football.provider.model.FootballProviderCapabilities
import at.hrechny.predictionsbot.football.provider.model.FootballRoundSyncDto
import at.hrechny.predictionsbot.model.ExternalApiProviderId

interface FootballDataProvider {
    val providerId: ExternalApiProviderId
    val capabilities: FootballProviderCapabilities

    fun getRounds(competitionExternalId: String, seasonYear: String): List<FootballRoundSyncDto>

    fun getSeasonFixtures(competitionExternalId: String, seasonYear: String): List<FootballFixtureSyncDto>

    fun getFixturesByExternalIds(fixtureExternalIds: List<String>): List<FootballFixtureSyncDto>
}
