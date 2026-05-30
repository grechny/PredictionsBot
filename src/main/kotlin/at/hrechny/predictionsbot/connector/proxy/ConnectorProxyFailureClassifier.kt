package at.hrechny.predictionsbot.connector.proxy

import at.hrechny.predictionsbot.exception.ApiConnectorException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Singleton
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@Singleton
open class ConnectorProxyFailureClassifier {
    open fun isProxyFailure(exception: Throwable): Boolean {
        if (exception is ApiConnectorException) {
            return false
        }
        if (exception is HttpClientResponseException) {
            return exception.response.status.code == PROXY_AUTHENTICATION_REQUIRED_STATUS
        }
        return exception.causes().any { cause ->
            cause is ConnectException ||
                cause is UnknownHostException ||
                cause is SocketTimeoutException ||
                cause.isConnectionReset()
        }
    }

    private fun Throwable.causes(): Sequence<Throwable> =
        generateSequence(this) { cause -> cause.cause }

    private fun Throwable.isConnectionReset(): Boolean =
        this is SocketException && message?.contains("Connection reset", ignoreCase = true) == true

    private companion object {
        const val PROXY_AUTHENTICATION_REQUIRED_STATUS = 407
    }
}
