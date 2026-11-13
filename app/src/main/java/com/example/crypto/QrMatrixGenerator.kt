package com.example.crypto

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

data class QrMatrix(
    val size: Int,
    val modules: BooleanArray
) {
    fun isDark(x: Int, y: Int): Boolean = modules[y * size + x]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrMatrix) return false
        return size == other.size && modules.contentEquals(other.modules)
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + modules.contentHashCode()
        return result
    }
}

/**
 * Generates QR Code matrices 100% offline using ZXing.
 */
object QrMatrixGenerator {
    fun generate(content: String, quietZone: Int = 2): QrMatrix {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to quietZone
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val size = maxOf(width, height)
        val modules = BooleanArray(size * size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                modules[y * size + x] = bitMatrix.get(x, y)
            }
        }
        return QrMatrix(size = size, modules = modules)
    }
}
