package com.example.multiplegraphqlendpointsdemo.config.multiEndpoint

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component


@Component
@ConfigurationProperties(prefix = "spring.graphql")
class GraphQlMultiEndpointProperties {
    lateinit var multiEndpoints: List<GraphQlMultiSchemaEntry>

    class GraphQlMultiSchemaEntry {
        lateinit var name: String
        lateinit var path: String
        lateinit var locations: Array<String>
        var considerSharedControllers: Boolean = false
    }
}
