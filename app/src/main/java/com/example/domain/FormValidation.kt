package com.example.domain

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val errorMessage: String) : ValidationResult()

    val isValid: Boolean get() = this is Valid
    val errorOrNull: String? get() = (this as? Invalid)?.errorMessage
}

object FormValidation {

    // Plain decimal numbers only. toDoubleOrNull() alone also accepts "NaN", "Infinity",
    // exponents like "1e5" and hex floats, which must not be saved as amounts.
    private val PLAIN_DECIMAL = Regex("""^(\d+(\.\d*)?|\.\d+)$""")

    private val CURRENCY_MARKERS = Regex("""(?i)₹|\$|\bINR\b|\bRs\.?""")

    // Accept what people naturally type or paste: "₹1,250", "Rs. 99", "INR 40", "$5".
    private fun normalize(rawInput: String): String =
        rawInput.replace(CURRENCY_MARKERS, "").replace(",", "").trim()

    fun validateAmount(rawInput: String): ValidationResult {
        val trimmed = normalize(rawInput)
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Please enter an amount")
        }

        val amount = trimmed.toDoubleOrNull()
        if (amount == null || amount.isNaN()) {
            return ValidationResult.Invalid("Please enter a valid numeric amount")
        }

        if (amount <= 0.0) {
            return ValidationResult.Invalid("Amount must be greater than ₹0")
        }

        if (!PLAIN_DECIMAL.matches(trimmed)) {
            return ValidationResult.Invalid("Please enter a valid numeric amount")
        }

        if (amount > 10_000_000.0) {
            return ValidationResult.Invalid("Amount cannot exceed ₹1,00,00,000")
        }

        // Check decimal places: at most 2 decimal digits allowed
        val decimalIndex = trimmed.indexOf('.')
        if (decimalIndex != -1 && trimmed.length - decimalIndex - 1 > 2) {
            return ValidationResult.Invalid("Amount cannot have more than 2 decimal places")
        }

        return ValidationResult.Valid
    }

    fun validateTitle(rawTitle: String): ValidationResult {
        val trimmed = rawTitle.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Description / title cannot be empty")
        }
        if (trimmed.length > 60) {
            return ValidationResult.Invalid("Description must be 60 characters or fewer")
        }
        return ValidationResult.Valid
    }

    fun validateCategory(category: String?): ValidationResult {
        if (category.isNullOrBlank()) {
            return ValidationResult.Invalid("Please select a category")
        }
        return ValidationResult.Valid
    }

    fun parseAmount(rawInput: String): Double? {
        val trimmed = normalize(rawInput)
        if (!PLAIN_DECIMAL.matches(trimmed)) return null
        return trimmed.toDoubleOrNull()
    }
}
