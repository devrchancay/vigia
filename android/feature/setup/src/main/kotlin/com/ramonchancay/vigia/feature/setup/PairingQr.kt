package com.ramonchancay.vigia.feature.setup

import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** What the QR encodes: `vigia://pair?url=<base url>&token=<token>`, read by the vigia CLI (milestone M5). */
internal object PairingQr {
    fun uri(baseUrl: String, token: String): String = Uri.Builder()
        .scheme("vigia")
        .authority("pair")
        .appendQueryParameter("url", baseUrl)
        .appendQueryParameter("token", token)
        .build()
        .toString()

    /** Square matrix of modules: [size] per side, row-major [dark] flags. */
    class Matrix(val size: Int, val dark: BooleanArray)

    /**
     * Encodes at the smallest size the content needs (requested size 0): one entry per module, so the drawing
     * scales it and nothing is cropped however long the URL and token are.
     */
    fun matrix(content: String): Matrix {
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        val size = bits.width
        return Matrix(size, BooleanArray(size * size) { bits[it % size, it / size] })
    }
}
