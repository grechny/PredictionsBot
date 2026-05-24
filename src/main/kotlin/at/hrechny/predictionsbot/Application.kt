package at.hrechny.predictionsbot

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.runtime.Micronaut
import java.time.Clock

class Application {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Micronaut.run(Application::class.java, *args)
        }
    }

    @Factory
    class ApplicationFactory {
        @Bean
        fun clock(): Clock = Clock.systemUTC()
    }
}
