package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "issued_licenses",
    indices = [
        Index(value = ["deviceCode"]),
        Index(value = ["issuedAt"])
    ]
)
data class IssuedLicenseEntity(
    @PrimaryKey val licenseId: String,
    val customerName: String,
    val customerPhone: String,
    val deviceCode: String,
    val plan: String, // "LIFETIME" or "TRIAL"
    val issuedAt: Long,
    val expiresAt: Long?,
    val token: String,
    val notes: String,
    val upgradedFromLicenseId: String? = null
)
