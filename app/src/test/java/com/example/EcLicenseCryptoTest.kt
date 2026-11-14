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
import java.security.Signature
import java.util.Base64

class EcLicenseCryptoTest {

    @Test
    fun `SmartLedger bundled public key decodes cleanly as EC P-256`() {
        val pub = EcLicenseCrypto.decodePublicKeyPem(EcLicenseCrypto.SMARTLEDGER_DEFAULT_PUBLIC_KEY_PEM)
        assertEquals("EC", pub.algorithm)
        assertEquals(64, EcLicenseCrypto.smartLedgerDefaultFingerprintSha256().split(':').size * 2)
        assertTrue(EcLicenseCrypto.matchesSmartLedgerDefaultKey(EcLicenseCrypto.SMARTLEDGER_DEFAULT_PUBLIC_KEY_PEM))
    }

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
        assertFalse(EcLicenseCrypto.matchesSmartLedgerDefaultKey(pem))
    }

    @Test
    fun `sign and verify LIFETIME SLK1 token uses v1 schema, SLK1 signing input, and 64-byte P1363 signature`() {
        val pair = EcLicenseCrypto.generateEcP256KeyPair()
        val deviceCode = DeviceCodeValidator.buildValidCode("9A7B3C2D4E5")
        val issuedAt = System.currentTimeMillis() - 1_000L

        val payload = EcLicenseCrypto.buildPayload(
            deviceCode = deviceCode,
            plan = LicensePlan.LIFETIME,
            issuedAtMillis = issuedAt,
            licenseId = "LIC-20260001"
        )
        val result = EcLicenseCrypto.issueAndSign(payload, pair.private, pair.public)

        assertTrue(result.selfVerified)
        assertTrue(result.token.startsWith("SLK1."))
        val parts = result.token.split('.')
        assertEquals(3, parts.size)
        assertFalse(result.token.contains("="))

        // Verify signature is exact 64-byte IEEE P1363 (r || s)
        val rawSigBytes = Base64.getUrlDecoder().decode(parts[2])
        assertEquals(64, rawSigBytes.size)

        // Verify raw JCA signature against exact signingInput = "SLK1." + payloadB64
        val signingInput = "SLK1.${parts[1]}"
        val derFromP1363 = EcLicenseCrypto.p1363ToDer(rawSigBytes)
        val jcaVerifier = Signature.getInstance("SHA256withECDSA")
        jcaVerifier.initVerify(pair.public)
        jcaVerifier.update(signingInput.toByteArray(StandardCharsets.UTF_8))
        assertTrue(jcaVerifier.verify(derFromP1363))

        // Verify exact JSON schema fields
        assertTrue(result.payloadJson.contains("\"v\":1"))
        assertTrue(result.payloadJson.contains("\"kid\":\"LOCAL2\""))
        assertTrue(result.payloadJson.contains("\"product\":\"SMARTLEDGER\""))
        assertTrue(result.payloadJson.contains("\"type\":\"LOCAL\""))
        assertTrue(result.payloadJson.contains("\"licenseId\":\"LIC-20260001\""))
        assertTrue(result.payloadJson.contains("\"plan\":\"LIFETIME\""))
        assertTrue(result.payloadJson.contains("\"maxDevices\":1"))
        assertTrue(result.payloadJson.contains("\"active\":true"))
        assertFalse(result.payloadJson.contains("\"alg\""))
        assertFalse(result.payloadJson.contains("\"trialEndsAt\""))
        assertFalse(result.payloadJson.contains("\"revoked\""))

        val verified = EcLicenseCrypto.verifyToken(result.token, pair.public)
        assertNotNull(verified)
        assertEquals(deviceCode, verified!!.payload.deviceCode)
        assertEquals("LIFETIME", verified.payload.plan)
        assertNull(verified.payload.trialEndsAt)
    }

    @Test
    fun `trial plans compute exact future trialEndsAt for 7, 14, 30 days and custom date`() {
        val pair = EcLicenseCrypto.generateEcP256KeyPair()
        val deviceCode = DeviceCodeValidator.buildValidCode("ABCD2345EFG")
        val now = System.currentTimeMillis()

        listOf(7, 14, 30).forEach { days ->
            val payload = EcLicenseCrypto.buildPayload(
                deviceCode = deviceCode,
                plan = LicensePlan.TRIAL,
                issuedAtMillis = now,
                trialDays = days,
                nowMillis = now
            )
            val expectedEnd = now + days.toLong() * 86_400_000L
            assertEquals(expectedEnd, payload.trialEndsAt)
            val signed = EcLicenseCrypto.issueAndSign(payload, pair.private, pair.public)
            assertTrue(signed.payloadJson.contains("\"trialEndsAt\":$expectedEnd"))
            val verified = EcLicenseCrypto.verifyToken(signed.token, pair.public)
            assertNotNull(verified)
            assertEquals(expectedEnd, verified!!.payload.trialEndsAt)
        }

        val customEnd = now + 45L * 86_400_000L
        val customPayload = EcLicenseCrypto.buildPayload(
            deviceCode = deviceCode,
            plan = LicensePlan.TRIAL,
            issuedAtMillis = now,
            customTrialEndsAtMillis = customEnd,
            nowMillis = now
        )
        assertEquals(customEnd, customPayload.trialEndsAt)
    }

    @Test
    fun `tampering a single byte in payload or signature or signing without SLK1 prefix is rejected`() {
        val pair = EcLicenseCrypto.generateEcP256KeyPair()
        val otherPair = EcLicenseCrypto.generateEcP256KeyPair()
        val deviceCode = DeviceCodeValidator.buildValidCode("23456789ABC")
        val now = System.currentTimeMillis()

        val payload = EcLicenseCrypto.buildPayload(
            deviceCode = deviceCode,
            plan = LicensePlan.LIFETIME,
            issuedAtMillis = now
        )
        val signed = EcLicenseCrypto.issueAndSign(payload, pair.private, pair.public)
        val parts = signed.token.split('.')

        // 1) Tamper 1 byte in payload
        val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
        val tamperedPayloadBytes = payloadBytes.copyOf()
        tamperedPayloadBytes[tamperedPayloadBytes.size / 2] =
            (tamperedPayloadBytes[tamperedPayloadBytes.size / 2].toInt() xor 0x01).toByte()
        val tamperedPayloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayloadBytes)
        assertNull(EcLicenseCrypto.verifyToken("${parts[0]}.$tamperedPayloadB64.${parts[2]}", pair.public))

        // 2) Tamper 1 byte in 64-byte P1363 signature
        val sigBytes = Base64.getUrlDecoder().decode(parts[2])
        val tamperedSigBytes = sigBytes.copyOf()
        tamperedSigBytes[tamperedSigBytes.lastIndex] =
            (tamperedSigBytes[tamperedSigBytes.lastIndex].toInt() xor 0x01).toByte()
        val tamperedSigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedSigBytes)
        assertNull(EcLicenseCrypto.verifyToken("${parts[0]}.${parts[1]}.$tamperedSigB64", pair.public))

        // 3) Signing raw JSON bytes without "SLK1." prefix must fail verifyToken
        val rawDerSig = EcLicenseCrypto.signBytes(signed.payloadJson.toByteArray(StandardCharsets.UTF_8), pair.private)
        val rawP1363B64 = Base64.getUrlEncoder().withoutPadding().encodeToString(EcLicenseCrypto.derToP1363(rawDerSig))
        assertNull(EcLicenseCrypto.verifyToken("SLK1.${parts[1]}.$rawP1363B64", pair.public))

        // 4) Wrong public key fails
        assertNotEquals(pair.public, otherPair.public)
        assertNull(EcLicenseCrypto.verifyToken(signed.token, otherPair.public))
    }

    @Test
    fun `qr matrix encodes full SLK1 token offline`() {
        val pair = EcLicenseCrypto.generateEcP256KeyPair()
        val deviceCode = DeviceCodeValidator.buildValidCode("QWERTY23456")
        val payload = EcLicenseCrypto.buildPayload(deviceCode, LicensePlan.LIFETIME)
        val signed = EcLicenseCrypto.issueAndSign(payload, pair.private, pair.public)

        val qr = QrMatrixGenerator.generate(signed.token)
        assertTrue(qr.size >= 29)
        assertTrue(qr.modules.any { it })
        assertTrue(qr.modules.any { !it })
    }

    @Test
    fun `key ID validation enforces LOCAL pattern and strictly rejects LOCAL1`() {
        assertTrue(EcLicenseCrypto.isValidKeyId("LOCAL2"))
        assertTrue(EcLicenseCrypto.isValidKeyId("LOCAL3"))
        assertTrue(EcLicenseCrypto.isValidKeyId("LOCAL_2026-A"))
        assertTrue(EcLicenseCrypto.isValidKeyId("local2"))

        assertFalse(EcLicenseCrypto.isValidKeyId("LOCAL1"))
        assertFalse(EcLicenseCrypto.isValidKeyId("local1"))
        assertFalse(EcLicenseCrypto.isValidKeyId("LOCAL"))
        assertFalse(EcLicenseCrypto.isValidKeyId("KEY2"))
        assertFalse(EcLicenseCrypto.isValidKeyId("LOCAL@BAD"))
        assertFalse(EcLicenseCrypto.isValidKeyId(""))

        assertEquals("LOCAL2", EcLicenseCrypto.suggestNextKeyId(emptyList()))
        assertEquals("LOCAL3", EcLicenseCrypto.suggestNextKeyId(listOf("LOCAL2")))
        assertEquals("LOCAL5", EcLicenseCrypto.suggestNextKeyId(listOf("LOCAL2", "LOCAL4")))
    }

    @Test
    fun `formatKeyringPem includes kid headers and rejects LOCAL1 in buildPayload`() {
        val pair2 = EcLicenseCrypto.generateEcP256KeyPair()
        val pair3 = EcLicenseCrypto.generateEcP256KeyPair()
        val keyring = EcLicenseCrypto.formatKeyringPem(
            listOf(
                "LOCAL2" to EcLicenseCrypto.encodePublicKeyPem(pair2.public),
                "LOCAL3" to EcLicenseCrypto.encodePublicKeyPem(pair3.public)
            )
        )
        assertTrue(keyring.contains("# kid: LOCAL2\n-----BEGIN PUBLIC KEY-----"))
        assertTrue(keyring.contains("# kid: LOCAL3\n-----BEGIN PUBLIC KEY-----"))
        assertFalse(keyring.contains("PRIVATE KEY"))

        val deviceCode = DeviceCodeValidator.buildValidCode("ZXCVBNM2345")
        val payload3 = EcLicenseCrypto.buildPayload(deviceCode, LicensePlan.LIFETIME, kid = "LOCAL3")
        assertEquals("LOCAL3", payload3.kid)

        var rejectedLegacy = false
        try {
            EcLicenseCrypto.buildPayload(deviceCode, LicensePlan.LIFETIME, kid = "LOCAL1")
        } catch (_: IllegalArgumentException) {
            rejectedLegacy = true
        }
        assertTrue(rejectedLegacy)
    }
}
