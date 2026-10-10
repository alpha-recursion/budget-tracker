package com.example

import com.example.data.local.TransactionEntity
import com.example.domain.CategoryPropagation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryPropagationTest {

    private fun tx(id: Long, title: String, category: String = "Other", type: String = "EXPENSE") =
        TransactionEntity(id = id, title = title, amount = 10.0, type = type, category = category, date = id)

    @Test
    fun findsTruncatedSpellingsOfTheSameVendor() {
        val all = listOf(
            tx(1, "Some Other T Center"),
            tx(2, "Some Other T C"),
            tx(3, "Some Other T Cen"),
            tx(4, "Some Other T Cen", category = "Food & Dining"), // already right
            tx(5, "Zepto"),
            tx(6, "Some Other T C", type = "INCOME") // different direction
        )
        val edited = tx(2, "Some Other T C", category = "Food & Dining")
        val similar = CategoryPropagation.similarTo(edited, "Food & Dining", all)
        assertEquals(listOf(1L, 3L), similar.map { it.id })
        assertEquals("Some Other T Center", CategoryPropagation.vendorName(edited, similar))
    }

    @Test
    fun ambiguousShortTitleIsNotPropagated() {
        val all = listOf(tx(1, "Ramesh Kumar"), tx(2, "Ramesh Patil"), tx(3, "Ramesh"))
        assertTrue(CategoryPropagation.similarTo(tx(3, "Ramesh", "Food & Dining"), "Food & Dining", all).isEmpty())
    }
}
