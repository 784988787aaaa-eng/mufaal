package com.example.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.crypto.DeviceCodeValidator
import com.example.crypto.LicensePlan
import com.example.crypto.QrMatrixGenerator
import com.example.data.IssuedLicenseEntity
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LicenseShareHelper {

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun readClipboardText(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    fun formatDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.US)
        return sdf.format(Date(millis))
    }

    fun formatDateOnly(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        return sdf.format(Date(millis))
    }

    fun buildWhatsAppActivationMessage(license: IssuedLicenseEntity): String {
        val planEnum = LicensePlan.fromWireName(license.plan)
        val greeting = if (license.customerName.isNotBlank()) {
            "مرحباً ${license.customerName.trim()}،"
        } else {
            "مرحباً،"
        }
        val planText = when (planEnum) {
            LicensePlan.LIFETIME -> "ترخيص دائم (مدى الحياة)"
            LicensePlan.TRIAL -> {
                val endText = license.expiresAt?.let { formatDateTime(it) } ?: "محددة"
                "ترخيص فترة تجريبية (ساري حتى: $endText)"
            }
        }
        return buildString {
            appendLine(greeting)
            appendLine("تم إصدار كود التفعيل الخاص بتطبيق *الدفتر الذكي*:")
            appendLine()
            appendLine("• نوع الخطة: $planText")
            appendLine("• كود الجهاز: `${license.deviceCode}`")
            appendLine("• رقم الترخيص: `${license.licenseId}`")
            appendLine()
            appendLine("*خطوات التفعيل داخل التطبيق:*")
            appendLine("1) افتح تطبيق الدفتر الذكي ← الإعدادات ← التفعيل.")
            appendLine("2) انسخ كود التفعيل التالي كاملاً والصقه في خانة التفعيل (أو استورد ملف `.slic` / امسح رمز QR).")
            appendLine("3) اضغط على *تفعيل الآن* (يعمل بدون إنترنت).")
            appendLine()
            appendLine("كود التفعيل:")
            append(license.token)
        }
    }

    fun sanitizePhoneForWhatsApp(rawPhone: String): String {
        val normalized = DeviceCodeValidator.normalizeInput(rawPhone)
        var digits = normalized.filter { it.isDigit() }
        if (digits.startsWith("00")) {
            digits = digits.drop(2)
        }
        return digits
    }

    fun openWhatsAppWithMessage(context: Context, rawPhone: String, message: String) {
        val digits = sanitizePhoneForWhatsApp(rawPhone)
        if (digits.length >= 7) {
            val uri = Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}")
            val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val started = runCatching { context.startActivity(viewIntent) }.isSuccess
            if (started) return
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(sendIntent, "إرسال رسالة التفعيل عبر").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareSlicFile(context: Context, licenseId: String, token: String) {
        val dir = ensureExportsDir(context)
        val file = File(dir, "SMARTLEDGER_${licenseId}.slic")
        file.writeText(token, StandardCharsets.UTF_8)
        shareFileUri(
            context = context,
            file = file,
            mimeType = "application/octet-stream",
            chooserTitle = "مشاركة ملف الترخيص (.slic)"
        )
    }

    fun sharePublicKeyPemFile(context: Context, publicKeyPem: String) {
        val dir = ensureExportsDir(context)
        val file = File(dir, "license_local_ec_public.pem")
        file.writeText(publicKeyPem, StandardCharsets.UTF_8)
        shareFileUri(
            context = context,
            file = file,
            mimeType = "application/x-pem-file",
            chooserTitle = "مشاركة المفتاح العام (license_local_ec_public.pem)"
        )
    }

    fun renderQrBitmap(token: String, sizePx: Int = 768): Bitmap {
        val matrix = QrMatrixGenerator.generate(token, quietZone = 2)
        val modules = matrix.size
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val cellSize = sizePx.toFloat() / modules.toFloat()
        val paint = Paint().apply {
            color = Color.parseColor("#061712")
            style = Paint.Style.FILL
            isAntiAlias = false
        }
        for (y in 0 until modules) {
            for (x in 0 until modules) {
                if (matrix.isDark(x, y)) {
                    val left = x * cellSize
                    val top = y * cellSize
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, paint)
                }
            }
        }
        return bitmap
    }

    fun shareQrBitmap(context: Context, licenseId: String, bitmap: Bitmap) {
        val dir = ensureExportsDir(context)
        val file = File(dir, "SMARTLEDGER_QR_${licenseId}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        shareFileUri(
            context = context,
            file = file,
            mimeType = "image/png",
            chooserTitle = "مشاركة رمز QR للترخيص"
        )
    }

    private fun ensureExportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun shareFileUri(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
