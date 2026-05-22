package at.hrechny.predictionsbot.database

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager

@Testcontainers(disabledWithoutDocker = true)
class FlywayPostgresMigrationTest {
    @Test
    fun baselineMigrationCreatesExpectedPostgresSchema() {
        val flyway = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .load()

        val result = flyway.migrate()

        assertThat(result.migrationsExecuted).isEqualTo(1)
        DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).use { connection ->
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
