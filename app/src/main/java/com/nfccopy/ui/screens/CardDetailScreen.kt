package com.nfccopy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nfccopy.data.repository.CardRepository
import com.nfccopy.model.NfcCard
import com.nfccopy.ui.components.SectorBlockView
import com.nfccopy.ui.theme.NfcOrange
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    cardId: Long,
    repository: CardRepository,
    onBack: () -> Unit
) {
    var card by remember { mutableStateOf<NfcCard?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cardId) {
        card = repository.getCardById(cardId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card?.label?.ifBlank { "Kartendetails" } ?: "Kartendetails") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        card?.let { c ->
                            scope.launch {
                                repository.deleteCard(c)
                                onBack()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Delete, "Löschen")
                    }
                }
            )
        }
    ) { padding ->
        val currentCard = card
        if (currentCard == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentCard.isSensitive) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NfcOrange.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = NfcOrange)
                            Text(
                                "Diese Karte könnte sensible Daten enthalten.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                DetailRow("Typ", currentCard.type.displayName)
                DetailRow("UID", currentCard.uid)
                DetailRow("Technologie", currentCard.technology)

                if (currentCard.atqa.isNotBlank()) DetailRow("ATQA", currentCard.atqa)
                if (currentCard.sak.isNotBlank()) DetailRow("SAK", currentCard.sak)
                if (currentCard.ats.isNotBlank()) DetailRow("ATS", currentCard.ats)
                if (currentCard.sectorCount > 0) DetailRow("Sektoren", "${currentCard.sectorCount}")
                if (currentCard.blockCount > 0) DetailRow("Blöcke", "${currentCard.blockCount}")

                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)
                DetailRow("Gelesen am", dateFormat.format(Date(currentCard.timestamp)))

                if (currentCard.ndefMessages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("NDEF-Nachrichten", style = MaterialTheme.typography.titleMedium)
                    currentCard.ndefMessages.forEach { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (currentCard.sectorData.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Speicherinhalt", style = MaterialTheme.typography.titleMedium)
                    SectorBlockView(sectorData = currentCard.sectorData)
                }

                if (currentCard.rawData.isNotBlank() && currentCard.sectorData.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Rohdaten", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = currentCard.rawData,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}
