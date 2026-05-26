package at.hrechny.predictionsbot.connector

import at.hrechny.predictionsbot.model.FootballFixtureSyncDto
import at.hrechny.predictionsbot.model.FootballProviderCapabilities
import at.hrechny.predictionsbot.model.FootballRoundSyncDto
import at.hrechny.predictionsbot.model.FootballDataProviderId

interface FootballDataProvider {
    val providerId: FootballDataProviderId
    val capabilities: FootballProviderCapabilities

    fun getRounds(competitionExternalId: String, seasonYear: String): List<FootballRoundSyncDto>

    fun getSeasonFixtures(competitionExternalId: String, seasonYear: String): List<FootballFixtureSyncDto>

    fun getFixturesByExternalIds(fixtureExternalIds: List<String>): List<FootballFixtureSyncDto>
}
