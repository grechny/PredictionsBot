package at.hrechny.predictionsbot.connector.proxy

import at.hrechny.predictionsbot.connector.impl.apifootball.ApiFootballConnector
import at.hrechny.predictionsbot.service.telegram.TelegramService
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.runtime.event.ApplicationStartupEvent
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
open class ConnectorProxyStartup(
    private val connectorProxyManager: ConnectorProxyManager,
    private val telegramService: TelegramService,
) : ApplicationEventListener<ApplicationStartupEvent> {
    override fun onApplicationEvent(event: ApplicationStartupEvent) {
        if (!connectorProxyManager.usesWebshare(ApiFootballConnector.NAME)) {
            return
        }

        try {
            connectorProxyManager.initialize(ApiFootballConnector.NAME)
        } catch (exception: RuntimeException) {
            try {
                telegramService.sendErrorReport(exception)
            } catch (reportException: RuntimeException) {
                log.error("Failed to send connector proxy startup error report", reportException)
            }
            throw exception
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ConnectorProxyStartup::class.java)
    }
}
