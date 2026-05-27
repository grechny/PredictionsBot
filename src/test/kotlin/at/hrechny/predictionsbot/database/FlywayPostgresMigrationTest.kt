package at.hrechny.predictionsbot.database

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
class FlywayPostgresMigrationTest {
    @Test
    fun baselineMigrationCreatesExpectedPostgresSchema() {
        val flywayToV1 = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .target("1")
            .baselineOnMigrate(true)
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .load()

        val v1Result = flywayToV1.migrate()

        assertThat(v1Result.migrationsExecuted).isEqualTo(1)
        DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).use { connection ->
            val legacyAuditId = UUID.randomUUID()
            val competitionId = UUID.randomUUID()
            val homeTeamId = UUID.randomUUID()
            val awayTeamId = UUID.randomUUID()
            val seasonId = UUID.randomUUID()
            val roundId = UUID.randomUUID()
            val matchId = UUID.randomUUID()
            connection.prepareStatement(
                """
                insert into public.audit (id, api_key, api_provider, request_uri, request_date, success)
                values (?, 'secret-api-key', 'API_FOOTBALL', '/fixtures?league=39&season=2025', timestamp '2026-05-26 14:36:39', true)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, legacyAuditId)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                insert into public.competitions (id, api_football_id, name)
                values (?, 39, 'Premier League')
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, competitionId)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                insert into public.teams (id, api_football_id, name, logo_url)
                values (?, 1, 'Arsenal', 'arsenal.png'),
                       (?, 2, 'Chelsea', 'chelsea.png')
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, homeTeamId)
                statement.setObject(2, awayTeamId)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                insert into public.seasons (id, active, year, competition_id)
                values (?, true, '2026', ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, seasonId)
                statement.setObject(2, competitionId)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                insert into public.rounds (id, order_number, api_football_id, type, season_id)
                values (?, 1, 'Regular Season - 1', 'SEASON', ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, roundId)
                statement.setObject(2, seasonId)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                insert into public.matches (
                    id,
                    api_football_id,
                    away_team_score,
                    home_team_score,
                    start_time,
                    status,
                    away_team_id,
                    home_team_id,
                    round_id
                )
                values (?, 100, null, null, timestamp '2026-05-27 18:30:00', 'PLANNED', ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, matchId)
                statement.setObject(2, awayTeamId)
                statement.setObject(3, homeTeamId)
                statement.setObject(4, roundId)
                statement.executeUpdate()
            }

            val flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load()
            val v2Result = flyway.migrate()

            assertThat(v2Result.migrationsExecuted).isEqualTo(1)

            connection.prepareStatement(
                """
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                and table_name in (
                    'audit',
                    'competitions',
                    'leagues',
                    'leagues_competitions',
                    'leagues_users',
                    'matches',
                    'predictions',
                    'api_connector_ids',
                    'api_connector_mapping_candidates',
                    'rounds',
                    'seasons',
                    'teams',
                    'users',
                    'users_competitions'
                )
                order by table_name
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val tableNames = generateSequence {
                        if (resultSet.next()) resultSet.getString("table_name") else null
                    }.toList()

                    assertThat(tableNames).containsExactly(
                        "api_connector_ids",
                        "api_connector_mapping_candidates",
                        "audit",
                        "competitions",
                        "leagues",
                        "leagues_competitions",
                        "leagues_users",
                        "matches",
                        "predictions",
                        "rounds",
                        "seasons",
                        "teams",
                        "users",
                        "users_competitions",
                    )
                }
            }

            connection.prepareStatement(
                """
                select connector_name
                from public.audit
                where id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, legacyAuditId)
                statement.executeQuery().use { resultSet ->
                    assertThat(resultSet.next()).isTrue()
                    assertThat(resultSet.getString("connector_name")).isEqualTo("api-football")
                }
            }

            connection.prepareStatement(
                """
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'api_connector_ids'
                order by ordinal_position
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val columnNames = generateSequence {
                        if (resultSet.next()) resultSet.getString("column_name") else null
                    }.toList()

                    assertThat(columnNames).contains(
                        "id",
                        "connector_code",
                        "entity_type",
                        "connector_entity_id",
                        "internal_id",
                        "created_at",
                        "updated_at",
                    )
                    assertThat(columnNames).doesNotContain("api_key", "secret", "token", "authorization")
                }
            }

            connection.prepareStatement(
                """
                select connector_code, entity_type, connector_entity_id, internal_id
                from public.api_connector_ids
                order by entity_type, connector_entity_id
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val mappings = generateSequence {
                        if (resultSet.next()) {
                            listOf(
                                resultSet.getString("connector_code"),
                                resultSet.getString("entity_type"),
                                resultSet.getString("connector_entity_id"),
                                resultSet.getObject("internal_id", UUID::class.java).toString(),
                            ).joinToString(":")
                        } else {
                            null
                        }
                    }.toList()

                    assertThat(mappings).contains(
                        "api-football:COMPETITION:39:$competitionId",
                        "api-football:MATCH:100:$matchId",
                        "api-football:TEAM:1:$homeTeamId",
                        "api-football:TEAM:2:$awayTeamId",
                    )
                }
            }

            connection.prepareStatement(
                """
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'api_connector_mapping_candidates'
                order by ordinal_position
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val columnNames = generateSequence {
                        if (resultSet.next()) resultSet.getString("column_name") else null
                    }.toList()

                    assertThat(columnNames).contains(
                        "id",
                        "connector_code",
                        "value_type",
                        "raw_value",
                        "context_json",
                        "suggested_value",
                        "suggestion_confidence",
                        "suggestion_source",
                        "status",
                        "first_seen_at",
                        "last_seen_at",
                        "decided_at",
                        "decided_by",
                    )
                }
            }

            connection.prepareStatement(
                """
                select indexname
                from pg_indexes
                where schemaname = 'public'
                  and tablename = 'api_connector_mapping_candidates'
                  and indexname = 'idx_api_connector_mapping_candidates_status'
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    assertThat(resultSet.next()).isTrue()
                }
            }

            listOf("competitions", "matches", "rounds", "teams").forEach { tableName ->
                connection.prepareStatement(
                    """
                    select column_name
                    from information_schema.columns
                    where table_schema = 'public'
                      and table_name = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, tableName)
                    statement.executeQuery().use { resultSet ->
                        val columnNames = generateSequence {
                            if (resultSet.next()) resultSet.getString("column_name") else null
                        }.toList()

                        assertThat(columnNames).doesNotContain("api_football_id")
                    }
                }
            }

            connection.prepareStatement(
                """
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'audit'
                order by ordinal_position
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val columnNames = generateSequence {
                        if (resultSet.next()) resultSet.getString("column_name") else null
                    }.toList()

                    assertThat(columnNames).contains(
                        "id",
                        "connector_name",
                        "request_uri",
                        "request_date",
                        "success",
                        "failure_reason",
                    )
                    assertThat(columnNames).doesNotContain("api_key", "secret", "token", "authorization")
                }
            }

            connection.prepareStatement(
                """
                select indexname
                from pg_indexes
                where schemaname = 'public'
                  and tablename = 'audit'
                  and indexname = 'idx_audit_connector_name_request_date'
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    assertThat(resultSet.next()).isTrue()
                }
            }

            connection.prepareStatement(
                """
                select constraint_name
                from information_schema.table_constraints
                where table_schema = 'public'
                  and table_name = 'api_connector_ids'
                  and constraint_name = 'uk_api_connector_ids_connector_entity'
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    assertThat(resultSet.next()).isTrue()
                }
            }

            connection.prepareStatement(
                """
                select constraint_name
                from information_schema.table_constraints
                where table_schema = 'public'
                  and table_name = 'api_connector_ids'
                  and constraint_name = 'uk_api_connector_ids_connector_internal'
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    assertThat(resultSet.next()).isFalse()
                }
            }

            connection.prepareStatement(
                """
                select indexname
                from pg_indexes
                where schemaname = 'public'
                  and tablename = 'api_connector_ids'
                  and indexname = 'idx_api_connector_ids_connector_internal'
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    assertThat(resultSet.next()).isTrue()
                }
            }

            connection.prepareStatement(
                """
                insert into public.api_connector_ids (
                    id,
                    connector_code,
                    entity_type,
                    connector_entity_id,
                    internal_id,
                    created_at,
                    updated_at
                )
                values (?, 'api-football', 'COMPETITION', 'premier-league', ?, now(), now())
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, competitionId)
                assertThat(statement.executeUpdate()).isEqualTo(1)
            }
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:16")
            .withDatabaseName("predictionsbot")
            .withUsername("predictionsbot")
            .withPassword("predictionsbot")
    }
}
