package com.example.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Password-based authenticated encryption (PBKDF2WithHmacSHA256 + AES-256-GCM)
 * for developer license registry backups (`.slbak`) and private key vault backups (`.slkey`).
 */
@OptIn(ExperimentalEncodingApi::class)
object BackupCrypto {

    const val BACKUP_FORMAT_VERSION = 1
    const val TYPE_REGISTRY = "SMARTLEDGER_REGISTRY_BACKUP"
    const val TYPE_PRIVATE_KEY = "SMARTLEDGER_KEYPAIR_BACKUP"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    private val secureRandom = SecureRandom()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Serializable
    data class EncryptedEnvelope(
        val format: String = "SLBAK1",
        val version: Int = BACKUP_FORMAT_VERSION,
        val type: String,
        val kdf: String = "PBKDF2WithHmacSHA256",
        val iterations: Int = PBKDF2_ITERATIONS,
        val saltB64: String,
        val ivB64: String,
        val ciphertextB64: String,
        val createdAt: Long
    )

    @Serializable
    data class KeyPairBackupPayload(
        val privateKeyPkcs8B64: String,
        val publicKeyPem: String,
        val fingerprintSha256: String,
        val createdAt: Long
    )

    fun isPasswordStrong(password: String): Boolean {
        return password.length >= 8
    }

    fun encrypt(
        plaintext: String,
        password: String,
        backupType: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        require(password.length >= 8) { "كلمة المرور يجب أن لا تقل عن 8 أحرف" }
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
        val key = deriveAesKey(password, salt, PBKDF2_ITERATIONS)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(backupType.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

        val envelope = EncryptedEnvelope(
            type = backupType,
            saltB64 = Base64.Default.encode(salt),
            ivB64 = Base64.Default.encode(iv),
            ciphertextB64 = Base64.Default.encode(ciphertext),
            createdAt = nowMillis
        )
        return json.encodeToString(envelope)
    }

    fun decrypt(
        envelopeJson: String,
        password: String,
        expectedType: String
    ): String {
        val envelope = json.decodeFromString<EncryptedEnvelope>(envelopeJson)
        require(envelope.format == "SLBAK1") { "صيغة النسخة الاحتياطية غير مدعومة" }
        require(envelope.type == expectedType) { "نوع النسخة الاحتياطية غير مطابق ($expectedType)" }
        require(envelope.iterations in 10_000..1_000_000) { "معاملات التشفير غير صالحة" }

        val salt = Base64.Default.decode(envelope.saltB64)
        val iv = Base64.Default.decode(envelope.ivB64)
        val ciphertext = Base64.Default.decode(envelope.ciphertextB64)
        val key = deriveAesKey(password, salt, envelope.iterations)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(envelope.type.toByteArray(StandardCharsets.UTF_8))
        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, StandardCharsets.UTF_8)
    }

    private fun deriveAesKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(derived, "AES")
    }
}
