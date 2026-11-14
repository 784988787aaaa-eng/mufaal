package com.example

import com.example.crypto.DeviceCodeValidator
import com.example.crypto.LicensePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCodeValidatorTest {

    @Test
    fun `parses SmartLedger LIFETIME WhatsApp request with invisible unicode chars`() {
        val valid = DeviceCodeValidator.buildValidCode("7F8A2B9C4D3")
        val msg = "\u200Fمرحباً، أطلب تفعيل محلي لتطبيق الدفتر الذكي.\n" +
            "\u2066نوع الطلب: ترخيص محلي دائم (مدى الحياة)\u2069\n" +
            "\uFEFFرمز الجهاز: \u202A${valid.lowercase()}\u202C\u200B"

        val parsed = DeviceCodeValidator.parseActivationRequest(msg)
        assertEquals(valid, parsed.deviceCode)
        assertEquals(LicensePlan.LIFETIME, parsed.detectedPlan)
        assertTrue(DeviceCodeValidator.validate(parsed.deviceCode!!).isFullyValid)
    }

    @Test
    fun `parses SmartLedger TRIAL WhatsApp request and auto-detects TRIAL plan`() {
        val valid = DeviceCodeValidator.buildValidCode("K9P4M2X7Q8Z")
        val msg = """
            مرحباً، أطلب تفعيل محلي لتطبيق الدفتر الذكي.
            نوع الطلب: ترخيص محلي مؤقت (تجريبي)
            رمز الجهاز: $valid
        """.trimIndent()

        val parsed = DeviceCodeValidator.parseActivationRequest(msg)
        assertEquals(valid, parsed.deviceCode)
        assertEquals(LicensePlan.TRIAL, parsed.detectedPlan)
        assertTrue(DeviceCodeValidator.validate(parsed.deviceCode!!).isFullyValid)
    }

    @Test
    fun `rejects ambiguous characters 0 O 1 I from 32-char alphabet`() {
        assertFalse(DeviceCodeValidator.validate("SLD-0O1I-ABCD-EFGH").isFormatValid)
        assertFalse(DeviceCodeValidator.validate("SLD-ABCD-EFGH-JKL0").isFormatValid)
        assertFalse(DeviceCodeValidator.validate("SLD-ABCD-EFGH-JKLO").isFormatValid)
        assertFalse(DeviceCodeValidator.validate("SLD-ABCD-EFGH-JKL1").isFormatValid)
        assertFalse(DeviceCodeValidator.validate("SLD-ABCD-EFGH-JKLI").isFormatValid)
    }

    @Test
    fun `detects Luhn mod-32 check character typo and transposition errors`() {
        val valid = DeviceCodeValidator.buildValidCode("K9P4M2X7Q8Z")
        assertTrue(DeviceCodeValidator.validate(valid).isFullyValid)

        val lastChar = valid.last()
        val wrongChar = if (lastChar == 'A') 'B' else 'A'
        val mutatedCheck = valid.dropLast(1) + wrongChar
        val res1 = DeviceCodeValidator.validate(mutatedCheck)
        assertTrue(res1.isFormatValid)
        assertFalse(res1.isCheckDigitValid)
        assertEquals(lastChar, res1.expectedCheckDigit)
        assertEquals(wrongChar, res1.actualCheckDigit)

        val bodyChar = valid[4]
        val replacedBodyChar = if (bodyChar == 'Z') 'Y' else 'Z'
        val mutatedBody = valid.substring(0, 4) + replacedBodyChar + valid.substring(5)
        val res2 = DeviceCodeValidator.validate(mutatedBody)
        assertTrue(res2.isFormatValid)
        assertFalse(res2.isCheckDigitValid)
    }

    @Test
    fun `returns null when message contains no SLD device code`() {
        val text = "مرحباً أريد الاشتراك في الدفتر الذكي، كيف يتم الدفع؟"
        val parsed = DeviceCodeValidator.parseActivationRequest(text)
        assertNull(parsed.deviceCode)
        assertNull(parsed.detectedPlan)
        assertFalse(DeviceCodeValidator.validate("ABC-234").isFormatValid)
        assertNotNull(DeviceCodeValidator.buildValidCode("23456789ABC"))
    }
}
