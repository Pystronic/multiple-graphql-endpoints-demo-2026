package com.example.multiplegraphqlendpointsdemo.book

import java.time.LocalDate

object BookDataSource {
    val books = listOf(
        Book(1, "Book1", LocalDate.now().minusYears(10), "4/5", 1),
        Book(2, "Book2", LocalDate.now().minusYears(5), "5/5", 2),
        Book(3, "Book3", LocalDate.now().minusYears(1), "2/5", 3)
    )
}
