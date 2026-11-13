package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.LicenseRepository
import com.example.security.BiometricAuthHelper
import com.example.security.EncryptedPrefsVaultStorage
import com.example.security.KeyVault
import com.example.ui.SmartLedgerApp
import com.example.ui.viewmodel.LicenseStudioViewModel

class MainActivity : FragmentActivity() {

    private lateinit var viewModel: LicenseStudioViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val repository = LicenseRepository(db.issuedLicenseDao())
        val keyVault = KeyVault(EncryptedPrefsVaultStorage(applicationContext))
        val factory = LicenseStudioViewModel.Factory(repository, keyVault)
        viewModel = ViewModelProvider(this, factory)[LicenseStudioViewModel::class.java]

        val biometricAvailable = BiometricAuthHelper.isBiometricAvailable(this)

        setContent {
            SmartLedgerApp(
                viewModel = viewModel,
                biometricAvailable = biometricAvailable,
                onRequestBiometricPrompt = {
                    BiometricAuthHelper.authenticate(
                        activity = this,
                        onSuccess = { viewModel.unlockWithBiometric() },
                        onError = { err ->
                            Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
        }
    }
}
