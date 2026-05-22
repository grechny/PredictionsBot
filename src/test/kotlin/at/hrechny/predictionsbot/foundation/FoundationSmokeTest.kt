package at.hrechny.predictionsbot.foundation

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@MicronautTest
@Property(name = "foundation.greeting", value = "Hi")
class FoundationSmokeTest {
    @Inject
    lateinit var foundationService: FoundationService

    @Inject
    lateinit var foundationConfig: FoundationConfig

    @field:Client("/")
    @Inject
    lateinit var client: HttpClient

    @Test
    fun contextStartsAndInjectsFoundationBeans() {
        assertThat(foundationConfig.greeting).isEqualTo("Hi")
        assertThat(foundationService.echo(FoundationRequest("Alice")).message)
            .isEqualTo("Hi, Alice")
    }

    @Test
    fun httpRouteSerializesJsonResponse() {
        val response = client.toBlocking().retrieve(
            HttpRequest.POST("/foundation/echo", FoundationRequest("Alice")),
            FoundationResponse::class.java,
        )

        assertThat(response.message).isEqualTo("Hi, Alice")
    }

    @Test
    fun validationRejectsBlankName() {
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.POST("/foundation/echo", FoundationRequest("")),
                FoundationResponse::class.java,
            )
        }

        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }
}
