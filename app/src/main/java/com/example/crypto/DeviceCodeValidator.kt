package com.example.crypto

import java.util.Locale

/**
 * Validator, extractor, and Luhn mod-32 check-character engine for SmartLedger device codes.
 *
 * Canonical format: `SLD-XXXX-XXXX-XXXC`
 * - Prefix: `SLD`
 * - Alphabet (32 chars, excluding 0 / O / 1 / I): `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`
 * - Check character (`C`): 12th character computed via Luhn mod-32 over the first 11 characters.
 */
object DeviceCodeValidator {

    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val RADIX = 32

    private val INVISIBLE_CHARS_REGEX =
        Regex("[\u200B-\u200F\u202A-\u202E\u2060\u2066-\u2069\uFEFF]")
    private val MULTI_WHITESPACE_REGEX = Regex("\\s+")

    private val STRICT_REGEX =
        Regex("^SLD-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$")

    private val EXTRACT_REGEX = Regex(
        pattern = """SLD[\s\-]*([A-HJ-NP-Z2-9]{4})[\s\-]*([A-HJ-NP-Z2-9]{4})[\s\-]*([A-HJ-NP-Z2-9]{4})"""
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

    data class ParsedActivationRequest(
        val deviceCode: String?,
        val detectedPlan: LicensePlan?
    )

    /**
     * Strips invisible Unicode control characters, normalizes dashes, collapses extra spaces,
     * and converts ASCII letters to UPPERCASE.
     */
    fun cleanMessageText(raw: String): String {
        return raw
            .replace(INVISIBLE_CHARS_REGEX, "")
            .replace("–", "-")
            .replace("—", "-")
            .replace("−", "-")
            .replace(MULTI_WHITESPACE_REGEX, " ")
            .trim()
    }

    /**
     * Normalizes device code input (removes invisible Unicode characters, normalizes dashes,
     * trims spaces, uppercases).
     */
    fun normalizeInput(raw: String): String {
        return raw
            .replace(INVISIBLE_CHARS_REGEX, "")
            .replace("–", "-")
            .replace("—", "-")
            .replace("−", "-")
            .trim()
            .replace(MULTI_WHITESPACE_REGEX, "")
            .uppercase(Locale.US)
    }

    /**
     * Extracts a `SLD-XXXX-XXXX-XXXC` device code from arbitrary text (such as a full WhatsApp request).
     * Returns null if no matching device code pattern is found.
     */
    fun extractFromText(rawMessage: String): String? {
        val cleaned = cleanMessageText(rawMessage).uppercase(Locale.US)
        val match = EXTRACT_REGEX.find(cleaned) ?: return null
        val (g1, g2, g3) = match.destructured
        return "SLD-$g1-$g2-$g3"
    }

    /**
     * Detects the requested license plan from a SmartLedger WhatsApp activation request message.
     */
    fun detectPlanFromText(rawMessage: String): LicensePlan? {
        val cleaned = cleanMessageText(rawMessage)
        val upper = cleaned.uppercase(Locale.US)
        return when {
            cleaned.contains("مؤقت") || cleaned.contains("تجريبي") || upper.contains("TRIAL") ->
                LicensePlan.TRIAL
            cleaned.contains("دائم") || cleaned.contains("مدى الحياة") || upper.contains("LIFETIME") ->
                LicensePlan.LIFETIME
            else -> null
        }
    }

    /**
     * Parses both the device code and the requested plan from a pasted activation request.
     */
    fun parseActivationRequest(rawMessage: String): ParsedActivationRequest {
        return ParsedActivationRequest(
            deviceCode = extractFromText(rawMessage),
            detectedPlan = detectPlanFromText(rawMessage)
        )
    }

    /**
     * Validates both canonical format (`SLD-[A-HJ-NP-Z2-9]{4}-...`) and the Luhn mod-32 check character.
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
        val elevenChars = compact.substring(0, 11)
        val actualCheck = compact[11]
        val expectedCheck = computeCheckDigit(elevenChars)
        return ValidationResult(
            normalizedCode = normalized,
            isFormatValid = true,
            isCheckDigitValid = actualCheck == expectedCheck,
            expectedCheckDigit = expectedCheck,
            actualCheckDigit = actualCheck
        )
    }

    /**
     * Computes Luhn mod-32 check character over the first 11 characters (`indices 0..10`).
     */
    fun computeCheckDigit(elevenChars: String): Char {
        require(elevenChars.length == 11) {
            "Expected exactly 11 characters, got ${elevenChars.length}"
        }
        var factor = 2
        var sum = 0
        for (i in 10 downTo 0) {
            val codePoint = ALPHABET.indexOf(elevenChars[i])
            require(codePoint >= 0) {
                "Unsupported character '${elevenChars[i]}' in device code"
            }
            var addend = factor * codePoint
            factor = if (factor == 2) 1 else 2
            addend = (addend / RADIX) + (addend % RADIX)
            sum += addend
        }
        val checkCodePoint = (RADIX - (sum % RADIX)) % RADIX
        return ALPHABET[checkCodePoint]
    }

    /**
     * Helper to build a valid formatted `SLD-XXXX-XXXX-XXXC` device code from 11 valid alphabet chars.
     */
    fun buildValidCode(elevenChars: String): String {
        val upper = normalizeInput(elevenChars).replace("-", "")
        require(upper.length == 11) { "Expected 11 valid alphabet characters" }
        val check = computeCheckDigit(upper)
        val full = upper + check
        return "SLD-${full.substring(0, 4)}-${full.substring(4, 8)}-${full.substring(8, 12)}"
    }
}
