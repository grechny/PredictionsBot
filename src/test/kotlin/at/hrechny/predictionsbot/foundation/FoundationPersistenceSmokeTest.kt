package at.hrechny.predictionsbot.foundation

import at.hrechny.predictionsbot.database.repository.UserRepository
import at.hrechny.predictionsbot.mapper.CompetitionMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.sql.DataSource

@MicronautTest
class FoundationPersistenceSmokeTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var competitionMapper: CompetitionMapper

    @Test
    fun datasourceRunsJdbcRoundTripAgainstH2() {
        assertThat(userRepository).isNotNull()
        assertThat(competitionMapper).isNotNull()

        dataSource.connection.use { connection ->
            assertThat(connection.metaData.url).contains("jdbc:h2:mem:predictions_micronaut")

            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    create table if not exists foundation_persistence_smoke (
                        id integer primary key,
                        name varchar(64) not null
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate("delete from foundation_persistence_smoke")
                statement.executeUpdate("insert into foundation_persistence_smoke (id, name) values (1, 'micronaut')")
            }

            connection.prepareStatement("select name from foundation_persistence_smoke where id = ?").use { statement ->
                statement.setInt(1, 1)

                statement.executeQuery().use { resultSet ->
                    assertThat(resultSet.next()).isTrue()
                    assertThat(resultSet.getString("name")).isEqualTo("micronaut")
                    assertThat(resultSet.next()).isFalse()
                }
            }

            connection.createStatement().use { statement ->
                statement.executeUpdate("drop table foundation_persistence_smoke")
            }
        }
    }
}
