package com.example.domain

import com.example.data.local.TransactionEntity
import java.util.Locale

/** A one-tap expense: the payee, its usual amount and category. */
data class QuickChip(val key: String, val title: String, val amount: Double, val category: String, val pinned: Boolean)

/** A chip the owner pinned so it always shows, even without recent history. */
data class QuickPin(val title: String, val amount: Double, val category: String) {
    val key: String get() = QuickAdd.key(title)
}

/** Quick-add chips from the owner's own habits. Pure Kotlin. */
object QuickAdd {

    const val WINDOW_DAYS = 60
    const val MAX_CHIPS = 8
    private const val MIN_TIMES = 2
    private const val DAY_MS = 86_400_000L

    fun key(title: String): String = title.trim().lowercase(Locale.US).replace(Regex("""\s+"""), " ")

    /**
     * Pinned chips first (in pin order), then payees paid at least twice in the last 60 days,
     * most frequent first. The amount is the one used most often (latest on a tie) and the
     * category is the latest one used. Hidden payees never show.
     */
    fun chips(
        transactions: List<TransactionEntity>,
        pins: List<QuickPin>,
        hidden: Set<String>,
        nowMs: Long
    ): List<QuickChip> {
        val since = nowMs - WINDOW_DAYS * DAY_MS
        val pinnedKeys = pins.map { it.key }.toSet()
        val pinned = pins.filter { it.key !in hidden }
            .map { QuickChip(it.key, it.title, it.amount, it.category, pinned = true) }
        val automatic = transactions
            .filter { it.type == "EXPENSE" && it.date in since..nowMs && it.title.isNotBlank() }
            .groupBy { key(it.title) }
            .filterKeys { it !in hidden && it !in pinnedKeys }
            .filterValues { it.size >= MIN_TIMES }
            .map { (k, list) ->
                val latest = list.maxBy { it.date }
                val usual = list.groupBy { it.amount }
                    .maxWith(compareBy<Map.Entry<Double, List<TransactionEntity>>> { it.value.size }.thenBy { e -> e.value.maxOf { it.date } })
                    .key
                Triple(QuickChip(k, latest.title, usual, latest.category, pinned = false), list.size, latest.date)
            }
            .sortedWith(compareByDescending<Triple<QuickChip, Int, Long>> { it.second }.thenByDescending { it.third })
            .map { it.first }
        return (pinned + automatic).take(MAX_CHIPS)
    }

    /** Pins as a simple text list (title, amount, category per line, tab separated). */
    fun encodePins(pins: List<QuickPin>): String =
        pins.joinToString("\n") { listOf(clean(it.title), it.amount.toString(), clean(it.category)).joinToString("\t") }

    fun decodePins(text: String?): List<QuickPin> =
        text.orEmpty().lines().mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size != 3) return@mapNotNull null
            val amount = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            if (parts[0].isBlank() || amount <= 0) null else QuickPin(parts[0], amount, parts[2])
        }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
}
