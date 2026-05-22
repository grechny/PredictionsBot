package at.hrechny.predictionsbot.exception.interceptor

import at.hrechny.predictionsbot.service.telegram.TelegramService
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import jakarta.inject.Singleton

@Singleton
class ExceptionInterceptor(
    private val telegramService: TelegramService,
) : MethodInterceptor<Any, Any> {
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        try {
            return context.proceed()
        } catch (exception: RuntimeException) {
            telegramService.sendErrorReport(exception)
            throw exception
        }
    }
}
