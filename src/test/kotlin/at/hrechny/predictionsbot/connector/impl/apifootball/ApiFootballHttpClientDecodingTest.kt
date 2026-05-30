package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixtureStatusEnum
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.runtime.server.EmbeddedServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiFootballHttpClientDecodingTest {
    @Test
    fun apiFootballClientDecodesJsonBodyFromRealHttpResponse() {
        ApplicationContext.run(
            EmbeddedServer::class.java,
            mapOf(
                "application.url" to "http://localhost",
                "connectors.api-football.apiKey" to "api-football-key",
                "connectors.api-football.dayStarts" to "00:00",
                "connectors.api-football.maxAttempts" to 100,
                "connectors.api-football.url" to "http://localhost:$PORT/v3",
                "jpa.default.properties.hibernate.dialect" to "org.hibernate.dialect.H2Dialect",
                "jpa.default.properties.hibernate.hbm2ddl.auto" to "create-drop",
                "micronaut.server.port" to PORT,
                "secrets.telegramKey" to "telegram-secret",
                "schedulers.fixtures.enabled" to false,
                "telegram.polling.enabled" to false,
                "telegram.reportTo" to "1",
                "telegram.token" to "telegram-token",
            ),
            "test",
            TEST_ENVIRONMENT,
        ).use { server ->
            val client = server.applicationContext.getBean(ApiFootballClient::class.java)

            val fixtures = client.getFixtures(39L, "2025")

            assertThat(fixtures).hasSize(2)
            val fixture = fixtures.first()
            assertThat(fixture.fixture?.id).isEqualTo(1378969L)
            assertThat(fixture.fixture?.status?.status).isEqualTo(FixtureStatusEnum.FT)
            assertThat(fixture.league?.round).isEqualTo("Regular Season - 1")
            assertThat(fixture.teams?.home?.name).isEqualTo("Liverpool")
            assertThat(fixture.teams?.away?.name).isEqualTo("Bournemouth")
            assertThat(fixture.goals?.home).isEqualTo(4)
            assertThat(fixture.goals?.away).isEqualTo(2)

            val notStartedFixture = fixtures[1]
            assertThat(notStartedFixture.fixture?.id).isEqualTo(1378970L)
            assertThat(notStartedFixture.fixture?.status?.status).isEqualTo(FixtureStatusEnum.NS)
            assertThat(notStartedFixture.teams?.home?.winner).isNull()
            assertThat(notStartedFixture.teams?.away?.winner).isNull()
        }
    }

    @Controller("/v3")
    @Requires(env = [TEST_ENVIRONMENT])
    internal class ApiFootballStubController {
        @Get("/fixtures{?league,season}")
        @Produces(MediaType.APPLICATION_JSON)
        fun fixtures(
            @QueryValue league: Long,
            @QueryValue season: String,
        ): String {
            require(league == 39L)
            require(season == "2025")
            return """
                {
                  "get": "fixtures",
                  "parameters": {
                    "season": "2025",
                    "league": "39"
                  },
                  "errors": [],
                  "results": 2,
                  "paging": {
                    "current": 1,
                    "total": 1
                  },
                  "response": [
                    {
                      "fixture": {
                        "id": 1378969,
                        "referee": "A. Taylor",
                        "timezone": "UTC",
                        "date": "2025-08-15T19:00:00+00:00",
                        "timestamp": 1755284400,
                        "periods": {
                          "first": 1755284400,
                          "second": 1755288000
                        },
                        "venue": {
                          "id": 550,
                          "name": "Anfield",
                          "city": "Liverpool"
                        },
                        "status": {
                          "long": "Match Finished",
                          "short": "FT",
                          "elapsed": 90,
                          "extra": 7
                        }
                      },
                      "league": {
                        "id": 39,
                        "name": "Premier League",
                        "country": "England",
                        "logo": "https://media.api-sports.io/football/leagues/39.png",
                        "flag": "https://media.api-sports.io/flags/gb-eng.svg",
                        "season": 2025,
                        "round": "Regular Season - 1",
                        "standings": true
                      },
                      "teams": {
                        "home": {
                          "id": 40,
                          "name": "Liverpool",
                          "logo": "https://media.api-sports.io/football/teams/40.png",
                          "winner": true
                        },
                        "away": {
                          "id": 35,
                          "name": "Bournemouth",
                          "logo": "https://media.api-sports.io/football/teams/35.png",
                          "winner": false
                        }
                      },
                      "goals": {
                        "home": 4,
                        "away": 2
                      },
                      "score": {
                        "halftime": {
                          "home": 1,
                          "away": 0
                        },
                        "fulltime": {
                          "home": 4,
                          "away": 2
                        }
                      }
                    },
                    {
                      "fixture": {
                        "id": 1378970,
                        "timezone": "UTC",
                        "date": "2025-08-16T11:30:00+00:00",
                        "timestamp": 1755343800,
                        "status": {
                          "long": "Not Started",
                          "short": "NS",
                          "elapsed": null
                        }
                      },
                      "league": {
                        "id": 39,
                        "name": "Premier League",
                        "country": "England",
                        "season": 2025,
                        "round": "Regular Season - 1"
                      },
                      "teams": {
                        "home": {
                          "id": 42,
                          "name": "Arsenal",
                          "logo": "https://media.api-sports.io/football/teams/42.png",
                          "winner": null
                        },
                        "away": {
                          "id": 50,
                          "name": "Manchester City",
                          "logo": "https://media.api-sports.io/football/teams/50.png",
                          "winner": null
                        }
                      },
                      "goals": {
                        "home": null,
                        "away": null
                      },
                      "score": {
                        "halftime": {
                          "home": null,
                          "away": null
                        },
                        "fulltime": {
                          "home": null,
                          "away": null
                        },
                        "extratime": {
                          "home": null,
                          "away": null
                        },
                        "penalty": {
                          "home": null,
                          "away": null
                        }
                      }
                    }
                  ]
                }
            """.trimIndent()
        }
    }

    private companion object {
        const val PORT = 18081
        const val TEST_ENVIRONMENT = "api-football-client-decoding-test"
    }
}
