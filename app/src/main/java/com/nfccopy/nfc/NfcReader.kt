package com.nfccopy.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import com.nfccopy.model.CardType
import com.nfccopy.model.NfcCard
import com.nfccopy.util.ByteUtils

class NfcReader {

    fun readTag(tag: Tag): NfcCard {
        val uid = ByteUtils.bytesToHex(tag.id)
        val techList = tag.techList.toList()
        val technology = techList.joinToString(", ") { it.substringAfterLast(".") }

        return when {
            techList.any { it == MifareClassic::class.java.name } -> readMifareClassic(tag, uid, technology)
            techList.any { it == MifareUltralight::class.java.name } -> readMifareUltralight(tag, uid, technology)
            techList.any { it == IsoDep::class.java.name } -> readIsoDep(tag, uid, technology)
            techList.any { it == Ndef::class.java.name } -> readNdef(tag, uid, technology)
            techList.any { it == NfcA::class.java.name } -> readNfcA(tag, uid, technology)
            techList.any { it == NfcB::class.java.name } -> readNfcB(tag, uid, technology)
            techList.any { it == NfcF::class.java.name } -> readNfcF(tag, uid, technology)
            techList.any { it == NfcV::class.java.name } -> readNfcV(tag, uid, technology)
            else -> NfcCard(uid = uid, type = CardType.UNKNOWN, technology = technology)
        }
    }

    private fun readMifareClassic(tag: Tag, uid: String, technology: String): NfcCard {
        val mifare = MifareClassic.get(tag) ?: return NfcCard(uid = uid, type = CardType.MIFARE_CLASSIC, technology = technology)
        val sectorData = mutableMapOf<Int, List<String>>()
        val rawDataBuilder = StringBuilder()

        try {
            mifare.connect()
            for (sectorIndex in 0 until mifare.sectorCount) {
                val authenticated = mifare.authenticateSectorWithKeyA(sectorIndex, MifareClassic.KEY_DEFAULT)
                        || mifare.authenticateSectorWithKeyB(sectorIndex, MifareClassic.KEY_DEFAULT)
                        || mifare.authenticateSectorWithKeyA(sectorIndex, MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY)
                        || mifare.authenticateSectorWithKeyA(sectorIndex, MifareClassic.KEY_NFC_FORUM)

                if (authenticated) {
                    val blockList = mutableListOf<String>()
                    val firstBlock = mifare.sectorToBlock(sectorIndex)
                    for (blockIndex in firstBlock until firstBlock + mifare.getBlockCountInSector(sectorIndex)) {
                        try {
                            val data = mifare.readBlock(blockIndex)
                            val hex = ByteUtils.bytesToHex(data)
                            blockList.add(hex)
                            rawDataBuilder.append(hex)
                        } catch (_: Exception) {
                            blockList.add("ERROR")
                        }
                    }
                    sectorData[sectorIndex] = blockList
                } else {
                    sectorData[sectorIndex] = listOf("AUTH_FAILED")
                }
            }

            val nfcA = NfcA.get(tag)
            val atqa = nfcA?.atqa?.let { ByteUtils.bytesToHex(it) } ?: ""
            val sak = nfcA?.sak?.let { "%02X".format(it.toInt()) } ?: ""

            return NfcCard(
                uid = uid,
                type = CardType.MIFARE_CLASSIC,
                technology = technology,
                atqa = atqa,
                sak = sak,
                sectorCount = mifare.sectorCount,
                blockCount = mifare.blockCount,
                sectorData = sectorData,
                rawData = rawDataBuilder.toString(),
                isSensitive = true
            )
        } catch (e: Exception) {
            return NfcCard(
                uid = uid,
                type = CardType.MIFARE_CLASSIC,
                technology = technology,
                rawData = "Read error: ${e.message}"
            )
        } finally {
            try { mifare.close() } catch (_: Exception) {}
        }
    }

    private fun readMifareUltralight(tag: Tag, uid: String, technology: String): NfcCard {
        val ultralight = MifareUltralight.get(tag) ?: return NfcCard(uid = uid, type = CardType.MIFARE_ULTRALIGHT, technology = technology)
        val rawDataBuilder = StringBuilder()
        val sectorData = mutableMapOf<Int, List<String>>()

        try {
            ultralight.connect()
            val pageList = mutableListOf<String>()
            for (page in 0 until 44 step 4) {
                try {
                    val data = ultralight.readPages(page)
                    val hex = ByteUtils.bytesToHex(data)
                    pageList.add(hex)
                    rawDataBuilder.append(hex)
                } catch (_: Exception) {
                    break
                }
            }
            sectorData[0] = pageList

            return NfcCard(
                uid = uid,
                type = CardType.MIFARE_ULTRALIGHT,
                technology = technology,
                sectorData = sectorData,
                rawData = rawDataBuilder.toString()
            )
        } catch (e: Exception) {
            return NfcCard(uid = uid, type = CardType.MIFARE_ULTRALIGHT, technology = technology, rawData = "Read error: ${e.message}")
        } finally {
            try { ultralight.close() } catch (_: Exception) {}
        }
    }

    private fun readIsoDep(tag: Tag, uid: String, technology: String): NfcCard {
        val isoDep = IsoDep.get(tag) ?: return NfcCard(uid = uid, type = CardType.ISODEP, technology = technology)

        try {
            isoDep.connect()
            val ats = isoDep.historicalBytes?.let { ByteUtils.bytesToHex(it) } ?: ""

            val nfcA = NfcA.get(tag)
            val atqa = nfcA?.atqa?.let { ByteUtils.bytesToHex(it) } ?: ""
            val sak = nfcA?.sak?.let { "%02X".format(it.toInt()) } ?: ""

            val type = if (sak == "20") CardType.MIFARE_DESFIRE else CardType.ISODEP

            return NfcCard(
                uid = uid,
                type = type,
                technology = technology,
                atqa = atqa,
                sak = sak,
                ats = ats,
                rawData = "MaxTransceiveLength: ${isoDep.maxTransceiveLength}"
            )
        } catch (e: Exception) {
            return NfcCard(uid = uid, type = CardType.ISODEP, technology = technology, rawData = "Read error: ${e.message}")
        } finally {
            try { isoDep.close() } catch (_: Exception) {}
        }
    }

    private fun readNdef(tag: Tag, uid: String, technology: String): NfcCard {
        val ndef = Ndef.get(tag) ?: return NfcCard(uid = uid, type = CardType.NDEF, technology = technology)
        val messages = mutableListOf<String>()

        try {
            ndef.connect()
            ndef.ndefMessage?.records?.forEach { record ->
                val payload = String(record.payload, Charsets.UTF_8)
                messages.add("Type: ${String(record.type)}, Payload: $payload")
            }

            return NfcCard(
                uid = uid,
                type = CardType.NDEF,
                technology = technology,
                ndefMessages = messages,
                rawData = ndef.ndefMessage?.toByteArray()?.let { ByteUtils.bytesToHex(it) } ?: ""
            )
        } catch (e: Exception) {
            return NfcCard(uid = uid, type = CardType.NDEF, technology = technology, rawData = "Read error: ${e.message}")
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }

    private fun readNfcA(tag: Tag, uid: String, technology: String): NfcCard {
        val nfcA = NfcA.get(tag) ?: return NfcCard(uid = uid, type = CardType.NFC_A, technology = technology)
        return try {
            NfcCard(
                uid = uid,
                type = CardType.NFC_A,
                technology = technology,
                atqa = ByteUtils.bytesToHex(nfcA.atqa),
                sak = "%02X".format(nfcA.sak.toInt())
            )
        } catch (_: Exception) {
            NfcCard(uid = uid, type = CardType.NFC_A, technology = technology)
        }
    }

    private fun readNfcB(tag: Tag, uid: String, technology: String): NfcCard {
        val nfcB = NfcB.get(tag) ?: return NfcCard(uid = uid, type = CardType.NFC_B, technology = technology)
        return try {
            NfcCard(
                uid = uid,
                type = CardType.NFC_B,
                technology = technology,
                rawData = "AppData: ${ByteUtils.bytesToHex(nfcB.applicationData)}, ProtocolInfo: ${ByteUtils.bytesToHex(nfcB.protocolInfo)}"
            )
        } catch (_: Exception) {
            NfcCard(uid = uid, type = CardType.NFC_B, technology = technology)
        }
    }

    private fun readNfcF(tag: Tag, uid: String, technology: String): NfcCard {
        val nfcF = NfcF.get(tag) ?: return NfcCard(uid = uid, type = CardType.NFC_F, technology = technology)
        return try {
            NfcCard(
                uid = uid,
                type = CardType.NFC_F,
                technology = technology,
                rawData = "Manufacturer: ${ByteUtils.bytesToHex(nfcF.manufacturer)}, SystemCode: ${ByteUtils.bytesToHex(nfcF.systemCode)}"
            )
        } catch (_: Exception) {
            NfcCard(uid = uid, type = CardType.NFC_F, technology = technology)
        }
    }

    private fun readNfcV(tag: Tag, uid: String, technology: String): NfcCard {
        val nfcV = NfcV.get(tag) ?: return NfcCard(uid = uid, type = CardType.NFC_V, technology = technology)
        return try {
            NfcCard(
                uid = uid,
                type = CardType.NFC_V,
                technology = technology,
                rawData = "DsfId: ${"%02X".format(nfcV.dsfId.toInt())}, ResponseFlags: ${"%02X".format(nfcV.responseFlags.toInt())}"
            )
        } catch (_: Exception) {
            NfcCard(uid = uid, type = CardType.NFC_V, technology = technology)
        }
    }
}
