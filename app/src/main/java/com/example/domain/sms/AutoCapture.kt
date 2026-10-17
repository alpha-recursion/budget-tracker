package com.example.domain.sms

import com.example.data.model.TransactionType

/** A transaction live capture records without asking, using the payee's remembered choice. */
data class AutoCaptured(
    val candidate: ImportCandidate,
    val category: String,
    val isSelfTransfer: Boolean
)

data class CaptureDecision(
    val autoAdd: List<AutoCaptured>,
    val toReview: List<ImportCandidate>
)

/** The last transaction live capture handled, shown in the notification and on Home. */
data class CapturedPayment(
    val title: String,
    val amount: Double,
    val type: TransactionType,
    /** Category it was added under; null while it waits for review. */
    val addedAs: String?
)

/**
 * What live capture did since the owner last opened the app. Pure Kotlin so the rules are unit
 * tested; persisted by the app between broadcasts.
 */
data class CaptureState(
    /** Dedupe keys of transactions waiting for review (a set, so a repeated SMS counts once). */
    val pendingKeys: Set<String> = emptySet(),
    /** A scan from this time shows every waiting transaction. */
    val pendingSinceMs: Long? = null,
    /** When the newest waiting transaction arrived. */
    val lastPendingAt: Long? = null,
    val addedCount: Int = 0,
    val addedTotal: Double = 0.0,
    val latest: CapturedPayment? = null
) {
    val pendingCount: Int get() = pendingKeys.size

    val hasNews: Boolean get() = pendingKeys.isNotEmpty() || addedCount > 0

    /** Adds what one incoming SMS produced; self transfers and reversals are recorded silently. */
    fun record(decision: CaptureDecision, arrivedAtMs: Long, scanFromMs: Long): CaptureState {
        val added = decision.autoAdd.filterNot { it.isSelfTransfer || it.candidate.cancelsKey != null }
        val newPending = decision.toReview.map { it.dedupeKey }.filterNot { it in pendingKeys }
        val latestPayment = decision.toReview.maxByOrNull { it.receivedAt }?.let {
            CapturedPayment(it.title, it.parsed.amount, it.parsed.type, addedAs = null)
        } ?: added.maxByOrNull { it.candidate.receivedAt }?.let {
            CapturedPayment(it.candidate.title, it.candidate.parsed.amount, it.candidate.parsed.type, addedAs = it.category)
        }
        return copy(
            pendingKeys = pendingKeys + newPending,
            pendingSinceMs = if (newPending.isEmpty()) pendingSinceMs else minOf(pendingSinceMs ?: scanFromMs, scanFromMs),
            lastPendingAt = if (newPending.isEmpty()) lastPendingAt else maxOf(lastPendingAt ?: arrivedAtMs, arrivedAtMs),
            addedCount = addedCount + added.size,
            addedTotal = addedTotal + added.sumOf { it.candidate.parsed.amount },
            latest = latestPayment ?: latest
        )
    }

    /**
     * Clears the waiting list after a review that covered it: the scan started at or before the
     * oldest waiting SMS and ran after the newest one arrived. Unticked items then simply show up
     * in the next manual scan, as usual.
     */
    fun afterReview(scanFromMs: Long, scannedAtMs: Long): CaptureState {
        val since = pendingSinceMs ?: return this
        val last = lastPendingAt ?: return this
        if (scanFromMs > since || scannedAtMs < last) return this
        return dismissPending()
    }

    /** Forgets the waiting list; the SMS stay in the inbox for a manual scan. */
    fun dismissPending(): CaptureState = copy(
        pendingKeys = emptySet(),
        pendingSinceMs = null,
        lastPendingAt = null,
        latest = latest?.takeIf { it.addedAs != null }
    )

    /** The owner opened the app: auto-added payments are now visible in the list. */
    fun seen(): CaptureState = copy(
        addedCount = 0,
        addedTotal = 0.0,
        latest = latest?.takeIf { pendingKeys.isNotEmpty() && it.addedAs == null }
    )
}

object AutoCapture {

    /** How far before an SMS arrives the review scan starts, for clock differences. */
    const val SCAN_MARGIN_MS = 10 * 60 * 1000L

    /**
     * Owner's rule: money to or from a payee whose category was already chosen is added straight
     * away, and a reversal of a recorded payment cancels it; everything else waits for review.
     * Only clean messages with a UPI reference qualify: possible duplicates of a hand-entered
     * payment need a human, and without a reference the dedupe key depends on the arrival time,
     * which the inbox may record slightly differently - a later scan could import it twice.
     */
    fun decide(plan: ImportPlan): CaptureDecision {
        val autoAdd = ArrayList<AutoCaptured>()
        val toReview = ArrayList<ImportCandidate>()
        for (group in plan.groups) {
            for (item in group.items) {
                val automatic = item.cancelsKey != null || (
                    group.hasRememberedChoice &&
                        item.parsed.upiRef != null &&
                        item.selectedByDefault &&
                        !item.looksLikeSelfTransfer
                    )
                if (automatic) {
                    autoAdd += AutoCaptured(item, group.suggestedCategory, group.suggestedSelfTransfer)
                } else {
                    toReview += item
                }
            }
        }
        return CaptureDecision(autoAdd, toReview)
    }
}
