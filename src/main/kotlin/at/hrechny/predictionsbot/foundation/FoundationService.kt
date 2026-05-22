package at.hrechny.predictionsbot.foundation

import jakarta.inject.Singleton

@Singleton
class FoundationService(
    private val foundationConfig: FoundationConfig,
) {
    fun echo(request: FoundationRequest): FoundationResponse =
        FoundationResponse("${foundationConfig.greeting}, ${request.name}")
}
