package com.example.data.backup

import android.content.Context
import com.example.data.local.CategoryStyleStore
import com.example.data.local.PlanningStore
import com.example.data.local.RewardsStore
import com.example.data.local.RoundUpStore
import com.example.data.reminders.DailyReviewStore

/**
 * Moves the settings kept in SharedPreferences in and out of a backup, and clears them when
 * all data is erased, so backup, restore and erase cover more than the database.
 */
class PhoneSettingsBackup(context: Context) {

    private val dailyReview = DailyReviewStore(context)
    private val planning = PlanningStore(context)
    private val roundUps = RoundUpStore(context)
    private val rewards = RewardsStore(context)
    private val categoryStyles = CategoryStyleStore(context)

    fun read(): PhoneSettings = PhoneSettings(
        checkInDays = dailyReview.days(),
        planning = planning.read(),
        roundUps = roundUps.read(),
        seenBadges = rewards.seenBadges(),
        lastLevel = rewards.lastLevel(),
        categoryStyles = categoryStyles.read()
    )

    fun apply(settings: PhoneSettings) {
        dailyReview.replaceAll(settings.checkInDays)
        planning.update { settings.planning }
        roundUps.update { settings.roundUps }
        rewards.replaceProgress(settings.seenBadges, settings.lastLevel)
        categoryStyles.replaceAll(settings.categoryStyles)
    }

    fun clear() = apply(PhoneSettings())
}
