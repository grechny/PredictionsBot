package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.ApiConnector
import at.hrechny.predictionsbot.connector.model.FixtureSyncDto
import at.hrechny.predictionsbot.connector.model.RoundSyncDto
import at.hrechny.predictionsbot.exception.ApiConnectorException
import jakarta.inject.Singleton

@Singleton
class ApiFootballConnector(
    private val apiFootballClient: ApiFootballClient,
    private val apiFootballFixtureMapper: ApiFootballFixtureMapper,
) : ApiConnector {
    override val name: String = NAME

    override fun getRounds(competitionExternalId: String, seasonYear: String): List<RoundSyncDto> =
        execute {
            apiFootballClient
                .getRounds(parseNumericExternalId("competition", competitionExternalId), seasonYear)
                .map(apiFootballFixtureMapper::toRoundSyncDto)
        }

    override fun getSeasonFixtures(competitionExternalId: String, seasonYear: String): List<FixtureSyncDto> =
        execute {
            apiFootballClient
                .getFixtures(parseNumericExternalId("competition", competitionExternalId), seasonYear)
                .map(apiFootballFixtureMapper::toFixtureSyncDto)
        }

    override fun getFixturesByExternalIds(fixtureExternalIds: List<String>): List<FixtureSyncDto> =
        execute {
            if (fixtureExternalIds.isEmpty()) {
                return@execute emptyList()
            }
            val fixtureIds = fixtureExternalIds.map { externalId -> parseNumericExternalId("fixture", externalId) }
            apiFootballClient.getFixtures(fixtureIds).map(apiFootballFixtureMapper::toFixtureSyncDto)
        }

    private fun parseNumericExternalId(entityName: String, externalId: String): Long =
        externalId.toLongOrNull()
            ?: throw ApiConnectorException(
                name,
                ApiConnectorException.Reason.INVALID_RESPONSE,
                "API-Football $entityName external id must be numeric: $externalId",
            )

    private fun <T> execute(action: () -> T): T =
        try {
            action()
        } catch (exception: ApiConnectorException) {
            throw exception
        } catch (exception: Exception) {
            throw ApiConnectorException(
                name,
                ApiConnectorException.Reason.INVALID_RESPONSE,
                exception.message,
                exception,
            )
        }

    companion object {
        const val NAME = "api-football"
    }
}
