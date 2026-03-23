package com.example.multiplegraphqlendpointsdemo

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.graphql.server.WebGraphQlHandler
import org.springframework.graphql.test.tester.WebGraphQlTester

@TestConfiguration
class TestBeanConfiguration {
    @Bean
    fun internalGraphQlTester(
        @Qualifier("internalSchemaWebGraphQlHandler")
        webGraphqlHandler: WebGraphQlHandler
    ): WebGraphQlTester {
        println("Creating internalGraphQlTester with handler: $webGraphqlHandler")
        return WebGraphQlTester
            .builder(webGraphqlHandler)
            .build()
    }

    @Bean
    fun externalGraphQlTester(
        @Qualifier("externalSchemaWebGraphQlHandler")
        webGraphqlHandler: WebGraphQlHandler
    ): WebGraphQlTester =
        WebGraphQlTester
            .builder(webGraphqlHandler)
            .build()
}
