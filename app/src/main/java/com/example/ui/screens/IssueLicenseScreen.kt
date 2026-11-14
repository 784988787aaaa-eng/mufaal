package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.LicensePlan
import com.example.security.VaultMetadata
import com.example.share.LicenseShareHelper
import com.example.ui.theme.CryptoMonoFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.IssueFormState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IssueLicenseScreen(
    formState: IssueFormState,
    vaultMetadata: VaultMetadata,
    onDeviceCodeChange: (String) -> Unit,
    onSmartPaste: (String?) -> Unit,
    onPlanChange: (LicensePlan) -> Unit,
    onSelectTrialDays: (Int) -> Unit,
    onSelectCustomTrialDate: (Long) -> Unit,
    onCustomerNameChange: (String) -> Unit,
    onCustomerPhoneChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onGenerateKeyQuickAction: () -> Unit,
    onOpenVaultTab: () -> Unit,
    onIssueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }

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
            if (!vaultMetadata.hasKeyPair) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "مفتاح التوقيع الخاص غير مُنشأ بعد",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = "لتوقيع تراخيص SLK1، قم بتوليد زوج مفاتيح ECDSA P-256 جديد داخل الخزنة أو استيراد مفتاحك الحالي.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onGenerateKeyQuickAction,
                                modifier = Modifier.testTag("quick_generate_key_button")
                            ) {
                                Text("توليد مفتاح P-256 الآن")
                            }
                            OutlinedButton(onClick = onOpenVaultTab) {
                                Text("فتح الخزنة")
                            }
                        }
                    }
                }
            } else if (!vaultMetadata.matchesSmartLedgerDefaultKey) {
                Surface(
                    color = WarningAmber.copy(alpha = 0.13f),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "تذكير: المفتاح النشط مخصّص. تأكد من تحديث license_local_ec_public.pem و FALLBACK_LOCAL_EC_PUBLIC_KEY في تطبيق الدفتر الذكي ليقبل التوقيع.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (formState.upgradingFromLicenseId != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "وضع الترقية إلى مدى الحياة (من: ${formState.upgradingFromLicenseId})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 1. Device Code Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "كود جهاز العميل",
                            style = MaterialTheme.typography.titleMedium
                        )
                        FilledTonalButton(
                            onClick = {
                                val text = LicenseShareHelper.readClipboardText(context)
                                onSmartPaste(text)
                            },
                            modifier = Modifier.testTag("smart_paste_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("لصق ذكي")
                        }
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = formState.deviceCodeInput,
                            onValueChange = onDeviceCodeChange,
                            label = { Text("Device Code (SLD-XXXX-XXXX-XXXC)") },
                            placeholder = {
                                Text(
                                    text = "SLD-XXXX-XXXX-XXXC",
                                    fontFamily = CryptoMonoFamily
                                )
                            },
                            textStyle = TextStyle(
                                fontFamily = CryptoMonoFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("device_code_input")
                        )
                    }

                    // Instant Luhn mod-32 Validation Pill
                    if (formState.deviceCodeInput.isNotBlank()) {
                        val v = formState.validation
                        val (bg, fg, icon, msg) = when {
                            v.isFullyValid -> Quad(
                                SuccessGreen.copy(alpha = 0.14f),
                                SuccessGreen,
                                Icons.Default.CheckCircle,
                                "الصيغة سليمة وحرف التحقق (${v.actualCheckDigit}) مطابق (Luhn mod-32) ✓"
                            )
                            v.isFormatValid && !v.isCheckDigitValid -> Quad(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer,
                                Icons.Default.ErrorOutline,
                                "رمز الجهاز يحوي خطأ إملائياً! حرف التحقق (${v.actualCheckDigit}) لا يطابق المتوقع (${v.expectedCheckDigit}) — الإصدار محظور."
                            )
                            else -> Quad(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer,
                                Icons.Default.ErrorOutline,
                                "الصيغة غير مكتملة أو تحوي أحرفاً مستبعدة (0/O/1/I) — الصيغة المطلوبة: SLD-XXXX-XXXX-XXXC"
                            )
                        }
                        Surface(
                            color = bg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = fg
                                )
                            }
                        }
                    }

                    // Duplicate Device Alert
                    AnimatedVisibility(visible = formState.duplicateDeviceRecords.isNotEmpty()) {
                        Surface(
                            color = WarningAmber.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = WarningAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "تنبيه: تم إصدار ${formState.duplicateDeviceRecords.size} ترخيص مسبقاً لهذا الجهاز!",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = WarningAmber
                                    )
                                }
                                val latest = formState.duplicateDeviceRecords.first()
                                Text(
                                    text = "آخر إصدار: ${LicensePlan.fromWireName(latest.plan).arabicLabel} • ${LicenseShareHelper.formatDateTime(latest.issuedAt)}" +
                                        if (latest.customerName.isNotBlank()) " • للعميل: ${latest.customerName}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 2. Plan Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "نوع خطة الترخيص",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (formState.autoDetectedPlan != null) {
                            Surface(
                                color = SuccessGreen.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "مكتشف تلقائياً ✓",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuccessGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        LicensePlan.entries.forEachIndexed { index, planOption ->
                            SegmentedButton(
                                selected = formState.plan == planOption,
                                onClick = { onPlanChange(planOption) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = LicensePlan.entries.size
                                ),
                                modifier = Modifier.testTag("plan_${planOption.wireName.lowercase()}")
                            ) {
                                Text(planOption.arabicLabel)
                            }
                        }
                    }

                    AnimatedVisibility(visible = formState.plan == LicensePlan.TRIAL) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "مدة الفترة التجريبية",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(7 to "7 أيام", 14 to "14 يومًا", 30 to "30 يومًا").forEach { (days, label) ->
                                    FilterChip(
                                        selected = formState.trialDurationDays == days && formState.customTrialEndsAtMillis == null,
                                        onClick = { onSelectTrialDays(days) },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("trial_chip_$days")
                                    )
                                }
                                FilterChip(
                                    selected = formState.customTrialEndsAtMillis != null,
                                    onClick = { showDatePicker = true },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        val customLabel = formState.customTrialEndsAtMillis?.let {
                                            "حتى ${LicenseShareHelper.formatDateOnly(it)}"
                                        } ?: "تاريخ مخصص"
                                        Text(customLabel)
                                    },
                                    modifier = Modifier.testTag("trial_chip_custom")
                                )
                            }

                            var daysField by remember(formState.trialDurationDays, formState.customTrialEndsAtMillis) {
                                mutableStateOf(
                                    if (formState.customTrialEndsAtMillis == null && formState.trialDurationDays != null) {
                                        formState.trialDurationDays.toString()
                                    } else {
                                        ""
                                    }
                                )
                            }
                            OutlinedTextField(
                                value = daysField,
                                onValueChange = { raw ->
                                    val digits = raw.filter(Char::isDigit).take(4)
                                    daysField = digits
                                    val parsedDays = digits.toIntOrNull()
                                    if (parsedDays != null && parsedDays > 0) {
                                        onSelectTrialDays(parsedDays)
                                    }
                                },
                                label = { Text("عدد الأيام (مثال: 7، 14، 30، 90)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("trial_days_input")
                            )
                        }
                    }
                }
            }

            // 3. Customer Reference Archive Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "بيانات العميل (أرشيف المطور)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "تُحفظ محلياً في سجل المطور فقط ولا تُدرج في حمولة SLK1 الموقّعة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = formState.customerName,
                        onValueChange = onCustomerNameChange,
                        label = { Text("اسم العميل") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_name_input")
                    )

                    OutlinedTextField(
                        value = formState.customerPhone,
                        onValueChange = onCustomerPhoneChange,
                        label = { Text("رقم الواتساب / الهاتف (مثال: 9665XXXXXXXX)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_phone_input")
                    )

                    OutlinedTextField(
                        value = formState.notes,
                        onValueChange = onNotesChange,
                        label = { Text("السعر / ملاحظات") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_notes_input")
                    )
                }
            }

            Button(
                onClick = onIssueClick,
                enabled = vaultMetadata.hasKeyPair && formState.canIssue,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("issue_license_button")
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (formState.isIssuing) {
                        "جاري التوقيع..."
                    } else {
                        val kidLabel = vaultMetadata.activeKid ?: "SLK1"
                        "توقيع وإصدار الترخيص ($kidLabel)"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(72.dp))
        }
    }

    if (showDatePicker) {
        val initialMillis = remember { System.currentTimeMillis() + 7L * 24L * 3600L * 1000L }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = pickerState.selectedDateMillis
                        if (selected != null) {
                            // End of the chosen day in UTC (23:59:59)
                            val endOfDay = selected + (23L * 3600L + 59L * 60L + 59L) * 1000L
                            onSelectCustomTrialDate(endOfDay)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("اعتماد التاريخ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("إلغاء")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
