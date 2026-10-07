package com.rihal.roompanel.ui.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Encode [text] as a QR matrix (with a 2-module quiet zone), or null if it can't be encoded. */
fun qrMatrix(text: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )
}.getOrNull()

/** Black-on-white QR (scanners need the contrast whatever the panel colour behind it). */
@Composable
fun QrCode(text: String, size: Dp, description: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { qrMatrix(text) } ?: return
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        drawRect(Color.White)
        val cell = this.size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}
