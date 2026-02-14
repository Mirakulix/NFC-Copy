package com.nfccopy.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nfccopy.data.repository.CardRepository
import com.nfccopy.nfc.NfcManager
import com.nfccopy.nfc.NfcWriter
import com.nfccopy.ui.components.CardInfoCard
import com.nfccopy.ui.components.NfcStatusIndicator
import com.nfccopy.ui.theme.NfcGreen
import com.nfccopy.ui.viewmodel.WriteViewModel

@Composable
fun WriteScreen(
    nfcManager: NfcManager,
    nfcWriter: NfcWriter,
    repository: CardRepository
) {
    val viewModel = remember { WriteViewModel(nfcWriter, repository) }
    val state by viewModel.state.collectAsState()
    val savedCards by viewModel.savedCards.collectAsState()

    DisposableEffect(state) {
        if (state is WriteViewModel.WriteState.WaitingForTarget) {
            nfcManager.enableReaderMode { tag ->
                viewModel.onTagDiscovered(tag)
            }
        }
        onDispose {
            nfcManager.disableReaderMode()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NfcStatusIndicator(
            isSupported = nfcManager.isNfcSupported,
            isEnabled = nfcManager.isNfcEnabled
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (val currentState = state) {
            is WriteViewModel.WriteState.Idle -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Auf NFC-Karte schreiben", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Wählen Sie eine gespeicherte Karte als Quelle",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { viewModel.selectSource() },
                        enabled = nfcManager.isNfcEnabled && savedCards.isNotEmpty()
                    ) {
                        Text("Quellkarte wählen")
                    }
                }
            }
            is WriteViewModel.WriteState.SelectSource -> {
                Text("Quellkarte auswählen", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(savedCards, key = { it.id }) { card ->
                        CardInfoCard(card = card, onClick = { viewModel.onSourceSelected(card) })
                    }
                }
                OutlinedButton(onClick = { viewModel.reset() }) {
                    Text("Abbrechen")
                }
            }
            is WriteViewModel.WriteState.WaitingForTarget -> {
                val infiniteTransition = rememberInfiniteTransition(label = "write_pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                    label = "write_pulse_alpha"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Nfc, null,
                        modifier = Modifier.size(100.dp).alpha(alpha),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Zielkarte anlegen", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Quelle: ${currentState.sourceCard.label.ifBlank { currentState.sourceCard.uid }}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OutlinedButton(onClick = { viewModel.reset() }) {
                        Text("Abbrechen")
                    }
                }
            }
            is WriteViewModel.WriteState.Writing -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Schreibe auf Karte...", style = MaterialTheme.typography.headlineSmall)
            }
            is WriteViewModel.WriteState.Success -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = NfcGreen)
                    Text(currentState.message, style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { viewModel.reset() }) { Text("Fertig") }
                }
            }
            is WriteViewModel.WriteState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Error, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                    Text("Fehler", style = MaterialTheme.typography.headlineSmall)
                    Text(currentState.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.reset() }) { Text("Zurück") }
                }
            }
        }
    }
}
