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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.gateway.GatewayCapability
import com.example.security.VaultMetadata
import com.example.share.LicenseShareHelper
import com.example.ui.components.MonospaceCodeBox
import com.example.ui.components.OfflineSecurityBadge
import com.example.ui.components.PasswordBackupDialog
import com.example.ui.theme.CryptoMonoFamily
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

@Composable
fun KeyVaultScreen(
    metadata: VaultMetadata,
    gatewayCapability: GatewayCapability,
    biometricAvailable: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onGenerateKeyPair: () -> Unit,
    onImportKeyPairPem: (privPem: String, pubPem: String, allowOverwrite: Boolean) -> Boolean,
    onExportEncryptedKeyBackup: suspend (String) -> Result<String>,
    onRestoreEncryptedKeyBackup: suspend (String, String, Boolean) -> Result<VaultMetadata>,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onChangePin: (currentPin: String, newPin: String) -> Boolean,
    onLockVaultNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showGenerateConfirm by remember { mutableStateOf(false) }
    var showImportPemDialog by remember { mutableStateOf(false) }
    var showExportKeyBackupDialog by remember { mutableStateOf(false) }
    var pendingKeyBackupContent by remember { mutableStateOf<String?>(null) }
    var pendingRestoreKeyJson by remember { mutableStateOf<String?>(null) }
    var showChangePinDialog by remember { mutableStateOf(false) }

    val savePublicPemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-pem-file")
    ) { uri ->
        val pem = metadata.publicKeyPem
        if (uri != null && pem != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pem.toByteArray(StandardCharsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "تم حفظ license_local_ec_public.pem", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "تم حفظ النسخة المشفرة للمفتاح الخاص (.slkey)", Toast.LENGTH_LONG).show()
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
                    .height(140.dp)
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
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
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
                                text = "خزنة التوقيع المشفرة (KeyVault)",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = if (metadata.hasKeyPair) {
                                    "ECDSA P-256 (secp256r1) • معرف المفتاح: LOCAL2 • ${metadata.keySource?.arabicLabel ?: ""}"
                                } else {
                                    "لم يتم إعداد زوج مفاتيح التوقيع بعد"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Public Key PEM Export Card (Always shareable for SmartLedger assets/)
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
                        text = "تصدير المفتاح العام للتطبيق (license_local_ec_public.pem)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "انقل هذا الملف إلى مجلد assets/ في تطبيق الدفتر الذكي. لا يحتوي على المفتاح الخاص مطلقاً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (metadata.hasKeyPair && metadata.fingerprintSha256 != null && metadata.publicKeyPem != null) {
                        Text(
                            text = "بصمة المفتاح العام (SHA-256 Fingerprint):",
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
                            text = "المفتاح العام PEM:",
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
                        Text(
                            text = "قم بتوليد زوج المفاتيح أولاً ليظهر المفتاح العام وبصمته.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Private Key Management (Protected)
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
                            text = "إدارة المفتاح الخاص (محمي بـ Android Keystore)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "المفتاح الخاص محفوظ مشفراً ولا يغادر الخزنة أبداً في النسخ العادية. يُسمح بتصديره فقط داخل نسخة احتياطية مشفرة بكلمة مرور قوية (AES-GCM + PBKDF2).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            if (metadata.hasKeyPair) {
                                showGenerateConfirm = true
                            } else {
                                onGenerateKeyPair()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_keypair_button")
                    ) {
                        Text(if (metadata.hasKeyPair) "توليد زوج مفاتيح جديد (استبدال)" else "توليد زوج مفاتيح ECDSA P-256 جديد")
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
                            Text("تصدير نسخة احتياطية مشفرة للمفتاح الخاص (.slkey)")
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
                            text = "طبقة مجردة مفصولة تماماً تتيح مستقبلاً ربط وحدة تراخيص الحسابات السحابية (ACCOUNT على Cloudflare Worker) دون أي تعديل على محرك التوقيع المحلي.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(72.dp))
        }
    }

    if (showGenerateConfirm) {
        AlertDialog(
            onDismissRequest = { showGenerateConfirm = false },
            title = { Text("تحذير استبدال مفتاح التوقيع") },
            text = {
                Text(
                    "يوجد مفتاح توقيع حالي! إذا قمت بتوليد مفتاح جديد، فلن يقبل تطبيق الدفتر الذكي التراخيص الجديدة إلا بعد تحديث ملف license_local_ec_public.pem داخله. تأكد من أخذ نسخة احتياطية مشفرة أولاً."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGenerateConfirm = false
                        onGenerateKeyPair()
                    }
                ) {
                    Text("توليد واستبدال")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showImportPemDialog) {
        ImportPemKeyDialog(
            hasExistingKey = metadata.hasKeyPair,
            onDismiss = { showImportPemDialog = false },
            onConfirm = { privPem, pubPem, allowOverwrite ->
                if (onImportKeyPairPem(privPem, pubPem, allowOverwrite)) {
                    showImportPemDialog = false
                }
            }
        )
    }

    if (showExportKeyBackupDialog) {
        PasswordBackupDialog(
            title = "تصدير نسخة مشفرة للمفتاح الخاص",
            subtitle = "أدخل كلمة مرور قوية لتشفير المفتاح الخاص والعام بخوارزمية AES-256-GCM + PBKDF2. احفظ الملف في مكان آمن جداً.",
            confirmPasswordRequired = true,
            confirmButtonLabel = "تشفير وحفظ (.slkey)",
            onDismiss = { showExportKeyBackupDialog = false },
            onConfirm = { password ->
                showExportKeyBackupDialog = false
                scope.launch {
                    onExportEncryptedKeyBackup(password)
                        .onSuccess { envelope ->
                            pendingKeyBackupContent = envelope
                            saveKeyBackupLauncher.launch("smartledger_ec_private_backup.slkey")
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
            title = "استعادة المفتاح الخاص من نسخة مشفرة",
            subtitle = "أدخل كلمة المرور لفك تشفير ملف .slkey واستعادة زوج المفاتيح إلى الخزنة.",
            confirmPasswordRequired = false,
            confirmButtonLabel = "فك التشفير والاستعادة",
            onDismiss = { pendingRestoreKeyJson = null },
            onConfirm = { password ->
                pendingRestoreKeyJson = null
                scope.launch {
                    onRestoreEncryptedKeyBackup(envelopeJson, password, true)
                        .onSuccess {
                            Toast.makeText(context, "تمت استعادة زوج المفاتيح بنجاح", Toast.LENGTH_LONG).show()
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

@Composable
private fun ImportPemKeyDialog(
    hasExistingKey: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (privPem: String, pubPem: String, allowOverwrite: Boolean) -> Unit
) {
    var privText by remember { mutableStateOf("") }
    var pubText by remember { mutableStateOf("") }
    var allowOverwrite by remember { mutableStateOf(!hasExistingKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استيراد زوج مفاتيح ECDSA P-256") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "الصق المفتاح الخاص (PKCS#8 PEM أو Base64) والمفتاح العام المطابق (X.509 PEM):",
                    style = MaterialTheme.typography.bodySmall
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = privText,
                        onValueChange = { privText = it },
                        label = { Text("Private Key (PKCS#8 PEM / Base64)") },
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = CryptoMonoFamily),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pubText,
                        onValueChange = { pubText = it },
                        label = { Text("Public Key (-----BEGIN PUBLIC KEY-----)") },
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = CryptoMonoFamily),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (hasExistingKey) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allowOverwrite, onCheckedChange = { allowOverwrite = it })
                        Text("أؤكد استبدال المفتاح الحالي", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(privText, pubText, allowOverwrite) },
                enabled = privText.isNotBlank() && pubText.isNotBlank() && (!hasExistingKey || allowOverwrite)
            ) {
                Text("استيراد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onConfirm: (currentPin: String, newPin: String) -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تغيير رمز PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { currentPin = it.filter(Char::isDigit).take(8); error = null },
                    label = { Text("رمز PIN الحالي") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(8); error = null },
                    label = { Text("رمز PIN الجديد (4-8 أرقام)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin.length !in 4..8) {
                        error = "الرمز الجديد يجب أن يكون من 4 إلى 8 أرقام"
                    } else {
                        onConfirm(currentPin, newPin)
                    }
                }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
