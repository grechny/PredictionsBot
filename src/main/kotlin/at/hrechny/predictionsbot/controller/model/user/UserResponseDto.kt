package at.hrechny.predictionsbot.controller.model.user

import io.micronaut.core.annotation.Introspected

@Introspected
class UserResponseDto {
    var id: Long? = null
    var name: String? = null
}
