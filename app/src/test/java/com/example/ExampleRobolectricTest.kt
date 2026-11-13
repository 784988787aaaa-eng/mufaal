package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.DeviceCodeValidator
import com.example.crypto.EcLicenseCrypto
import com.example.crypto.LicensePlan
import com.example.data.AppDatabase
import com.example.data.IssuedLicenseEntity
import com.example.data.LicenseRepository
import com.example.security.InMemoryVaultStorage
import com.example.security.KeyVault
import com.example.ui.SmartLedgerApp
import com.example.ui.viewmodel.LicenseStudioViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name matches Arabic title and INTERNET permission is absent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("مُفعّل الدفتر الذكي", appName)

        @Suppress("DEPRECATION")
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = pkgInfo.requestedPermissions?.toList() ?: emptyList()
        assertFalse(
            "App must have zero INTERNET permission",
            requested.contains(Manifest.permission.INTERNET)
        )
    }

    @Test
    fun `Room repository persists licenses, detects duplicates, and restores encrypted backup`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db1 = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo1 = LicenseRepository(db1.issuedLicenseDao())

        val deviceCode = DeviceCodeValidator.buildValidCode("TEST1234567")
        val pair = EcLicenseCrypto.generateEcP256KeyPair()
        val payload = EcLicenseCrypto.buildPayload(deviceCode, LicensePlan.TRIAL, trialDays = 14)
        val signed = EcLicenseCrypto.issueAndSign(payload, pair.private, pair.public)

        val record = IssuedLicenseEntity(
            licenseId = signed.payload.licenseId,
            customerName = "خالد العتيبي",
            customerPhone = "966500000001",
            deviceCode = deviceCode,
            plan = signed.payload.plan,
            issuedAt = signed.payload.iat,
            expiresAt = signed.payload.trialEndsAt,
            token = signed.token,
            notes = "تجريبي 14 يوم"
        )
        repo1.saveIssuedLicense(record)

        // Duplicate detection
        val matches = repo1.findByDeviceCode(deviceCode)
        assertEquals(1, matches.size)
        assertEquals("خالد العتيبي", matches.first().customerName)

        // Search by customer name
        val searchHits = repo1.searchLicenses("العتيبي").first()
        assertEquals(1, searchHits.size)

        // Encrypted registry backup & restore into fresh database
        val backupJson = repo1.exportEncryptedBackup("StrongArchive#2026")
        db1.close()

        val db2 = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo2 = LicenseRepository(db2.issuedLicenseDao())
        val restoredCount = repo2.restoreEncryptedBackup(backupJson, "StrongArchive#2026")
        assertEquals(1, restoredCount)

        val restoredAll = repo2.allLicenses.first()
        assertEquals(1, restoredAll.size)
        assertEquals(signed.token, restoredAll.first().token)
        db2.close()
    }

    @Test
    fun `CUJ setup PIN, unlock vault, generate key, and issue SLK1 license`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        val repo = LicenseRepository(db.issuedLicenseDao())
        val keyVault = KeyVault(InMemoryVaultStorage())
        val viewModel = LicenseStudioViewModel(
            repository = repo,
            keyVault = keyVault,
            workerDispatcher = kotlinx.coroutines.Dispatchers.Main.immediate
        )
        val capturedEvents = mutableListOf<String>()
        val eventJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            viewModel.events.collect { capturedEvents.add(it) }
        }

        composeRule.setContent {
            SmartLedgerApp(
                viewModel = viewModel,
                biometricAvailable = false,
                onRequestBiometricPrompt = {}
            )
        }
        composeRule.waitForIdle()

        // 1) First-time PIN setup: enter 1234 -> submit -> confirm 1234 -> submit
        repeat(2) {
            composeRule.onNodeWithTag("pin_key_1").performScrollTo().performClick()
            composeRule.onNodeWithTag("pin_key_2").performScrollTo().performClick()
            composeRule.onNodeWithTag("pin_key_3").performScrollTo().performClick()
            composeRule.onNodeWithTag("pin_key_4").performScrollTo().performClick()
            composeRule.onNodeWithTag("pin_submit_key").performScrollTo().performClick()
            composeRule.waitForIdle()
        }

        assertTrue(
            "Expected vault unlocked, got ${viewModel.authState.value}",
            viewModel.authState.value.isUnlocked
        )

        // 2) Wait for unlocked IssueLicenseScreen and generate ECDSA P-256 keypair quickly
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("quick_generate_key_button")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("quick_generate_key_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(
            "Expected keypair generated after clicking quick_generate_key_button",
            viewModel.vaultMetadata.value.hasKeyPair
        )

        // 3) Enter a valid device code and issue LIFETIME license
        val validCode = DeviceCodeValidator.buildValidCode("A1B2C3D4E5F")
        composeRule.onNodeWithTag("device_code_input").performScrollTo().performTextInput(validCode)
        composeRule.onNodeWithTag("customer_name_input").performScrollTo().performTextInput("شركة النور")
        composeRule.waitForIdle()

        assertTrue(
            "Expected form canIssue=true, got ${viewModel.issueForm.value}",
            viewModel.issueForm.value.canIssue
        )

        composeRule.mainClock.advanceTimeBy(4_500)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("issue_license_button").performScrollTo().assertIsDisplayed().performClick()
        try {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodes(hasTestTag("whatsapp_send_button")).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            eventJob.cancel()
            throw AssertionError(
                "Timed out waiting for whatsapp_send_button; events=$capturedEvents; selectedLicense=${viewModel.selectedLicense.value}; isIssuing=${viewModel.issueForm.value.isIssuing}",
                e
            )
        }
        eventJob.cancel()

        // 4) Verify Result screen delivery buttons are displayed
        composeRule.onNodeWithTag("whatsapp_send_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("copy_token_only_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("show_qr_button").performScrollTo().assertIsDisplayed()

        db.close()
    }
}
