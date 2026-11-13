package com.example

import com.example.crypto.DeviceCodeValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCodeValidatorTest {

    @Test
    fun `extracts device code from messy Arabic WhatsApp messages`() {
        val valid = DeviceCodeValidator.buildValidCode("7F8A2B9C4D1")
        val compact = valid.removePrefix("SLD-").replace("-", "")
        val g1 = compact.substring(0, 4)
        val g2 = compact.substring(4, 8)
        val g3 = compact.substring(8, 12)

        val whatsappMessage = """
            السلام عليكم أستاذ،
            أريد تفعيل تطبيق الدفتر الذكي مدى الحياة.
            \u200Fكود جهازي هو: sld–${g1.lowercase()} ${g2.lowercase()}—$g3
            اسمي أحمد محمد، شكراً لك!
        """.trimIndent()

        val extracted = DeviceCodeValidator.extractFromText(whatsappMessage)
        assertEquals(valid, extracted)

        val validation = DeviceCodeValidator.validate(extracted!!)
        assertTrue(validation.isFormatValid)
        assertTrue(validation.isCheckDigitValid)
        assertTrue(validation.isFullyValid)
    }

    @Test
    fun `normalizes Eastern Arabic numerals during extraction and validation`() {
        val valid = DeviceCodeValidator.buildValidCode("1234567890A")
        val easternMessage = "كود الجهاز: SLD-${valid.drop(4).replace('1', '١').replace('2', '٢').replace('3', '٣')}"

        val extracted = DeviceCodeValidator.extractFromText(easternMessage)
        assertNotNull(extracted)
        assertEquals(valid, extracted)
        assertTrue(DeviceCodeValidator.validate(extracted!!).isFullyValid)
    }

    @Test
    fun `detects check digit typo and transposition errors`() {
        val valid = DeviceCodeValidator.buildValidCode("K9P4M2X7Q8Z")
        assertTrue(DeviceCodeValidator.validate(valid).isFullyValid)

        // Mutate the check digit (last char)
        val lastChar = valid.last()
        val wrongChar = if (lastChar == 'A') 'B' else 'A'
        val mutatedCheck = valid.dropLast(1) + wrongChar
        val res1 = DeviceCodeValidator.validate(mutatedCheck)
        assertTrue(res1.isFormatValid)
        assertFalse(res1.isCheckDigitValid)
        assertEquals(lastChar, res1.expectedCheckDigit)
        assertEquals(wrongChar, res1.actualCheckDigit)

        // Mutate a character inside the body
        val bodyChar = valid[4]
        val replacedBodyChar = if (bodyChar == 'Z') 'Y' else 'Z'
        val mutatedBody = valid.substring(0, 4) + replacedBodyChar + valid.substring(5)
        val res2 = DeviceCodeValidator.validate(mutatedBody)
        assertTrue(res2.isFormatValid)
        assertFalse(res2.isCheckDigitValid)
    }

    @Test
    fun `returns null when WhatsApp message contains no SLD device code`() {
        val text = "مرحباً أريد الاشتراك في الدفتر الذكي، كيف يتم الدفع؟"
        assertNull(DeviceCodeValidator.extractFromText(text))
        assertFalse(DeviceCodeValidator.validate("ABC-123").isFormatValid)
    }
}
