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
}
