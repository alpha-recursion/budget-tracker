package com.example.domain

import com.example.data.local.TransactionEntity
import com.example.data.model.Category
import com.example.data.model.TransactionType
import java.util.Locale

/** Why a category was suggested. */
enum class GuessSource { KEYWORD, LEARNED }

sealed interface AutoSuggestion {
    /** [matched] is the word or phrase that decided it, shown to the owner ("pharmacy"). */
    data class Category(val name: String, val source: GuessSource, val matched: String) : AutoSuggestion

    /** Looks like a person: no category is guessed; the owner picks lending or a category. */
    data object Person : AutoSuggestion
}

/**
 * What the owner's own categorised payees say about words in payee names: after "Ak pan shop"
 * is filed under Tea & Snacks, "pan shop" points there for any new pan shop. Built from the
 * transactions, so every correction teaches it without a separate store.
 */
class CategoryModel private constructor(
    // type -> feature (word or two-word phrase) -> category -> number of distinct payees
    private val counts: Map<TransactionType, Map<String, Map<String, Int>>>
) {
    /**
     * @param strongOnly only a two-word phrase or a word two payees agree on; strong enough to
     *   trust even for a name that could be a person's.
     */
    fun predict(
        payee: String,
        type: TransactionType,
        validCategories: Set<String>,
        strongOnly: Boolean = false
    ): AutoSuggestion.Category? {
        val byFeature = counts[type] ?: return null
        return AutoCategorizer.features(payee)
            .mapNotNull { feature ->
                val dist = byFeature[feature] ?: return@mapNotNull null
                val (category, n) = dist.maxByOrNull { it.value } ?: return@mapNotNull null
                val purity = n.toDouble() / dist.values.sum()
                val isPhrase = ' ' in feature
                // A single word needs two payees agreeing, unless it is long enough to be distinctive.
                val enough = isPhrase || n >= 2 || (!strongOnly && feature.length >= 6)
                if (category !in validCategories || purity < MIN_PURITY || !enough) null
                else Triple(feature, category, n)
            }
            // Phrases beat single words; then the most payees.
            .maxWithOrNull(compareBy<Triple<String, String, Int>>({ ' ' in it.first }, { it.third }))
            ?.let { (feature, category, _) -> AutoSuggestion.Category(category, GuessSource.LEARNED, feature) }
    }

    companion object {
        private const val MIN_PURITY = 0.75
        val EMPTY = CategoryModel(emptyMap())

        /** Learns from transactions with a real category (not "Other") and a business-like payee. */
        fun learn(transactions: List<TransactionEntity>): CategoryModel {
            val other = Category.OTHER.displayName
            val counts = HashMap<TransactionType, HashMap<String, HashMap<String, Int>>>()
            transactions
                .filter { !it.category.equals(other, ignoreCase = true) }
                .groupBy { (if (it.type == TransactionType.INCOME.name) TransactionType.INCOME else TransactionType.EXPENSE) to AutoCategorizer.normalize(it.title) }
                .forEach { (key, txs) ->
                    val (type, title) = key
                    if (title.isEmpty() || AutoCategorizer.looksLikePerson(title)) return@forEach
                    // One vote per payee: its most used category (latest wins a tie).
                    val category = txs.groupBy { it.category }.entries
                        .maxWithOrNull(
                            compareBy<Map.Entry<String, List<TransactionEntity>>>({ it.value.size }, { e -> e.value.maxOf { t -> t.date } })
                        )!!.key
                    val byFeature = counts.getOrPut(type) { HashMap() }
                    AutoCategorizer.features(title).forEach { feature ->
                        val dist = byFeature.getOrPut(feature) { HashMap() }
                        dist[category] = (dist[category] ?: 0) + 1
                    }
                }
            return CategoryModel(counts)
        }
    }
}

/**
 * Suggests a category for a payee nobody has categorised yet, entirely on the phone:
 * 1. built-in keywords for Indian payees ("pharmacy" -> Healthcare, "pan shop" -> Tea & Snacks),
 * 2. a strong match with what the owner's own categorised payees taught ([CategoryModel]),
 * 3. a personal name -> [AutoSuggestion.Person] (no guess),
 * 4. a weaker learned match.
 * The owner's remembered choice for the exact payee is applied before this, by the caller.
 */
object AutoCategorizer {

    private class KeywordRule(val pattern: Regex, val targets: List<String>, val type: TransactionType = TransactionType.EXPENSE)

    private fun rule(pattern: String, vararg targets: String, type: TransactionType = TransactionType.EXPENSE) =
        KeywordRule(Regex(pattern), targets.toList(), type)

    // Checked in order; each lists categories to use, the first one that exists wins (so a
    // deleted or renamed category falls back to a broader one).
    private val KEYWORDS = listOf(
        rule("""hospital|clinic|pharma|medical|medico|chemist|diagnostic|patholog|path ?lab|dental|dentist|doctor|nursing ?home|wellness ?forever|apollo|netmeds|\b1mg\b|practo|ayurved""", "Healthcare"),
        rule("""\bjio\b|airtel|vodafone|\bvi (?:prepaid|postpaid)\b|\bbsnl\b|recharge|prepaid|postpaid""", "Mobile & Recharge", "Utilities & Bills"),
        rule("""petrol|\bfuel|diesel|filling ?station|\bcng\b|indian ?oil|\biocl\b|\bbpcl\b|\bhpcl\b|\bmotors?\b|auto ?service|garage|\btyres?\b|puncture|automobile|two ?wheeler|service ?station""", "Fuel & Vehicle", "Transportation"),
        rule("""\buber\b|\bola\b|rapido|\bmetro\b|\bncmc\b|irctc|railway|\bmsrtc\b|redbus|\bbus\b|fastag|parking|\btoll\b""", "Transportation"),
        rule("""\btea\b|\bchai\b|\bcafe\b|café|coffee|paan|\bpan ?shop|panshop|snack|wada ?pav|vada ?pav|\bbhel\b|chaat|samosa|juice|amrutatulya|yewale|tapri|ice ?cream|kulfi""", "Tea & Snacks", "Food & Dining"),
        rule("""hotel|restaurant|\bdhaba\b|\bveg\b|dosa|idli|biryani|kitchen|khanawal|khanaval|\bmess\b|bhojan|\bfoods?\b|pizza|burger|zomato|swiggy|eatery|dining|poli ?bhaji|misal|\bsweets?\b|mithai|bakery|chinese""", "Food & Dining"),
        rule("""super ?market|supermarket|\bmart\b|kirana|provision|general ?stores?|\btraders?\b|grocer|\bdairy\b|\bmilk\b|zepto|blinkit|instamart|big ?basket|\bd ?mart\b|\bmarket\b|vegetable|\bsabzi\b|\bsabji\b|\bfruits?\b""", "Groceries", "Shopping"),
        rule("""zudio|myntra|amazon|flipkart|\bajio\b|meesho|nykaa|cosmetic|fashion|garment|footwear|\bshoes?\b|decathlon|\btrends\b|\bmall\b|westside|electronics|stationery""", "Shopping"),
        rule("""electricity|msedcl|mahavitaran|\bmseb\b|water ?bill|broadband|fibernet|\bgas\b|mahanagar|\bdth\b|tata ?play""", "Utilities & Bills"),
        rule("""bookmyshow|\bpvr\b|\binox\b|cinema|multiplex|netflix|spotify|hotstar|prime ?video|youtube""", "Entertainment"),
        rule("""\brent\b|society|maintenance|nobroker""", "Housing & Rent"),
        rule("""salary|payroll""", "Salary & Wages", type = TransactionType.INCOME),
        rule("""dividend|\binterest\b|mutual ?fund|zerodha|groww""", "Investments", type = TransactionType.INCOME)
    )

    // Words that mark a business, so a name like "Mohan Enterprises" is not taken for a person.
    private val BUSINESS = Regex(
        """\b(?:shop|store|stores|enterprises?|traders?|industr\w*|limited|ltd|pvt|private|services?|centre|center|agency|agencies|corporation|company|bank|india|kendra|bhandar|mandir|ventures?|group|solutions|technologies|tech|online|digital|associates|brothers|bros|sons|wines?|liquor|bar|beer|x?press|express|studio|works|world|point|house|care|plaza|palace)\b"""
    )
    private val TITLES = setOf("mr", "mrs", "ms", "miss", "smt", "kum")

    // Ignored in learning: honorifics and filler common to many unrelated payees.
    private val STOP_WORDS = setOf("shri", "shree", "sri", "the", "and", "of", "mr", "mrs", "ms", "new", "pvt", "ltd", "limited", "private")
    // Too common to decide anything alone, but still useful inside a phrase ("pan shop").
    private val GENERIC_WORDS = setOf(
        "shop", "store", "stores", "centre", "center", "enterprises", "enterprise", "traders", "trader",
        "services", "service", "india", "pune", "mumbai", "mh", "co", "company", "kendra", "and", "sons"
    )

    fun suggest(
        payee: String,
        type: TransactionType,
        validCategories: Set<String>,
        model: CategoryModel = CategoryModel.EMPTY
    ): AutoSuggestion? {
        val text = normalize(payee)
        if (text.isEmpty()) return null
        keywordMatch(text, type, validCategories)?.let { return it }
        // A shop with a plain name ("Narayan Stola Corner") would look like a person; a strong
        // learned match decides first. Weak single-word matches never override a person.
        model.predict(text, type, validCategories, strongOnly = true)?.let { return it }
        if (looksLikePerson(text)) return AutoSuggestion.Person
        return model.predict(text, type, validCategories)
    }

    private fun keywordMatch(text: String, type: TransactionType, validCategories: Set<String>): AutoSuggestion.Category? {
        for (rule in KEYWORDS) {
            if (rule.type != type) continue
            val match = rule.pattern.find(text) ?: continue
            val target = rule.targets.firstOrNull { it in validCategories } ?: continue
            // Show whole words: "pharma" matched inside "pharmacyipd" reads as "pharmacyipd".
            var start = match.range.first
            var end = match.range.last + 1
            while (start > 0 && text[start - 1].isLetter()) start--
            while (end < text.length && text[end].isLetter()) end++
            return AutoSuggestion.Category(target, GuessSource.KEYWORD, text.substring(start, end).trim())
        }
        return null
    }

    /** "Mr Rohan Mehta", "Priya Nair": two to four plain words, or an honorific, and no business word. */
    fun looksLikePerson(payee: String): Boolean {
        val text = normalize(payee)
        if (text.any { it.isDigit() } || BUSINESS.containsMatchIn(text)) return false
        val words = text.replace(Regex("""[^a-z ]"""), " ").split(' ').filter { it.isNotBlank() }
        if (words.size >= 2 && words.first() in TITLES) return true
        return words.size in 2..4 && words.all { it.length >= 2 }
    }

    /** Lower case, single spaces: "SHREE  Balaji S MART." -> "shree balaji s mart." */
    internal fun normalize(payee: String): String =
        payee.lowercase(Locale.US).replace(Regex("""\s+"""), " ").trim()

    /** Two-word phrases and distinctive single words of a payee name, used for learning. */
    internal fun features(payee: String): List<String> {
        val words = normalize(payee).split(Regex("""[^a-z]+""")).filter { it.length >= 2 && it !in STOP_WORDS }
        val phrases = words.zipWithNext { a, b -> "$a $b" }
        return (phrases + words.filter { it !in GENERIC_WORDS }).distinct()
    }

    /** "Other" payments the categoriser can now place, grouped by payee, most payments first. */
    fun suggestForOther(
        transactions: List<TransactionEntity>,
        validCategories: Set<String>,
        model: CategoryModel
    ): List<OtherFix> {
        val other = Category.OTHER.displayName
        return transactions
            .filter { it.category.equals(other, ignoreCase = true) }
            .groupBy { (if (it.type == TransactionType.INCOME.name) TransactionType.INCOME else TransactionType.EXPENSE) to normalize(it.title) }
            .mapNotNull { (key, txs) ->
                val (type, _) = key
                val suggestion = suggest(txs.first().title, type, validCategories, model) as? AutoSuggestion.Category
                    ?: return@mapNotNull null
                if (suggestion.name.equals(other, ignoreCase = true)) return@mapNotNull null
                OtherFix(
                    title = txs.maxByOrNull { it.date }!!.title,
                    type = type,
                    transactionIds = txs.map { it.id },
                    total = txs.sumOf { it.amount },
                    suggestion = suggestion
                )
            }
            .sortedWith(compareByDescending<OtherFix> { it.transactionIds.size }.thenByDescending { it.total })
    }
}

/** Payments to one payee, currently "Other", that can be moved to [suggestion]. */
data class OtherFix(
    val title: String,
    val type: TransactionType,
    val transactionIds: List<Long>,
    val total: Double,
    val suggestion: AutoSuggestion.Category
)
