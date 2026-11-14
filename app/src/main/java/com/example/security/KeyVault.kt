package com.example.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.crypto.BackupCrypto
import com.example.crypto.EcLicenseCrypto
import com.example.crypto.LicensePayload
import com.example.crypto.SignedLicenseResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
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

data class KeySummary(
    val kid: String,
    val publicKeyPem: String,
    val fingerprintSha256: String,
    val keySource: KeySource,
    val createdAt: Long,
    val isActive: Boolean,
    val isTrustedInKeyring: Boolean,
    val matchesSmartLedgerDefaultKey: Boolean
)

data class VaultMetadata(
    val hasKeyPair: Boolean,
    val activeKid: String? = null,
    val publicKeyPem: String? = null,
    val fingerprintSha256: String? = null,
    val matchesSmartLedgerDefaultKey: Boolean = false,
    val keySource: KeySource? = null,
    val keyCreatedAt: Long? = null,
    val isPinConfigured: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val keys: List<KeySummary> = emptyList(),
    val suggestedNextKid: String = EcLicenseCrypto.EXPECTED_KEY_ID
) {
    val trustedKeyCount: Int get() = keys.count { it.isTrustedInKeyring }
}

/**
 * Hardware-backed KeyVault managing:
 * 1) Developer PIN lock (salted PBKDF2-HMAC-SHA256) + Biometric preference
 * 2) Multi-key ECDSA P-256 (secp256r1) Keyring with Key ID (`LOCAL2`, `LOCAL3`, ...) & Key Rotation
 * 3) One-way signing (private keys never leave the vault in plain exports)
 * 4) X.509 PEM export (Active single PEM, Keyring `# kid: ...` PEM, or `<kid>.pem`)
 * 5) Password-encrypted keyring backup/restore (AES-256-GCM + PBKDF2 `.slkey`)
 */
class KeyVault(private val storage: VaultStorage) {

    @Serializable
    private data class StoredKeyEntry(
        val kid: String,
        val privateKeyPkcs8B64: String,
        val publicKeyPem: String,
        val keySource: String,
        val createdAt: Long,
        val isActive: Boolean,
        val isTrustedInKeyring: Boolean = true
    )

    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    fun getMetadata(): VaultMetadata {
        val entries = loadKeyring()
        val summaries = entries.mapNotNull { entry ->
            val fingerprint = runCatching {
                val pub = EcLicenseCrypto.decodePublicKeyPem(entry.publicKeyPem)
                EcLicenseCrypto.publicKeyFingerprintSha256(pub)
            }.getOrNull() ?: return@mapNotNull null

            val source = KeySource.entries.firstOrNull { it.name == entry.keySource } ?: KeySource.GENERATED
            KeySummary(
                kid = entry.kid,
                publicKeyPem = entry.publicKeyPem,
                fingerprintSha256 = fingerprint,
                keySource = source,
                createdAt = entry.createdAt,
                isActive = entry.isActive,
                isTrustedInKeyring = entry.isTrustedInKeyring,
                matchesSmartLedgerDefaultKey = EcLicenseCrypto.matchesSmartLedgerDefaultKey(entry.publicKeyPem)
            )
        }

        val active = summaries.firstOrNull { it.isActive } ?: summaries.firstOrNull()
        val pinConfigured = !storage.getString(KEY_PIN_HASH).isNullOrBlank()
        val biometricEnabled = storage.getBoolean(KEY_BIOMETRIC_ENABLED, false)

        return VaultMetadata(
            hasKeyPair = active != null,
            activeKid = active?.kid,
            publicKeyPem = active?.publicKeyPem,
            fingerprintSha256 = active?.fingerprintSha256,
            matchesSmartLedgerDefaultKey = active?.matchesSmartLedgerDefaultKey == true,
            keySource = active?.keySource,
            keyCreatedAt = active?.createdAt,
            isPinConfigured = pinConfigured,
            isBiometricEnabled = biometricEnabled,
            keys = summaries,
            suggestedNextKid = EcLicenseCrypto.suggestNextKeyId(summaries.map { it.kid })
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

    fun generateNewKeyPair(
        kid: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): VaultMetadata {
        val existing = loadKeyring()
        val targetKid = EcLicenseCrypto.normalizeKeyId(
            kid ?: EcLicenseCrypto.suggestNextKeyId(existing.map { it.kid })
        )
        require(EcLicenseCrypto.isValidKeyId(targetKid)) {
            "معرّف المفتاح ($targetKid) غير صالح! يجب أن يطابق ^LOCAL[A-Z0-9_-]{1,24}$ ويُمنع استخدام LOCAL1."
        }
        require(existing.none { it.kid == targetKid }) {
            "معرّف المفتاح $targetKid موجود مسبقاً في السجل. اختر معرّفاً جديداً للتدوير (مثل ${EcLicenseCrypto.suggestNextKeyId(existing.map { it.kid })})."
        }

        val pair = EcLicenseCrypto.generateEcP256KeyPair()
        val privB64 = EcLicenseCrypto.encodePrivateKeyPkcs8Base64(pair.private)
        val pubPem = EcLicenseCrypto.encodePublicKeyPem(pair.public)

        val updated = existing.map { it.copy(isActive = false) } + StoredKeyEntry(
            kid = targetKid,
            privateKeyPkcs8B64 = privB64,
            publicKeyPem = pubPem,
            keySource = KeySource.GENERATED.name,
            createdAt = nowMillis,
            isActive = true,
            isTrustedInKeyring = true
        )
        saveKeyring(updated)
        return getMetadata()
    }

    fun importKeyPairPem(
        privateKeyPemOrB64: String,
        publicKeyPem: String,
        allowOverwrite: Boolean,
        kid: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): VaultMetadata {
        val existing = loadKeyring()
        val targetKid = EcLicenseCrypto.normalizeKeyId(
            kid ?: if (existing.isEmpty()) EcLicenseCrypto.EXPECTED_KEY_ID else EcLicenseCrypto.suggestNextKeyId(existing.map { it.kid })
        )
        require(EcLicenseCrypto.isValidKeyId(targetKid)) {
            "معرّف المفتاح ($targetKid) غير صالح! يجب أن يطابق ^LOCAL[A-Z0-9_-]{1,24}$ ويُمنع استخدام LOCAL1."
        }
        val duplicateIndex = existing.indexOfFirst { it.kid == targetKid }
        require(duplicateIndex < 0 || allowOverwrite) {
            "معرّف المفتاح $targetKid موجود بالفعل. فعّل خيار الاستبدال أو استخدم معرّفاً جديداً."
        }

        val privateKey = EcLicenseCrypto.decodePrivateKey(privateKeyPemOrB64)
        val publicKey = EcLicenseCrypto.decodePublicKeyPem(publicKeyPem)
        require(EcLicenseCrypto.verifyKeyPairMatch(privateKey, publicKey)) {
            "المفتاح الخاص لا يطابق المفتاح العام المرفق!"
        }

        val newEntry = StoredKeyEntry(
            kid = targetKid,
            privateKeyPkcs8B64 = EcLicenseCrypto.encodePrivateKeyPkcs8Base64(privateKey),
            publicKeyPem = EcLicenseCrypto.encodePublicKeyPem(publicKey),
            keySource = KeySource.IMPORTED.name,
            createdAt = nowMillis,
            isActive = true,
            isTrustedInKeyring = true
        )

        val updated = existing
            .filterNot { it.kid == targetKid }
            .map { it.copy(isActive = false) } + newEntry
        saveKeyring(updated)
        return getMetadata()
    }

    fun setActiveKey(kid: String): VaultMetadata {
        val normalizedKid = EcLicenseCrypto.normalizeKeyId(kid)
        val existing = loadKeyring()
        require(existing.any { it.kid == normalizedKid }) {
            "المفتاح $normalizedKid غير موجود في سجل المفاتيح"
        }
        val updated = existing.map { entry ->
            if (entry.kid == normalizedKid) {
                entry.copy(isActive = true, isTrustedInKeyring = true)
            } else {
                entry.copy(isActive = false)
            }
        }
        saveKeyring(updated)
        return getMetadata()
    }

    fun setKeyTrustedInKeyring(kid: String, trusted: Boolean): VaultMetadata {
        val normalizedKid = EcLicenseCrypto.normalizeKeyId(kid)
        val existing = loadKeyring()
        val target = existing.firstOrNull { it.kid == normalizedKid }
            ?: error("المفتاح $normalizedKid غير موجود في سجل المفاتيح")
        require(!target.isActive || trusted) {
            "لا يمكن إلغاء اعتماد المفتاح النشط حالياً ($normalizedKid). فعّل مفتاحاً آخر أولاً."
        }
        val updated = existing.map { entry ->
            if (entry.kid == normalizedKid) entry.copy(isTrustedInKeyring = trusted) else entry
        }
        saveKeyring(updated)
        return getMetadata()
    }

    fun exportPublicKeyPem(): String {
        val entries = loadKeyring()
        val active = entries.firstOrNull { it.isActive } ?: entries.firstOrNull()
        return active?.publicKeyPem
            ?: error("لا يوجد مفتاح عام مخزن بعد. يرجى توليد زوج المفاتيح أولاً.")
    }

    fun exportPublicKeyPemByKid(kid: String): String {
        val normalizedKid = EcLicenseCrypto.normalizeKeyId(kid)
        val entries = loadKeyring()
        return entries.firstOrNull { it.kid == normalizedKid }?.publicKeyPem
            ?: error("لم يتم العثور على المفتاح $normalizedKid")
    }

    fun exportKeyringPublicPem(): String {
        val trusted = loadKeyring().filter { it.isTrustedInKeyring || it.isActive }
        require(trusted.isNotEmpty()) {
            "لا توجد مفاتيح عامة معتمدة للتصدير"
        }
        return EcLicenseCrypto.formatKeyringPem(trusted.map { it.kid to it.publicKeyPem })
    }

    /**
     * Exports the full Keyring (private + public keys + metadata) ONLY inside a password-encrypted
     * AES-256-GCM + PBKDF2 envelope.
     */
    fun exportEncryptedKeyPairBackup(
        password: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val entries = loadKeyring()
        require(entries.isNotEmpty()) { "لا يوجد أي زوج مفاتيح لتصديره" }
        val active = entries.firstOrNull { it.isActive } ?: entries.first()
        val activePub = EcLicenseCrypto.decodePublicKeyPem(active.publicKeyPem)

        val backupItems = entries.map { entry ->
            val pub = EcLicenseCrypto.decodePublicKeyPem(entry.publicKeyPem)
            BackupCrypto.KeyringBackupItem(
                kid = entry.kid,
                privateKeyPkcs8B64 = entry.privateKeyPkcs8B64,
                publicKeyPem = entry.publicKeyPem,
                fingerprintSha256 = EcLicenseCrypto.publicKeyFingerprintSha256(pub),
                keySource = entry.keySource,
                createdAt = entry.createdAt,
                isActive = entry.isActive,
                isTrustedInKeyring = entry.isTrustedInKeyring
            )
        }

        val payload = BackupCrypto.KeyPairBackupPayload(
            privateKeyPkcs8B64 = active.privateKeyPkcs8B64,
            publicKeyPem = active.publicKeyPem,
            fingerprintSha256 = EcLicenseCrypto.publicKeyFingerprintSha256(activePub),
            createdAt = nowMillis,
            kid = active.kid,
            keys = backupItems
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
        val existing = loadKeyring()
        require(existing.isEmpty() || allowOverwrite) {
            "يوجد مفاتيح مخزنة بالفعل. فعّل خيار الاستبدال الصريح للمتابعة."
        }
        val decrypted = BackupCrypto.decrypt(
            envelopeJson = encryptedEnvelopeJson,
            password = password,
            expectedType = BackupCrypto.TYPE_PRIVATE_KEY
        )
        val payload = json.decodeFromString<BackupCrypto.KeyPairBackupPayload>(decrypted)
        if (payload.keys.isEmpty()) {
            return importKeyPairPem(
                privateKeyPemOrB64 = payload.privateKeyPkcs8B64,
                publicKeyPem = payload.publicKeyPem,
                allowOverwrite = allowOverwrite,
                kid = payload.kid,
                nowMillis = nowMillis
            )
        }

        val restoredEntries = payload.keys.map { item ->
            val normalizedKid = EcLicenseCrypto.normalizeKeyId(item.kid)
            require(EcLicenseCrypto.isValidKeyId(normalizedKid)) {
                "معرّف المفتاح في النسخة الاحتياطية غير صالح: $normalizedKid"
            }
            val priv = EcLicenseCrypto.decodePrivateKey(item.privateKeyPkcs8B64)
            val pub = EcLicenseCrypto.decodePublicKeyPem(item.publicKeyPem)
            require(EcLicenseCrypto.verifyKeyPairMatch(priv, pub)) {
                "المفتاح الخاص لا يطابق العام للمعرف $normalizedKid"
            }
            StoredKeyEntry(
                kid = normalizedKid,
                privateKeyPkcs8B64 = EcLicenseCrypto.encodePrivateKeyPkcs8Base64(priv),
                publicKeyPem = EcLicenseCrypto.encodePublicKeyPem(pub),
                keySource = item.keySource,
                createdAt = item.createdAt,
                isActive = item.isActive,
                isTrustedInKeyring = item.isTrustedInKeyring
            )
        }.let { list ->
            if (list.any { it.isActive }) list else list.mapIndexed { idx, e -> e.copy(isActive = idx == list.lastIndex) }
        }
        saveKeyring(restoredEntries)
        return getMetadata()
    }

    /**
     * Signs a canonical [LicensePayload] inside the vault using the key matching `payload.kid`
     * (or the active key) and immediately self-verifies it with the corresponding public key.
     */
    fun signLicense(payload: LicensePayload): SignedLicenseResult {
        val entries = loadKeyring()
        val matching = entries.firstOrNull { it.kid == payload.kid }
            ?: entries.firstOrNull { it.isActive }
            ?: error("لم يتم إعداد مفتاح التوقيع الخاص في الخزنة بعد")
        require(matching.kid == payload.kid) {
            "المفتاح المطلوب (${payload.kid}) لا يطابق المفتاح النشط (${matching.kid})"
        }
        val privateKey = EcLicenseCrypto.decodePrivateKey(matching.privateKeyPkcs8B64)
        val publicKey = EcLicenseCrypto.decodePublicKeyPem(matching.publicKeyPem)
        return EcLicenseCrypto.issueAndSign(payload, privateKey, publicKey)
    }

    /**
     * Verifies a token by matching its payload `kid` against the stored public key in the keyring.
     */
    fun verifyTokenWithVaultKey(token: String): Boolean {
        val entries = loadKeyring()
        if (entries.isEmpty()) return false
        val tokenKid = EcLicenseCrypto.extractPayloadUnsafe(token)?.kid
        val candidates = if (tokenKid != null) {
            val exact = entries.filter { it.kid == EcLicenseCrypto.normalizeKeyId(tokenKid) }
            if (exact.isNotEmpty()) exact else entries
        } else {
            entries
        }
        return candidates.any { entry ->
            runCatching {
                val publicKey = EcLicenseCrypto.decodePublicKeyPem(entry.publicKeyPem)
                EcLicenseCrypto.verifyToken(token, publicKey) != null
            }.getOrDefault(false)
        }
    }

    private fun loadKeyring(): List<StoredKeyEntry> {
        val rawJson = storage.getString(KEY_KEYRING_JSON)
        if (!rawJson.isNullOrBlank()) {
            val parsed = runCatching { json.decodeFromString<List<StoredKeyEntry>>(rawJson) }.getOrNull()
            if (parsed != null) return parsed
        }
        // Seamless migration from legacy single-key storage
        val privB64 = storage.getString(KEY_PRIVATE_PKCS8)
        val pubPem = storage.getString(KEY_PUBLIC_PEM)
        if (!privB64.isNullOrBlank() && !pubPem.isNullOrBlank()) {
            val migrated = listOf(
                StoredKeyEntry(
                    kid = EcLicenseCrypto.EXPECTED_KEY_ID,
                    privateKeyPkcs8B64 = privB64,
                    publicKeyPem = pubPem,
                    keySource = storage.getString(KEY_SOURCE) ?: KeySource.GENERATED.name,
                    createdAt = storage.getString(KEY_CREATED_AT)?.toLongOrNull() ?: System.currentTimeMillis(),
                    isActive = true,
                    isTrustedInKeyring = true
                )
            )
            saveKeyring(migrated)
            return migrated
        }
        return emptyList()
    }

    private fun saveKeyring(entries: List<StoredKeyEntry>) {
        storage.putString(KEY_KEYRING_JSON, json.encodeToString(entries))
        val active = entries.firstOrNull { it.isActive } ?: entries.firstOrNull()
        if (active != null) {
            storage.putString(KEY_PRIVATE_PKCS8, active.privateKeyPkcs8B64)
            storage.putString(KEY_PUBLIC_PEM, active.publicKeyPem)
            storage.putString(KEY_SOURCE, active.keySource)
            storage.putString(KEY_CREATED_AT, active.createdAt.toString())
        }
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
        private const val KEY_KEYRING_JSON = "ec_p256_keyring_json_v1"
        private const val KEY_PRIVATE_PKCS8 = "ec_p256_private_pkcs8_b64"
        private const val KEY_PUBLIC_PEM = "ec_p256_public_pem"
        private const val KEY_SOURCE = "ec_key_source"
        private const val KEY_CREATED_AT = "ec_key_created_at"
        private const val KEY_PIN_SALT = "vault_pin_salt_b64"
        private const val KEY_PIN_HASH = "vault_pin_hash_b64"
        private const val KEY_BIOMETRIC_ENABLED = "vault_biometric_enabled"
    }
}
