package at.hrechny.predictionsbot

import at.hrechny.predictionsbot.controller.CompetitionController
import at.hrechny.predictionsbot.service.telegram.MessageListener
import io.micronaut.context.ApplicationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApplicationContextStartupTest {
    @Test
    fun applicationContextStartsWithRealBeanGraph() {
        ApplicationContext.run(
            mapOf(
                "application.url" to "http://localhost",
                "connectors.api-football.apiKey" to "api-football-key",
                "connectors.api-football.dayStarts" to "00:00",
                "connectors.api-football.maxAttempts" to 100,
                "connectors.api-football.url" to "http://localhost:18080/v3",
                "jpa.default.properties.hibernate.dialect" to "org.hibernate.dialect.H2Dialect",
                "jpa.default.properties.hibernate.hbm2ddl.auto" to "create-drop",
                "secrets.telegramKey" to "telegram-secret",
                "schedulers.fixtures.enabled" to false,
                "telegram.polling.enabled" to false,
                "telegram.reportTo" to "1",
                "telegram.token" to "telegram-token",
            ),
            "test",
        ).use { context ->
            assertThat(context.isRunning).isTrue()
            assertThat(context.getBean(CompetitionController::class.java)).isNotNull()
            assertThat(context.getBean(MessageListener::class.java)).isNotNull()
        }
    }
}
