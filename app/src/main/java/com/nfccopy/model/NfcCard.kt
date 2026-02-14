package com.nfccopy.model

data class NfcCard(
    val id: Long = 0,
    val uid: String,
    val type: CardType,
    val technology: String,
    val atqa: String = "",
    val sak: String = "",
    val ats: String = "",
    val sectorCount: Int = 0,
    val blockCount: Int = 0,
    val sectorData: Map<Int, List<String>> = emptyMap(),
    val rawData: String = "",
    val ndefMessages: List<String> = emptyList(),
    val label: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSensitive: Boolean = false
)

enum class CardType(val displayName: String) {
    MIFARE_CLASSIC("MIFARE Classic"),
    MIFARE_ULTRALIGHT("MIFARE Ultralight"),
    MIFARE_DESFIRE("MIFARE DESFire"),
    NTAG("NTAG"),
    ISODEP("ISO-DEP"),
    NFC_A("NFC-A"),
    NFC_B("NFC-B"),
    NFC_F("NFC-F (FeliCa)"),
    NFC_V("NFC-V"),
    NDEF("NDEF"),
    UNKNOWN("Unbekannt")
}
