package at.hrechny.predictionsbot.config

import at.hrechny.predictionsbot.exception.NotFoundException
import at.hrechny.predictionsbot.exception.RequestValidationException
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.http.HttpRequest
import jakarta.inject.Singleton
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory

@Singleton
class ExceptionHandlerConfig : ExceptionHandler<Exception, HttpResponse<Any>> {
    override fun handle(request: HttpRequest<Any>, exception: Exception): HttpResponse<Any> {
        if (exception is HttpStatusException) {
            log.error("Request failed: ", exception)
            return HttpResponse.status(exception.status)
        }
        if (isBadRequest(exception)) {
            log.error("Bad request: ", exception)
            return HttpResponse.status(HttpStatus.BAD_REQUEST)
        }
        log.error("An unhandled exception was caught: ", exception)
        return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
    }

    private fun isBadRequest(exception: Exception): Boolean =
        exception is ConversionErrorException ||
            exception is ConstraintViolationException ||
            exception is org.hibernate.exception.ConstraintViolationException ||
            exception is RequestValidationException ||
            exception is NotFoundException

    private companion object {
        val log = LoggerFactory.getLogger(ExceptionHandlerConfig::class.java)
    }
}
