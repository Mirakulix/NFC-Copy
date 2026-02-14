package com.nfccopy.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nfccopy.data.repository.CardRepository
import com.nfccopy.nfc.NfcManager
import com.nfccopy.nfc.NfcReader
import com.nfccopy.ui.components.CardInfoCard
import com.nfccopy.ui.components.NfcStatusIndicator
import com.nfccopy.ui.viewmodel.ReadViewModel

@Composable
fun ReadScreen(
    nfcManager: NfcManager,
    nfcReader: NfcReader,
    repository: CardRepository,
    onCardRead: (Long) -> Unit
) {
    val viewModel = remember { ReadViewModel(nfcReader, repository) }
    val state by viewModel.state.collectAsState()
    var labelInput by remember { mutableStateOf("") }

    DisposableEffect(state) {
        if (state is ReadViewModel.ReadState.Waiting) {
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NfcStatusIndicator(
            isSupported = nfcManager.isNfcSupported,
            isEnabled = nfcManager.isNfcEnabled
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (val currentState = state) {
            is ReadViewModel.ReadState.Idle -> {
                IdleContent(
                    nfcEnabled = nfcManager.isNfcEnabled,
                    onStartReading = { viewModel.startWaiting() }
                )
            }
            is ReadViewModel.ReadState.Waiting -> {
                WaitingContent(onCancel = { viewModel.stopWaiting() })
            }
            is ReadViewModel.ReadState.Reading -> {
                ReadingContent()
            }
            is ReadViewModel.ReadState.Success -> {
                SuccessContent(
                    card = currentState.card,
                    savedId = currentState.savedId,
                    labelInput = labelInput,
                    onLabelChange = { labelInput = it },
                    onSave = {
                        viewModel.saveCard(currentState.card, labelInput)
                    },
                    onViewDetails = {
                        currentState.savedId?.let { onCardRead(it) }
                    },
                    onReadAnother = {
                        labelInput = ""
                        viewModel.reset()
                    }
                )
            }
            is ReadViewModel.ReadState.Error -> {
                ErrorContent(
                    message = currentState.message,
                    onRetry = { viewModel.startWaiting() },
                    onDismiss = { viewModel.reset() }
                )
            }
        }
    }
}

@Composable
private fun IdleContent(nfcEnabled: Boolean, onStartReading: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "NFC-Karte auslesen",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Legen Sie eine NFC-Karte an die Rückseite Ihres Geräts",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onStartReading,
            enabled = nfcEnabled
        ) {
            Text("Karte lesen")
        }
    }
}

@Composable
private fun WaitingContent(onCancel: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier
                .size(100.dp)
                .alpha(alpha),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Warte auf Karte...",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Halten Sie die Karte an die Rückseite des Geräts",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onCancel) {
            Text("Abbrechen")
        }
    }
}

@Composable
private fun ReadingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Text(
            text = "Karte wird gelesen...",
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
private fun SuccessContent(
    card: com.nfccopy.model.NfcCard,
    savedId: Long?,
    labelInput: String,
    onLabelChange: (String) -> Unit,
    onSave: () -> Unit,
    onViewDetails: () -> Unit,
    onReadAnother: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = com.nfccopy.ui.theme.NfcGreen
        )
        Text(
            text = "Karte erfolgreich gelesen!",
            style = MaterialTheme.typography.headlineSmall
        )

        CardInfoCard(card = card, onClick = {})

        if (savedId == null) {
            OutlinedTextField(
                value = labelInput,
                onValueChange = onLabelChange,
                label = { Text("Bezeichnung (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Karte speichern")
            }
        } else {
            Text(
                text = "Karte gespeichert!",
                style = MaterialTheme.typography.bodyLarge,
                color = com.nfccopy.ui.theme.NfcGreen
            )
            Button(onClick = onViewDetails, modifier = Modifier.fillMaxWidth()) {
                Text("Details anzeigen")
            }
        }

        OutlinedButton(onClick = onReadAnother, modifier = Modifier.fillMaxWidth()) {
            Text("Weitere Karte lesen")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = "Fehler beim Lesen",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        Button(onClick = onRetry) {
            Text("Erneut versuchen")
        }
        OutlinedButton(onClick = onDismiss) {
            Text("Zurück")
        }
    }
}
