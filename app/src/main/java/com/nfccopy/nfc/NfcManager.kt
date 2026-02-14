package com.nfccopy.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Build
import android.os.Bundle

class NfcManager(private val activity: Activity) {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val isNfcSupported: Boolean get() = nfcAdapter != null

    val isNfcEnabled: Boolean get() = nfcAdapter?.isEnabled == true

    /**
     * Uses enableReaderMode (API 19+) which works reliably across all Android versions.
     * This bypasses the foreground dispatch system and gives direct tag access.
     */
    fun enableReaderMode(callback: (Tag) -> Unit) {
        nfcAdapter?.enableReaderMode(
            activity,
            { tag -> callback(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE,
            Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
            }
        )
    }

    fun disableReaderMode() {
        nfcAdapter?.disableReaderMode(activity)
    }

    /**
     * Enables foreground dispatch as a fallback for older devices or
     * when reader mode is not suitable. Handles PendingIntent flag
     * differences between Android 12+ (API 31) and earlier versions.
     */
    fun enableForegroundDispatch() {
        val intent = Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        // Android 12 (API 31) requires FLAG_MUTABLE or FLAG_IMMUTABLE on PendingIntents
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(activity, 0, intent, flags)

        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        )

        val techLists = arrayOf(
            arrayOf(NfcA::class.java.name),
            arrayOf(NfcB::class.java.name),
            arrayOf(NfcF::class.java.name),
            arrayOf(NfcV::class.java.name),
            arrayOf(IsoDep::class.java.name),
            arrayOf(MifareClassic::class.java.name),
            arrayOf(MifareUltralight::class.java.name),
            arrayOf(Ndef::class.java.name),
            arrayOf(NdefFormatable::class.java.name)
        )

        nfcAdapter?.enableForegroundDispatch(activity, pendingIntent, filters, techLists)
    }

    fun disableForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(activity)
    }

    /**
     * Checks if MIFARE Classic is supported on this device.
     * Many modern Android devices (especially those with Broadcom NFC chips)
     * don't support MIFARE Classic.
     */
    val isMifareClassicSupported: Boolean
        get() = try {
            // Check if the device's NFC chip supports MIFARE Classic
            Class.forName("android.nfc.tech.MifareClassic")
            true
        } catch (_: Exception) {
            false
        }

    /**
     * Android version-specific NFC capability info.
     *
     * Key restrictions by Android version:
     * - API 26 (Android 8): Min SDK - full NFC support
     * - API 29 (Android 10): Background NFC tag reading restricted
     * - API 31 (Android 12): PendingIntent must use FLAG_MUTABLE/IMMUTABLE
     * - API 33 (Android 13): Tighter background restrictions
     * - API 34 (Android 14): HCE routing table changes, NDEF push removed
     */
    fun getNfcCapabilities(): NfcCapabilities {
        val sdkVersion = Build.VERSION.SDK_INT
        return NfcCapabilities(
            canReadInBackground = sdkVersion < Build.VERSION_CODES.Q, // < Android 10
            canUseNdefPush = sdkVersion < Build.VERSION_CODES.Q, // Android Beam removed in 10
            hasHceRestrictions = sdkVersion >= 34, // Android 14
            requiresMutablePendingIntent = sdkVersion >= Build.VERSION_CODES.S, // Android 12+
            supportsReaderMode = true // API 19+, always true for our minSdk 26
        )
    }
}

data class NfcCapabilities(
    val canReadInBackground: Boolean,
    val canUseNdefPush: Boolean,
    val hasHceRestrictions: Boolean,
    val requiresMutablePendingIntent: Boolean,
    val supportsReaderMode: Boolean
)
