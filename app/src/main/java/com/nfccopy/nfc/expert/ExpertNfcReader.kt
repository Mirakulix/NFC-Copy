package com.nfccopy.nfc.expert

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import com.nfccopy.util.ByteUtils

/**
 * Expert NFC reader that attempts to read all data from NFC tags,
 * including encrypted sectors using known key dictionaries.
 */
class ExpertNfcReader(private val keyManager: MifareKeyManager = MifareKeyManager()) {

    data class ExpertReadResult(
        val uid: String,
        val technology: String,
        val sectorResults: Map<Int, SectorResult>,
        val hardwareInfo: HardwareInfo,
        val rawDump: String,
        val warnings: List<String>
    )

    @Suppress("ArrayInDataClass")
    data class SectorResult(
        val sectorIndex: Int,
        val blocks: List<BlockResult>,
        val keyA: ByteArray? = null,
        val keyB: ByteArray? = null,
        val accessBits: String = "",
        val isAuthenticated: Boolean = false
    )

    @Suppress("ArrayInDataClass")
    data class BlockResult(
        val blockIndex: Int,
        val data: ByteArray?,
        val dataHex: String,
        val isReadable: Boolean,
        val error: String? = null
    )

    data class HardwareInfo(
        val uid: String,
        val uidLength: Int,
        val atqa: String,
        val sak: String,
        val ats: String,
        val chipType: String,
        val sectorCount: Int,
        val blockCount: Int,
        val totalSize: Int,
        val technologies: List<String>,
        val isWritable: Boolean,
        val additionalInfo: Map<String, String>
    )

    /**
     * Performs deep read of a MIFARE Classic tag using all known keys.
     * Attempts Key A and Key B authentication with every key in the dictionary.
     */
    fun expertReadMifareClassic(tag: Tag): ExpertReadResult {
        val uid = ByteUtils.bytesToHex(tag.id)
        val techList = tag.techList.toList()
        val technology = techList.joinToString(", ") { it.substringAfterLast(".") }
        val warnings = mutableListOf<String>()

        val mifare = MifareClassic.get(tag)
            ?: return ExpertReadResult(uid, technology, emptyMap(), getBasicHardwareInfo(tag), "", listOf("Kein MIFARE Classic Tag"))

        val sectorResults = mutableMapOf<Int, SectorResult>()
        val rawDumpBuilder = StringBuilder()

        try {
            mifare.connect()

            val hwInfo = HardwareInfo(
                uid = uid,
                uidLength = tag.id.size,
                atqa = NfcA.get(tag)?.atqa?.let { ByteUtils.bytesToHex(it) } ?: "",
                sak = NfcA.get(tag)?.sak?.let { "%02X".format(it.toInt()) } ?: "",
                ats = "",
                chipType = getMifareClassicType(mifare),
                sectorCount = mifare.sectorCount,
                blockCount = mifare.blockCount,
                totalSize = mifare.size,
                technologies = techList.map { it.substringAfterLast(".") },
                isWritable = true,
                additionalInfo = mapOf(
                    "Typ" to getMifareClassicType(mifare),
                    "Größe" to "${mifare.size} Bytes",
                    "Sektoren" to "${mifare.sectorCount}",
                    "Blöcke" to "${mifare.blockCount}"
                )
            )

            for (sectorIndex in 0 until mifare.sectorCount) {
                val blocks = mutableListOf<BlockResult>()
                var foundKeyA: ByteArray? = null
                var foundKeyB: ByteArray? = null
                var authenticated = false

                // Try all known keys for Key A
                for (key in keyManager.getAllKeys()) {
                    try {
                        if (mifare.authenticateSectorWithKeyA(sectorIndex, key)) {
                            foundKeyA = key.copyOf()
                            authenticated = true
                            break
                        }
                    } catch (_: Exception) {
                        // Key didn't work, try next
                    }
                }

                // Try all known keys for Key B
                for (key in keyManager.getAllKeys()) {
                    try {
                        if (mifare.authenticateSectorWithKeyB(sectorIndex, key)) {
                            foundKeyB = key.copyOf()
                            if (!authenticated) authenticated = true
                            break
                        }
                    } catch (_: Exception) {
                        // Key didn't work, try next
                    }
                }

                if (authenticated) {
                    // Re-authenticate with the found key to read
                    val reAuth = if (foundKeyA != null) {
                        mifare.authenticateSectorWithKeyA(sectorIndex, foundKeyA)
                    } else if (foundKeyB != null) {
                        mifare.authenticateSectorWithKeyB(sectorIndex, foundKeyB)
                    } else false

                    if (reAuth) {
                        val firstBlock = mifare.sectorToBlock(sectorIndex)
                        val blockCount = mifare.getBlockCountInSector(sectorIndex)

                        for (blockOffset in 0 until blockCount) {
                            val blockIndex = firstBlock + blockOffset
                            try {
                                val data = mifare.readBlock(blockIndex)
                                val hex = ByteUtils.bytesToHex(data)
                                blocks.add(BlockResult(blockIndex, data, hex, true))
                                rawDumpBuilder.append(hex)
                            } catch (e: Exception) {
                                blocks.add(BlockResult(blockIndex, null, "", false, e.message))
                            }
                        }

                        // Parse access bits from sector trailer (last block)
                        val trailerBlock = blocks.lastOrNull()
                        val accessBits = if (trailerBlock?.data != null && trailerBlock.data.size >= 16) {
                            parseAccessBits(trailerBlock.data)
                        } else ""

                        sectorResults[sectorIndex] = SectorResult(
                            sectorIndex, blocks, foundKeyA, foundKeyB, accessBits, true
                        )
                    }
                } else {
                    warnings.add("Sektor $sectorIndex: Keine passenden Schlüssel gefunden (${keyManager.getKeyCount()} Schlüssel getestet)")
                    sectorResults[sectorIndex] = SectorResult(sectorIndex, emptyList(), null, null, "", false)
                }
            }

            return ExpertReadResult(uid, technology, sectorResults, hwInfo, rawDumpBuilder.toString(), warnings)
        } catch (e: Exception) {
            warnings.add("Fehler: ${e.message}")
            return ExpertReadResult(uid, technology, sectorResults, getBasicHardwareInfo(tag), rawDumpBuilder.toString(), warnings)
        } finally {
            try { mifare.close() } catch (_: Exception) {}
        }
    }

    /**
     * Deep read of MIFARE Ultralight tags - reads all pages including
     * configuration and lock pages.
     */
    fun expertReadMifareUltralight(tag: Tag): ExpertReadResult {
        val uid = ByteUtils.bytesToHex(tag.id)
        val techList = tag.techList.toList()
        val technology = techList.joinToString(", ") { it.substringAfterLast(".") }
        val warnings = mutableListOf<String>()
        val sectorResults = mutableMapOf<Int, SectorResult>()
        val rawDumpBuilder = StringBuilder()

        val ultralight = MifareUltralight.get(tag)
            ?: return ExpertReadResult(uid, technology, emptyMap(), getBasicHardwareInfo(tag), "", listOf("Kein MIFARE Ultralight Tag"))

        try {
            ultralight.connect()

            val ultralightType = when (ultralight.type) {
                MifareUltralight.TYPE_ULTRALIGHT -> "MIFARE Ultralight (64 Bytes)"
                MifareUltralight.TYPE_ULTRALIGHT_C -> "MIFARE Ultralight C (192 Bytes)"
                else -> "Unbekannt"
            }

            val maxPages = when (ultralight.type) {
                MifareUltralight.TYPE_ULTRALIGHT -> 16
                MifareUltralight.TYPE_ULTRALIGHT_C -> 48
                else -> 64 // Try to read as many as possible
            }

            val blocks = mutableListOf<BlockResult>()
            for (page in 0 until maxPages) {
                try {
                    val data = ultralight.readPages(page) // Reads 4 pages (16 bytes) at once
                    // Only take the first 4 bytes (1 page)
                    val pageData = data.copyOfRange(0, minOf(4, data.size))
                    val hex = ByteUtils.bytesToHex(pageData)
                    blocks.add(BlockResult(page, pageData, hex, true))
                    rawDumpBuilder.append(hex)
                } catch (e: Exception) {
                    blocks.add(BlockResult(page, null, "", false, e.message))
                    // Stop reading if we hit a protected area
                    if (e.message?.contains("Transceive failed") == true) {
                        warnings.add("Seite $page: Lesevorgang blockiert (möglicherweise geschützt)")
                        break
                    }
                }
            }

            val hwInfo = HardwareInfo(
                uid = uid,
                uidLength = tag.id.size,
                atqa = NfcA.get(tag)?.atqa?.let { ByteUtils.bytesToHex(it) } ?: "",
                sak = NfcA.get(tag)?.sak?.let { "%02X".format(it.toInt()) } ?: "",
                ats = "",
                chipType = ultralightType,
                sectorCount = 1,
                blockCount = blocks.size,
                totalSize = blocks.size * 4,
                technologies = techList.map { it.substringAfterLast(".") },
                isWritable = true,
                additionalInfo = mapOf(
                    "Typ" to ultralightType,
                    "Seiten gelesen" to "${blocks.count { it.isReadable }}/${blocks.size}",
                    "Größe" to "${blocks.size * 4} Bytes"
                )
            )

            sectorResults[0] = SectorResult(0, blocks, null, null, "", true)
            return ExpertReadResult(uid, technology, sectorResults, hwInfo, rawDumpBuilder.toString(), warnings)
        } catch (e: Exception) {
            warnings.add("Fehler: ${e.message}")
            return ExpertReadResult(uid, technology, sectorResults, getBasicHardwareInfo(tag), rawDumpBuilder.toString(), warnings)
        } finally {
            try { ultralight.close() } catch (_: Exception) {}
        }
    }

    /**
     * Deep read of ISO-DEP tags using APDU commands.
     */
    fun expertReadIsoDep(tag: Tag): ExpertReadResult {
        val uid = ByteUtils.bytesToHex(tag.id)
        val techList = tag.techList.toList()
        val technology = techList.joinToString(", ") { it.substringAfterLast(".") }
        val warnings = mutableListOf<String>()
        val additionalInfo = mutableMapOf<String, String>()

        val isoDep = IsoDep.get(tag)
            ?: return ExpertReadResult(uid, technology, emptyMap(), getBasicHardwareInfo(tag), "", listOf("Kein ISO-DEP Tag"))

        try {
            isoDep.connect()
            isoDep.timeout = 5000

            val ats = isoDep.historicalBytes?.let { ByteUtils.bytesToHex(it) } ?: ""
            val hiLayerResponse = isoDep.hiLayerResponse?.let { ByteUtils.bytesToHex(it) } ?: ""
            val maxTransceive = isoDep.maxTransceiveLength

            additionalInfo["ATS"] = ats.ifBlank { "N/A" }
            additionalInfo["HiLayer Response"] = hiLayerResponse.ifBlank { "N/A" }
            additionalInfo["Max Transceive"] = "$maxTransceive Bytes"

            // Try SELECT commands for common AIDs
            val commonAids = listOf(
                "325041592E5359532E4444463031" to "PPSE (Proximity Payment)",
                "315041592E5359532E4444463031" to "PSE (Payment System)",
                "A000000003101001" to "Visa Credit",
                "A000000003101002" to "Visa Debit",
                "A000000004101001" to "Mastercard Credit",
                "A0000000041010" to "Mastercard",
                "D2760000850101" to "NDEF Application"
            )

            val responseInfo = StringBuilder()
            for ((aid, name) in commonAids) {
                try {
                    val selectCmd = buildSelectAidApdu(aid)
                    val response = isoDep.transceive(selectCmd)
                    val responseHex = ByteUtils.bytesToHex(response)
                    if (response.size >= 2) {
                        val sw = ByteUtils.bytesToHex(response.copyOfRange(response.size - 2, response.size))
                        if (sw == "9000" || sw.startsWith("61")) {
                            additionalInfo["AID: $name"] = "Vorhanden ($responseHex)"
                            responseInfo.append("$name: $responseHex\n")
                        }
                    }
                } catch (_: Exception) {
                    // AID not present or not selectable
                }
            }

            val nfcA = NfcA.get(tag)
            val hwInfo = HardwareInfo(
                uid = uid,
                uidLength = tag.id.size,
                atqa = nfcA?.atqa?.let { ByteUtils.bytesToHex(it) } ?: "",
                sak = nfcA?.sak?.let { "%02X".format(it.toInt()) } ?: "",
                ats = ats,
                chipType = determineIsoDepChipType(nfcA?.sak?.toInt() ?: 0, ats),
                sectorCount = 0,
                blockCount = 0,
                totalSize = 0,
                technologies = techList.map { it.substringAfterLast(".") },
                isWritable = false,
                additionalInfo = additionalInfo
            )

            return ExpertReadResult(uid, technology, emptyMap(), hwInfo, responseInfo.toString(), warnings)
        } catch (e: Exception) {
            warnings.add("Fehler: ${e.message}")
            return ExpertReadResult(uid, technology, emptyMap(), getBasicHardwareInfo(tag), "", warnings)
        } finally {
            try { isoDep.close() } catch (_: Exception) {}
        }
    }

    /**
     * Generic expert read that auto-detects the tag type.
     */
    fun expertRead(tag: Tag): ExpertReadResult {
        val techList = tag.techList.toList()
        return when {
            techList.any { it == MifareClassic::class.java.name } -> expertReadMifareClassic(tag)
            techList.any { it == MifareUltralight::class.java.name } -> expertReadMifareUltralight(tag)
            techList.any { it == IsoDep::class.java.name } -> expertReadIsoDep(tag)
            else -> {
                val uid = ByteUtils.bytesToHex(tag.id)
                val technology = techList.joinToString(", ") { it.substringAfterLast(".") }
                ExpertReadResult(uid, technology, emptyMap(), getBasicHardwareInfo(tag), "",
                    listOf("Expertenmodus für ${technology} nicht verfügbar"))
            }
        }
    }

    private fun buildSelectAidApdu(aid: String): ByteArray {
        val aidBytes = ByteUtils.hexToBytes(aid)
        return byteArrayOf(
            0x00, // CLA
            0xA4.toByte(), // INS: SELECT
            0x04, // P1: Select by name
            0x00, // P2
            aidBytes.size.toByte() // Lc
        ) + aidBytes + byteArrayOf(0x00) // Le
    }

    private fun getMifareClassicType(mifare: MifareClassic): String {
        return when (mifare.type) {
            MifareClassic.TYPE_CLASSIC -> "MIFARE Classic (${mifare.size} Bytes)"
            MifareClassic.TYPE_PLUS -> "MIFARE Plus (${mifare.size} Bytes)"
            MifareClassic.TYPE_PRO -> "MIFARE Pro (${mifare.size} Bytes)"
            else -> "Unbekannt (${mifare.size} Bytes)"
        }
    }

    private fun determineIsoDepChipType(sak: Int, ats: String): String {
        return when {
            sak == 0x20 && ats.startsWith("75") -> "MIFARE DESFire EV1"
            sak == 0x20 && ats.startsWith("78") -> "MIFARE DESFire EV2"
            sak == 0x20 -> "MIFARE DESFire"
            sak == 0x28 -> "JCOP (Java Card)"
            sak == 0x38 -> "MIFARE SmartMX"
            else -> "ISO-DEP (SAK: %02X)".format(sak)
        }
    }

    private fun parseAccessBits(trailerBlock: ByteArray): String {
        if (trailerBlock.size < 16) return ""

        val byte6 = trailerBlock[6].toInt() and 0xFF
        val byte7 = trailerBlock[7].toInt() and 0xFF
        val byte8 = trailerBlock[8].toInt() and 0xFF

        return "Access Bytes: ${"%02X %02X %02X".format(byte6, byte7, byte8)} " +
                "(User Byte: ${"%02X".format(trailerBlock[9].toInt() and 0xFF)})"
    }

    private fun getBasicHardwareInfo(tag: Tag): HardwareInfo {
        val nfcA = NfcA.get(tag)
        return HardwareInfo(
            uid = ByteUtils.bytesToHex(tag.id),
            uidLength = tag.id.size,
            atqa = nfcA?.atqa?.let { ByteUtils.bytesToHex(it) } ?: "",
            sak = nfcA?.sak?.let { "%02X".format(it.toInt()) } ?: "",
            ats = "",
            chipType = "Unbekannt",
            sectorCount = 0,
            blockCount = 0,
            totalSize = 0,
            technologies = tag.techList.map { it.substringAfterLast(".") },
            isWritable = false,
            additionalInfo = emptyMap()
        )
    }
}
