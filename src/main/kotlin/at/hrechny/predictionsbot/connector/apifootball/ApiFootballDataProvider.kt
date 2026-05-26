package at.hrechny.predictionsbot.connector.apifootball

import at.hrechny.predictionsbot.connector.apifootball.exception.ApiFootballConnectorException
import at.hrechny.predictionsbot.connector.football.FootballDataProvider
import at.hrechny.predictionsbot.connector.football.FootballDataProviderException
import at.hrechny.predictionsbot.connector.football.model.FootballDataType
import at.hrechny.predictionsbot.connector.football.model.FootballFixtureSyncDto
import at.hrechny.predictionsbot.connector.football.model.FootballFreshness
import at.hrechny.predictionsbot.connector.football.model.FootballProviderCapabilities
import at.hrechny.predictionsbot.connector.football.model.FootballRoundSyncDto
import at.hrechny.predictionsbot.model.ExternalApiProviderId
import jakarta.inject.Singleton

@Singleton
open class ApiFootballDataProvider(
    private val apiFootballConnector: ApiFootballConnector,
    private val apiFootballFixtureMapper: ApiFootballFixtureMapper,
) : FootballDataProvider {
    override val providerId: ExternalApiProviderId = ExternalApiProviderId.API_FOOTBALL

    override val capabilities = FootballProviderCapabilities(
        providerId = providerId,
        dataTypes = setOf(
            FootballDataType.ROUNDS,
            FootballDataType.SEASON_FIXTURES,
            FootballDataType.LIVE_FIXTURES,
            FootballDataType.SCORES,
            FootballDataType.TEAMS,
            FootballDataType.PROVIDER_METADATA,
        ),
        freshness = FootballFreshness.NEAR_REAL_TIME,
        supportsLiveScores = true,
    )

    fun providerCode(): String = providerId.value

    override fun getRounds(competitionExternalId: String, seasonYear: String): List<FootballRoundSyncDto> =
        translateConnectorFailures {
            apiFootballConnector
                .getRounds(parseNumericExternalId("competition", competitionExternalId), seasonYear)
                .map(apiFootballFixtureMapper::toRoundSyncDto)
        }

    override fun getSeasonFixtures(competitionExternalId: String, seasonYear: String): List<FootballFixtureSyncDto> =
        translateConnectorFailures {
            apiFootballConnector
                .getFixtures(parseNumericExternalId("competition", competitionExternalId), seasonYear)
                .map(apiFootballFixtureMapper::toFixtureSyncDto)
        }

    override fun getFixturesByExternalIds(fixtureExternalIds: List<String>): List<FootballFixtureSyncDto> =
        translateConnectorFailures {
            if (fixtureExternalIds.isEmpty()) {
                return@translateConnectorFailures emptyList()
            }
            val fixtureIds = fixtureExternalIds.map { externalId -> parseNumericExternalId("fixture", externalId) }
            apiFootballConnector.getFixtures(fixtureIds).map(apiFootballFixtureMapper::toFixtureSyncDto)
        }

    private fun parseNumericExternalId(entityName: String, externalId: String): Long =
        externalId.toLongOrNull()
            ?: throw FootballDataProviderException(
                providerId,
                FootballDataProviderException.Reason.INVALID_RESPONSE,
                "API-Football $entityName external id must be numeric: $externalId",
            )

    private fun <T> translateConnectorFailures(action: () -> T): T =
        try {
            action()
        } catch (exception: FootballDataProviderException) {
            throw exception
        } catch (exception: ApiFootballConnectorException) {
            throw FootballDataProviderException(
                providerId,
                mapReason(exception.reason),
                exception.message,
                exception,
            )
        } catch (exception: IllegalArgumentException) {
            throw FootballDataProviderException(
                providerId,
                FootballDataProviderException.Reason.INVALID_RESPONSE,
                exception.message,
                exception,
            )
        } catch (exception: NullPointerException) {
            throw FootballDataProviderException(
                providerId,
                FootballDataProviderException.Reason.INVALID_RESPONSE,
                exception.message,
                exception,
            )
        }

    private fun mapReason(reason: ApiFootballConnectorException.Reason): FootballDataProviderException.Reason =
        when (reason) {
            ApiFootballConnectorException.Reason.REQUEST_ERROR -> FootballDataProviderException.Reason.REQUEST_ERROR
            ApiFootballConnectorException.Reason.INVALID_RESPONSE -> FootballDataProviderException.Reason.INVALID_RESPONSE
            ApiFootballConnectorException.Reason.TOO_OFTEN_REQUESTS -> FootballDataProviderException.Reason.TOO_OFTEN_REQUESTS
            ApiFootballConnectorException.Reason.QUOTA_EXCEEDED -> FootballDataProviderException.Reason.QUOTA_EXCEEDED
        }
}
