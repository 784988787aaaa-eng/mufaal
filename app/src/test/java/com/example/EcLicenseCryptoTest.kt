package com.example

import com.example.crypto.DeviceCodeValidator
import com.example.crypto.EcLicenseCrypto
import com.example.crypto.LicensePlan
import com.example.crypto.QrMatrixGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class EcLicenseCryptoTest {

    @Test
    fun `generate keypair PEM roundtrip preserves public key and SHA-256 fingerprint`() {
        val pair = EcLicenseCrypto.generateEcP256KeyPair()
        val pem = EcLicenseCrypto.encodePublicKeyPem(pair.public)

        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue(pem.trim().endsWith("-----END PUBLIC KEY-----"))

        val decodedPublic = EcLicenseCrypto.decodePublicKeyPem(pem)
        assertEquals(
            EcLicenseCrypto.publicKeyFingerprintSha256(pair.public),
            EcLicenseCrypto.publicKeyFingerprintSha256(decodedPublic)
        )
        assertTrue(EcLicenseCrypto.verifyKeyPairMatch(pair.private, decodedPublic))
    }

    @Test
    fun `sign and verify LIFETIME SLK1 token matches v2 contract`() {
        val pair = EcLicenseCrypto.generateEcP256KeyPair()
        val deviceCode = DeviceCodeValidator.buildValidCode("9A7B3C2D1E5")
        val issuedAt = 1_700_000_000_000L

        val payload = EcLicenseCrypto.buildPayload(
            deviceCode = deviceCode,
            plan = LicensePlan.LIFETIME,
            issuedAtMillis = issuedAt,
            licenseId = "SLIC-TEST-0001"
        )
        val result = EcLicenseCrypto.issueAndSign(payload, pair.private, pair.public)

        assertTrue(result.selfVerified)
        assertTrue(result.token.startsWith("SLK1."))
        assertEquals(3, result.token.split('.').size)
        assertFalse(result.token.contains("=")) // Base64UrlNoPadding

        // Exact JSON contract checks including explicit "trialEndsAt":null and omitted customerHint
        assertTrue(result.payloadJson.contains("\"v\":2"))
        assertTrue(result.payloadJson.contains("\"kid\":\"LOCAL2\""))
        assertTrue(result.payloadJson.contains("\"alg\":\"ES256\""))
        assertTrue(result.payloadJson.contains("\"product\":\"SMARTLEDGER\""))
        assertTrue(result.payloadJson.contains("\"type\":\"LOCAL\""))
        assertTrue(result.payloadJson.contains("\"plan\":\"LIFETIME\""))
        assertTrue(result.payloadJson.contains("\"trialEndsAt\":null"))
        assertFalse(result.payloadJson.contains("customerHint"))

        val verified = EcLicenseCrypto.verifyToken(result.token, pair.public)
        assertNotNull(verified)
        assertEquals(deviceCode, verified!!.payload.deviceCode)
        assertEquals("LIFETIME", verified.payload.plan)
        assertNull(verified.payload.trialEndsAt)
    }

    @Test
    fun `trial plans compute exact trialEndsAt for 7, 14, 30 days and custom date`() {
        val pair = EcLicenseCrypto.generateEcP256KeyPair()
        val deviceCode = DeviceCodeValidator.buildValidCode("ABCD1234EFG")
        val issuedAt = 1_700_000_000_000L

        listOf(7, 14, 30).forEach { days ->
            val payload = EcLicenseCrypto.buildPayload(
                deviceCode = deviceCode,
                plan = LicensePlan.TRIAL,
                issuedAtMillis = issuedAt,
                trialDays = days
            )
            val expectedEnd = issuedAt + days.toLong() * 24L * 60L * 60L * 1000L
            assertEquals(expectedEnd, payload.trialEndsAt)
            val signed = EcLicenseCrypto.issueAndSign(payload, pair.private, pair.public)
            val verified = EcLicenseCrypto.verifyToken(signed.token, pair.public)
            assertNotNull(verified)
            assertEquals(expectedEnd, verified!!.payload.trialEndsAt)
        }

        val customEnd = issuedAt + 45L * 24L * 3600L * 1000L
        val customPayload = EcLicenseCrypto.buildPayload(
            deviceCode = deviceCode,
            plan = LicensePlan.TRIAL,
            issuedAtMillis = issuedAt,
            customTrialEndsAtMillis = customEnd
        )
        assertEquals(customEnd, customPayload.trialEndsAt)
    }

    @Test
    fun `tampering a single byte in payload or signature is rejected`() {
        val pair = EcLicenseCrypto.generateEcP256KeyPair()
        val otherPair = EcLicenseCrypto.generateEcP256KeyPair()
        val deviceCode = DeviceCodeValidator.buildValidCode("123456789AB")

        val payload = EcLicenseCrypto.buildPayload(
            deviceCode = deviceCode,
            plan = LicensePlan.LIFETIME,
            issuedAtMillis = 1_700_000_000_000L
        )
        val signed = EcLicenseCrypto.issueAndSign(payload, pair.private, pair.public)
        val parts = signed.token.split('.')

        // 1) Tamper 1 byte in decoded payload
        val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
        val tamperedPayloadBytes = payloadBytes.copyOf()
        tamperedPayloadBytes[tamperedPayloadBytes.size / 2] =
            (tamperedPayloadBytes[tamperedPayloadBytes.size / 2].toInt() xor 0x01).toByte()
        val tamperedPayloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayloadBytes)
        val tamperedPayloadToken = "${parts[0]}.$tamperedPayloadB64.${parts[2]}"
        assertNull(EcLicenseCrypto.verifyToken(tamperedPayloadToken, pair.public))

        // 2) Tamper 1 byte in signature
        val sigBytes = Base64.getUrlDecoder().decode(parts[2])
        val tamperedSigBytes = sigBytes.copyOf()
        tamperedSigBytes[tamperedSigBytes.lastIndex] =
            (tamperedSigBytes[tamperedSigBytes.lastIndex].toInt() xor 0x01).toByte()
        val tamperedSigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedSigBytes)
        val tamperedSigToken = "${parts[0]}.${parts[1]}.$tamperedSigB64"
        assertNull(EcLicenseCrypto.verifyToken(tamperedSigToken, pair.public))

        // 3) Verify with wrong public key
        assertNotEquals(pair.public, otherPair.public)
        assertNull(EcLicenseCrypto.verifyToken(signed.token, otherPair.public))
    }

    @Test
    fun `qr matrix encodes full SLK1 token offline`() {
        val pair = EcLicenseCrypto.generateEcP256KeyPair()
        val deviceCode = DeviceCodeValidator.buildValidCode("QWERTY12345")
        val payload = EcLicenseCrypto.buildPayload(deviceCode, LicensePlan.LIFETIME)
        val signed = EcLicenseCrypto.issueAndSign(payload, pair.private, pair.public)

        val qr = QrMatrixGenerator.generate(signed.token)
        assertTrue(qr.size >= 29)
        assertTrue(qr.modules.any { it })
        assertTrue(qr.modules.any { !it })
    }
}
