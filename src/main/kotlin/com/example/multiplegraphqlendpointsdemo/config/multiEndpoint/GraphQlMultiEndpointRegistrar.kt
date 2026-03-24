package com.example.multiplegraphqlendpointsdemo.config.multiEndpoint

import graphql.execution.instrumentation.Instrumentation
import graphql.introspection.Introspection
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.boot.graphql.autoconfigure.GraphQlProperties
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.graphql.data.GraphQlArgumentBinder
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer
import org.springframework.graphql.execution.*
import org.springframework.graphql.server.WebGraphQlHandler
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.webmvc.GraphQlHttpHandler
import org.springframework.graphql.server.webmvc.GraphiQlHandler
import org.springframework.graphql.server.webmvc.SchemaHandler
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.servlet.function.*
import java.io.IOException
import java.util.concurrent.Executor


@Configuration
@Order(0)
@EnableConfigurationProperties(GraphQlProperties::class)
class GraphQlMultiEndpointRegistrar(
    private val graphqlProperties: GraphQlProperties,
    private val multiEndpointProperties: GraphQlMultiEndpointProperties,
    private val applicationContext: ApplicationContext,
    private val beanFactory: ConfigurableBeanFactory,
    @field:Qualifier("applicationTaskExecutor")
    private val applicationTaskExecutor: Executor,
    private val resourcePatternResolver: ResourcePatternResolver,
    private val argumentResolvers: ObjectProvider<HandlerMethodArgumentResolver>,
    private val exceptionResolvers: ObjectProvider<DataFetcherExceptionResolver>,
    private val subscriptionExceptionResolvers: ObjectProvider<SubscriptionExceptionResolver>,
    private val instrumentations: ObjectProvider<Instrumentation>,
    private val wiringConfigurers: ObjectProvider<RuntimeWiringConfigurer>,
    private val sourceCustomizers: ObjectProvider<GraphQlSourceBuilderCustomizer>,
    private val interceptors: ObjectProvider<WebGraphQlInterceptor>
) {
    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(GraphQlMultiEndpointRegistrar::class.java)
        private val SUPPORTED_MEDIA_TYPES = arrayOf(
            MediaType.APPLICATION_GRAPHQL_RESPONSE,
            MediaType.APPLICATION_JSON
        )
    }

    @PostConstruct
    fun registerConfiguredMultiEndpoints() {
        val batchLoaderRegistryProvider = applicationContext.getBeanProvider(BatchLoaderRegistry::class.java)
        if (batchLoaderRegistryProvider.ifAvailable == null) {
            beanFactory.registerSingleton("batchLoaderRegistry", DefaultBatchLoaderRegistry())
        }
        val batchLoaderRegistry = batchLoaderRegistryProvider.ifAvailable!!

        val defaultWiringConfigurers = wiringConfigurers.orderedStream().toList()

        multiEndpointProperties.multiEndpoints.forEach { endpointProperties ->
            val annotationConfigurer = createEndpointAnnotatedControllerConfigurer(endpointProperties)
            val graphQlSource = createGraphqlSource(
                endpointProperties,
                graphqlProperties,
                defaultWiringConfigurers + listOf(annotationConfigurer),
            )

            val executionService = DefaultExecutionGraphQlService(graphQlSource)
            executionService.addDataLoaderRegistrar(batchLoaderRegistry)

            val webGraphqlHandler = WebGraphQlHandler
                .builder(executionService)
                .interceptors(interceptors.orderedStream().toList())
                .build()

            val routerFunctions = createRouterFunction(
                endpointProperties,
                graphqlProperties,
                webGraphqlHandler,
                graphQlSource
            )

            beanFactory.registerSingleton("${endpointProperties.name}GraphqlSource", graphQlSource)
            beanFactory.registerSingleton("${endpointProperties.name}ExecutionGraphQlService", executionService)
            beanFactory.registerSingleton("${endpointProperties.name}WebGraphQlHandler", webGraphqlHandler)
            beanFactory.registerSingleton("${endpointProperties.name}GraphQlRouterFunction", routerFunctions)

            beanFactory.registerDependentBean("${endpointProperties.name}GraphqlSource", "${endpointProperties.name}ExecutionGraphQlService")
            beanFactory.registerDependentBean("${endpointProperties.name}ExecutionGraphQlService", "${endpointProperties.name}WebGraphQlHandler")
            beanFactory.registerDependentBean("${endpointProperties.name}WebGraphQlHandler", "${endpointProperties.name}GraphQlRouterFunction")
        }

        if (!graphqlProperties.schema.introspection.isEnabled) {
            Introspection.enabledJvmWide(false)
        }
    }

    private fun createEndpointAnnotatedControllerConfigurer(
        endpointProperties: GraphQlMultiEndpointProperties.GraphQlMultiSchemaEntry,
    ): AnnotatedControllerConfigurer {
        val controllerConfigurer = applicationContext.autowireCapableBeanFactory.initializeBean(
            AnnotatedControllerConfigurer(),
            "${endpointProperties.name}AnnotationConfigurer"
        ) as AnnotatedControllerConfigurer

        controllerConfigurer.apply {
            configureBinder { options: GraphQlArgumentBinder.Options ->
                options.conversionService(
                    ApplicationConversionService.getSharedInstance()
                )
            }

            setExecutor(applicationTaskExecutor)

            argumentResolvers.orderedStream().forEach { resolver: HandlerMethodArgumentResolver ->
                addCustomArgumentResolver(resolver)
            }
        }

        // Custom annotation resolution
        controllerConfigurer.setControllerPredicate { clazz ->
            clazz.annotations.any {
                (it is EndpointController && it.value == endpointProperties.name) ||
                        (it is Controller && endpointProperties.considerSharedControllers)
            }
        }

        return controllerConfigurer
    }

    private fun createGraphqlSource(
        schemaEndpointEntry: GraphQlMultiEndpointProperties.GraphQlMultiSchemaEntry,
        properties: GraphQlProperties,
        wiringConfigurers: List<RuntimeWiringConfigurer>,
    ): GraphQlSource {
        val schemaResources = resolveSchemaResources(
            resourcePatternResolver,
            schemaEndpointEntry.locations,
            properties.schema.fileExtensions
        )

        val builder = setupGraphQlSourceBuilder(
            schemaResources,
            wiringConfigurers,
        )

        if (properties.schema.inspection.isEnabled) {
            builder.inspectSchemaMappings { message: SchemaReport ->
                logger.info("Schema report for '${schemaEndpointEntry.name}' at endpoint '${schemaEndpointEntry.path}':")
                logger.info(
                    message.toString()
                )
            }
        }

        return builder.build()
    }

    private fun createRouterFunction(
        schemaEndpointEntry: GraphQlMultiEndpointProperties.GraphQlMultiSchemaEntry,
        properties: GraphQlProperties,
        webGraphqlHandler: WebGraphQlHandler,
        graphQlSource: GraphQlSource
    ): RouterFunction<ServerResponse> {
        val graphqlHttpHandler = GraphQlHttpHandler(webGraphqlHandler)

        return RouterFunctions.route()
            .GET(schemaEndpointEntry.path, this::onlyAllowPost)
            .POST(
                schemaEndpointEntry.path,
                RequestPredicates.contentType(MediaType.APPLICATION_JSON)
                    .and(RequestPredicates.accept(*SUPPORTED_MEDIA_TYPES)),
                graphqlHttpHandler::handleRequest
            )
            .apply {
                if (properties.graphiql.isEnabled) {
                    val graphiQlHandler = GraphiQlHandler(schemaEndpointEntry.path, properties.websocket.path)
                    GET(properties.graphiql.path, graphiQlHandler::handleRequest)
                }
            }
            .apply {
                if (properties.schema.printer.isEnabled) {
                    val schemaHandler = SchemaHandler(graphQlSource)
                    GET("${schemaEndpointEntry.path}/schema", schemaHandler::handleRequest)
                }
            }
            .build()

    }

    private fun setupGraphQlSourceBuilder(
        schemaResources: Array<Resource>,
        wiringConfigurers: Iterable<RuntimeWiringConfigurer>,
    ): GraphQlSource.SchemaResourceBuilder {
        val builder = GraphQlSource.schemaResourceBuilder()
            .schemaResources(*schemaResources)
            .exceptionResolvers(exceptionResolvers.orderedStream().toList())
            .subscriptionExceptionResolvers(subscriptionExceptionResolvers.orderedStream().toList())
            .instrumentation(instrumentations.orderedStream().toList())
            .configureTypeDefinitions(ConnectionTypeDefinitionConfigurer())

        wiringConfigurers.forEach(builder::configureRuntimeWiring)
        sourceCustomizers.orderedStream().forEach { customizer -> customizer.customize(builder) }
        return builder
    }

    private fun onlyAllowPost(@Suppress("UNUSED_PARAMETER") request: ServerRequest): ServerResponse {
        return ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED).headers(this::onlyAllowPost).build()
    }

    private fun onlyAllowPost(headers: HttpHeaders) {
        headers.allow = setOf(HttpMethod.POST)
    }

    private fun resolveSchemaResources(
        resolver: ResourcePatternResolver,
        locations: Array<String>,
        extensions: Array<String>
    ): Array<Resource> {
        val resources = mutableListOf<Resource>()
        locations.forEach { location ->
            extensions.forEach { extension ->
                resources.addAll(resolveSchemaResource(resolver, "$location*$extension"))
            }
        }
        return resources.toTypedArray()
    }

    private fun resolveSchemaResource(resolver: ResourcePatternResolver, pattern: String): List<Resource> {
        return try {
            resolver.getResources(pattern).toList()
        } catch (ex: IOException) {
            emptyList()
        }
    }
}
