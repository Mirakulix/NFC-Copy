package com.nfccopy.service

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.nfccopy.util.ByteUtils

/**
 * Host Card Emulation Service.
 *
 * Android HCE limitations by version:
 * - All versions: Only ISO 14443-4 (ISO-DEP) can be emulated via HCE
 * - All versions: UID cannot be freely set (device uses its own or random UID)
 * - Android 10 (API 29): Android Beam (NDEF Push) removed
 * - Android 12 (API 31): Secure NFC setting may block HCE when screen is off
 * - Android 14 (API 34): Observe mode for HCE, stricter AID routing
 *
 * MIFARE Classic / MIFARE Ultralight / NFC-A/B/F/V cannot be emulated via HCE.
 * Only ISO 14443-4 based cards (ISO-DEP, e.g. DESFire, payment cards) can be emulated.
 */
class HceService : HostApduService() {

    private var cardUid: String = ""
    private var cardRawData: String = ""
    private var cardType: String = ""
    private var isActive: Boolean = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EMULATION -> {
                cardUid = intent.getStringExtra(EXTRA_CARD_UID) ?: ""
                cardRawData = intent.getStringExtra(EXTRA_CARD_RAW_DATA) ?: ""
                cardType = intent.getStringExtra(EXTRA_CARD_TYPE) ?: ""
                isActive = true
                Log.d(TAG, "Emulation started for card type: $cardType")
            }
            ACTION_STOP_EMULATION -> {
                isActive = false
                cardUid = ""
                cardRawData = ""
                cardType = ""
                Log.d(TAG, "Emulation stopped")
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        if (!isActive) return SW_UNKNOWN

        val hexCommand = ByteUtils.bytesToHex(commandApdu)
        Log.d(TAG, "Received APDU: $hexCommand")

        // SELECT AID command (CLA=00, INS=A4, P1=04, P2=00)
        if (hexCommand.startsWith("00A40400")) {
            Log.d(TAG, "SELECT AID - responding with success")
            return STATUS_SUCCESS
        }

        // GET UID command (pseudo-APDU used by some readers)
        if (hexCommand.startsWith("FFCA0000")) {
            return if (cardUid.isNotEmpty()) {
                Log.d(TAG, "GET UID - responding with: $cardUid")
                ByteUtils.hexToBytes(cardUid) + STATUS_SUCCESS
            } else {
                SW_UNKNOWN
            }
        }

        // READ BINARY command (CLA=00, INS=B0)
        if (hexCommand.startsWith("00B0")) {
            return if (cardRawData.isNotEmpty()) {
                try {
                    val data = ByteUtils.hexToBytes(cardRawData)
                    Log.d(TAG, "READ BINARY - responding with ${data.size} bytes")
                    data + STATUS_SUCCESS
                } catch (_: Exception) {
                    SW_UNKNOWN
                }
            } else {
                SW_FILE_NOT_FOUND
            }
        }

        // GET RESPONSE command (CLA=00, INS=C0)
        if (hexCommand.startsWith("00C00000")) {
            return STATUS_SUCCESS
        }

        Log.d(TAG, "Unknown APDU command: $hexCommand")
        return SW_INS_NOT_SUPPORTED
    }

    override fun onDeactivated(reason: Int) {
        val reasonStr = when (reason) {
            DEACTIVATION_LINK_LOSS -> "link loss"
            DEACTIVATION_DESELECTED -> "deselected"
            else -> "unknown ($reason)"
        }
        Log.d(TAG, "HCE deactivated: $reasonStr")
    }

    companion object {
        private const val TAG = "HceService"

        const val ACTION_START_EMULATION = "com.nfccopy.START_EMULATION"
        const val ACTION_STOP_EMULATION = "com.nfccopy.STOP_EMULATION"
        const val EXTRA_CARD_UID = "card_uid"
        const val EXTRA_CARD_RAW_DATA = "card_raw_data"
        const val EXTRA_CARD_TYPE = "card_type"

        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val SW_UNKNOWN = byteArrayOf(0x6F.toByte(), 0x00.toByte())
        private val SW_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00.toByte())
    }
}
