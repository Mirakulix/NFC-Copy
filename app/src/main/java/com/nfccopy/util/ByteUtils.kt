package com.nfccopy.util

object ByteUtils {
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace(":", "")
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun bytesToHexFormatted(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    fun bytesToAscii(bytes: ByteArray): String {
        return bytes.map { b ->
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() || c.isWhitespace() || c in "!@#\$%^&*()_+-=[]{}|;':\",./<>?") c else '.'
        }.joinToString("")
    }
}
