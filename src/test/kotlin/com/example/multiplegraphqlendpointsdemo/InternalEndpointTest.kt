package com.example.multiplegraphqlendpointsdemo

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.graphql.test.tester.WebGraphQlTester
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@Import(TestBeanConfiguration::class)
@SpringJUnitConfig
@SpringBootTest(classes = [MultipleGraphqlEndpointsDemoApplication::class])
class InternalEndpointTest {
    @Autowired
    @Qualifier("internalGraphQlTester")
    lateinit var graphQlTester: WebGraphQlTester

    @Test
    fun `test internal endpoint querying`() {
        graphQlTester
            .document("""
                query {
                    books {
                        id
                        name
                        review
                    } 
                }
            """.trimIndent())
            .execute()
            .path("books[*].name")
            .entityList(String::class.java)
            .containsExactly("Book1", "Book2", "Book3")
            .path("books[*].id")
            .entityList(Int::class.java)
            .containsExactly(1, 2, 3)
    }
}
