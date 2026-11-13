package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crypto.DeviceCodeValidator
import com.example.crypto.EcLicenseCrypto
import com.example.crypto.LicensePlan
import com.example.data.IssuedLicenseEntity
import com.example.data.LicenseRepository
import com.example.gateway.DisabledLicenseBackendGateway
import com.example.gateway.GatewayCapability
import com.example.gateway.LicenseBackendGateway
import com.example.security.KeyVault
import com.example.security.VaultMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IssueFormState(
    val deviceCodeInput: String = "",
    val validation: DeviceCodeValidator.ValidationResult = DeviceCodeValidator.validate(""),
    val allowCheckDigitOverride: Boolean = false,
    val plan: LicensePlan = LicensePlan.LIFETIME,
    val trialDurationDays: Int? = 14,
    val customTrialEndsAtMillis: Long? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val notes: String = "",
    val includeCustomerHintInPayload: Boolean = false,
    val upgradingFromLicenseId: String? = null,
    val duplicateDeviceRecords: List<IssuedLicenseEntity> = emptyList(),
    val isIssuing: Boolean = false
) {
    val canIssue: Boolean
        get() = validation.isFormatValid &&
            (validation.isCheckDigitValid || allowCheckDigitOverride) &&
            (plan == LicensePlan.LIFETIME || trialDurationDays != null || customTrialEndsAtMillis != null) &&
            !isIssuing
}

data class LicenseStats(
    val total: Int = 0,
    val lifetimeCount: Int = 0,
    val trialCount: Int = 0
)

data class AuthUiState(
    val isUnlocked: Boolean = false,
    val isPinConfigured: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val pinError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class LicenseStudioViewModel(
    private val repository: LicenseRepository,
    private val keyVault: KeyVault,
    val backendGateway: LicenseBackendGateway = DisabledLicenseBackendGateway(),
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _authState = MutableStateFlow(
        AuthUiState(
            isUnlocked = false,
            isPinConfigured = keyVault.isPinConfigured(),
            isBiometricEnabled = keyVault.isBiometricEnabled()
        )
    )
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _vaultMetadata = MutableStateFlow(keyVault.getMetadata())
    val vaultMetadata: StateFlow<VaultMetadata> = _vaultMetadata.asStateFlow()

    private val _issueForm = MutableStateFlow(IssueFormState())
    val issueForm: StateFlow<IssueFormState> = _issueForm.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _planFilter = MutableStateFlow<LicensePlan?>(null)
    val planFilter: StateFlow<LicensePlan?> = _planFilter.asStateFlow()

    private val _selectedLicense = MutableStateFlow<IssuedLicenseEntity?>(null)
    val selectedLicense: StateFlow<IssuedLicenseEntity?> = _selectedLicense.asStateFlow()

    private val _selectedSelfVerified = MutableStateFlow<Boolean?>(null)
    val selectedSelfVerified: StateFlow<Boolean?> = _selectedSelfVerified.asStateFlow()

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val gatewayCapability: GatewayCapability = backendGateway.capability

    val filteredLicenses: StateFlow<List<IssuedLicenseEntity>> = combine(
        _searchQuery.flatMapLatest { query -> repository.searchLicenses(query) },
        _planFilter
    ) { list, filter ->
        if (filter == null) list else list.filter { it.plan == filter.wireName }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val stats: StateFlow<LicenseStats> = repository.allLicenses
        .map { list ->
            LicenseStats(
                total = list.size,
                lifetimeCount = list.count { it.plan == LicensePlan.LIFETIME.wireName },
                trialCount = list.count { it.plan == LicensePlan.TRIAL.wireName }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LicenseStats()
        )

    fun toggleTheme() {
        _darkTheme.value = !_darkTheme.value
    }

    // --- Authentication & Vault Lock ---

    fun setupInitialPin(pin: String, enableBiometric: Boolean) {
        runCatching {
            keyVault.setPin(pin)
            keyVault.setBiometricEnabled(enableBiometric)
        }.onSuccess {
            _vaultMetadata.value = keyVault.getMetadata()
            _authState.value = AuthUiState(
                isUnlocked = true,
                isPinConfigured = true,
                isBiometricEnabled = enableBiometric,
                pinError = null
            )
            emitEvent("تم تأمين الخزنة برمز PIN بنجاح")
        }.onFailure { e ->
            _authState.update { it.copy(pinError = e.message ?: "رمز PIN غير صالح") }
        }
    }

    fun unlockWithPin(pin: String): Boolean {
        val ok = keyVault.verifyPin(pin)
        if (ok) {
            _authState.update { it.copy(isUnlocked = true, pinError = null) }
        } else {
            _authState.update { it.copy(pinError = "رمز PIN غير صحيح") }
        }
        return ok
    }

    fun unlockWithBiometric() {
        _authState.update { it.copy(isUnlocked = true, pinError = null) }
    }

    fun clearPinError() {
        _authState.update { it.copy(pinError = null) }
    }

    fun lockVault() {
        _authState.update { it.copy(isUnlocked = false, pinError = null) }
    }

    fun changePin(currentPin: String, newPin: String): Boolean {
        if (!keyVault.verifyPin(currentPin)) {
            emitEvent("رمز PIN الحالي غير صحيح")
            return false
        }
        return runCatching {
            keyVault.setPin(newPin)
        }.onSuccess {
            _vaultMetadata.value = keyVault.getMetadata()
            emitEvent("تم تحديث رمز PIN بنجاح")
        }.onFailure { e ->
            emitEvent(e.message ?: "تعذر تحديث رمز PIN")
        }.isSuccess
    }

    fun setBiometricEnabled(enabled: Boolean) {
        keyVault.setBiometricEnabled(enabled)
        _vaultMetadata.value = keyVault.getMetadata()
        _authState.update { it.copy(isBiometricEnabled = enabled) }
    }

    // --- KeyVault Management ---

    fun generateKeyPair() {
        viewModelScope.launch {
            val metadata = withContext(workerDispatcher) {
                keyVault.generateNewKeyPair()
            }
            _vaultMetadata.value = metadata
            emitEvent("تم توليد زوج مفاتيح ECDSA P-256 وحفظه مشفراً داخل الخزنة")
        }
    }

    fun importKeyPairPem(
        privateKeyInput: String,
        publicKeyPem: String,
        allowOverwrite: Boolean
    ): Boolean {
        return runCatching {
            keyVault.importKeyPairPem(privateKeyInput, publicKeyPem, allowOverwrite)
        }.onSuccess { metadata ->
            _vaultMetadata.value = metadata
            emitEvent("تم استيراد المفتاح الخاص والعام وحفظهما بأمان")
        }.onFailure { e ->
            emitEvent(e.message ?: "فشل استيراد المفاتيح")
        }.isSuccess
    }

    fun exportPublicKeyPem(): String? {
        return runCatching { keyVault.exportPublicKeyPem() }.getOrNull()
    }

    suspend fun exportEncryptedKeyBackup(password: String): Result<String> {
        return withContext(workerDispatcher) {
            runCatching { keyVault.exportEncryptedKeyPairBackup(password) }
        }
    }

    suspend fun restoreEncryptedKeyBackup(
        envelopeJson: String,
        password: String,
        allowOverwrite: Boolean
    ): Result<VaultMetadata> {
        return withContext(workerDispatcher) {
            runCatching {
                keyVault.importEncryptedKeyPairBackup(envelopeJson, password, allowOverwrite)
            }.onSuccess { meta ->
                _vaultMetadata.value = meta
            }
        }
    }

    // --- Issue License Form ---

    fun updateDeviceCode(raw: String) {
        val normalized = DeviceCodeValidator.normalizeInput(raw)
        val validation = DeviceCodeValidator.validate(normalized)
        _issueForm.update {
            it.copy(
                deviceCodeInput = normalized,
                validation = validation,
                allowCheckDigitOverride = if (validation.isCheckDigitValid) false else it.allowCheckDigitOverride
            )
        }
        checkDuplicateDevice(validation.normalizedCode)
    }

    fun smartPasteFromText(clipboardText: String?): Boolean {
        if (clipboardText.isNullOrBlank()) {
            emitEvent("الحافظة فارغة")
            return false
        }
        val extracted = DeviceCodeValidator.extractFromText(clipboardText)
        return if (extracted != null) {
            updateDeviceCode(extracted)
            val validation = DeviceCodeValidator.validate(extracted)
            if (validation.isCheckDigitValid) {
                emitEvent("تم استخراج كود الجهاز والتحقق منه: $extracted")
            } else {
                emitEvent("تم استخراج $extracted — تنبيه: حرف التحقق غير مطابق!")
            }
            true
        } else {
            emitEvent("لم يتم العثور على كود جهاز بصيغة SLD-XXXX-XXXX-XXXC في النص")
            false
        }
    }

    fun setCheckDigitOverride(allow: Boolean) {
        _issueForm.update { it.copy(allowCheckDigitOverride = allow) }
    }

    fun setPlan(plan: LicensePlan) {
        _issueForm.update { state ->
            state.copy(
                plan = plan,
                trialDurationDays = if (plan == LicensePlan.TRIAL && state.trialDurationDays == null && state.customTrialEndsAtMillis == null) 14 else state.trialDurationDays
            )
        }
    }

    fun selectTrialDays(days: Int) {
        _issueForm.update {
            it.copy(
                plan = LicensePlan.TRIAL,
                trialDurationDays = days,
                customTrialEndsAtMillis = null
            )
        }
    }

    fun selectCustomTrialDate(endMillis: Long) {
        _issueForm.update {
            it.copy(
                plan = LicensePlan.TRIAL,
                trialDurationDays = null,
                customTrialEndsAtMillis = endMillis
            )
        }
    }

    fun updateCustomerName(value: String) {
        _issueForm.update { it.copy(customerName = value) }
    }

    fun updateCustomerPhone(value: String) {
        _issueForm.update { it.copy(customerPhone = value) }
    }

    fun updateNotes(value: String) {
        _issueForm.update { it.copy(notes = value) }
    }

    fun setIncludeCustomerHint(include: Boolean) {
        _issueForm.update { it.copy(includeCustomerHintInPayload = include) }
    }

    fun resetIssueForm() {
        _issueForm.value = IssueFormState()
    }

    fun prefillUpgradeToLifetime(existing: IssuedLicenseEntity) {
        val validation = DeviceCodeValidator.validate(existing.deviceCode)
        _issueForm.value = IssueFormState(
            deviceCodeInput = validation.normalizedCode,
            validation = validation,
            allowCheckDigitOverride = !validation.isCheckDigitValid,
            plan = LicensePlan.LIFETIME,
            customerName = existing.customerName,
            customerPhone = existing.customerPhone,
            notes = if (existing.notes.isBlank()) {
                "ترقية من تجريبي (${existing.licenseId})"
            } else {
                "${existing.notes} — ترقية من تجريبي"
            },
            upgradingFromLicenseId = existing.licenseId
        )
        checkDuplicateDevice(validation.normalizedCode)
    }

    private fun checkDuplicateDevice(normalizedCode: String) {
        if (normalizedCode.length < 8) {
            _issueForm.update { it.copy(duplicateDeviceRecords = emptyList()) }
            return
        }
        viewModelScope.launch {
            val matches = repository.findByDeviceCode(normalizedCode)
            _issueForm.update { it.copy(duplicateDeviceRecords = matches) }
        }
    }

    fun issueAndSignLicense(onIssued: (IssuedLicenseEntity) -> Unit) {
        val current = _issueForm.value
        if (!_vaultMetadata.value.hasKeyPair) {
            emitEvent("يرجى توليد أو استيراد مفتاح التوقيع من الخزنة أولاً")
            return
        }
        if (!current.canIssue) {
            emitEvent("تحقق من صحة كود الجهاز وخيارات الخطة أولاً")
            return
        }

        _issueForm.update { it.copy(isIssuing = true) }
        viewModelScope.launch {
            val outcome = runCatching {
                val now = System.currentTimeMillis()
                val hint = if (current.includeCustomerHintInPayload) current.customerName else null
                val payload = EcLicenseCrypto.buildPayload(
                    deviceCode = current.validation.normalizedCode,
                    plan = current.plan,
                    issuedAtMillis = now,
                    trialDays = current.trialDurationDays,
                    customTrialEndsAtMillis = current.customTrialEndsAtMillis,
                    customerHint = hint
                )
                val signed = withContext(workerDispatcher) {
                    keyVault.signLicense(payload)
                }
                val entity = IssuedLicenseEntity(
                    licenseId = signed.payload.licenseId,
                    customerName = current.customerName.trim(),
                    customerPhone = current.customerPhone.trim(),
                    deviceCode = signed.payload.deviceCode,
                    plan = signed.payload.plan,
                    issuedAt = signed.payload.iat,
                    expiresAt = signed.payload.trialEndsAt,
                    token = signed.token,
                    notes = current.notes.trim(),
                    upgradedFromLicenseId = current.upgradingFromLicenseId
                )
                repository.saveIssuedLicense(entity)
                entity to signed.selfVerified
            }

            _issueForm.update { it.copy(isIssuing = false) }
            outcome.onSuccess { (entity, verified) ->
                _selectedLicense.value = entity
                _selectedSelfVerified.value = verified
                _issueForm.value = IssueFormState()
                onIssued(entity)
            }.onFailure { err ->
                emitEvent(err.message ?: "حدث خطأ أثناء توقيع الترخيص")
            }
        }
    }

    // --- History & Result Management ---

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setPlanFilter(plan: LicensePlan?) {
        _planFilter.value = plan
    }

    fun openLicenseResult(license: IssuedLicenseEntity) {
        _selectedLicense.value = license
        viewModelScope.launch(workerDispatcher) {
            _selectedSelfVerified.value = keyVault.verifyTokenWithVaultKey(license.token)
        }
    }

    fun loadLicenseById(licenseId: String) {
        viewModelScope.launch {
            val found = repository.getById(licenseId)
            if (found != null) {
                openLicenseResult(found)
            }
        }
    }

    fun deleteLicense(licenseId: String) {
        viewModelScope.launch {
            repository.deleteLicense(licenseId)
            emitEvent("تم حذف السجل")
        }
    }

    suspend fun exportRegistryBackup(password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching { repository.exportEncryptedBackup(password) }
        }
    }

    suspend fun restoreRegistryBackup(envelopeJson: String, password: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            runCatching { repository.restoreEncryptedBackup(envelopeJson, password) }
        }
    }

    fun emitEvent(message: String) {
        _events.tryEmit(message)
    }

    class Factory(
        private val repository: LicenseRepository,
        private val keyVault: KeyVault,
        private val gateway: LicenseBackendGateway = DisabledLicenseBackendGateway()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LicenseStudioViewModel(repository, keyVault, gateway) as T
        }
    }
}
