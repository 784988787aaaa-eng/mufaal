package com.example.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.crypto.BackupCrypto
import com.example.crypto.EcLicenseCrypto
import com.example.crypto.LicensePayload
import com.example.crypto.SignedLicenseResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Abstract encrypted key-value storage so [KeyVault] works both with Android Keystore
 * ([EncryptedSharedPreferences]) in production and [InMemoryVaultStorage] in JVM unit tests.
 */
interface VaultStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}

class EncryptedPrefsVaultStorage(context: Context) : VaultStorage {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "smartledger_keyvault_encrypted_v1"
    }
}

class InMemoryVaultStorage : VaultStorage {
    private val strings = mutableMapOf<String, String>()
    private val booleans = mutableMapOf<String, Boolean>()

    override fun getString(key: String): String? = strings[key]
    override fun putString(key: String, value: String) { strings[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = booleans[key] ?: defaultValue
    override fun putBoolean(key: String, value: Boolean) { booleans[key] = value }
    override fun remove(key: String) {
        strings.remove(key)
        booleans.remove(key)
    }
}

enum class KeySource(val arabicLabel: String) {
    GENERATED("تم توليده داخل الخزنة"),
    IMPORTED("مستورد بشكل آمن")
}

data class VaultMetadata(
    val hasKeyPair: Boolean,
    val publicKeyPem: String?,
    val fingerprintSha256: String?,
    val keySource: KeySource?,
    val keyCreatedAt: Long?,
    val isPinConfigured: Boolean,
    val isBiometricEnabled: Boolean
)

/**
 * Hardware-backed KeyVault managing:
 * 1) Developer PIN lock (salted PBKDF2-HMAC-SHA256) + Biometric preference
 * 2) ECDSA P-256 (secp256r1) signing keypair stored encrypted via Android Keystore MasterKey
 * 3) One-way signing (private key never exposed to UI or plain backups)
 * 4) Password-encrypted private key export/import (AES-256-GCM + PBKDF2)
 */
class KeyVault(private val storage: VaultStorage) {

    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    fun getMetadata(): VaultMetadata {
        val pubPem = storage.getString(KEY_PUBLIC_PEM)
        val privB64 = storage.getString(KEY_PRIVATE_PKCS8)
        val hasPair = !pubPem.isNullOrBlank() && !privB64.isNullOrBlank()
        val fingerprint = if (hasPair) {
            runCatching {
                val pub = EcLicenseCrypto.decodePublicKeyPem(pubPem!!)
                EcLicenseCrypto.publicKeyFingerprintSha256(pub)
            }.getOrNull()
        } else null

        val source = storage.getString(KEY_SOURCE)?.let { raw ->
            KeySource.entries.firstOrNull { it.name == raw }
        }
        val createdAt = storage.getString(KEY_CREATED_AT)?.toLongOrNull()
        val pinConfigured = !storage.getString(KEY_PIN_HASH).isNullOrBlank()
        val biometricEnabled = storage.getBoolean(KEY_BIOMETRIC_ENABLED, false)

        return VaultMetadata(
            hasKeyPair = hasPair,
            publicKeyPem = if (hasPair) pubPem else null,
            fingerprintSha256 = fingerprint,
            keySource = source,
            keyCreatedAt = createdAt,
            isPinConfigured = pinConfigured,
            isBiometricEnabled = biometricEnabled
        )
    }

    fun isPinConfigured(): Boolean = !storage.getString(KEY_PIN_HASH).isNullOrBlank()

    @OptIn(ExperimentalEncodingApi::class)
    fun setPin(pin: String) {
        require(pin.length in 4..8 && pin.all { it.isDigit() }) {
            "رمز PIN يجب أن يتكون من 4 إلى 8 أرقام"
        }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val hash = hashPin(pin, salt)
        storage.putString(KEY_PIN_SALT, Base64.Default.encode(salt))
        storage.putString(KEY_PIN_HASH, Base64.Default.encode(hash))
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun verifyPin(pin: String): Boolean {
        val saltB64 = storage.getString(KEY_PIN_SALT) ?: return false
        val expectedHashB64 = storage.getString(KEY_PIN_HASH) ?: return false
        val salt = Base64.Default.decode(saltB64)
        val expectedHash = Base64.Default.decode(expectedHashB64)
        val actualHash = hashPin(pin, salt)
        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
    }

    fun isBiometricEnabled(): Boolean = storage.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun generateNewKeyPair(nowMillis: Long = System.currentTimeMillis()): VaultMetadata {
        val pair = EcLicenseCrypto.generateEcP256KeyPair()
        val privB64 = EcLicenseCrypto.encodePrivateKeyPkcs8Base64(pair.private)
        val pubPem = EcLicenseCrypto.encodePublicKeyPem(pair.public)
        storage.putString(KEY_PRIVATE_PKCS8, privB64)
        storage.putString(KEY_PUBLIC_PEM, pubPem)
        storage.putString(KEY_SOURCE, KeySource.GENERATED.name)
        storage.putString(KEY_CREATED_AT, nowMillis.toString())
        return getMetadata()
    }

    /**
     * Imports a matching ECDSA P-256 private + public keypair securely into the vault.
     * Validates that the keys are valid EC keys and match each other before persisting.
     */
    fun importKeyPairPem(
        privateKeyPemOrB64: String,
        publicKeyPem: String,
        allowOverwrite: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): VaultMetadata {
        val current = getMetadata()
        require(!current.hasKeyPair || allowOverwrite) {
            "يوجد زوج مفاتيح مخزن بالفعل. فعّل خيار الاستبدال الصريح للمتابعة."
        }
        val privateKey = EcLicenseCrypto.decodePrivateKey(privateKeyPemOrB64)
        val publicKey = EcLicenseCrypto.decodePublicKeyPem(publicKeyPem)
        require(EcLicenseCrypto.verifyKeyPairMatch(privateKey, publicKey)) {
            "المفتاح الخاص لا يطابق المفتاح العام المرفق!"
        }
        storage.putString(KEY_PRIVATE_PKCS8, EcLicenseCrypto.encodePrivateKeyPkcs8Base64(privateKey))
        storage.putString(KEY_PUBLIC_PEM, EcLicenseCrypto.encodePublicKeyPem(publicKey))
        storage.putString(KEY_SOURCE, KeySource.IMPORTED.name)
        storage.putString(KEY_CREATED_AT, nowMillis.toString())
        return getMetadata()
    }

    fun exportPublicKeyPem(): String {
        return storage.getString(KEY_PUBLIC_PEM)
            ?: error("لا يوجد مفتاح عام مخزن بعد. يرجى توليد زوج المفاتيح أولاً.")
    }

    /**
     * Exports the private key ONLY inside a password-encrypted AES-256-GCM + PBKDF2 envelope.
     */
    fun exportEncryptedKeyPairBackup(
        password: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val privB64 = storage.getString(KEY_PRIVATE_PKCS8)
            ?: error("لا يوجد مفتاح خاص لتصديره")
        val pubPem = storage.getString(KEY_PUBLIC_PEM)
            ?: error("لا يوجد مفتاح عام لتصديره")
        val pubKey = EcLicenseCrypto.decodePublicKeyPem(pubPem)
        val payload = BackupCrypto.KeyPairBackupPayload(
            privateKeyPkcs8B64 = privB64,
            publicKeyPem = pubPem,
            fingerprintSha256 = EcLicenseCrypto.publicKeyFingerprintSha256(pubKey),
            createdAt = nowMillis
        )
        val payloadJson = json.encodeToString(payload)
        return BackupCrypto.encrypt(
            plaintext = payloadJson,
            password = password,
            backupType = BackupCrypto.TYPE_PRIVATE_KEY,
            nowMillis = nowMillis
        )
    }

    fun importEncryptedKeyPairBackup(
        encryptedEnvelopeJson: String,
        password: String,
        allowOverwrite: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): VaultMetadata {
        val current = getMetadata()
        require(!current.hasKeyPair || allowOverwrite) {
            "يوجد زوج مفاتيح مخزن بالفعل. فعّل خيار الاستبدال الصريح للمتابعة."
        }
        val decrypted = BackupCrypto.decrypt(
            envelopeJson = encryptedEnvelopeJson,
            password = password,
            expectedType = BackupCrypto.TYPE_PRIVATE_KEY
        )
        val payload = json.decodeFromString<BackupCrypto.KeyPairBackupPayload>(decrypted)
        return importKeyPairPem(
            privateKeyPemOrB64 = payload.privateKeyPkcs8B64,
            publicKeyPem = payload.publicKeyPem,
            allowOverwrite = allowOverwrite,
            nowMillis = nowMillis
        )
    }

    /**
     * Signs a canonical [LicensePayload] inside the vault and immediately self-verifies it.
     */
    fun signLicense(payload: LicensePayload): SignedLicenseResult {
        val (privateKey, publicKey) = loadKeyPair()
        return EcLicenseCrypto.issueAndSign(payload, privateKey, publicKey)
    }

    fun verifyTokenWithVaultKey(token: String): Boolean {
        val pubPem = storage.getString(KEY_PUBLIC_PEM) ?: return false
        val publicKey = EcLicenseCrypto.decodePublicKeyPem(pubPem)
        return EcLicenseCrypto.verifyToken(token, publicKey) != null
    }

    private fun loadKeyPair(): Pair<PrivateKey, PublicKey> {
        val privB64 = storage.getString(KEY_PRIVATE_PKCS8)
            ?: error("لم يتم إعداد مفتاح التوقيع الخاص في الخزنة بعد")
        val pubPem = storage.getString(KEY_PUBLIC_PEM)
            ?: error("لم يتم إعداد المفتاح العام في الخزنة بعد")
        return EcLicenseCrypto.decodePrivateKey(privB64) to EcLicenseCrypto.decodePublicKeyPem(pubPem)
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PIN_PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val result = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return result
    }

    companion object {
        private const val PIN_PBKDF2_ITERATIONS = 100_000
        private const val KEY_PRIVATE_PKCS8 = "ec_p256_private_pkcs8_b64"
        private const val KEY_PUBLIC_PEM = "ec_p256_public_pem"
        private const val KEY_SOURCE = "ec_key_source"
        private const val KEY_CREATED_AT = "ec_key_created_at"
        private const val KEY_PIN_SALT = "vault_pin_salt_b64"
        private const val KEY_PIN_HASH = "vault_pin_hash_b64"
        private const val KEY_BIOMETRIC_ENABLED = "vault_biometric_enabled"
    }
}
