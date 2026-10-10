package com.example.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formatters {

    // Indian rupees with Indian digit grouping (₹1,23,456.00). The symbol is added by hand so
    // it is always "₹" regardless of what the platform's currency data uses for en-IN.
    private val rupeeNumberFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    // Formatting only happens on the main thread, so these can be shared instead of
    // allocating a SimpleDateFormat for every transaction row on every recomposition.
    private val fullDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val shortDateFormat = SimpleDateFormat("MMM d", Locale.US)
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.US)

    fun formatCurrency(amount: Double): String {
        return if (amount < 0) {
            "-₹" + rupeeNumberFormat.format(-amount)
        } else {
            "₹" + rupeeNumberFormat.format(amount)
        }
    }

    fun formatSignedCurrency(amount: Double, type: String): String {
        val formatted = formatCurrency(amount)
        return if (type.equals("INCOME", ignoreCase = true)) {
            "+$formatted"
        } else {
            "-$formatted"
        }
    }

    fun formatPercentage(percentage: Double): String {
        return String.format(Locale.US, "%.1f%%", percentage)
    }

    fun formatDate(timestampMs: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestampMs }

        val isToday = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        if (isToday) return "Today"

        now.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) return "Yesterday"

        return fullDateFormat.format(Date(timestampMs))
    }

    fun formatShortDate(timestampMs: Long): String {
        return shortDateFormat.format(Date(timestampMs))
    }

    fun formatMonthYear(timestampMs: Long): String {
        return monthYearFormat.format(Date(timestampMs))
    }
}

fun getCategoryIcon(iconKey: String): ImageVector {
    return when (iconKey) {
        "restaurant" -> Icons.Default.Restaurant
        "shopping_bag" -> Icons.Default.ShoppingBag
        "home" -> Icons.Default.Home
        "directions_car" -> Icons.Default.DirectionsCar
        "bolt" -> Icons.Default.Bolt
        "movie" -> Icons.Default.Movie
        "local_hospital" -> Icons.Default.LocalHospital
        "work" -> Icons.Default.Work
        "laptop" -> Icons.Default.Laptop
        "trending_up" -> Icons.AutoMirrored.Filled.TrendingUp
        else -> Icons.Default.MoreHoriz
    }
}
