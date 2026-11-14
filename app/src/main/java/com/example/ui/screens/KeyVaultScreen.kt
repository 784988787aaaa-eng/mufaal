package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.gateway.GatewayCapability
import com.example.security.VaultMetadata
import com.example.share.LicenseShareHelper
import com.example.ui.components.MonospaceCodeBox
import com.example.ui.components.OfflineSecurityBadge
import com.example.ui.components.PasswordBackupDialog
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

private enum class PublicExportMode {
    ACTIVE_SINGLE,
    KEYRING_ROTATION
}

@Composable
fun KeyVaultScreen(
    metadata: VaultMetadata,
    gatewayCapability: GatewayCapability,
    biometricAvailable: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onGenerateKeyPair: (String?) -> Unit,
    onImportKeyPairPem: (privPem: String, pubPem: String, allowOverwrite: Boolean, kid: String?) -> Boolean,
    onSetActiveKey: (String) -> Unit,
    onSetKeyTrustedInKeyring: (String, Boolean) -> Unit,
    onExportKeyringPublicPem: () -> String?,
    onExportEncryptedKeyBackup: suspend (String) -> Result<String>,
    onRestoreEncryptedKeyBackup: suspend (String, String, Boolean) -> Result<VaultMetadata>,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onChangePin: (currentPin: String, newPin: String) -> Boolean,
    onLockVaultNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var exportMode by remember { mutableStateOf(PublicExportMode.ACTIVE_SINGLE) }
    var showGenerateDialog by remember { mutableStateOf(false) }
    var showImportPemDialog by remember { mutableStateOf(false) }
    var showExportKeyBackupDialog by remember { mutableStateOf(false) }
    var pendingPublicPemToSave by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingKeyBackupContent by remember { mutableStateOf<String?>(null) }
    var pendingRestoreKeyJson by remember { mutableStateOf<String?>(null) }
    var showChangePinDialog by remember { mutableStateOf(false) }

    val savePublicPemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-pem-file")
    ) { uri ->
        val pending = pendingPublicPemToSave
        pendingPublicPemToSave = null
        val pem = pending?.second ?: metadata.publicKeyPem
        val label = pending?.first ?: "license_local_ec_public.pem"
        if (uri != null && pem != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pem.toByteArray(StandardCharsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "تم حفظ $label بنجاح", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val saveKeyBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingKeyBackupContent
        pendingKeyBackupContent = null
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(StandardCharsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "تم حفظ النسخة المشفرة للخزنة (.slkey)", Toast.LENGTH_LONG).show()
            }
        }
    }

    val openKeyBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    String(input.readBytes(), StandardCharsets.UTF_8)
                }
            }.onSuccess { raw ->
                if (!raw.isNullOrBlank()) {
                    pendingRestoreKeyJson = raw
                }
            }
        }
    }

    val keyringPem = remember(metadata.keys) {
        if (metadata.hasKeyPair) onExportKeyringPublicPem() else null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Vault Header Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vault_banner_1794598050139),
                    contentDescription = "خلفية خزنة المفاتيح",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OfflineSecurityBadge()
                        FilledTonalButton(
                            onClick = onLockVaultNow,
                            modifier = Modifier.testTag("lock_vault_now_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("قفل الآن")
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "خزنة التوقيع وتدوير المفاتيح (KeyVault)",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = if (metadata.hasKeyPair) {
                                    "النشط: ${metadata.activeKid ?: "LOCAL2"} • عدد المفاتيح: ${metadata.keys.size} (معتمد: ${metadata.trustedKeyCount})"
                                } else {
                                    "ECDSA P-256 (secp256r1) • لم يتم إنشاء مفتاح توقيع بعد"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Public Key Export Card (Single active vs Full Keyring for SmartLedger assets/)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "تصدير المفتاح العام لتطبيق الدفتر الذكي (assets/)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "يُصدر المفتاح العام بترميز X.509 PEM فقط لوضعه في assets/license_local_ec_public.pem (أو assets/license_local_keys/<kid>.pem). لا يحتوي على أي مفتاح خاص.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (metadata.hasKeyPair && metadata.fingerprintSha256 != null && metadata.publicKeyPem != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = exportMode == PublicExportMode.ACTIVE_SINGLE,
                                onClick = { exportMode = PublicExportMode.ACTIVE_SINGLE },
                                label = { Text("المفتاح النشط (${metadata.activeKid})") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_mode_single_chip")
                            )
                            FilterChip(
                                selected = exportMode == PublicExportMode.KEYRING_ROTATION,
                                onClick = { exportMode = PublicExportMode.KEYRING_ROTATION },
                                label = { Text("التصدير الشامل Keyring (${metadata.trustedKeyCount})") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_mode_keyring_chip")
                            )
                        }

                        if (exportMode == PublicExportMode.ACTIVE_SINGLE) {
                            val matchesAppKey = metadata.matchesSmartLedgerDefaultKey
                            Surface(
                                color = if (matchesAppKey) SuccessGreen.copy(alpha = 0.14f) else WarningAmber.copy(alpha = 0.14f),
                                border = BorderStroke(
                                    1.dp,
                                    if (matchesAppKey) SuccessGreen.copy(alpha = 0.45f) else WarningAmber.copy(alpha = 0.45f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (matchesAppKey) {
                                        "✓ المفتاح النشط (${metadata.activeKid}) مطابق للمفتاح المدمج في الدفتر الذكي."
                                    } else {
                                        "⚠ المفتاح النشط (${metadata.activeKid}) جديد: استبدل assets/license_local_ec_public.pem (أو استخدم التصدير الشامل Keyring أدناه للحفاظ على التراخيص السابقة) وحدّث FALLBACK في LicenseCrypto.kt."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (matchesAppKey) SuccessGreen else WarningAmber,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            Text(
                                text = "بصمة SHA-256 للمفتاح النشط (${metadata.activeKid}):",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            MonospaceCodeBox(
                                text = metadata.fingerprintSha256,
                                onCopy = {
                                    LicenseShareHelper.copyToClipboard(
                                        context,
                                        "SHA-256 Fingerprint",
                                        metadata.fingerprintSha256
                                    )
                                    Toast.makeText(context, "تم نسخ بصمة SHA-256", Toast.LENGTH_SHORT).show()
                                }
                            )

                            Text(
                                text = "محتوى license_local_ec_public.pem (${metadata.activeKid}):",
                                style = MaterialTheme.typography.labelLarge
                            )
                            MonospaceCodeBox(
                                text = metadata.publicKeyPem.trim(),
                                maxLines = 6,
                                onCopy = {
                                    LicenseShareHelper.copyToClipboard(
                                        context,
                                        "Public Key PEM",
                                        metadata.publicKeyPem
                                    )
                                    Toast.makeText(context, "تم نسخ المفتاح العام PEM", Toast.LENGTH_SHORT).show()
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        LicenseShareHelper.sharePublicKeyPemFile(context, metadata.publicKeyPem)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("share_public_pem_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("مشاركة PEM")
                                }

                                OutlinedButton(
                                    onClick = {
                                        pendingPublicPemToSave = "license_local_ec_public.pem" to metadata.publicKeyPem
                                        savePublicPemLauncher.launch("license_local_ec_public.pem")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_public_pem_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("حفظ الملف")
                                }
                            }
                        } else {
                            val exportText = keyringPem ?: metadata.publicKeyPem
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "يحتوي هذا التصدير على جميع المفاتيح المعتمدة (${metadata.trustedKeyCount}) مسبوقة بوسم # kid: <ID> لتضمينها مباشرة في license_local_ec_public.pem ودعم التراخيص القديمة والجديدة معاً.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            MonospaceCodeBox(
                                text = exportText.trim(),
                                maxLines = 8,
                                onCopy = {
                                    LicenseShareHelper.copyToClipboard(context, "Keyring PEM", exportText)
                                    Toast.makeText(context, "تم نسخ Keyring PEM الشامل", Toast.LENGTH_SHORT).show()
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        LicenseShareHelper.sharePublicKeyPemFile(
                                            context = context,
                                            publicKeyPem = exportText,
                                            fileName = "license_local_ec_public.pem"
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("share_keyring_pem_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("مشاركة Keyring")
                                }
                                OutlinedButton(
                                    onClick = {
                                        pendingPublicPemToSave = "license_local_ec_public.pem" to exportText
                                        savePublicPemLauncher.launch("license_local_ec_public.pem")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_keyring_pem_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("حفظ Keyring")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "قم بتوليد أول زوج مفاتيح (${metadata.suggestedNextKid}) ليظهر المفتاح العام وبصمته وخيارات التصدير.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Keyring & Key Rotation Management Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            text = "سجل وتدوير المفاتيح الخاصة (Android Keystore)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "المفاتيح الخاصة محفوظة مشفرة داخل الخزنة ولا تغادر الجهاز إلا ضمن نسخة .slkey مشفرة بكلمة مرور (AES-256-GCM). يُمنع استخدام المعرف المحجوز LOCAL1.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { showGenerateDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_keypair_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (metadata.hasKeyPair) {
                                "إنشاء وتدوير إلى مفتاح جديد (${metadata.suggestedNextKid})"
                            } else {
                                "توليد أول زوج مفاتيح ECDSA P-256 (${metadata.suggestedNextKid})"
                            }
                        )
                    }

                    if (metadata.keys.isNotEmpty()) {
                        Text(
                            text = "المفاتيح المحفوظة في الخزنة (${metadata.keys.size}):",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        metadata.keys.asReversed().forEach { keyItem ->
                            KeyringEntryItemCard(
                                key = keyItem,
                                onSetActive = { onSetActiveKey(keyItem.kid) },
                                onSetTrusted = { trusted -> onSetKeyTrustedInKeyring(keyItem.kid, trusted) },
                                onCopySinglePem = {
                                    LicenseShareHelper.copyToClipboard(
                                        context,
                                        "${keyItem.kid} Public PEM",
                                        keyItem.publicKeyPem
                                    )
                                    Toast.makeText(context, "تم نسخ ${keyItem.kid}.pem", Toast.LENGTH_SHORT).show()
                                },
                                onShareNamedPem = {
                                    LicenseShareHelper.sharePublicKeyPemFile(
                                        context = context,
                                        publicKeyPem = keyItem.publicKeyPem,
                                        fileName = "${keyItem.kid}.pem"
                                    )
                                },
                                onSaveNamedPem = {
                                    val fileName = "${keyItem.kid}.pem"
                                    pendingPublicPemToSave = fileName to keyItem.publicKeyPem
                                    savePublicPemLauncher.launch(fileName)
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showImportPemDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("استيراد PEM")
                        }
                        OutlinedButton(
                            onClick = { openKeyBackupLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("استعادة .slkey")
                        }
                    }

                    if (metadata.hasKeyPair) {
                        FilledTonalButton(
                            onClick = { showExportKeyBackupDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_encrypted_key_backup_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تصدير نسخة احتياطية مشفرة لكافة المفاتيح (.slkey)")
                        }
                    }
                }
            }

            // Security & Appearance Preferences
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("إعدادات الأمان والمظهر", style = MaterialTheme.typography.titleMedium)

                    if (biometricAvailable) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null)
                                Text("الفتح ببصمة الإصبع", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = metadata.isBiometricEnabled,
                                onCheckedChange = onSetBiometricEnabled
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.DarkMode, contentDescription = null)
                            Text("المظهر الداكن للخزنة", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { onToggleTheme() }
                        )
                    }

                    OutlinedButton(
                        onClick = { showChangePinDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تغيير رمز PIN للخزنة")
                    }
                }
            }

            // Cloud Extensibility Gateway Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "بوابة السحابة (LicenseBackendGateway)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val statusText = when (gatewayCapability) {
                            is GatewayCapability.DisabledOfflineOnly ->
                                "الحالة: معطلة افتراضياً (وضع أوفلاين بالكامل دون أي صلاحية إنترنت)."
                            is GatewayCapability.CloudWorkerConfigured ->
                                "متصلة: ${gatewayCapability.workerBaseUrl}"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "طبقة مجردة مفصولة تماماً تتيح مستقبلاً ربط وحدة تراخيص الحسابات السحابية دون أي تعديل على محرك التوقيع المحلي.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(72.dp))
        }
    }

    if (showGenerateDialog) {
        GenerateRotateKeyDialog(
            hasExistingKey = metadata.hasKeyPair,
            suggestedKid = metadata.suggestedNextKid,
            existingKids = metadata.keys.map { it.kid }.toSet(),
            onDismiss = { showGenerateDialog = false },
            onConfirm = { requestedKid ->
                showGenerateDialog = false
                onGenerateKeyPair(requestedKid)
            }
        )
    }

    if (showImportPemDialog) {
        ImportPemKeyDialog(
            suggestedKid = metadata.suggestedNextKid,
            existingKids = metadata.keys.map { it.kid }.toSet(),
            onDismiss = { showImportPemDialog = false },
            onConfirm = { privPem, pubPem, allowOverwrite, kid ->
                if (onImportKeyPairPem(privPem, pubPem, allowOverwrite, kid)) {
                    showImportPemDialog = false
                }
            }
        )
    }

    if (showExportKeyBackupDialog) {
        PasswordBackupDialog(
            title = "تصدير نسخة مشفرة لكافة مفاتيح الخزنة",
            subtitle = "أدخل كلمة مرور قوية لتشفير جميع المفاتيح الخاصة والعامة (Keyring) بخوارزمية AES-256-GCM + PBKDF2.",
            confirmPasswordRequired = true,
            confirmButtonLabel = "تشفير وحفظ (.slkey)",
            onDismiss = { showExportKeyBackupDialog = false },
            onConfirm = { password ->
                showExportKeyBackupDialog = false
                scope.launch {
                    onExportEncryptedKeyBackup(password)
                        .onSuccess { envelope ->
                            pendingKeyBackupContent = envelope
                            saveKeyBackupLauncher.launch("smartledger_keyring_backup.slkey")
                        }
                        .onFailure { err ->
                            Toast.makeText(context, err.message ?: "فشل التشفير", Toast.LENGTH_LONG).show()
                        }
                }
            }
        )
    }

    pendingRestoreKeyJson?.let { envelopeJson ->
        PasswordBackupDialog(
            title = "استعادة المفاتيح من نسخة مشفرة",
            subtitle = "أدخل كلمة المرور لفك تشفير ملف .slkey واستعادة سجل المفاتيح بالكامل إلى الخزنة.",
            confirmPasswordRequired = false,
            confirmButtonLabel = "فك التشفير والاستعادة",
            onDismiss = { pendingRestoreKeyJson = null },
            onConfirm = { password ->
                pendingRestoreKeyJson = null
                scope.launch {
                    onRestoreEncryptedKeyBackup(envelopeJson, password, true)
                        .onSuccess {
                            Toast.makeText(context, "تمت استعادة سجل المفاتيح بنجاح", Toast.LENGTH_LONG).show()
                        }
                        .onFailure { err ->
                            Toast.makeText(
                                context,
                                err.message ?: "فشل فك التشفير: كلمة المرور غير صحيحة",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
        )
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onConfirm = { cur, next ->
                if (onChangePin(cur, next)) {
                    showChangePinDialog = false
                }
            }
        )
    }
}
