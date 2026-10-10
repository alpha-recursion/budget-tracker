package com.example.data.model

/**
 * A category as the app displays it: either one of the built-in [Category] values or a
 * category the user created. Transactions and budgets reference categories by [name].
 */
data class CategoryInfo(
    val name: String,
    val iconKey: String,
    val colorHex: Long,
    val type: TransactionType,
    val isCustom: Boolean = false
) {
    companion object {
        val builtIns: List<CategoryInfo> = Category.entries.map {
            CategoryInfo(it.displayName, it.iconKey, it.colorHex, it.defaultType)
        }

        private val other: CategoryInfo = builtIns.first { it.name == Category.OTHER.displayName }

        /** Categories offered for [type]; "Other" is available for both types. */
        fun forType(all: List<CategoryInfo>, type: TransactionType): List<CategoryInfo> =
            all.filter { it.type == type || it.name == other.name }

        /**
         * Looks up [name] among [all] (built-ins + custom). Unknown names, e.g. a deleted custom
         * category, keep their name but use the "Other" icon and colour.
         */
        fun resolve(name: String, all: List<CategoryInfo> = builtIns): CategoryInfo {
            all.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
            val builtIn = Category.fromDisplayName(name)
            if (builtIn != Category.OTHER) return resolve(builtIn.displayName)
            return other.copy(name = name)
        }
    }
}
