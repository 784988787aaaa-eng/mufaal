package com.example.crypto

import java.util.Locale

/**
 * Validator, extractor, and check-digit engine for SmartLedger device codes.
 *
 * Canonical format: `SLD-XXXX-XXXX-XXXC`
 * - Prefix: `SLD`
 * - Payload characters: 11 alphanumeric characters (`0-9`, `A-Z`)
 * - Check digit (`C`): 12th alphanumeric character computed via weighted Luhn-mod-36
 */
object DeviceCodeValidator {

    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val RADIX = ALPHABET.length // 36

    private val STRICT_REGEX = Regex("^SLD-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")
    private val EXTRACT_REGEX = Regex(
        pattern = """SLD[\s\-–—_]*([A-Za-z0-9٠-٩]{4})[\s\-–—_]*([A-Za-z0-9٠-٩]{4})[\s\-–—_]*([A-Za-z0-9٠-٩]{4})""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    data class ValidationResult(
        val normalizedCode: String,
        val isFormatValid: Boolean,
        val isCheckDigitValid: Boolean,
        val expectedCheckDigit: Char? = null,
        val actualCheckDigit: Char? = null
    ) {
        val isFullyValid: Boolean get() = isFormatValid && isCheckDigitValid
    }

    /**
     * Normalizes raw user input (trims RTL/LTR marks, converts Eastern Arabic digits,
     * normalizes dashes, uppercases).
     */
    fun normalizeInput(raw: String): String {
        val cleaned = raw
            .replace("\u200F", "")
            .replace("\u200E", "")
            .replace("\u202A", "")
            .replace("\u202B", "")
            .replace("\u202C", "")
            .replace("–", "-")
            .replace("—", "-")
            .replace("−", "-")
            .trim()
        val sb = StringBuilder(cleaned.length)
        for (ch in cleaned) {
            sb.append(
                when (ch) {
                    in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                    in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                    else -> ch
                }
            )
        }
        return sb.toString().uppercase(Locale.US)
    }

    /**
     * Extracts a `SLD-XXXX-XXXX-XXXC` device code from arbitrary text (such as a full WhatsApp message).
     * Returns null if no matching device code pattern is found.
     */
    fun extractFromText(rawMessage: String): String? {
        val normalized = normalizeInput(rawMessage)
        val match = EXTRACT_REGEX.find(normalized) ?: return null
        val (g1, g2, g3) = match.destructured
        return "SLD-$g1-$g2-$g3"
    }

    /**
     * Validates both canonical format and the trailing check digit.
     */
    fun validate(rawInput: String): ValidationResult {
        val normalized = normalizeInput(rawInput)
        if (!STRICT_REGEX.matches(normalized)) {
            return ValidationResult(
                normalizedCode = normalized,
                isFormatValid = false,
                isCheckDigitValid = false
            )
        }
        val compact = normalized.removePrefix("SLD-").replace("-", "") // 12 chars
        val body11 = compact.substring(0, 11)
        val actualCheck = compact[11]
        val expectedCheck = computeCheckDigit(body11)
        return ValidationResult(
            normalizedCode = normalized,
            isFormatValid = true,
            isCheckDigitValid = actualCheck == expectedCheck,
            expectedCheckDigit = expectedCheck,
            actualCheckDigit = actualCheck
        )
    }

    /**
     * Computes Luhn-mod-36 check digit over the 11 alphanumeric payload characters.
     */
    fun computeCheckDigit(body11: String): Char {
        require(body11.length == 11) { "Body must be exactly 11 characters, got ${body11.length}" }
        var factor = 2
        var sum = 0
        for (i in body11.length - 1 downTo 0) {
            val codePoint = ALPHABET.indexOf(body11[i])
            require(codePoint >= 0) { "Unsupported character '${body11[i]}' in device code" }
            var addend = factor * codePoint
            factor = if (factor == 2) 1 else 2
            addend = (addend / RADIX) + (addend % RADIX)
            sum += addend
        }
        val remainder = sum % RADIX
        val checkCodePoint = (RADIX - remainder) % RADIX
        return ALPHABET[checkCodePoint]
    }

    /**
     * Helper to build a valid formatted device code from 11 alphanumeric characters.
     */
    fun buildValidCode(body11: String): String {
        val upper = normalizeInput(body11).replace("-", "")
        require(upper.length == 11) { "Expected 11 alphanumeric characters" }
        val check = computeCheckDigit(upper)
        val full = upper + check
        return "SLD-${full.substring(0, 4)}-${full.substring(4, 8)}-${full.substring(8, 12)}"
    }
}
