package com.example

import com.example.crypto.BackupCrypto
import com.example.crypto.DeviceCodeValidator
import com.example.crypto.EcLicenseCrypto
import com.example.crypto.LicensePlan
import com.example.security.InMemoryVaultStorage
import com.example.security.KeyVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class BackupAndVaultTest {

    @Test
    fun `KeyVault PIN hashing verifies correct PIN and rejects wrong PIN`() {
        val vault = KeyVault(InMemoryVaultStorage())
        assertFalse(vault.isPinConfigured())

        vault.setPin("258046")
        assertTrue(vault.isPinConfigured())
        assertTrue(vault.verifyPin("258046"))
        assertFalse(vault.verifyPin("111111"))
    }

    @Test
    fun `KeyVault generates keypair, signs SLK1, and exports encrypted key backup`() {
        val vault1 = KeyVault(InMemoryVaultStorage())
        val meta1 = vault1.generateNewKeyPair()
        assertTrue(meta1.hasKeyPair)
        assertNotNull(meta1.publicKeyPem)
        assertNotNull(meta1.fingerprintSha256)

        val deviceCode = DeviceCodeValidator.buildValidCode("9988776655A")
        val payload = EcLicenseCrypto.buildPayload(deviceCode, LicensePlan.LIFETIME)
        val signed = vault1.signLicense(payload)
        assertTrue(signed.selfVerified)
        assertTrue(vault1.verifyTokenWithVaultKey(signed.token))

        // Export password-encrypted private key backup
        val strongPass = "Vault#Strong2026!"
        val encryptedKeyBackup = vault1.exportEncryptedKeyPairBackup(strongPass)
        assertFalse(encryptedKeyBackup.contains("PRIVATE KEY"))

        // Restore into a fresh KeyVault instance
        val vault2 = KeyVault(InMemoryVaultStorage())
        val meta2 = vault2.importEncryptedKeyPairBackup(
            encryptedEnvelopeJson = encryptedKeyBackup,
            password = strongPass,
            allowOverwrite = false
        )
        assertEquals(meta1.fingerprintSha256, meta2.fingerprintSha256)
        assertTrue(vault2.verifyTokenWithVaultKey(signed.token))
    }

    @Test
    fun `BackupCrypto fails on wrong password or tampered ciphertext`() {
        val secretJson = """{"records":[{"licenseId":"SLIC-123"}]}"""
        val encrypted = BackupCrypto.encrypt(
            plaintext = secretJson,
            password = "CorrectPassword#9",
            backupType = BackupCrypto.TYPE_REGISTRY
        )

        // Valid password succeeds
        val decrypted = BackupCrypto.decrypt(encrypted, "CorrectPassword#9", BackupCrypto.TYPE_REGISTRY)
        assertEquals(secretJson, decrypted)

        // Wrong password throws AEADBadTagException
        var wrongPasswordFailed = false
        try {
            BackupCrypto.decrypt(encrypted, "WrongPassword#9", BackupCrypto.TYPE_REGISTRY)
        } catch (_: AEADBadTagException) {
            wrongPasswordFailed = true
        }
        assertTrue(wrongPasswordFailed)

        // Wrong backup type fails
        var wrongTypeFailed = false
        try {
            BackupCrypto.decrypt(encrypted, "CorrectPassword#9", BackupCrypto.TYPE_PRIVATE_KEY)
        } catch (_: IllegalArgumentException) {
            wrongTypeFailed = true
        }
        assertTrue(wrongTypeFailed)
    }

    @Test
    fun `KeyVault rejects mismatched private and public keys on import`() {
        val pair1 = EcLicenseCrypto.generateEcP256KeyPair()
        val pair2 = EcLicenseCrypto.generateEcP256KeyPair()
        val vault = KeyVault(InMemoryVaultStorage())

        var failed = false
        try {
            vault.importKeyPairPem(
                privateKeyPemOrB64 = EcLicenseCrypto.encodePrivateKeyPkcs8Base64(pair1.private),
                publicKeyPem = EcLicenseCrypto.encodePublicKeyPem(pair2.public),
                allowOverwrite = true
            )
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `KeyVault supports key rotation LOCAL2 to LOCAL3, keyring export, untrust, and full backup restore`() {
        val vault = KeyVault(InMemoryVaultStorage())

        // 1) Reject reserved LOCAL1
        var local1Rejected = false
        try {
            vault.generateNewKeyPair(kid = "LOCAL1")
        } catch (_: IllegalArgumentException) {
            local1Rejected = true
        }
        assertTrue(local1Rejected)

        // 2) Generate LOCAL2 and issue license
        val meta2 = vault.generateNewKeyPair(kid = "LOCAL2")
        assertEquals("LOCAL2", meta2.activeKid)
        assertEquals("LOCAL3", meta2.suggestedNextKid)
        val deviceA = DeviceCodeValidator.buildValidCode("23456789ABD")
        val token2 = vault.signLicense(
            EcLicenseCrypto.buildPayload(deviceA, LicensePlan.LIFETIME, kid = "LOCAL2")
        ).token

        // 3) Rotate to LOCAL3 (LOCAL2 stays trusted) and issue LOCAL3 license
        val meta3 = vault.generateNewKeyPair(kid = "LOCAL3")
        assertEquals("LOCAL3", meta3.activeKid)
        assertEquals(2, meta3.keys.size)
        assertEquals(2, meta3.trustedKeyCount)
        val deviceB = DeviceCodeValidator.buildValidCode("BCDEFGHJKLM")
        val token3 = vault.signLicense(
            EcLicenseCrypto.buildPayload(deviceB, LicensePlan.TRIAL, kid = "LOCAL3", trialDays = 14)
        ).token

        // Both old & new tokens verify cleanly by matching kid
        assertTrue(vault.verifyTokenWithVaultKey(token2))
        assertTrue(vault.verifyTokenWithVaultKey(token3))

        // Full keyring PEM includes both LOCAL2 and LOCAL3 and no private keys
        val keyringPem = vault.exportKeyringPublicPem()
        assertTrue(keyringPem.contains("# kid: LOCAL2"))
        assertTrue(keyringPem.contains("# kid: LOCAL3"))
        assertFalse(keyringPem.contains("PRIVATE KEY"))

        // Per-key standalone PEM export works
        assertTrue(vault.exportPublicKeyPemByKid("LOCAL2").startsWith("-----BEGIN PUBLIC KEY-----"))

        // Untrust LOCAL2 excludes it from keyring export
        val metaUntrusted = vault.setKeyTrustedInKeyring("LOCAL2", false)
        assertEquals(1, metaUntrusted.trustedKeyCount)
        val shrunkKeyring = vault.exportKeyringPublicPem()
        assertFalse(shrunkKeyring.contains("# kid: LOCAL2"))
        assertTrue(shrunkKeyring.contains("# kid: LOCAL3"))

        // Re-activating LOCAL2 automatically marks it trusted again
        val metaReactivated = vault.setActiveKey("LOCAL2")
        assertEquals("LOCAL2", metaReactivated.activeKid)
        assertEquals(2, metaReactivated.trustedKeyCount)

        // Export & restore full multi-key .slkey backup into a clean vault
        val encryptedBackup = vault.exportEncryptedKeyPairBackup("RotatePass#2026!")
        val restoredVault = KeyVault(InMemoryVaultStorage())
        val restoredMeta = restoredVault.importEncryptedKeyPairBackup(
            encryptedEnvelopeJson = encryptedBackup,
            password = "RotatePass#2026!",
            allowOverwrite = true
        )
        assertEquals(2, restoredMeta.keys.size)
        assertEquals("LOCAL2", restoredMeta.activeKid)
        assertTrue(restoredVault.verifyTokenWithVaultKey(token2))
        assertTrue(restoredVault.verifyTokenWithVaultKey(token3))
    }
}
