package at.hrechny.predictionsbot.foundation

import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import jakarta.validation.Valid

@Validated
@Controller("/foundation")
class FoundationController(
    private val foundationService: FoundationService,
) {
    @Post("/echo")
    fun echo(@Body @Valid request: FoundationRequest): FoundationResponse =
        foundationService.echo(request)
}
