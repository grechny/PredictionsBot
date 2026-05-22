package at.hrechny.predictionsbot.exception.interceptor

import io.micronaut.aop.Around
import io.micronaut.context.annotation.Type

@Around
@Type(ExceptionInterceptor::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class EnableErrorReport
