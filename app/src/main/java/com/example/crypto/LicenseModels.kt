package com.example.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class LicensePlan(val wireName: String, val arabicLabel: String) {
    LIFETIME("LIFETIME", "مدى الحياة"),
    TRIAL("TRIAL", "فترة تجريبية");

    companion object {
        fun fromWireName(value: String): LicensePlan {
            return entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) } ?: LIFETIME
        }
    }
}

@Serializable
data class LicensePayload(
    val v: Int = 1,
    val kid: String = "LOCAL2",
    val product: String = "SMARTLEDGER",
    val type: String = "LOCAL",
    val licenseId: String,
    val deviceCode: String,
    val plan: String,
    val iat: Long,
    val trialEndsAt: Long? = null,
    val maxDevices: Int = 1,
    val active: Boolean = true,
    val revoked: Boolean? = null
) {
    /**
     * Serializes strictly to compact JSON matching SmartLedger's SLK1 local license schema.
     * - `trialEndsAt` is emitted ONLY when `plan == "TRIAL"` (omitted for `LIFETIME`).
     * - `revoked` is never emitted during issuance.
     */
    fun toCanonicalJson(): String {
        val obj: JsonObject = buildJsonObject {
            put("v", v)
            put("kid", kid)
            put("product", product)
            put("type", type)
            put("licenseId", licenseId)
            put("deviceCode", deviceCode)
            put("plan", plan)
            put("iat", iat)
            if (plan == LicensePlan.TRIAL.wireName && trialEndsAt != null) {
                put("trialEndsAt", trialEndsAt)
            }
            put("maxDevices", maxDevices)
            put("active", active)
        }
        return obj.toString()
    }
}

data class SignedLicenseResult(
    val token: String,
    val payload: LicensePayload,
    val payloadJson: String,
    val selfVerified: Boolean,
    val keyFingerprintSha256: String
)

data class VerifiedLicense(
    val payload: LicensePayload,
    val payloadJson: String
)
