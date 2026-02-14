package com.nfccopy.nfc.expert

/**
 * Manages known MIFARE Classic keys for authentication attempts.
 * Contains commonly used default keys and allows adding custom keys.
 *
 * MIFARE Classic uses Crypto1 cipher with 48-bit keys.
 * Each sector has two keys: Key A and Key B.
 */
class MifareKeyManager {

    private val knownKeys = mutableListOf<ByteArray>()

    init {
        // Default factory keys
        addDefaultKeys()
    }

    private fun addDefaultKeys() {
        knownKeys.addAll(
            listOf(
                // Factory default keys
                byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
                byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()),
                byteArrayOf(0xB0.toByte(), 0xB1.toByte(), 0xB2.toByte(), 0xB3.toByte(), 0xB4.toByte(), 0xB5.toByte()),
                byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte()),
                byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),

                // MIFARE Application Directory (MAD)
                byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()),

                // NFC Forum
                byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte()),

                // Common transport/access keys
                byteArrayOf(0x4D.toByte(), 0x3A.toByte(), 0x99.toByte(), 0xC3.toByte(), 0x51.toByte(), 0xDD.toByte()),
                byteArrayOf(0x1A.toByte(), 0x98.toByte(), 0x2C.toByte(), 0x7E.toByte(), 0x45.toByte(), 0x9A.toByte()),
                byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()),
                byteArrayOf(0x71.toByte(), 0x4C.toByte(), 0x5C.toByte(), 0x88.toByte(), 0x6E.toByte(), 0x97.toByte()),
                byteArrayOf(0x58.toByte(), 0x7E.toByte(), 0xE5.toByte(), 0xF9.toByte(), 0x35.toByte(), 0x0F.toByte()),
                byteArrayOf(0xA2.toByte(), 0x29.toByte(), 0x3D.toByte(), 0x96.toByte(), 0x37.toByte(), 0x84.toByte()),
                byteArrayOf(0x53.toByte(), 0x3C.toByte(), 0xB6.toByte(), 0xC7.toByte(), 0x23.toByte(), 0xF6.toByte()),
                byteArrayOf(0x8F.toByte(), 0xD0.toByte(), 0xA4.toByte(), 0xF2.toByte(), 0x56.toByte(), 0xE9.toByte()),

                // Known public transport keys
                byteArrayOf(0xFC.toByte(), 0x00.toByte(), 0x01.toByte(), 0x87.toByte(), 0x78.toByte(), 0xF7.toByte()),
                byteArrayOf(0x09.toByte(), 0x12.toByte(), 0x5D.toByte(), 0xA4.toByte(), 0x8B.toByte(), 0x4A.toByte()),
                byteArrayOf(0x19.toByte(), 0x19.toByte(), 0x53.toByte(), 0x19.toByte(), 0x19.toByte(), 0x53.toByte()),
                byteArrayOf(0x34.toByte(), 0x56.toByte(), 0x78.toByte(), 0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte()),
            )
        )
    }

    fun addKey(key: ByteArray) {
        if (key.size == 6 && knownKeys.none { it.contentEquals(key) }) {
            knownKeys.add(key)
        }
    }

    fun addKeyFromHex(hexKey: String) {
        val clean = hexKey.replace(" ", "").replace(":", "")
        if (clean.length == 12) {
            try {
                val key = ByteArray(6) { i ->
                    clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
                addKey(key)
            } catch (_: Exception) {
                // Invalid hex
            }
        }
    }

    fun getAllKeys(): List<ByteArray> = knownKeys.toList()

    fun getKeyCount(): Int = knownKeys.size

    fun clearCustomKeys() {
        knownKeys.clear()
        addDefaultKeys()
    }
}
