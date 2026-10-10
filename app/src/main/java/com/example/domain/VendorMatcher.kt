package com.example.domain

import java.util.Locale

/**
 * Recognises the same vendor written slightly differently. Banks cut long payee names at
 * different lengths, so one shop can arrive as "SOME OTHER T CENTER", "SOME OTHER T C" and
 * "SOME OTHER T CEN".
 *
 * Two names are the same vendor when, ignoring case, spaces and symbols, one is the start of
 * the other and the shorter one has at least [MIN_PREFIX_LENGTH] letters/digits.
 */
object VendorMatcher {

    const val MIN_PREFIX_LENGTH = 5

    /** "SOME OTHER T C." -> "someothertc"; VPAs use the part before '@' ("swiggy@icici" -> "swiggy"). */
    fun compact(name: String): String =
        name.substringBefore('@').lowercase(Locale.US).filter { it.isLetterOrDigit() }

    private fun isPrefixVariant(shorter: String, longer: String): Boolean =
        shorter.length >= MIN_PREFIX_LENGTH && longer.startsWith(shorter)

    /**
     * Groups [names] into vendors. Returns, for every input name, the canonical name of its
     * group: the fullest (longest) name in that group.
     *
     * A short name joins a group only if it is the start of exactly one group's name; if it
     * could belong to several (e.g. "Ramesh" vs "Ramesh Kumar" and "Ramesh Patil") it stays
     * on its own rather than guessing.
     */
    fun canonicalNames(names: Collection<String>): Map<String, String> {
        // One representative original spelling per compact key: the longest original text.
        val byKey = names.filter { compact(it).isNotEmpty() }
            .groupBy { compact(it) }
            .mapValues { (_, spellings) -> spellings.maxBy { it.length } }

        // Longest keys first, so every group root is at least as long as later keys.
        val roots = ArrayList<String>()
        val rootOf = HashMap<String, String>()
        for (key in byKey.keys.sortedByDescending { it.length }) {
            val matches = roots.filter { isPrefixVariant(key, it) }
            val root = if (matches.size == 1) matches.single() else key.also { roots += it }
            rootOf[key] = root
        }

        return names.associateWith { name ->
            val key = compact(name)
            if (key.isEmpty()) name else byKey.getValue(rootOf.getValue(key))
        }
    }

    /** Group identity for [name] (compact form of its canonical name) within [canonical]. */
    fun groupKey(name: String, canonical: Map<String, String>): String =
        compact(canonical[name] ?: name)

    /**
     * Picks the entry of [candidates] (compact names) that is the same vendor as [name]:
     * an exact match first, otherwise the single prefix-related candidate. Null if none or
     * ambiguous.
     */
    fun bestMatch(name: String, candidates: Collection<String>): String? {
        val key = compact(name)
        if (key.isEmpty()) return null
        if (key in candidates) return key
        val related = candidates.filter { c ->
            isPrefixVariant(key, c) || isPrefixVariant(c, key)
        }
        return related.singleOrNull()
    }
}
