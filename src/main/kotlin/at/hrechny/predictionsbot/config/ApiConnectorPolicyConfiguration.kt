package at.hrechny.predictionsbot.config

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected

@ConfigurationProperties("api-connectors")
class ApiConnectorPolicyConfiguration {
    var connectors: MutableMap<String, ApiConnectorDefinition> = mutableMapOf()
    var routing: ApiConnectorRoutingConfiguration = ApiConnectorRoutingConfiguration()
}

@Introspected
class ApiConnectorDefinition {
    var enabled: Boolean = false
    var dataTypes: MutableList<String> = mutableListOf()
    var competitions: MutableList<String> = mutableListOf()
    var freshness: String? = null
    var quotaWindows: MutableList<ApiConnectorQuotaConfiguration> = mutableListOf()
    var health: ApiConnectorHealthConfiguration = ApiConnectorHealthConfiguration()
}

@Introspected
class ApiConnectorQuotaConfiguration {
    var window: String? = null
    var maxRequests: Int? = null
}

@Introspected
class ApiConnectorRoutingConfiguration {
    var fallbackOrder: MutableList<String> = mutableListOf()
    var fieldPriority: MutableMap<String, MutableList<String>> = mutableMapOf()
    var mergePolicy: ApiConnectorMergePolicyConfiguration = ApiConnectorMergePolicyConfiguration()
    var quotaWindows: MutableMap<String, MutableList<ApiConnectorQuotaConfiguration>> = mutableMapOf()
}

@Introspected
class ApiConnectorMergePolicyConfiguration {
    var strategy: String = "PRIMARY_ONLY"
    var fieldOwners: MutableMap<String, String> = mutableMapOf()
}

@Introspected
class ApiConnectorHealthConfiguration {
    var derivedFromAudit: Boolean = true
    var recentWindowMinutes: Int = 60
}
