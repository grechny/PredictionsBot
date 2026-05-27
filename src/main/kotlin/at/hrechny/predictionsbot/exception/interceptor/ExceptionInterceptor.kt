package at.hrechny.predictionsbot.exception.interceptor

import at.hrechny.predictionsbot.service.telegram.TelegramService
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.BeanProvider
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
class ExceptionInterceptor(
    private val telegramServiceProvider: BeanProvider<TelegramService>,
) : MethodInterceptor<Any, Any> {
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        try {
            return context.proceed()
        } catch (exception: RuntimeException) {
            try {
                telegramServiceProvider.get().sendErrorReport(exception)
            } catch (reportException: RuntimeException) {
                log.error("Failed to send error report", reportException)
            }
            throw exception
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ExceptionInterceptor::class.java)
    }
}
