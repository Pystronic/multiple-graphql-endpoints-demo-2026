package com.example.multiplegraphqlendpointsdemo.book

import com.example.multiplegraphqlendpointsdemo.config.multiEndpoint.EndpointController
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping

@EndpointController("internalSchema")
class InternalBookController {

    @QueryMapping
    fun book(@Argument id: Long): Book {
        return BookDataSource.books.first { it.id == id }
    }

    @QueryMapping
    fun books(): List<Book> {
        return BookDataSource.books
    }
}
