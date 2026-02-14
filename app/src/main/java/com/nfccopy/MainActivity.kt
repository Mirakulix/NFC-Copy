package com.nfccopy

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.nfccopy.nfc.CardEmulator
import com.nfccopy.nfc.NfcManager
import com.nfccopy.nfc.NfcReader
import com.nfccopy.nfc.NfcWriter
import com.nfccopy.ui.navigation.AppNavGraph
import com.nfccopy.ui.theme.NFCCopyTheme

class MainActivity : ComponentActivity() {

    private lateinit var nfcManager: NfcManager
    private val nfcReader = NfcReader()
    private val nfcWriter = NfcWriter()
    private lateinit var cardEmulator: CardEmulator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcManager = NfcManager(this)
        cardEmulator = CardEmulator(this)

        val app = application as NFCCopyApp

        setContent {
            NFCCopyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph(
                        nfcManager = nfcManager,
                        nfcReader = nfcReader,
                        nfcWriter = nfcWriter,
                        cardEmulator = cardEmulator,
                        repository = app.repository
                    )
                }
            }
        }

        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Enable foreground dispatch as fallback when not in reader mode
        // This ensures NFC works across all Android versions
        nfcManager.enableForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        nfcManager.disableForegroundDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                } else {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                }
                tag?.let {
                    // Tag discovered via foreground dispatch
                    // Reader mode callbacks handle this in the active screen
                }
            }
        }
    }
}
