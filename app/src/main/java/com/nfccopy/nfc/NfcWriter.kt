package com.nfccopy.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.nfccopy.model.CardType
import com.nfccopy.model.NfcCard
import com.nfccopy.util.ByteUtils

class NfcWriter {

    sealed class WriteResult {
        data object Success : WriteResult()
        data class Error(val message: String) : WriteResult()
        data class Partial(val sectorsWritten: Int, val totalSectors: Int) : WriteResult()
    }

    fun writeToTag(tag: Tag, sourceCard: NfcCard): WriteResult {
        return when (sourceCard.type) {
            CardType.MIFARE_CLASSIC -> writeMifareClassic(tag, sourceCard)
            CardType.MIFARE_ULTRALIGHT -> writeMifareUltralight(tag, sourceCard)
            CardType.NDEF -> writeNdef(tag, sourceCard)
            else -> WriteResult.Error("Schreiben für ${sourceCard.type.displayName} nicht unterstützt")
        }
    }

    private fun writeMifareClassic(tag: Tag, sourceCard: NfcCard): WriteResult {
        val mifare = MifareClassic.get(tag) ?: return WriteResult.Error("Zielkarte ist kein MIFARE Classic Tag")

        try {
            mifare.connect()
            var sectorsWritten = 0

            for ((sectorIndex, blocks) in sourceCard.sectorData) {
                if (blocks.firstOrNull() == "AUTH_FAILED" || blocks.firstOrNull() == "ERROR") continue

                val authenticated = mifare.authenticateSectorWithKeyA(sectorIndex, MifareClassic.KEY_DEFAULT)
                        || mifare.authenticateSectorWithKeyB(sectorIndex, MifareClassic.KEY_DEFAULT)

                if (!authenticated) continue

                val firstBlock = mifare.sectorToBlock(sectorIndex)
                val lastBlockInSector = firstBlock + mifare.getBlockCountInSector(sectorIndex) - 1

                for ((blockOffset, hexData) in blocks.withIndex()) {
                    if (hexData == "ERROR" || hexData == "AUTH_FAILED") continue
                    val blockIndex = firstBlock + blockOffset

                    // Skip block 0 (manufacturer block) and sector trailer blocks
                    if (blockIndex == 0 || blockIndex == lastBlockInSector) continue

                    try {
                        val data = ByteUtils.hexToBytes(hexData)
                        if (data.size == 16) {
                            mifare.writeBlock(blockIndex, data)
                        }
                    } catch (_: Exception) {
                        // Continue with next block
                    }
                }
                sectorsWritten++
            }

            return if (sectorsWritten > 0) {
                if (sectorsWritten == sourceCard.sectorData.size) WriteResult.Success
                else WriteResult.Partial(sectorsWritten, sourceCard.sectorData.size)
            } else {
                WriteResult.Error("Keine Sektoren konnten geschrieben werden")
            }
        } catch (e: Exception) {
            return WriteResult.Error("Fehler beim Schreiben: ${e.message}")
        } finally {
            try { mifare.close() } catch (_: Exception) {}
        }
    }

    private fun writeMifareUltralight(tag: Tag, sourceCard: NfcCard): WriteResult {
        val ultralight = MifareUltralight.get(tag) ?: return WriteResult.Error("Zielkarte ist kein MIFARE Ultralight Tag")

        try {
            ultralight.connect()
            val pages = sourceCard.sectorData[0] ?: return WriteResult.Error("Keine Daten zum Schreiben")

            for ((index, hexData) in pages.withIndex()) {
                val pageNumber = 4 + index // Start writing from page 4 (user data area)
                try {
                    val fullData = ByteUtils.hexToBytes(hexData)
                    // Each page is 4 bytes, split the data
                    for (offset in fullData.indices step 4) {
                        val pageData = fullData.copyOfRange(offset, minOf(offset + 4, fullData.size))
                        if (pageData.size == 4) {
                            ultralight.writePage(pageNumber + (offset / 4), pageData)
                        }
                    }
                } catch (_: Exception) {
                    // Continue with next page
                }
            }

            return WriteResult.Success
        } catch (e: Exception) {
            return WriteResult.Error("Fehler beim Schreiben: ${e.message}")
        } finally {
            try { ultralight.close() } catch (_: Exception) {}
        }
    }

    private fun writeNdef(tag: Tag, sourceCard: NfcCard): WriteResult {
        if (sourceCard.rawData.isBlank()) return WriteResult.Error("Keine NDEF-Daten zum Schreiben")

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                if (!ndef.isWritable) return WriteResult.Error("Tag ist nicht beschreibbar")

                val bytes = ByteUtils.hexToBytes(sourceCard.rawData)
                val message = android.nfc.NdefMessage(bytes)

                if (ndef.maxSize < message.toByteArray().size) {
                    return WriteResult.Error("Tag hat nicht genug Speicher")
                }

                ndef.writeNdefMessage(message)
                return WriteResult.Success
            } catch (e: Exception) {
                return WriteResult.Error("Fehler beim Schreiben: ${e.message}")
            } finally {
                try { ndef.close() } catch (_: Exception) {}
            }
        }

        val formatable = NdefFormatable.get(tag) ?: return WriteResult.Error("Tag kann nicht formatiert werden")
        try {
            formatable.connect()
            val bytes = ByteUtils.hexToBytes(sourceCard.rawData)
            val message = android.nfc.NdefMessage(bytes)
            formatable.format(message)
            return WriteResult.Success
        } catch (e: Exception) {
            return WriteResult.Error("Fehler beim Formatieren: ${e.message}")
        } finally {
            try { formatable.close() } catch (_: Exception) {}
        }
    }
}
