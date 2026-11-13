package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.OfflineSecurityBadge
import com.example.ui.theme.CryptoMonoFamily
import com.example.ui.viewmodel.AuthUiState

@Composable
fun VaultLockScreen(
    authState: AuthUiState,
    biometricAvailable: Boolean,
    onSetupPin: (pin: String, enableBiometric: Boolean) -> Unit,
    onVerifyPin: (pin: String) -> Boolean,
    onRequestBiometricPrompt: () -> Unit,
    onClearError: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var firstSetupPin by remember { mutableStateOf<String?>(null) }
    var setupMismatchError by remember { mutableStateOf<String?>(null) }
    var enableBiometric by remember(biometricAvailable) { mutableStateOf(biometricAvailable) }

    val isSetupMode = !authState.isPinConfigured

    LaunchedEffect(isSetupMode, authState.isBiometricEnabled, biometricAvailable) {
        if (!isSetupMode && authState.isBiometricEnabled && biometricAvailable) {
            onRequestBiometricPrompt()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        RoundedCornerShape(22.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vault_banner_1794598050139),
                    contentDescription = "شعار خزنة التوقيع",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start
                ) {
                    OfflineSecurityBadge()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isSetupMode) Icons.Default.Security else Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "مُفعّل الدفتر الذكي",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (isSetupMode) {
                                    "إعداد قفل المطور (PIN) لأول مرة"
                                } else {
                                    "خزنة مفاتيح التوقيع محمية"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Text(
                text = when {
                    isSetupMode && firstSetupPin == null -> "اختر رمز PIN من 4 إلى 8 أرقام لحماية الخزنة"
                    isSetupMode && firstSetupPin != null -> "أعد إدخال رمز PIN للتأكيد"
                    else -> "أدخل رمز PIN لفتح خزنة التوقيع"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            // PIN dots display (LTR so dots progress consistently)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    val dotCount = maxOf(4, pinInput.length)
                    for (i in 0 until dotCount) {
                        val filled = i < pinInput.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }

            val displayedError = setupMismatchError ?: authState.pinError
            AnimatedVisibility(visible = displayedError != null) {
                Text(
                    text = displayedError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            if (isSetupMode && biometricAvailable) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { enableBiometric = !enableBiometric }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = enableBiometric,
                        onCheckedChange = { enableBiometric = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "تفعيل فتح الخزنة ببصمة الإصبع (BiometricPrompt)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Tactile Numeric Keypad in LTR order (1 2 3 / 4 5 6 / 7 8 9 / Back 0 Confirm)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { digit ->
                                PinDigitKey(
                                    label = digit,
                                    onClick = {
                                        onClearError()
                                        setupMismatchError = null
                                        if (pinInput.length < 8) {
                                            pinInput += digit
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Backspace
                        Surface(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onClearError()
                                    setupMismatchError = null
                                    if (pinInput.isNotEmpty()) {
                                        pinInput = pinInput.dropLast(1)
                                    }
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "مسح رقم",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        PinDigitKey(
                            label = "0",
                            onClick = {
                                onClearError()
                                setupMismatchError = null
                                if (pinInput.length < 8) {
                                    pinInput += "0"
                                }
                            }
                        )

                        // Confirm action key
                        val canSubmit = pinInput.length >= 4
                        Surface(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .testTag("pin_submit_key")
                                .clickable(enabled = canSubmit) {
                                    if (isSetupMode) {
                                        val first = firstSetupPin
                                        if (first == null) {
                                            firstSetupPin = pinInput
                                            pinInput = ""
                                        } else if (first == pinInput) {
                                            onSetupPin(pinInput, enableBiometric)
                                        } else {
                                            setupMismatchError = "الرمزان غير متطابقين، أعد المحاولة"
                                            firstSetupPin = null
                                            pinInput = ""
                                        }
                                    } else {
                                        val ok = onVerifyPin(pinInput)
                                        if (!ok) {
                                            pinInput = ""
                                        }
                                    }
                                },
                            color = if (canSubmit) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "تأكيد رمز PIN",
                                    tint = if (canSubmit) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (!isSetupMode && authState.isBiometricEnabled && biometricAvailable) {
                OutlinedButton(
                    onClick = onRequestBiometricPrompt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("biometric_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("فتح الخزنة ببصمة الإصبع")
                }
            }

            if (isSetupMode && firstSetupPin != null) {
                Button(
                    onClick = {
                        firstSetupPin = null
                        pinInput = ""
                        setupMismatchError = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تغيير الرمز الأول")
                }
            }
        }
    }
}

@Composable
private fun PinDigitKey(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .testTag("pin_key_$label")
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = CryptoMonoFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
