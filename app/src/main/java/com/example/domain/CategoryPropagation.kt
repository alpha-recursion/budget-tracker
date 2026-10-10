package com.example.domain

import com.example.data.local.TransactionEntity

/** Which other transactions should follow a category change made on one of them. */
object CategoryPropagation {

    /**
     * Transactions of the same type from the same vendor as [edited] (see [VendorMatcher])
     * that do not already have [newCategory]. [all] may contain the old version of [edited].
     */
    fun similarTo(edited: TransactionEntity, newCategory: String, all: List<TransactionEntity>): List<TransactionEntity> {
        if (VendorMatcher.compact(edited.title).isEmpty()) return emptyList()
        val sameType = all.filter { it.type == edited.type && it.id != edited.id }
        val canonical = VendorMatcher.canonicalNames(sameType.map { it.title } + edited.title)
        val target = VendorMatcher.groupKey(edited.title, canonical)
        return sameType.filter { tx ->
            tx.category != newCategory && VendorMatcher.groupKey(tx.title, canonical) == target
        }
    }

    /** Fullest spelling of the vendor, for messages like "Also change 3 from Some Other T Center?". */
    fun vendorName(edited: TransactionEntity, similar: List<TransactionEntity>): String =
        (similar.map { it.title } + edited.title).maxBy { VendorMatcher.compact(it).length }
}
