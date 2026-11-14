package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.crypto.EcLicenseCrypto
import com.example.crypto.LicensePlan
import com.example.data.IssuedLicenseEntity
import com.example.share.LicenseShareHelper
import com.example.ui.components.MonospaceCodeBox
import com.example.ui.components.QrCodePreviewDialog
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import java.nio.charset.StandardCharsets
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseResultScreen(
    license: IssuedLicenseEntity?,
    selfVerified: Boolean?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }
    var showPayloadJson by remember { mutableStateOf(false) }

    val saveSlicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && license != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(license.token.toByteArray(StandardCharsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "تم حفظ ملف الترخيص (.slic) بنجاح", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "تعذر حفظ الملف", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نتيجة التفعيل والمشاركة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (license == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("لم يتم تحديد ترخيص للعرض", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        @OptIn(ExperimentalEncodingApi::class)
        val decodedJson = remember(license.token) {
            runCatching {
                val parts = license.token.split('.')
                val urlDecoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
                String(urlDecoder.decode(parts[1]), StandardCharsets.UTF_8)
            }.getOrDefault("")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Self-Verify Banner
                val verified = selfVerified == true
                Surface(
                    color = if (verified) SuccessGreen.copy(alpha = 0.14f) else WarningAmber.copy(alpha = 0.15f),
                    border = BorderStroke(
                        1.dp,
                        if (verified) SuccessGreen.copy(alpha = 0.55f) else WarningAmber.copy(alpha = 0.55f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (verified) Icons.Default.Verified else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (verified) SuccessGreen else WarningAmber,
                            modifier = Modifier.size(26.dp)
                        )
                        val parsedPayload = remember(license.token) {
                            EcLicenseCrypto.extractPayloadUnsafe(license.token)
                        }
                        Column {
                            Text(
                                text = if (verified) {
                                    "تم التحقق الذاتي بالمفتاح (${parsedPayload?.kid ?: "LOCAL2"}) بنجاح ✓"
                                } else {
                                    "تعذر التحقق بالمفتاح (${parsedPayload?.kid ?: "غير معروف"})"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (verified) SuccessGreen else WarningAmber
                            )
                            Text(
                                text = "SHA256withECDSA • IEEE P1363 (64B) • v: ${parsedPayload?.v ?: 1} / ${parsedPayload?.kid ?: "LOCAL2"} • ${license.token.length} حرف",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Summary Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val planEnum = LicensePlan.fromWireName(license.plan)
                        SummaryRow("رقم الترخيص", license.licenseId)
                        SummaryRow("كود الجهاز", license.deviceCode)
                        SummaryRow("الخطة", "${planEnum.arabicLabel} (${license.plan})")
                        SummaryRow("تاريخ الإصدار", LicenseShareHelper.formatDateTime(license.issuedAt))
                        SummaryRow(
                            "تاريخ الانتهاء",
                            license.expiresAt?.let { LicenseShareHelper.formatDateTime(it) } ?: "مدى الحياة (دائم)"
                        )
                        if (license.customerName.isNotBlank()) {
                            SummaryRow("العميل", license.customerName)
                        }
                        if (license.customerPhone.isNotBlank()) {
                            SummaryRow("رقم الهاتف", license.customerPhone)
                        }
                        if (license.notes.isNotBlank()) {
                            SummaryRow("ملاحظات", license.notes)
                        }
                    }
                }

                // Signed SLK1 Token Box
                Text(
                    text = "كود التفعيل الموقّع (SLK1)",
                    style = MaterialTheme.typography.titleMedium
                )
                MonospaceCodeBox(
                    text = license.token,
                    maxLines = 4,
                    onCopy = {
                        LicenseShareHelper.copyToClipboard(context, "SmartLedger Token", license.token)
                        Toast.makeText(context, "تم نسخ كود التفعيل SLK1", Toast.LENGTH_SHORT).show()
                    }
                )

                // Expandable Raw Payload JSON
                TextButton(
                    onClick = { showPayloadJson = !showPayloadJson },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("معاينة حمولة JSON الموقّعة")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showPayloadJson) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
                AnimatedVisibility(visible = showPayloadJson) {
                    MonospaceCodeBox(
                        text = decodedJson,
                        onCopy = {
                            LicenseShareHelper.copyToClipboard(context, "Payload JSON", decodedJson)
                            Toast.makeText(context, "تم نسخ JSON", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // 4 Quick One-Tap Delivery Actions
                Text(
                    text = "تسليم سريع للعميل",
                    style = MaterialTheme.typography.titleMedium
                )

                Button(
                    onClick = {
                        val msg = LicenseShareHelper.buildWhatsAppActivationMessage(license)
                        LicenseShareHelper.openWhatsAppWithMessage(context, license.customerPhone, msg)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("whatsapp_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("إرسال عبر واتساب")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            LicenseShareHelper.copyToClipboard(context, "SLK1 License", license.token.trim())
                            Toast.makeText(context, "تم نسخ كود التفعيل SLK1", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("copy_token_only_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("نسخ كود التفعيل")
                    }

                    FilledTonalButton(
                        onClick = { showQrDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("show_qr_button")
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("عرض QR Code")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            LicenseShareHelper.shareSlicFile(context, license.deviceCode, license.token)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("share_slic_button")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تصدير ملف .slic")
                    }

                    OutlinedButton(
                        onClick = {
                            saveSlicLauncher.launch(LicenseShareHelper.slicFileName(license.deviceCode))
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("save_slic_saf_button")
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("حفظ ملف .slic")
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showQrDialog && license != null) {
        QrCodePreviewDialog(
            token = license.token,
            licenseId = license.licenseId,
            deviceCode = license.deviceCode,
            onShareQrImage = {
                val bmp = LicenseShareHelper.renderQrBitmap(license.token)
                LicenseShareHelper.shareQrBitmap(context, license.licenseId, bmp)
            },
            onDismiss = { showQrDialog = false }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
