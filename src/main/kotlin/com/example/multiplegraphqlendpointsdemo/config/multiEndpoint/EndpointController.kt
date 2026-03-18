package com.example.multiplegraphqlendpointsdemo.config.multiEndpoint

import org.springframework.core.annotation.AliasFor
import org.springframework.stereotype.Component


@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Component
annotation class EndpointController(
    @get:AliasFor(annotation = Component::class, attribute = "value")
    val value: String = "",
)
