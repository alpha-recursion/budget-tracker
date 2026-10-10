package com.example

import com.example.ui.util.ChartMath
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMathTest {
    @Test
    fun gridUsesFriendlySteps() {
        assertEquals(listOf(0.0, 200.0, 400.0, 600.0), ChartMath.gridValues(453.0))
        assertEquals(listOf(0.0, 500.0, 1000.0, 1500.0), ChartMath.gridValues(1250.0))
        assertEquals(listOf(0.0, 25.0, 50.0, 75.0, 100.0), ChartMath.gridValues(0.0))
        val big = ChartMath.gridValues(123_456.0)
        assertEquals(0.0, big.first(), 0.0)
        assert(big.last() >= 123_456.0 && big.size in 3..6)
    }

    @Test
    fun compactRupeeLabels() {
        assertEquals("₹0", ChartMath.compactRupees(0.0))
        assertEquals("₹750", ChartMath.compactRupees(750.0))
        assertEquals("₹1.5k", ChartMath.compactRupees(1500.0))
        assertEquals("₹15k", ChartMath.compactRupees(15000.0))
        assertEquals("₹1.5L", ChartMath.compactRupees(150_000.0))
    }
}
