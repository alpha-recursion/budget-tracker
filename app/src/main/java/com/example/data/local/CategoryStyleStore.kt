package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The icon and colour the owner picked for a built-in category. */
data class CategoryStyle(val iconKey: String, val colorHex: Long)

/**
 * Icon / colour overrides for built-in categories, keyed by category name. Custom categories
 * keep their style in the database; built-ins are fixed in code, so their changes live here.
 */
class CategoryStyleStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("category_styles", Context.MODE_PRIVATE)

    fun read(): Map<String, CategoryStyle> = decode(prefs.getStringSet(KEY_STYLES, null).orEmpty())

    val styles: Flow<Map<String, CategoryStyle>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_STYLES) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Sets [style] for [name], or removes the override when [style] is null (back to default). */
    @Synchronized
    fun set(name: String, style: CategoryStyle?) {
        val next = if (style == null) read() - name else read() + (name to style)
        replaceAll(next)
    }

    fun replaceAll(styles: Map<String, CategoryStyle>) {
        prefs.edit().putStringSet(KEY_STYLES, encode(styles)).apply()
    }

    companion object {
        private const val KEY_STYLES = "styles"

        // One "name<TAB>iconKey<TAB>colour" entry per category; names cannot contain tabs.
        internal fun encode(styles: Map<String, CategoryStyle>): Set<String> =
            styles.map { (name, s) -> "$name\t${s.iconKey}\t${s.colorHex}" }.toSet()

        internal fun decode(entries: Set<String>): Map<String, CategoryStyle> =
            entries.mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 3) return@mapNotNull null
                val color = parts[2].toLongOrNull() ?: return@mapNotNull null
                parts[0] to CategoryStyle(parts[1], color)
            }.toMap()
    }
}
