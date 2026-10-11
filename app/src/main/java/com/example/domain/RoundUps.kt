package com.example.domain

import com.example.data.local.TransactionEntity
import kotlin.math.ceil

/**
 * Round-up jar settings. The jar holds the spare change of every expense dated on or after
 * [startedAt]: [banked] keeps what was collected before the rounding unit last changed, and
 * [moved] is what has already gone into goals since then.
 */
data class RoundUpSettings(
    val enabled: Boolean = false,
    val unit: Int = 10,
    val startedAt: Long = 0L,
    val banked: Double = 0.0,
    val moved: Double = 0.0
)

/** Spare change from expenses, collected in a jar the owner empties into a goal. Pure Kotlin. */
object RoundUps {

    val UNITS = listOf(10, 50, 100)
    private const val EPSILON = 0.005

    /** ₹453 with unit 10 -> ₹7; an amount already on the unit adds nothing. */
    fun roundUp(amount: Double, unit: Int): Double {
        if (amount <= 0.0 || unit <= 0) return 0.0
        val up = ceil(amount / unit - 1e-9) * unit - amount
        return if (up < EPSILON) 0.0 else up
    }

    /** Spare change of expenses dated on or after [sinceMs]. */
    fun collected(transactions: List<TransactionEntity>, sinceMs: Long, unit: Int): Double =
        transactions
            .filter { it.type == "EXPENSE" && it.date >= sinceMs }
            .sumOf { roundUp(it.amount, unit) }

    /** What is in the jar now (never negative, e.g. after expenses were deleted). */
    fun jar(settings: RoundUpSettings, transactions: List<TransactionEntity>): Double {
        if (settings.startedAt <= 0L) return settings.banked.coerceAtLeast(0.0)
        val total = settings.banked + collected(transactions, settings.startedAt, settings.unit) - settings.moved
        return if (total < EPSILON) 0.0 else total
    }

    /** Turning on starts collecting from now; turning off keeps the jar so it can still be moved. */
    fun setEnabled(settings: RoundUpSettings, enabled: Boolean, transactions: List<TransactionEntity>, nowMs: Long): RoundUpSettings =
        when {
            enabled == settings.enabled -> settings
            enabled -> settings.copy(enabled = true, startedAt = nowMs, banked = jar(settings, transactions), moved = 0.0)
            else -> RoundUpSettings(enabled = false, unit = settings.unit, startedAt = 0L, banked = jar(settings, transactions))
        }

    /** A new unit applies from now on; what was collected so far stays in the jar. */
    fun changeUnit(settings: RoundUpSettings, unit: Int, transactions: List<TransactionEntity>, nowMs: Long): RoundUpSettings {
        if (unit == settings.unit) return settings
        val kept = jar(settings, transactions)
        return settings.copy(unit = unit, startedAt = if (settings.enabled) nowMs else 0L, banked = kept, moved = 0.0)
    }

    /** After [amount] was moved into a goal. */
    fun emptied(settings: RoundUpSettings, amount: Double): RoundUpSettings =
        if (settings.startedAt <= 0L) settings.copy(banked = (settings.banked - amount).coerceAtLeast(0.0))
        else settings.copy(moved = settings.moved + amount)
}
