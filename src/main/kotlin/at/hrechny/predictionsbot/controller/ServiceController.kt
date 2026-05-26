package at.hrechny.predictionsbot.controller

import at.hrechny.predictionsbot.controller.model.service.PushUpdateRequestDto
import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport
import at.hrechny.predictionsbot.service.predictor.UserService
import at.hrechny.predictionsbot.service.telegram.TelegramService
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import jakarta.validation.Valid

@Controller
@EnableErrorReport
open class ServiceController(
    private val userService: UserService,
    private val telegramService: TelegramService,
) {
    @Post(value = "/\${secrets.adminKey:}", consumes = [MediaType.APPLICATION_JSON])
    open fun pushUpdate(@Valid @Body pushUpdate: PushUpdateRequestDto): HttpResponse<Void> {
        userService.getUsers().forEach { user ->
            telegramService.pushUpdate(user.id, pushUpdate.message, pushUpdate.isUpdateCompetitionList())
        }
        return HttpResponse.ok()
    }
}
