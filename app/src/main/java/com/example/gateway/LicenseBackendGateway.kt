package com.example.gateway

import kotlinx.serialization.Serializable

/**
 * Decoupled backend gateway abstraction for future `ACCOUNT` cloud license management
 * (e.g., Cloudflare Worker API) without touching the offline local ECDSA signing engine.
 */
sealed interface GatewayCapability {
    data object DisabledOfflineOnly : GatewayCapability
    data class CloudWorkerConfigured(val workerBaseUrl: String) : GatewayCapability
}

@Serializable
data class CloudLicenseSyncRequest(
    val licenseId: String,
    val deviceCode: String,
    val plan: String,
    val issuedAt: Long,
    val expiresAt: Long?,
    val accountEmail: String? = null
)

@Serializable
data class CloudLicenseSyncResponse(
    val remoteRecordId: String,
    val syncedAt: Long
)

@Serializable
data class CloudAccountStatus(
    val accountId: String,
    val activeSeats: Int,
    val maxSeats: Int,
    val status: String
)

interface LicenseBackendGateway {
    val capability: GatewayCapability

    suspend fun syncIssuedLicense(
        request: CloudLicenseSyncRequest
    ): Result<CloudLicenseSyncResponse>

    suspend fun checkAccountLicenseStatus(
        accountId: String
    ): Result<CloudAccountStatus>
}

/**
 * Default offline-only implementation. Zero network calls, zero internet permission.
 */
class DisabledLicenseBackendGateway : LicenseBackendGateway {
    override val capability: GatewayCapability = GatewayCapability.DisabledOfflineOnly

    override suspend fun syncIssuedLicense(
        request: CloudLicenseSyncRequest
    ): Result<CloudLicenseSyncResponse> {
        return Result.failure(
            UnsupportedOperationException(
                "البوابة السحابية معطلة افتراضياً — هذا التطبيق يعمل أوفلاين 100%."
            )
        )
    }

    override suspend fun checkAccountLicenseStatus(
        accountId: String
    ): Result<CloudAccountStatus> {
        return Result.failure(
            UnsupportedOperationException(
                "البوابة السحابية معطلة افتراضياً — هذا التطبيق يعمل أوفلاين 100%."
            )
        )
    }
}
