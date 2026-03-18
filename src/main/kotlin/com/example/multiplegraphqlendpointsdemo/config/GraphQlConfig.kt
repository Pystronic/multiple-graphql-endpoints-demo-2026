package com.example.multiplegraphqlendpointsdemo.config

import graphql.scalars.ExtendedScalars
import graphql.schema.idl.RuntimeWiring.Builder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer

@Configuration
class GraphQlConfig {

    @Bean
    fun runtimeWiringConfigurer(): RuntimeWiringConfigurer {
        return RuntimeWiringConfigurer {
                wiringBuilder: Builder ->
            wiringBuilder
                .scalar(ExtendedScalars.Date)
                .scalar(ExtendedScalars.GraphQLLong)
        }
    }
}
