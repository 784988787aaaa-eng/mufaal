package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IssuedLicenseDao {

    @Query("SELECT * FROM issued_licenses ORDER BY issuedAt DESC")
    fun observeAll(): Flow<List<IssuedLicenseEntity>>

    @Query(
        """
        SELECT * FROM issued_licenses
        WHERE customerName LIKE '%' || :query || '%'
           OR customerPhone LIKE '%' || :query || '%'
           OR deviceCode LIKE '%' || :query || '%'
           OR licenseId LIKE '%' || :query || '%'
           OR notes LIKE '%' || :query || '%'
        ORDER BY issuedAt DESC
        """
    )
    fun search(query: String): Flow<List<IssuedLicenseEntity>>

    @Query("SELECT * FROM issued_licenses WHERE deviceCode = :deviceCode ORDER BY issuedAt DESC")
    fun observeByDeviceCode(deviceCode: String): Flow<List<IssuedLicenseEntity>>

    @Query("SELECT * FROM issued_licenses WHERE deviceCode = :deviceCode ORDER BY issuedAt DESC")
    suspend fun findByDeviceCode(deviceCode: String): List<IssuedLicenseEntity>

    @Query("SELECT * FROM issued_licenses WHERE licenseId = :licenseId LIMIT 1")
    suspend fun getById(licenseId: String): IssuedLicenseEntity?

    @Query("SELECT * FROM issued_licenses ORDER BY issuedAt DESC")
    suspend fun getAllOnce(): List<IssuedLicenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: IssuedLicenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<IssuedLicenseEntity>)

    @Query("DELETE FROM issued_licenses WHERE licenseId = :licenseId")
    suspend fun deleteById(licenseId: String)
}
