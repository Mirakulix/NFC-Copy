package com.nfccopy.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nfccopy.ui.theme.NfcGreen
import com.nfccopy.ui.theme.NfcRed

@Composable
fun NfcStatusIndicator(
    isSupported: Boolean,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = when {
            !isSupported -> MaterialTheme.colorScheme.error
            isEnabled -> NfcGreen
            else -> NfcRed
        },
        label = "nfc_status_color"
    )

    val text = when {
        !isSupported -> "NFC wird nicht unterstützt"
        isEnabled -> "NFC ist aktiviert"
        else -> "NFC ist deaktiviert"
    }

    Row(
        modifier = modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = "NFC Status",
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
