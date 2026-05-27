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
            connection.prepareStatement(
                """
                insert into public.audit (id, api_key, api_provider, request_uri, request_date, success)
                values (?, 'secret-api-key', 'API_FOOTBALL', '/fixtures?league=39&season=2025', timestamp '2026-05-26 14:36:39', true)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, legacyAuditId)
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
                        "scope_key",
                        "internal_id",
                        "created_at",
                        "updated_at",
                    )
                    assertThat(columnNames).doesNotContain("api_key", "secret", "token", "authorization")
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
