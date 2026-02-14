package com.nfccopy.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nfccopy.model.CardType
import com.nfccopy.model.NfcCard

@Entity(tableName = "nfc_cards")
data class NfcCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uid: String,
    val type: String,
    val technology: String,
    val atqa: String,
    val sak: String,
    val ats: String,
    val sectorCount: Int,
    val blockCount: Int,
    val sectorDataJson: String,
    val rawData: String,
    val ndefMessagesJson: String,
    val label: String,
    val timestamp: Long,
    val isSensitive: Boolean
) {
    fun toNfcCard(): NfcCard {
        val sectorData = parseSectorData(sectorDataJson)
        val ndefMessages = parseNdefMessages(ndefMessagesJson)
        return NfcCard(
            id = id,
            uid = uid,
            type = try { CardType.valueOf(type) } catch (_: Exception) { CardType.UNKNOWN },
            technology = technology,
            atqa = atqa,
            sak = sak,
            ats = ats,
            sectorCount = sectorCount,
            blockCount = blockCount,
            sectorData = sectorData,
            rawData = rawData,
            ndefMessages = ndefMessages,
            label = label,
            timestamp = timestamp,
            isSensitive = isSensitive
        )
    }

    companion object {
        fun fromNfcCard(card: NfcCard): NfcCardEntity {
            return NfcCardEntity(
                id = card.id,
                uid = card.uid,
                type = card.type.name,
                technology = card.technology,
                atqa = card.atqa,
                sak = card.sak,
                ats = card.ats,
                sectorCount = card.sectorCount,
                blockCount = card.blockCount,
                sectorDataJson = serializeSectorData(card.sectorData),
                rawData = card.rawData,
                ndefMessagesJson = serializeNdefMessages(card.ndefMessages),
                label = card.label,
                timestamp = card.timestamp,
                isSensitive = card.isSensitive
            )
        }

        private fun serializeSectorData(data: Map<Int, List<String>>): String {
            if (data.isEmpty()) return ""
            return data.entries.joinToString(";") { (sector, blocks) ->
                "$sector:${blocks.joinToString(",")}"
            }
        }

        private fun parseSectorData(json: String): Map<Int, List<String>> {
            if (json.isBlank()) return emptyMap()
            return try {
                json.split(";").associate { entry ->
                    val parts = entry.split(":", limit = 2)
                    parts[0].toInt() to parts[1].split(",")
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }

        private fun serializeNdefMessages(messages: List<String>): String {
            return messages.joinToString("|")
        }

        private fun parseNdefMessages(json: String): List<String> {
            if (json.isBlank()) return emptyList()
            return json.split("|")
        }
    }
}
