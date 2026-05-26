package at.hrechny.predictionsbot.mapper

import at.hrechny.predictionsbot.database.entity.UserEntity
import at.hrechny.predictionsbot.controller.model.user.UserResponseDto
import jakarta.inject.Singleton

@Singleton
class UserMapper {
    fun entityToModel(source: UserEntity): UserResponseDto =
        UserResponseDto().apply {
            id = source.id
            name = source.username
        }
}
