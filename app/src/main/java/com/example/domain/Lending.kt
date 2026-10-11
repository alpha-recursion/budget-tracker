package com.example.domain

import com.example.data.local.DebtEntryEntity
import com.example.data.model.TransactionType
import java.util.Locale
import kotlin.math.abs

/** How money moved between the owner and a person. */
enum class DebtKind(val label: String) {
    /** The owner gave money: they owe the owner more. */
    LENT("Lent"),

    /** They returned money the owner lent. */
    GOT_BACK("Got back"),

    /** They gave the owner money: the owner owes them more. */
    BORROWED("Borrowed"),

    /** The owner returned money they had borrowed. */
    PAID_BACK("Paid back");

    /** True when this kind moves the balance towards "they owe me". */
    val towardsTheyOweMe: Boolean get() = this == LENT || this == PAID_BACK

    /** Lending and borrowing can carry a due date; repayments cannot. */
    val canHaveDueDate: Boolean get() = this == LENT || this == BORROWED

    /** Money left the owner's account for LENT and PAID_BACK. */
    val transactionType: TransactionType
        get() = if (towardsTheyOweMe) TransactionType.EXPENSE else TransactionType.INCOME

    companion object {
        fun fromName(name: String): DebtKind? = entries.firstOrNull { it.name == name }

        /** The two kinds that match money going out (expense) or coming in (income). */
        fun forType(type: TransactionType): List<DebtKind> =
            if (type == TransactionType.EXPENSE) listOf(LENT, PAID_BACK) else listOf(GOT_BACK, BORROWED)
    }
}

/** One person's position. [balance] > 0: they owe the owner; < 0: the owner owes them. */
data class PersonBalance(
    val key: String,
    val name: String,
    val balance: Double,
    /** Newest first. */
    val entries: List<DebtEntryEntity>,
    /** Earliest due date of money still outstanding that has already passed. */
    val overdueSince: Long?,
    /** Earliest due date of money still outstanding that has not passed yet. */
    val nextDue: Long?,
    val lastActivity: Long
) {
    val isSettled: Boolean get() = abs(balance) < Lending.EPSILON
    val theyOweMe: Boolean get() = balance >= Lending.EPSILON
    val iOweThem: Boolean get() = balance <= -Lending.EPSILON
    val isOverdue: Boolean get() = overdueSince != null
}

data class LendingTotals(val owedToMe: Double, val iOwe: Double, val overdueCount: Int)

/** Money lent and borrowed, per person. Pure Kotlin so every rule is unit tested. */
object Lending {

    const val EPSILON = 0.005

    /** Groups entries for the same person whatever the capitalisation or spacing. */
    fun personKey(name: String): String =
        name.trim().lowercase(Locale.US).replace(Regex("""\s+"""), " ")

    /** Tidy display name: trimmed, single-spaced; ALL-CAPS bank names become Title Case. */
    fun displayName(name: String): String {
        val clean = name.trim().replace(Regex("""\s+"""), " ")
        if (clean.any { it.isLowerCase() }) return clean
        return clean.lowercase(Locale.US).split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase(Locale.US) }
        }
    }

    /**
     * Balances per person, people with money outstanding first (overdue, then largest), settled
     * people last. Repayments are matched to the oldest outstanding amount first, so a due date
     * stops counting once that money is back.
     */
    fun balances(entries: List<DebtEntryEntity>, todayStartMs: Long): List<PersonBalance> =
        entries.groupBy { it.personKey }
            .map { (key, personEntries) -> personBalance(key, personEntries, todayStartMs) }
            .sortedWith(
                compareBy<PersonBalance> { it.isSettled }
                    .thenByDescending { it.isOverdue }
                    .thenByDescending { abs(it.balance) }
                    .thenByDescending { it.lastActivity }
            )

    fun totals(balances: List<PersonBalance>): LendingTotals = LendingTotals(
        owedToMe = balances.filter { it.theyOweMe }.sumOf { it.balance },
        iOwe = balances.filter { it.iOweThem }.sumOf { -it.balance },
        overdueCount = balances.count { it.isOverdue }
    )

    /**
     * Sensible default when an SMS or transaction is marked as lending: money out is a loan
     * unless the owner owes this person; money in is a repayment only if they owe the owner.
     */
    fun defaultKind(type: TransactionType, balance: Double): DebtKind = when (type) {
        TransactionType.EXPENSE -> if (balance <= -EPSILON) DebtKind.PAID_BACK else DebtKind.LENT
        TransactionType.INCOME -> if (balance >= EPSILON) DebtKind.GOT_BACK else DebtKind.BORROWED
    }

    /**
     * The existing person a bank payee most likely is, e.g. "YOGESH BBAN SUPEKAR" -> "Yogesh".
     * Null when nobody matches or the match is ambiguous.
     */
    fun matchPerson(payee: String, people: List<PersonBalance>): PersonBalance? {
        val key = personKey(payee)
        people.firstOrNull { it.key == key }?.let { return it }
        val byCompact = people.associateBy { VendorMatcher.compact(it.name) }
        val match = VendorMatcher.bestMatch(payee, byCompact.keys)
        if (match != null) return byCompact[match]
        // A first name alone ("Yogesh") is too short for the vendor rule; accept it when it is
        // exactly the payee's first word and no one else shares it.
        val firstWord = key.substringBefore(' ')
        val sameFirst = people.filter { personKey(it.name) == firstWord }
        return sameFirst.singleOrNull()
    }

    private class Open(var remaining: Double, val due: Long?)

    private fun personBalance(key: String, entries: List<DebtEntryEntity>, todayStartMs: Long): PersonBalance {
        // Two queues of outstanding money; every movement first cancels the other side's oldest.
        val theyOwe = ArrayDeque<Open>()
        val iOwe = ArrayDeque<Open>()
        for (entry in entries.sortedWith(compareBy<DebtEntryEntity> { it.date }.thenBy { it.id })) {
            val kind = DebtKind.fromName(entry.kind) ?: continue
            val (reduce, grow) = if (kind.towardsTheyOweMe) iOwe to theyOwe else theyOwe to iOwe
            var left = entry.amount
            while (left > EPSILON && reduce.isNotEmpty()) {
                val oldest = reduce.first()
                val used = minOf(left, oldest.remaining)
                oldest.remaining -= used
                left -= used
                if (oldest.remaining < EPSILON) reduce.removeFirst()
            }
            if (left > EPSILON) grow.addLast(Open(left, if (kind.canHaveDueDate) entry.dueDate else null))
        }
        val open = theyOwe + iOwe
        val dues = open.mapNotNull { it.due }
        val newest = entries.maxWith(compareBy<DebtEntryEntity> { it.date }.thenBy { it.id })
        return PersonBalance(
            key = key,
            name = newest.person,
            balance = theyOwe.sumOf { it.remaining } - iOwe.sumOf { it.remaining },
            entries = entries.sortedWith(compareByDescending<DebtEntryEntity> { it.date }.thenByDescending { it.id }),
            overdueSince = dues.filter { it < todayStartMs }.minOrNull(),
            nextDue = dues.filter { it >= todayStartMs }.minOrNull(),
            lastActivity = newest.date
        )
    }
}
