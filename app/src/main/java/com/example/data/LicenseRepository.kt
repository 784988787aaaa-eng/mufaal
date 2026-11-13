package com.example.data

import com.example.crypto.BackupCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RegistryBackupPayload(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val records: List<IssuedLicenseEntity>
)

class LicenseRepository(private val dao: IssuedLicenseDao) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    val allLicenses: Flow<List<IssuedLicenseEntity>> = dao.observeAll()

    fun searchLicenses(query: String): Flow<List<IssuedLicenseEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) dao.observeAll() else dao.search(trimmed)
    }

    fun observeByDeviceCode(deviceCode: String): Flow<List<IssuedLicenseEntity>> {
        return dao.observeByDeviceCode(deviceCode)
    }

    suspend fun findByDeviceCode(deviceCode: String): List<IssuedLicenseEntity> {
        return dao.findByDeviceCode(deviceCode)
    }

    suspend fun getById(licenseId: String): IssuedLicenseEntity? {
        return dao.getById(licenseId)
    }

    suspend fun saveIssuedLicense(entity: IssuedLicenseEntity) {
        dao.insert(entity)
    }

    suspend fun deleteLicense(licenseId: String) {
        dao.deleteById(licenseId)
    }

    /**
     * Exports the developer's issued-license archive (never includes any private key)
     * encrypted with AES-256-GCM + PBKDF2.
     */
    suspend fun exportEncryptedBackup(
        password: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val records = dao.getAllOnce()
        val payload = RegistryBackupPayload(
            exportedAt = nowMillis,
            records = records
        )
        val payloadJson = json.encodeToString(payload)
        return BackupCrypto.encrypt(
            plaintext = payloadJson,
            password = password,
            backupType = BackupCrypto.TYPE_REGISTRY,
            nowMillis = nowMillis
        )
    }

    /**
     * Decrypts and restores an encrypted developer archive into Room (`REPLACE` merge by `licenseId`).
     * Returns the count of restored records.
     */
    suspend fun restoreEncryptedBackup(
        encryptedEnvelopeJson: String,
        password: String
    ): Int {
        val decrypted = BackupCrypto.decrypt(
            envelopeJson = encryptedEnvelopeJson,
            password = password,
            expectedType = BackupCrypto.TYPE_REGISTRY
        )
        val payload = json.decodeFromString<RegistryBackupPayload>(decrypted)
        dao.insertAll(payload.records)
        return payload.records.size
    }
}
