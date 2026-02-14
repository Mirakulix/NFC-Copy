package com.nfccopy.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nfccopy.nfc.CardEmulator
import com.nfccopy.data.repository.CardRepository
import com.nfccopy.ui.components.CardInfoCard
import com.nfccopy.ui.theme.NfcGreen
import com.nfccopy.ui.theme.NfcOrange
import com.nfccopy.ui.viewmodel.EmulateViewModel

@Composable
fun EmulateScreen(
    cardEmulator: CardEmulator,
    repository: CardRepository
) {
    val viewModel = remember { EmulateViewModel(cardEmulator, repository) }
    val state by viewModel.state.collectAsState()
    val savedCards by viewModel.savedCards.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val currentState = state) {
            is EmulateViewModel.EmulateState.Idle -> {
                Icon(
                    Icons.Default.PhonelinkRing,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Karte emulieren", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))

                // General HCE limitation warning
                Card(
                    colors = CardDefaults.cardColors(containerColor = NfcOrange.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, null, tint = NfcOrange)
                            Text(
                                "HCE-Einschränkungen",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "HCE kann nur ISO 14443-4 Karten emulieren (z.B. DESFire, ISO-DEP). " +
                                    "MIFARE Classic, Ultralight und andere Kartentypen können NICHT emuliert werden. " +
                                    "Die UID-Emulation ist nicht auf allen Geräten möglich.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Version-specific warnings
                if (Build.VERSION.SDK_INT >= 34) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    "Android ${Build.VERSION.RELEASE}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Ab Android 14 gelten strengere HCE-Einschränkungen. " +
                                        "Die AID-Routingtabelle wird strenger gehandhabt und die Emulation " +
                                        "funktioniert möglicherweise nicht mit allen Lesegeräten.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NfcOrange.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Bei aktiviertem Secure NFC (Android 12+) funktioniert die Emulation " +
                                        "nur bei entsperrtem Bildschirm.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Karte zum Emulieren auswählen:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (savedCards.isEmpty()) {
                    Text(
                        "Keine gespeicherten Karten vorhanden",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // Filter to show emulation-compatible cards with indicators
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(savedCards, key = { it.id }) { card ->
                            val canEmulate = cardEmulator.canEmulate(card)
                            Column {
                                CardInfoCard(
                                    card = card,
                                    onClick = {
                                        if (canEmulate) {
                                            viewModel.startEmulation(card)
                                        }
                                    }
                                )
                                if (!canEmulate) {
                                    Text(
                                        text = "${card.type.displayName} - nicht emulierbar (nur ISO-DEP)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is EmulateViewModel.EmulateState.Emulating -> {
                Icon(
                    Icons.Default.PhonelinkRing,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = NfcGreen
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Emulation aktiv",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NfcGreen
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentState.card.label.ifBlank { currentState.card.type.displayName },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "UID: ${currentState.card.uid}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace
                )

                // Show version-specific warning during active emulation
                cardEmulator.getEmulationWarning(currentState.card)?.let { warning ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NfcOrange.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.stopEmulation() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Emulation stoppen")
                }
            }
        }
    }
}
