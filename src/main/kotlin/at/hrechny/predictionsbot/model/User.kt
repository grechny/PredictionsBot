package at.hrechny.predictionsbot.model

import io.micronaut.core.annotation.Introspected

@Introspected
class User {
    var id: Long? = null
    var name: String? = null
}
