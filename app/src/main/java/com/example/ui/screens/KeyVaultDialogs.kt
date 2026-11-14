package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.crypto.EcLicenseCrypto
import com.example.security.KeySummary
import com.example.share.LicenseShareHelper
import com.example.ui.theme.CryptoMonoFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

@Composable
fun KeyringEntryItemCard(
    key: KeySummary,
    onSetActive: () -> Unit,
    onSetTrusted: (Boolean) -> Unit,
    onCopySinglePem: () -> Unit,
    onShareNamedPem: () -> Unit,
    onSaveNamedPem: () -> Unit
) {
    Surface(
        color = if (key.isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        },
        border = BorderStroke(
            width = if (key.isActive) 1.5.dp else 1.dp,
            color = if (key.isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("keyring_item_${key.kid}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = key.kid,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = CryptoMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (key.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (key.isActive) {
                            Surface(
                                color = SuccessGreen.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "نشط للتوقيع",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SuccessGreen
                                    )
                                }
                            }
                        } else if (!key.isTrustedInKeyring) {
                            Surface(
                                color = WarningAmber.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "مستبعد",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarningAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${key.keySource.arabicLabel} • ${LicenseShareHelper.formatDateTime(key.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!key.isActive) {
                    FilledTonalButton(
                        onClick = onSetActive,
                        modifier = Modifier.testTag("activate_key_${key.kid}")
                    ) {
                        Text("تفعيل")
                    }
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = "SHA-256: ${key.fingerprintSha256.take(35)}…",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = CryptoMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (key.isActive) {
                        "معتمد دائماً في التصدير الشامل (Keyring)"
                    } else {
                        "اعتماد في Keyring لدعم التراخيص السابقة"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = key.isTrustedInKeyring,
                    enabled = !key.isActive,
                    onCheckedChange = onSetTrusted,
                    modifier = Modifier.testTag("trust_key_switch_${key.kid}")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onShareNamedPem,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_named_pem_${key.kid}")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${key.kid}.pem")
                }
                OutlinedButton(
                    onClick = onSaveNamedPem,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_named_pem_${key.kid}")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("حفظ منفرد")
                }
                IconButton(
                    onClick = onCopySinglePem,
                    modifier = Modifier.testTag("copy_named_pem_${key.kid}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "نسخ المفتاح العام ${key.kid}"
                    )
                }
            }
        }
    }
}

@Composable
fun GenerateRotateKeyDialog(
    hasExistingKey: Boolean,
    suggestedKid: String,
    existingKids: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (kid: String) -> Unit
) {
    var kidText by remember { mutableStateOf(suggestedKid) }
    val normalized = EcLicenseCrypto.normalizeKeyId(kidText)
    val validationError = when {
        normalized == EcLicenseCrypto.RESERVED_LEGACY_KEY_ID ->
            "المعرف LOCAL1 محجوز للنظام القديم ويُمنع استخدامه"
        !EcLicenseCrypto.isValidKeyId(normalized) ->
            "يجب أن يطابق النمط ^LOCAL[A-Z0-9_-]{1,24}$ (مثال: LOCAL2، LOCAL3)"
        normalized in existingKids ->
            "المعرف $normalized مستخدم بالفعل — اختر معرفاً جديداً للتدوير مثل $suggestedKid"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (hasExistingKey) "تدوير وإنشاء زوج مفاتيح جديد" else "إنشاء أول زوج مفاتيح ECDSA P-256")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (hasExistingKey) {
                        "سيتم إنشاء زوج مفاتيح ECDSA P-256 جديد وتفعيله للتوقيع فوراً، مع الإبقاء على المفاتيح السابقة معتمدة في Keyring حتى لا تتعطل تراخيص العملاء القدامى."
                    } else {
                        "سيتم توليد زوج مفاتيح ECDSA P-256 مشفر داخل الخزنة باستخدام Android Keystore. يُنصح بالبدء بالمعرف القياسي LOCAL2."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = kidText,
                        onValueChange = { kidText = it.uppercase().filter { ch -> ch in 'A'..'Z' || ch in '0'..'9' || ch == '_' || ch == '-' } },
                        label = { Text("Key ID (kid)") },
                        placeholder = { Text(suggestedKid) },
                        singleLine = true,
                        isError = validationError != null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = CryptoMonoFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_kid_input")
                    )
                }
                if (validationError != null) {
                    Text(
                        text = validationError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(normalized) },
                enabled = validationError == null,
                modifier = Modifier.testTag("confirm_generate_key_button")
            ) {
                Text(if (hasExistingKey) "إنشاء وتدوير إلى $normalized" else "توليد $normalized")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun ImportPemKeyDialog(
    suggestedKid: String,
    existingKids: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (privPem: String, pubPem: String, allowOverwrite: Boolean, kid: String) -> Unit
) {
    val defaultKid = if (existingKids.isEmpty()) EcLicenseCrypto.EXPECTED_KEY_ID else suggestedKid
    var kidText by remember { mutableStateOf(defaultKid) }
    var privText by remember { mutableStateOf("") }
    var pubText by remember { mutableStateOf("") }

    val normalizedKid = EcLicenseCrypto.normalizeKeyId(kidText)
    val isDuplicateKid = normalizedKid in existingKids
    var allowOverwrite by remember { mutableStateOf(false) }

    val kidError = when {
        normalizedKid == EcLicenseCrypto.RESERVED_LEGACY_KEY_ID -> "المعرف LOCAL1 محجوز ويُمنع استخدامه"
        !EcLicenseCrypto.isValidKeyId(normalizedKid) -> "صيغة غير صالحة (يجب أن يبدأ بـ LOCAL مثل LOCAL2)"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استيراد زوج مفاتيح ECDSA P-256") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "حدد معرّف المفتاح (kid) ثم الصق المفتاح الخاص (PKCS#8) والمفتاح العام المطابق (X.509 PEM):",
                    style = MaterialTheme.typography.bodySmall
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = kidText,
                        onValueChange = { kidText = it.uppercase() },
                        label = { Text("Key ID (kid)") },
                        singleLine = true,
                        isError = kidError != null,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = CryptoMonoFamily),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                if (kidError != null) {
                    Text(kidError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(
                    onClick = { pubText = EcLicenseCrypto.SMARTLEDGER_DEFAULT_PUBLIC_KEY_PEM.trim() }
                ) {
                    Text("استخدام المفتاح العام الحالي للدفتر الذكي (LOCAL2)")
                }
                if (isDuplicateKid) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allowOverwrite, onCheckedChange = { allowOverwrite = it })
                        Text("المعرف $normalizedKid موجود مسبقاً — أؤكد استبداله", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(privText, pubText, allowOverwrite || !isDuplicateKid, normalizedKid) },
                enabled = kidError == null && privText.isNotBlank() && pubText.isNotBlank() && (!isDuplicateKid || allowOverwrite)
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
fun ChangePinDialog(
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
