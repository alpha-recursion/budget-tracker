package com.example

import com.example.domain.VendorMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VendorMatcherTest {

    @Test
    fun truncatedNamesBelongToTheFullestName() {
        val names = listOf("Some Other T Center", "Some Other T C", "Some Other T Cen", "Zepto")
        val canonical = VendorMatcher.canonicalNames(names)
        assertEquals("Some Other T Center", canonical["Some Other T C"])
        assertEquals("Some Other T Center", canonical["Some Other T Cen"])
        assertEquals("Some Other T Center", canonical["Some Other T Center"])
        assertEquals("Zepto", canonical["Zepto"])
    }

    @Test
    fun caseSpacesAndSymbolsAreIgnored() {
        val canonical = VendorMatcher.canonicalNames(listOf("MORNING TEA", "Morning Tea", "Devtea.", "zepto@ybl", "ZEPTO"))
        assertEquals(1, canonical.filterKeys { it.contains("tea", ignoreCase = true) }.values.toSet().size)
        assertEquals(VendorMatcher.compact(canonical.getValue("zepto@ybl")), VendorMatcher.compact(canonical.getValue("ZEPTO")))
    }

    @Test
    fun ambiguousShortNameStaysAlone() {
        val canonical = VendorMatcher.canonicalNames(listOf("Ramesh Kumar", "Ramesh Patil", "Ramesh"))
        assertEquals("Ramesh", canonical["Ramesh"])
        assertEquals("Ramesh Kumar", canonical["Ramesh Kumar"])
        assertEquals("Ramesh Patil", canonical["Ramesh Patil"])
    }

    @Test
    fun veryShortNamesNeverMerge() {
        // "Dev" (3 letters) must not swallow "Morning Tea".
        val canonical = VendorMatcher.canonicalNames(listOf("Dev", "Morning Tea"))
        assertEquals("Dev", canonical["Dev"])
    }

    @Test
    fun bestMatchPrefersExactThenSinglePrefix() {
        val rules = listOf("someothertcenter", "zepto", "rameshkumar", "rameshpatil")
        assertEquals("someothertcenter", VendorMatcher.bestMatch("SOME OTHER T C", rules))
        assertEquals("zepto", VendorMatcher.bestMatch("Zepto", rules))
        assertEquals("zepto", VendorMatcher.bestMatch("Zepto Cafe", rules)) // typed more than the rule
        assertNull(VendorMatcher.bestMatch("Ramesh", rules)) // ambiguous
        assertNull(VendorMatcher.bestMatch("Zep", rules)) // too short
    }
}
