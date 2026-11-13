package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.LicensePlan
import com.example.data.IssuedLicenseEntity
import com.example.share.LicenseShareHelper
import com.example.ui.components.PasswordBackupDialog
import com.example.ui.theme.CryptoMonoFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.LicenseStats
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

@Composable
fun IssuedLicensesScreen(
    licenses: List<IssuedLicenseEntity>,
    stats: LicenseStats,
    searchQuery: String,
    planFilter: LicensePlan?,
    onSearchQueryChange: (String) -> Unit,
    onPlanFilterChange: (LicensePlan?) -> Unit,
    onOpenLicense: (IssuedLicenseEntity) -> Unit,
    onUpgradeToLifetime: (IssuedLicenseEntity) -> Unit,
    onDeleteLicense: (String) -> Unit,
    onExportEncryptedBackup: suspend (String) -> Result<String>,
    onRestoreEncryptedBackup: suspend (String, String) -> Result<Int>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingDelete by remember { mutableStateOf<IssuedLicenseEntity?>(null) }
    var pendingBackupEnvelope by remember { mutableStateOf<String?>(null) }
    var pendingRestoreRawJson by remember { mutableStateOf<String?>(null) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }

    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingBackupEnvelope
        pendingBackupEnvelope = null
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(StandardCharsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "تم حفظ النسخة الاحتياطية المشفرة للسجل", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "تعذر حفظ النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    String(input.readBytes(), StandardCharsets.UTF_8)
                }
            }.onSuccess { raw ->
                if (!raw.isNullOrBlank()) {
                    pendingRestoreRawJson = raw
                }
            }.onFailure {
                Toast.makeText(context, "تعذر قراءة الملف المختار", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            Spacer(Modifier.height(12.dp))

            // Stats Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChipCard("الإجمالي", stats.total.toString(), Modifier.weight(1f))
                StatChipCard("مدى الحياة", stats.lifetimeCount.toString(), Modifier.weight(1f))
                StatChipCard("تجريبية", stats.trialCount.toString(), Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("ابحث بالاسم، رقم الهاتف، أو كود الجهاز...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input")
            )

            Spacer(Modifier.height(8.dp))

            // Filters + Encrypted Backup Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = planFilter == null,
                        onClick = { onPlanFilterChange(null) },
                        label = { Text("الكل") }
                    )
                    FilterChip(
                        selected = planFilter == LicensePlan.LIFETIME,
                        onClick = { onPlanFilterChange(LicensePlan.LIFETIME) },
                        label = { Text("مدى الحياة") }
                    )
                    FilterChip(
                        selected = planFilter == LicensePlan.TRIAL,
                        onClick = { onPlanFilterChange(LicensePlan.TRIAL) },
                        label = { Text("تجريبي") }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { showExportPasswordDialog = true },
                        modifier = Modifier.testTag("export_registry_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = "نسخ احتياطي مشفر للسجل"
                        )
                    }
                    IconButton(
                        onClick = { openBackupLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("restore_registry_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "استعادة نسخة احتياطية مشفرة"
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            if (licenses.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "لا توجد تراخيص مُصدرة بعد" else "لا توجد نتائج مطابقة للبحث",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "كل ترخيص يتم توقيعه يُحفظ تلقائياً في قاعدة البيانات المحلية المشفرة بالنسخ الاحتياطي.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(licenses, key = { it.licenseId }) { item ->
                        IssuedLicenseCard(
                            item = item,
                            onOpen = { onOpenLicense(item) },
                            onCopyToken = {
                                LicenseShareHelper.copyToClipboard(context, "SLK1", item.token)
                                Toast.makeText(context, "تم نسخ الكود", Toast.LENGTH_SHORT).show()
                            },
                            onResendWhatsApp = {
                                val msg = LicenseShareHelper.buildWhatsAppActivationMessage(item)
                                LicenseShareHelper.openWhatsAppWithMessage(context, item.customerPhone, msg)
                            },
                            onUpgrade = { onUpgradeToLifetime(item) },
                            onDelete = { pendingDelete = item }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف سجل الترخيص") },
            text = {
                Text("هل تريد حذف السجل ${target.licenseId} (${target.deviceCode}) من أرشيف الجهاز؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteLicense(target.licenseId)
                        pendingDelete = null
                    }
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showExportPasswordDialog) {
        PasswordBackupDialog(
            title = "تصدير نسخة احتياطية مشفرة للسجل",
            subtitle = "سيتم تشفير أرشيف العملاء والتراخيص بخوارزمية AES-256-GCM + PBKDF2. لا تتضمن هذه النسخة المفتاح الخاص للتوقيع.",
            confirmPasswordRequired = true,
            confirmButtonLabel = "تشفير وحفظ (.slbak)",
            onDismiss = { showExportPasswordDialog = false },
            onConfirm = { password ->
                showExportPasswordDialog = false
                scope.launch {
                    onExportEncryptedBackup(password)
                        .onSuccess { envelope ->
                            pendingBackupEnvelope = envelope
                            saveBackupLauncher.launch("smartledger_registry_backup.slbak")
                        }
                        .onFailure { err ->
                            Toast.makeText(context, err.message ?: "فشل التشفير", Toast.LENGTH_LONG).show()
                        }
                }
            }
        )
    }

    pendingRestoreRawJson?.let { rawJson ->
        PasswordBackupDialog(
            title = "استعادة سجل مشفر (.slbak)",
            subtitle = "أدخل كلمة المرور لفك تشفير النسخة الاحتياطية ودمجها مع السجل الحالي.",
            confirmPasswordRequired = false,
            confirmButtonLabel = "فك التشفير والاستعادة",
            onDismiss = { pendingRestoreRawJson = null },
            onConfirm = { password ->
                pendingRestoreRawJson = null
                scope.launch {
                    onRestoreEncryptedBackup(rawJson, password)
                        .onSuccess { count ->
                            Toast.makeText(context, "تمت استعادة $count سجل بنجاح", Toast.LENGTH_LONG).show()
                        }
                        .onFailure {
                            Toast.makeText(context, "فشل فك التشفير: كلمة المرور خاطئة أو الملف تالف", Toast.LENGTH_LONG).show()
                        }
                }
            }
        )
    }
}

@Composable
private fun StatChipCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontFamily = CryptoMonoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IssuedLicenseCard(
    item: IssuedLicenseEntity,
    onOpen: () -> Unit,
    onCopyToken: () -> Unit,
    onResendWhatsApp: () -> Unit,
    onUpgrade: () -> Unit,
    onDelete: () -> Unit
) {
    val planEnum = LicensePlan.fromWireName(item.plan)
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.customerName.ifBlank { "عميل بدون اسم" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.deviceCode,
                        fontFamily = CryptoMonoFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    color = if (planEnum == LicensePlan.LIFETIME) {
                        SuccessGreen.copy(alpha = 0.16f)
                    } else {
                        WarningAmber.copy(alpha = 0.16f)
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = planEnum.arabicLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (planEnum == LicensePlan.LIFETIME) SuccessGreen else WarningAmber,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "أُصدر: ${LicenseShareHelper.formatDateTime(item.issuedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.expiresAt != null) {
                    Text(
                        text = "ينتهي: ${LicenseShareHelper.formatDateOnly(item.expiresAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber
                    )
                }
            }

            if (item.customerPhone.isNotBlank() || item.notes.isNotBlank()) {
                Text(
                    text = listOf(item.customerPhone, item.notes).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onResendWhatsApp,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إعادة إرسال", style = MaterialTheme.typography.labelMedium)
                }

                if (planEnum == LicensePlan.TRIAL) {
                    OutlinedButton(
                        onClick = onUpgrade,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("upgrade_lifetime_${item.licenseId}")
                    ) {
                        Icon(Icons.Default.Upgrade, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ترقية لمدى الحياة", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = onCopyToken) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ كود التفعيل")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "حذف السجل",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
