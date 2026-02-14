package com.nfccopy.nfc.expert

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import com.nfccopy.util.ByteUtils

/**
 * Expert NFC writer that can write to specific blocks/pages,
 * modify sector trailers, and change access keys.
 */
class ExpertNfcWriter(private val keyManager: MifareKeyManager = MifareKeyManager()) {

    data class ExpertWriteResult(
        val success: Boolean,
        val message: String,
        val blocksWritten: Int = 0,
        val blocksFailed: Int = 0,
        val details: List<String> = emptyList()
    )

    /**
     * Write data to a specific block on a MIFARE Classic tag.
     * Requires authentication with the correct key for the sector.
     */
    fun writeBlock(
        tag: Tag,
        sectorIndex: Int,
        blockIndex: Int,
        data: ByteArray,
        keyA: ByteArray? = null,
        keyB: ByteArray? = null
    ): ExpertWriteResult {
        if (data.size != 16) {
            return ExpertWriteResult(false, "Blockdaten müssen genau 16 Bytes sein (aktuell: ${data.size})")
        }

        val mifare = MifareClassic.get(tag)
            ?: return ExpertWriteResult(false, "Kein MIFARE Classic Tag")

        try {
            mifare.connect()

            // Check if block 0 (manufacturer block) is targeted
            if (blockIndex == 0) {
                return ExpertWriteResult(false, "Block 0 (Herstellerblock) kann nicht beschrieben werden")
            }

            // Try to authenticate
            val authenticated = authenticateSector(mifare, sectorIndex, keyA, keyB)
            if (!authenticated) {
                return ExpertWriteResult(false, "Authentifizierung fehlgeschlagen für Sektor $sectorIndex")
            }

            mifare.writeBlock(blockIndex, data)
            return ExpertWriteResult(true, "Block $blockIndex erfolgreich geschrieben", blocksWritten = 1)
        } catch (e: Exception) {
            return ExpertWriteResult(false, "Fehler beim Schreiben: ${e.message}")
        } finally {
            try { mifare.close() } catch (_: Exception) {}
        }
    }

    /**
     * Write a complete sector dump to a MIFARE Classic tag.
     * Skips block 0 and sector trailers unless explicitly included.
     */
    fun writeSector(
        tag: Tag,
        sectorIndex: Int,
        blockData: Map<Int, ByteArray>,
        keyA: ByteArray? = null,
        keyB: ByteArray? = null,
        writeSectorTrailer: Boolean = false
    ): ExpertWriteResult {
        val mifare = MifareClassic.get(tag)
            ?: return ExpertWriteResult(false, "Kein MIFARE Classic Tag")

        try {
            mifare.connect()

            val authenticated = authenticateSector(mifare, sectorIndex, keyA, keyB)
            if (!authenticated) {
                return ExpertWriteResult(false, "Authentifizierung fehlgeschlagen für Sektor $sectorIndex")
            }

            val firstBlock = mifare.sectorToBlock(sectorIndex)
            val blockCountInSector = mifare.getBlockCountInSector(sectorIndex)
            val lastBlockInSector = firstBlock + blockCountInSector - 1
            var written = 0
            var failed = 0
            val details = mutableListOf<String>()

            for ((blockOffset, data) in blockData) {
                val absoluteBlock = firstBlock + blockOffset
                if (data.size != 16) {
                    details.add("Block $absoluteBlock: Übersprungen (ungültige Datenlänge: ${data.size})")
                    failed++
                    continue
                }
                if (absoluteBlock == 0) {
                    details.add("Block 0: Übersprungen (Herstellerblock)")
                    continue
                }
                if (absoluteBlock == lastBlockInSector && !writeSectorTrailer) {
                    details.add("Block $absoluteBlock: Übersprungen (Sektor-Trailer, nicht aktiviert)")
                    continue
                }

                try {
                    mifare.writeBlock(absoluteBlock, data)
                    details.add("Block $absoluteBlock: Geschrieben (${ByteUtils.bytesToHex(data)})")
                    written++
                } catch (e: Exception) {
                    details.add("Block $absoluteBlock: Fehler (${e.message})")
                    failed++
                }
            }

            return ExpertWriteResult(
                failed == 0, "$written Blöcke geschrieben, $failed fehlgeschlagen",
                written, failed, details
            )
        } catch (e: Exception) {
            return ExpertWriteResult(false, "Fehler: ${e.message}")
        } finally {
            try { mifare.close() } catch (_: Exception) {}
        }
    }

    /**
     * Change the keys and access bits of a MIFARE Classic sector.
     * WARNING: Setting wrong access bits can permanently lock the sector!
     */
    fun changeSectorKeys(
        tag: Tag,
        sectorIndex: Int,
        currentKeyA: ByteArray? = null,
        currentKeyB: ByteArray? = null,
        newKeyA: ByteArray,
        newKeyB: ByteArray,
        accessBits: ByteArray = byteArrayOf(0xFF.toByte(), 0x07.toByte(), 0x80.toByte(), 0x69.toByte())
    ): ExpertWriteResult {
        if (newKeyA.size != 6 || newKeyB.size != 6) {
            return ExpertWriteResult(false, "Schlüssel müssen 6 Bytes lang sein")
        }
        if (accessBits.size != 4) {
            return ExpertWriteResult(false, "Zugriffsbits müssen 4 Bytes lang sein")
        }

        val mifare = MifareClassic.get(tag)
            ?: return ExpertWriteResult(false, "Kein MIFARE Classic Tag")

        try {
            mifare.connect()

            val authenticated = authenticateSector(mifare, sectorIndex, currentKeyA, currentKeyB)
            if (!authenticated) {
                return ExpertWriteResult(false, "Authentifizierung fehlgeschlagen")
            }

            // Build sector trailer: KeyA (6) + Access Bits (4) + KeyB (6) = 16 bytes
            val trailer = newKeyA + accessBits + newKeyB

            val lastBlock = mifare.sectorToBlock(sectorIndex) + mifare.getBlockCountInSector(sectorIndex) - 1
            mifare.writeBlock(lastBlock, trailer)

            return ExpertWriteResult(true, "Schlüssel für Sektor $sectorIndex geändert")
        } catch (e: Exception) {
            return ExpertWriteResult(false, "Fehler beim Schlüsselwechsel: ${e.message}")
        } finally {
            try { mifare.close() } catch (_: Exception) {}
        }
    }

    /**
     * Write to a specific page on a MIFARE Ultralight tag.
     */
    fun writeUltralightPage(
        tag: Tag,
        pageNumber: Int,
        data: ByteArray
    ): ExpertWriteResult {
        if (data.size != 4) {
            return ExpertWriteResult(false, "Seitendaten müssen genau 4 Bytes sein (aktuell: ${data.size})")
        }
        if (pageNumber < 4) {
            return ExpertWriteResult(false, "Seiten 0-3 sind schreibgeschützt (UID/Konfiguration)")
        }

        val ultralight = MifareUltralight.get(tag)
            ?: return ExpertWriteResult(false, "Kein MIFARE Ultralight Tag")

        try {
            ultralight.connect()
            ultralight.writePage(pageNumber, data)
            return ExpertWriteResult(true, "Seite $pageNumber geschrieben (${ByteUtils.bytesToHex(data)})", blocksWritten = 1)
        } catch (e: Exception) {
            return ExpertWriteResult(false, "Fehler beim Schreiben: ${e.message}")
        } finally {
            try { ultralight.close() } catch (_: Exception) {}
        }
    }

    /**
     * Send raw NFC-A (ISO 14443-3A) transceive commands.
     */
    fun sendRawCommand(tag: Tag, command: ByteArray): Pair<ByteArray?, String> {
        val nfcA = NfcA.get(tag) ?: return null to "Kein NFC-A Tag"

        return try {
            nfcA.connect()
            nfcA.timeout = 2000
            val response = nfcA.transceive(command)
            response to ByteUtils.bytesToHex(response)
        } catch (e: Exception) {
            null to "Fehler: ${e.message}"
        } finally {
            try { nfcA.close() } catch (_: Exception) {}
        }
    }

    private fun authenticateSector(
        mifare: MifareClassic,
        sectorIndex: Int,
        keyA: ByteArray?,
        keyB: ByteArray?
    ): Boolean {
        // Try provided keys first
        if (keyA != null) {
            try {
                if (mifare.authenticateSectorWithKeyA(sectorIndex, keyA)) return true
            } catch (_: Exception) {}
        }
        if (keyB != null) {
            try {
                if (mifare.authenticateSectorWithKeyB(sectorIndex, keyB)) return true
            } catch (_: Exception) {}
        }

        // Fall back to key dictionary
        for (key in keyManager.getAllKeys()) {
            try {
                if (mifare.authenticateSectorWithKeyA(sectorIndex, key)) return true
            } catch (_: Exception) {}
            try {
                if (mifare.authenticateSectorWithKeyB(sectorIndex, key)) return true
            } catch (_: Exception) {}
        }

        return false
    }
}
