package at.hrechny.predictionsbot.connector.football.config

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected

@ConfigurationProperties("football-data")
class FootballProviderPolicyConfiguration {
    var providers: MutableMap<String, FootballProviderDefinition> = mutableMapOf()
    var routing: FootballRoutingConfiguration = FootballRoutingConfiguration()
}

@Introspected
class FootballProviderDefinition {
    var enabled: Boolean = false
    var dataTypes: MutableList<String> = mutableListOf()
    var competitions: MutableList<String> = mutableListOf()
    var freshness: String? = null
    var quotaWindows: MutableList<FootballQuotaConfiguration> = mutableListOf()
    var health: FootballHealthConfiguration = FootballHealthConfiguration()
}

@Introspected
class FootballQuotaConfiguration {
    var window: String? = null
    var maxRequests: Int? = null
}

@Introspected
class FootballRoutingConfiguration {
    var fallbackOrder: MutableList<String> = mutableListOf()
    var fieldPriority: MutableMap<String, MutableList<String>> = mutableMapOf()
    var mergePolicy: FootballMergePolicyConfiguration = FootballMergePolicyConfiguration()
    var quotaWindows: MutableMap<String, MutableList<FootballQuotaConfiguration>> = mutableMapOf()
}

@Introspected
class FootballMergePolicyConfiguration {
    var strategy: String = "PRIMARY_ONLY"
    var fieldOwners: MutableMap<String, String> = mutableMapOf()
}

@Introspected
class FootballHealthConfiguration {
    var derivedFromAudit: Boolean = true
    var recentWindowMinutes: Int = 60
}
