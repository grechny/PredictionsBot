package at.hrechny.predictionsbot.foundation

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected

@Introspected
@ConfigurationProperties("foundation")
class FoundationConfig {
    var greeting: String = "Hello"
}
