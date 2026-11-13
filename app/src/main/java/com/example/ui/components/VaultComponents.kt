package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.QrMatrix
import com.example.crypto.QrMatrixGenerator
import com.example.ui.theme.CryptoMonoFamily

@Composable
fun OfflineSecurityBadge(
    modifier: Modifier = Modifier,
    text: String = "أوفلاين 100% • ECDSA P-256"
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun MonospaceCodeBox(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onCopy: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                SelectionContainer(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = text,
                        fontFamily = CryptoMonoFamily,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        maxLines = maxLines,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (onCopy != null) {
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "نسخ",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun PasswordBackupDialog(
    title: String,
    subtitle: String,
    confirmPasswordRequired: Boolean,
    confirmButtonLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorText = null
                    },
                    label = { Text("كلمة المرور (8 أحرف على الأقل)") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "إظهار/إخفاء كلمة المرور"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (confirmPasswordRequired) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            errorText = null
                        },
                        label = { Text("تأكيد كلمة المرور") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        password.length < 8 -> errorText = "يجب أن لا تقل كلمة المرور عن 8 أحرف"
                        confirmPasswordRequired && password != confirmPassword ->
                            errorText = "كلمتا المرور غير متطابقتين"
                        else -> onConfirm(password)
                    }
                }
            ) {
                Text(confirmButtonLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun QrCodePreviewDialog(
    token: String,
    licenseId: String,
    deviceCode: String,
    onShareQrImage: () -> Unit,
    onDismiss: () -> Unit
) {
    val qrMatrix: QrMatrix? = remember(token) {
        runCatching { QrMatrixGenerator.generate(token, quietZone = 2) }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "رمز QR للتفعيل السريع",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "$licenseId • $deviceCode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (qrMatrix != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(12.dp)
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val modules = qrMatrix.size
                            val cellWidth = size.width / modules.toFloat()
                            val cellHeight = size.height / modules.toFloat()
                            val darkColor = Color(0xFF061712)
                            for (y in 0 until modules) {
                                for (x in 0 until modules) {
                                    if (qrMatrix.isDark(x, y)) {
                                        drawRect(
                                            color = darkColor,
                                            topLeft = Offset(x * cellWidth, y * cellHeight),
                                            size = Size(cellWidth + 0.5f, cellHeight + 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "تعذر توليد مصفوفة QR",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = "يحتوي هذا الرمز على التوقيع الرقمي SLK1 كاملاً ويمكن مسحه من تطبيق الدفتر الذكي دون إنترنت.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(onClick = onShareQrImage) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("مشاركة صورة QR")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}
