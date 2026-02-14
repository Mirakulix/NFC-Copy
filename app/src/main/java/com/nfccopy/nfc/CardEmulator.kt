package com.nfccopy.nfc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import com.nfccopy.model.CardType
import com.nfccopy.model.NfcCard
import com.nfccopy.service.HceService

class CardEmulator(private val context: Context) {

    var isEmulating: Boolean = false
        private set

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    /**
     * Check if HCE is available on this device.
     * HCE requires Android 4.4+ (API 19), which is always true for our minSdk 26.
     */
    val isHceAvailable: Boolean
        get() = nfcAdapter != null && context.packageManager.hasSystemFeature("android.hardware.nfc.hce")

    /**
     * Check if the card type can be emulated via HCE.
     * Only ISO 14443-4 (ISO-DEP) based cards can be emulated.
     */
    fun canEmulate(card: NfcCard): Boolean {
        return when (card.type) {
            CardType.ISODEP, CardType.MIFARE_DESFIRE -> true
            // MIFARE Classic, Ultralight, NFC-A/B/F/V cannot be emulated via Android HCE
            else -> false
        }
    }

    /**
     * Returns a human-readable reason why emulation might not work.
     */
    fun getEmulationWarning(card: NfcCard): String? {
        if (!isHceAvailable) {
            return "Dieses Gerät unterstützt kein Host Card Emulation (HCE)."
        }

        if (!canEmulate(card)) {
            return "${card.type.displayName} kann nicht über HCE emuliert werden. " +
                    "Nur ISO 14443-4 basierte Karten (ISO-DEP, DESFire) werden unterstützt."
        }

        if (Build.VERSION.SDK_INT >= 34) {
            return "Hinweis: Ab Android 14 gelten strengere HCE-Einschränkungen. " +
                    "Die Emulation funktioniert möglicherweise nicht mit allen Lesegeräten."
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return "Hinweis: Bei aktiviertem Secure NFC funktioniert die Emulation " +
                    "möglicherweise nur bei entsperrtem Bildschirm."
        }

        return null
    }

    fun startEmulation(card: NfcCard) {
        val intent = Intent(context, HceService::class.java).apply {
            action = HceService.ACTION_START_EMULATION
            putExtra(HceService.EXTRA_CARD_UID, card.uid)
            putExtra(HceService.EXTRA_CARD_RAW_DATA, card.rawData)
            putExtra(HceService.EXTRA_CARD_TYPE, card.type.name)
        }
        context.startService(intent)
        isEmulating = true

        // Set this service as the preferred payment service for foreground
        try {
            val cardEmulation = CardEmulation.getInstance(nfcAdapter)
            val componentName = ComponentName(context, HceService::class.java)
            cardEmulation.setPreferredService(context as android.app.Activity, componentName)
        } catch (_: Exception) {
            // Not all contexts are Activities, and this is optional
        }
    }

    fun stopEmulation() {
        val intent = Intent(context, HceService::class.java).apply {
            action = HceService.ACTION_STOP_EMULATION
        }
        context.startService(intent)
        isEmulating = false

        try {
            val cardEmulation = CardEmulation.getInstance(nfcAdapter)
            cardEmulation.unsetPreferredService(context as android.app.Activity)
        } catch (_: Exception) {
            // Not all contexts are Activities
        }
    }
}
