package com.example.domain

/**
 * What each kind of text field accepts. These run on every keystroke and paste, so a field can
 * never hold characters it should not (letters in an amount, digits in a person's name), and
 * leading spaces never get in. Trailing spaces are allowed while typing (the next word needs
 * them) and are removed when the value is saved, with [clean].
 */
object InputGuard {

    const val MAX_AMOUNT_DIGITS = 8
    const val MAX_DECIMALS = 2
    const val MAX_PERSON = 40
    const val MAX_PAYEE = 60
    const val MAX_NAME = 40
    const val MAX_CATEGORY = 30
    const val MAX_NOTE = 200
    const val MAX_SEARCH = 60
    const val MAX_SMS = 2000

    /**
     * Digits with at most one decimal point and two decimals; nothing else. A pasted "₹1,250"
     * or "Rs. 99" becomes "1250" or "99". No leading zeros ("007" -> "7", ".5" -> "0.5").
     */
    fun amount(raw: String): String {
        val text = raw.replace(FormValidation.CURRENCY_MARKERS, "")
        val out = StringBuilder()
        var seenDot = false
        var decimals = 0
        for (c in text) {
            when {
                c == '.' && !seenDot -> {
                    if (out.isEmpty()) out.append('0')
                    out.append('.')
                    seenDot = true
                }
                c in '0'..'9' && seenDot -> if (decimals < MAX_DECIMALS) {
                    out.append(c)
                    decimals++
                }
                c in '0'..'9' -> when {
                    out.toString() == "0" -> if (c != '0') out.setLength(0).also { out.append(c) }
                    out.length < MAX_AMOUNT_DIGITS -> out.append(c)
                }
            }
        }
        return out.toString()
    }

    /** A person's name: letters (any language), spaces, and . ' - only. No digits. */
    fun personName(raw: String): String =
        shape(raw, MAX_PERSON) { c -> isLetterOrMark(c) || c == '.' || c == '\'' || c == '’' || c == '-' }

    /** A merchant, payee or item name: letters, digits, spaces and punctuation. No emoji or symbols. */
    fun label(raw: String, max: Int): String = shape(raw, max) { isLabelChar(it) }

    fun payee(raw: String): String = label(raw, MAX_PAYEE)

    /** Free text for notes: anything printable (emoji included), no control characters. */
    fun note(raw: String, multiline: Boolean = false, max: Int = MAX_NOTE): String {
        val kept = raw.filter { c ->
            !isInvisible(c) || (multiline && c == '\n')
        }.replace('\r', ' ')
        return capped(kept.trimStart(), max)
    }

    /** Search box: printable text only, capped. */
    fun search(raw: String): String = capped(raw.filter { !isInvisible(it) }.trimStart(), MAX_SEARCH)

    /** A pasted bank SMS: keeps line breaks, drops other control characters. */
    fun sms(raw: String): String =
        capped(raw.filter { !isInvisible(it) || it == '\n' }.replace("\r", ""), MAX_SMS)

    /** Value to save: no leading or trailing spaces, inner runs of spaces made single. */
    fun clean(text: String): String = text.trim().replace(WHITESPACE, " ")

    /** True when the text has at least one letter or number, so "---" is not a name. */
    fun hasContent(text: String): Boolean = text.any { it.isLetterOrDigit() }

    /** A person's name needs at least one letter. */
    fun hasLetter(text: String): Boolean = text.any { it.isLetter() }

    private val WHITESPACE = Regex("""\s+""")

    private fun shape(raw: String, max: Int, allowed: (Char) -> Boolean): String {
        val out = StringBuilder()
        for (c in raw) {
            val ch = if (c.isWhitespace()) ' ' else c
            if (ch == ' ') {
                // No leading space, and never two in a row.
                if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
            } else if (allowed(ch) && !isInvisible(ch)) {
                out.append(ch)
            }
        }
        return capped(out.toString(), max)
    }

    private fun isLetterOrMark(c: Char): Boolean = c.isLetter() ||
        Character.getType(c).let {
            it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
        }

    private fun isLabelChar(c: Char): Boolean = isLetterOrMark(c) || c.isDigit() || c == '+' ||
        when (Character.getType(c).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
            Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION,
            Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION -> true
            else -> false
        }

    /** Control and invisible formatting characters (zero-width joiners stay for Indic scripts). */
    private fun isInvisible(c: Char): Boolean =
        c.isISOControl() || (Character.getType(c) == Character.FORMAT.toInt() && c != '‌' && c != '‍')

    /** Cuts to [max] characters without splitting an emoji's surrogate pair. */
    private fun capped(text: String, max: Int): String {
        if (text.length <= max) return text
        val cut = if (text[max - 1].isHighSurrogate()) max - 1 else max
        return text.substring(0, cut)
    }
}
