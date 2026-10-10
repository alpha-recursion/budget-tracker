package com.example.ui.util

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Pure helpers for chart axes (unit tested). */
object ChartMath {

    /** Rounds [value] up to 1, 2, 2.5 or 5 x 10^n. */
    fun niceStep(value: Double): Double {
        if (value <= 0.0) return 1.0
        val magnitude = 10.0.pow(ceil(log10(value)) - 1)
        val normalized = value / magnitude
        val nice = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= normalized - 1e-9 }
        return nice * magnitude
    }

    /**
     * Grid values from 0 to just above [maxValue] in friendly steps, aiming for about four
     * intervals (e.g. 453 -> 0, 200, 400, 600). Empty data still gets a small scale.
     */
    fun gridValues(maxValue: Double): List<Double> {
        val max = if (maxValue <= 0.0) 100.0 else maxValue
        val step = max(niceStep(max / 4.0), 1.0)
        val count = max(1, ceil(max / step - 1e-9).toInt())
        return List(count + 1) { it * step }
    }

    /** ₹0, ₹750, ₹1.2k, ₹15k, ₹1.5L (Indian lakh) for axis labels. */
    fun compactRupees(value: Double): String = when {
        value >= 100_000 -> "₹" + trim(value / 100_000) + "L"
        value >= 1_000 -> "₹" + trim(value / 1_000) + "k"
        else -> "₹" + value.toInt()
    }

    private fun trim(v: Double): String =
        if (v >= 10 || v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
}
