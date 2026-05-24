package at.hrechny.predictionsbot.mapper

import at.hrechny.predictionsbot.database.entity.UserEntity
import at.hrechny.predictionsbot.model.User
import jakarta.inject.Singleton

@Singleton
class UserMapper {
    fun entityToModel(source: UserEntity): User =
        User().apply {
            id = source.id
            name = source.username
        }
}
