package com.example.domain

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val errorMessage: String) : ValidationResult()

    val isValid: Boolean get() = this is Valid
    val errorOrNull: String? get() = (this as? Invalid)?.errorMessage
}

object FormValidation {

    fun validateAmount(rawInput: String): ValidationResult {
        val trimmed = rawInput.trim().replace("$", "").replace(",", "")
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Please enter an amount")
        }

        val amount = trimmed.toDoubleOrNull()
            ?: return ValidationResult.Invalid("Please enter a valid numeric amount")

        if (amount <= 0.0) {
            return ValidationResult.Invalid("Amount must be greater than $0.00")
        }

        if (amount > 10_000_000.0) {
            return ValidationResult.Invalid("Amount cannot exceed $10,000,000.00")
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
        val trimmed = rawInput.trim().replace("$", "").replace(",", "")
        return trimmed.toDoubleOrNull()
    }
}
