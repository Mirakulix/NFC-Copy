package com.nfccopy.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfccopy.nfc.NfcManager
import com.nfccopy.nfc.expert.ExpertNfcReader
import com.nfccopy.nfc.expert.ExpertNfcWriter
import com.nfccopy.ui.theme.HexBackground
import com.nfccopy.ui.theme.HexText
import com.nfccopy.ui.theme.NfcGreen
import com.nfccopy.ui.theme.NfcOrange
import com.nfccopy.ui.viewmodel.ExpertViewModel

@Composable
fun ExpertScreen(
    nfcManager: NfcManager,
    viewModel: ExpertViewModel = remember { ExpertViewModel() }
) {
    val state by viewModel.state.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()

    DisposableEffect(state) {
        if (state is ExpertViewModel.ExpertState.WaitingForTag || state is ExpertViewModel.ExpertState.WaitingForWriteTag) {
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
        // Expert mode header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.BugReport, null, tint = NfcOrange, modifier = Modifier.size(28.dp))
            Text("Expertenmodus", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Warning card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Vorsicht: Falsches Schreiben kann NFC-Tags dauerhaft beschädigen. " +
                            "Das Ändern von Zugriffsschlüsseln kann Sektoren unwiderruflich sperren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Mode selector chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentMode == ExpertViewModel.ExpertMode.DEEP_READ,
                onClick = { viewModel.setMode(ExpertViewModel.ExpertMode.DEEP_READ) },
                label = { Text("Deep Read", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Memory, null, Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = currentMode == ExpertViewModel.ExpertMode.WRITE_BLOCK,
                onClick = { viewModel.setMode(ExpertViewModel.ExpertMode.WRITE_BLOCK) },
                label = { Text("Schreiben", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Key, null, Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = currentMode == ExpertViewModel.ExpertMode.RAW_COMMAND,
                onClick = { viewModel.setMode(ExpertViewModel.ExpertMode.RAW_COMMAND) },
                label = { Text("Raw CMD", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Terminal, null, Modifier.size(16.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is ExpertViewModel.ExpertState.Idle -> {
                when (currentMode) {
                    ExpertViewModel.ExpertMode.DEEP_READ -> DeepReadIdleContent(viewModel, nfcManager)
                    ExpertViewModel.ExpertMode.WRITE_BLOCK -> WriteBlockIdleContent(viewModel, nfcManager)
                    ExpertViewModel.ExpertMode.CHANGE_KEYS -> ChangeKeysIdleContent(viewModel, nfcManager)
                    ExpertViewModel.ExpertMode.RAW_COMMAND -> RawCommandIdleContent(viewModel, nfcManager)
                }
            }
            is ExpertViewModel.ExpertState.WaitingForTag,
            is ExpertViewModel.ExpertState.WaitingForWriteTag -> {
                WaitingForTagContent { viewModel.reset() }
            }
            is ExpertViewModel.ExpertState.Reading -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Deep Read läuft...", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${viewModel.keyManager.getKeyCount()} Schlüssel werden getestet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is ExpertViewModel.ExpertState.Writing -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Schreibe...", style = MaterialTheme.typography.titleMedium)
            }
            is ExpertViewModel.ExpertState.ReadResult -> {
                val result = (state as ExpertViewModel.ExpertState.ReadResult).result
                DeepReadResultContent(result, viewModel)
            }
            is ExpertViewModel.ExpertState.WriteResult -> {
                val result = (state as ExpertViewModel.ExpertState.WriteResult).result
                WriteResultContent(result, viewModel)
            }
            is ExpertViewModel.ExpertState.RawCommandResult -> {
                val response = (state as ExpertViewModel.ExpertState.RawCommandResult).response
                RawCommandResultContent(response, viewModel)
            }
            is ExpertViewModel.ExpertState.Error -> {
                val message = (state as ExpertViewModel.ExpertState.Error).message
                Icon(Icons.Default.Error, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.reset() }) { Text("Zurück") }
            }
        }
    }
}

@Composable
private fun DeepReadIdleContent(viewModel: ExpertViewModel, nfcManager: NfcManager) {
    var customKey by remember { mutableStateOf("") }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.Memory, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Deep Read", style = MaterialTheme.typography.titleLarge)
        Text(
            "Liest alle Sektoren mit ${viewModel.keyManager.getKeyCount()} bekannten Schlüsseln. " +
                    "Zeigt Hardware-Info, Zugriffsrechte und gefundene Schlüssel an.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = customKey,
            onValueChange = { customKey = it.filter { c -> c.isLetterOrDigit() }.take(12) },
            label = { Text("Eigenen Schlüssel hinzufügen (12 Hex-Zeichen)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("z.B. FFFFFFFFFFFF") }
        )
        if (customKey.length == 12) {
            OutlinedButton(onClick = {
                viewModel.addCustomKey(customKey)
                customKey = ""
            }) {
                Text("Schlüssel hinzufügen (${viewModel.keyManager.getKeyCount()} gespeichert)")
            }
        }

        Button(
            onClick = { viewModel.startScan() },
            enabled = nfcManager.isNfcEnabled
        ) {
            Text("Tag scannen")
        }
    }
}

@Composable
private fun WriteBlockIdleContent(viewModel: ExpertViewModel, nfcManager: NfcManager) {
    var sectorStr by remember { mutableStateOf("0") }
    var blockStr by remember { mutableStateOf("1") }
    var dataHex by remember { mutableStateOf("") }
    var keyAHex by remember { mutableStateOf("FFFFFFFFFFFF") }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Block schreiben", style = MaterialTheme.typography.titleLarge)
        Text(
            "Schreibe Daten in einen bestimmten Block eines MIFARE Classic Tags.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = sectorStr, onValueChange = { sectorStr = it.filter { c -> c.isDigit() } },
                label = { Text("Sektor") }, modifier = Modifier.weight(1f), singleLine = true
            )
            OutlinedTextField(
                value = blockStr, onValueChange = { blockStr = it.filter { c -> c.isDigit() } },
                label = { Text("Block") }, modifier = Modifier.weight(1f), singleLine = true
            )
        }
        OutlinedTextField(
            value = dataHex,
            onValueChange = { dataHex = it.filter { c -> c.isLetterOrDigit() || c == ' ' }.take(47) },
            label = { Text("Daten (32 Hex-Zeichen = 16 Bytes)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("00000000000000000000000000000000") }
        )
        OutlinedTextField(
            value = keyAHex,
            onValueChange = { keyAHex = it.filter { c -> c.isLetterOrDigit() }.take(12) },
            label = { Text("Schlüssel A (12 Hex)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        val cleanData = dataHex.replace(" ", "")
        Button(
            onClick = {
                viewModel.writeSectorIndex = sectorStr.toIntOrNull() ?: 0
                viewModel.writeBlockIndex = blockStr.toIntOrNull() ?: 1
                viewModel.writeDataHex = cleanData
                viewModel.writeKeyAHex = keyAHex
                viewModel.startWriteScan()
            },
            enabled = nfcManager.isNfcEnabled && cleanData.length == 32
        ) {
            Text("Tag anlegen und schreiben")
        }
    }
}

@Composable
private fun ChangeKeysIdleContent(viewModel: ExpertViewModel, nfcManager: NfcManager) {
    // Merged into WriteBlock mode for simplicity
    Text("Schlüssel ändern", style = MaterialTheme.typography.titleLarge)
    Text(
        "Wechseln Sie über den 'Schreiben' Modus die Zugangsschlüssel.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun RawCommandIdleContent(viewModel: ExpertViewModel, nfcManager: NfcManager) {
    var cmdHex by remember { mutableStateOf("") }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.Terminal, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Raw NFC-A Kommando", style = MaterialTheme.typography.titleLarge)
        Text(
            "Sende ein rohes NFC-A (ISO 14443-3A) Transceive-Kommando an den Tag.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = cmdHex,
            onValueChange = { cmdHex = it.filter { c -> c.isLetterOrDigit() || c == ' ' } },
            label = { Text("Kommando (Hex)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("z.B. 60 00 (AUTH Block 0)") }
        )

        Button(
            onClick = {
                viewModel.rawCommandHex = cmdHex
                viewModel.setMode(ExpertViewModel.ExpertMode.RAW_COMMAND)
                viewModel.startWriteScan()
            },
            enabled = nfcManager.isNfcEnabled && cmdHex.replace(" ", "").length >= 2
        ) {
            Text("Kommando senden")
        }
    }
}

@Composable
private fun WaitingForTagContent(onCancel: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "expert_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "expert_pulse_alpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.Nfc, null, Modifier.size(80.dp).alpha(alpha), tint = NfcOrange)
        Text("Tag anlegen...", style = MaterialTheme.typography.headlineSmall)
        OutlinedButton(onClick = onCancel) { Text("Abbrechen") }
    }
}

@Composable
private fun DeepReadResultContent(result: ExpertNfcReader.ExpertReadResult, viewModel: ExpertViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hardware Info
        Text("Hardware-Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow("UID", result.hardwareInfo.uid)
                InfoRow("UID-Länge", "${result.hardwareInfo.uidLength} Bytes")
                InfoRow("Chip-Typ", result.hardwareInfo.chipType)
                if (result.hardwareInfo.atqa.isNotBlank()) InfoRow("ATQA", result.hardwareInfo.atqa)
                if (result.hardwareInfo.sak.isNotBlank()) InfoRow("SAK", result.hardwareInfo.sak)
                if (result.hardwareInfo.ats.isNotBlank()) InfoRow("ATS", result.hardwareInfo.ats)
                InfoRow("Technologien", result.hardwareInfo.technologies.joinToString(", "))
                result.hardwareInfo.additionalInfo.forEach { (k, v) -> InfoRow(k, v) }
            }
        }

        // Keys found
        if (result.sectorResults.any { it.value.keyA != null || it.value.keyB != null }) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Gefundene Schlüssel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NfcGreen.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    result.sectorResults.forEach { (sector, sr) ->
                        if (sr.keyA != null || sr.keyB != null) {
                            val keyAStr = sr.keyA?.let { com.nfccopy.util.ByteUtils.bytesToHex(it) } ?: "?"
                            val keyBStr = sr.keyB?.let { com.nfccopy.util.ByteUtils.bytesToHex(it) } ?: "?"
                            Text(
                                "Sektor $sector: A=$keyAStr  B=$keyBStr",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            if (sr.accessBits.isNotBlank()) {
                                Text("  ${sr.accessBits}", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Sector data dump
        if (result.sectorResults.any { it.value.blocks.isNotEmpty() }) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Speicherinhalt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            result.sectorResults.toSortedMap().forEach { (sector, sr) ->
                if (sr.blocks.isNotEmpty()) {
                    Text("Sektor $sector", style = MaterialTheme.typography.titleSmall)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(HexBackground)
                            .padding(8.dp)
                    ) {
                        sr.blocks.forEach { block ->
                            val text = if (block.isReadable) {
                                val formatted = block.dataHex.chunked(2).joinToString(" ")
                                val ascii = try {
                                    com.nfccopy.util.ByteUtils.bytesToAscii(
                                        com.nfccopy.util.ByteUtils.hexToBytes(block.dataHex)
                                    )
                                } catch (_: Exception) { "" }
                                "B${"%03d".format(block.blockIndex)}: $formatted |$ascii|"
                            } else {
                                "B${"%03d".format(block.blockIndex)}: [${block.error ?: "UNLESBAR"}]"
                            }
                            Text(
                                text, color = if (block.isReadable) HexText else NfcOrange,
                                fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }

        // Warnings
        if (result.warnings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Warnungen", style = MaterialTheme.typography.titleMedium)
            result.warnings.forEach { warning ->
                Text("- $warning", style = MaterialTheme.typography.bodySmall, color = NfcOrange)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
            Text("Neuer Scan")
        }
    }
}

@Composable
private fun WriteResultContent(result: ExpertNfcWriter.ExpertWriteResult, viewModel: ExpertViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
            null, Modifier.size(48.dp),
            tint = if (result.success) NfcGreen else MaterialTheme.colorScheme.error
        )
        Text(result.message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (result.details.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    result.details.forEach { detail ->
                        Text(detail, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }
        Button(onClick = { viewModel.reset() }) { Text("Zurück") }
    }
}

@Composable
private fun RawCommandResultContent(response: String, viewModel: ExpertViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Antwort", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = HexBackground)
        ) {
            Text(
                response, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                color = HexText, modifier = Modifier.padding(12.dp)
            )
        }
        Button(onClick = { viewModel.reset() }) { Text("Zurück") }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
