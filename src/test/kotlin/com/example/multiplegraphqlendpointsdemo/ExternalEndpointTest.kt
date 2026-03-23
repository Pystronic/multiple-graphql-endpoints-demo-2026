package com.example.multiplegraphqlendpointsdemo

import graphql.ErrorType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.graphql.test.tester.WebGraphQlTester
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@Import(TestBeanConfiguration::class)
@SpringBootTest(classes = [MultipleGraphqlEndpointsDemoApplication::class])
@SpringJUnitConfig
class ExternalEndpointTest {
    @Autowired
    @Qualifier("externalGraphQlTester")
    lateinit var graphQlTester: WebGraphQlTester

    @Test
    fun `test external endpoint mapping`() {
        graphQlTester
            .document("""
                query {
                    books {
                        name
                        review
                    } 
                }
            """.trimIndent())
            .execute()
            .path("books[*].name")
            .entityList(String::class.java)
            .containsExactly("EXTERNAL BOOK", "EXTERNAL BOOK", "EXTERNAL BOOK")
    }

    @Test
    fun `test external endpoint isolation`() {
        graphQlTester
            .document("""
                query {
                    books {
                        id
                        authorId
                    } 
                }
            """.trimIndent())
            .execute()
            .errors()
            .expect { it.errorType == ErrorType.ValidationError }
            .verify()
    }
}
