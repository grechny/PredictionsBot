package at.hrechny.predictionsbot.connector.proxy.webshare.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class WebshareProxyListResponse {
    var results: MutableList<WebshareProxyResponseDto> = mutableListOf()
}

@JsonIgnoreProperties(ignoreUnknown = true)
class WebshareProxyResponseDto {
    var id: String? = null

    @JsonProperty("proxy_address")
    var proxyAddress: String? = null

    var port: Int? = null
    var username: String? = null
    var password: String? = null
    var valid: Boolean? = null

    @JsonProperty("country_code")
    var countryCode: String? = null
}
