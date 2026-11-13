package com.example.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    val v: Int = 2,
    val kid: String = "LOCAL2",
    val alg: String = "ES256",
    val product: String = "SMARTLEDGER",
    val type: String = "LOCAL",
    val plan: String,
    val licenseId: String,
    val deviceCode: String,
    val iat: Long,
    val trialEndsAt: Long? = null,
    val customerHint: String? = null
) {
    /**
     * Serializes strictly according to SmartLedger v2 local license JSON contract.
     * `trialEndsAt` is ALWAYS explicitly emitted (even when `null`).
     * `customerHint` is omitted unless explicitly provided.
     */
    fun toCanonicalJson(): String {
        val obj: JsonObject = buildJsonObject {
            put("v", v)
            put("kid", kid)
            put("alg", alg)
            put("product", product)
            put("type", type)
            put("plan", plan)
            put("licenseId", licenseId)
            put("deviceCode", deviceCode)
            put("iat", iat)
            if (trialEndsAt != null) {
                put("trialEndsAt", trialEndsAt)
            } else {
                put("trialEndsAt", JsonPrimitive(null as String?))
            }
            if (!customerHint.isNullOrBlank()) {
                put("customerHint", customerHint.trim())
            }
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
