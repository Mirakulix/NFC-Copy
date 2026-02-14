package com.nfccopy.ui.viewmodel

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfccopy.nfc.expert.ExpertNfcReader
import com.nfccopy.nfc.expert.ExpertNfcWriter
import com.nfccopy.nfc.expert.MifareKeyManager
import com.nfccopy.util.ByteUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExpertViewModel : ViewModel() {

    val keyManager = MifareKeyManager()
    private val expertReader = ExpertNfcReader(keyManager)
    private val expertWriter = ExpertNfcWriter(keyManager)

    sealed class ExpertState {
        data object Idle : ExpertState()
        data object WaitingForTag : ExpertState()
        data object Reading : ExpertState()
        data class ReadResult(val result: ExpertNfcReader.ExpertReadResult) : ExpertState()
        data object WaitingForWriteTag : ExpertState()
        data object Writing : ExpertState()
        data class WriteResult(val result: ExpertNfcWriter.ExpertWriteResult) : ExpertState()
        data class RawCommandResult(val response: String) : ExpertState()
        data class Error(val message: String) : ExpertState()
    }

    enum class ExpertMode {
        DEEP_READ,
        WRITE_BLOCK,
        CHANGE_KEYS,
        RAW_COMMAND
    }

    private val _state = MutableStateFlow<ExpertState>(ExpertState.Idle)
    val state: StateFlow<ExpertState> = _state.asStateFlow()

    private val _currentMode = MutableStateFlow(ExpertMode.DEEP_READ)
    val currentMode: StateFlow<ExpertMode> = _currentMode.asStateFlow()

    // Write parameters
    var writeSectorIndex = 0
    var writeBlockIndex = 0
    var writeDataHex = ""
    var writeKeyAHex = ""
    var writeKeyBHex = ""
    var rawCommandHex = ""
    var newKeyAHex = "FFFFFFFFFFFF"
    var newKeyBHex = "FFFFFFFFFFFF"

    fun setMode(mode: ExpertMode) {
        _currentMode.value = mode
        _state.value = ExpertState.Idle
    }

    fun startScan() {
        _state.value = ExpertState.WaitingForTag
    }

    fun startWriteScan() {
        _state.value = ExpertState.WaitingForWriteTag
    }

    fun onTagDiscovered(tag: Tag) {
        when (_state.value) {
            is ExpertState.WaitingForTag -> performDeepRead(tag)
            is ExpertState.WaitingForWriteTag -> performWrite(tag)
            else -> {}
        }
    }

    private fun performDeepRead(tag: Tag) {
        _state.value = ExpertState.Reading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = expertReader.expertRead(tag)
                _state.value = ExpertState.ReadResult(result)
            } catch (e: Exception) {
                _state.value = ExpertState.Error("Fehler: ${e.message}")
            }
        }
    }

    private fun performWrite(tag: Tag) {
        _state.value = ExpertState.Writing
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (_currentMode.value) {
                    ExpertMode.WRITE_BLOCK -> {
                        val data = ByteUtils.hexToBytes(writeDataHex.replace(" ", ""))
                        val keyA = if (writeKeyAHex.isNotBlank()) ByteUtils.hexToBytes(writeKeyAHex) else null
                        val keyB = if (writeKeyBHex.isNotBlank()) ByteUtils.hexToBytes(writeKeyBHex) else null
                        val result = expertWriter.writeBlock(tag, writeSectorIndex, writeBlockIndex, data, keyA, keyB)
                        _state.value = ExpertState.WriteResult(result)
                    }
                    ExpertMode.CHANGE_KEYS -> {
                        val currentKeyA = if (writeKeyAHex.isNotBlank()) ByteUtils.hexToBytes(writeKeyAHex) else null
                        val currentKeyB = if (writeKeyBHex.isNotBlank()) ByteUtils.hexToBytes(writeKeyBHex) else null
                        val newKeyA = ByteUtils.hexToBytes(newKeyAHex)
                        val newKeyB = ByteUtils.hexToBytes(newKeyBHex)
                        val result = expertWriter.changeSectorKeys(tag, writeSectorIndex, currentKeyA, currentKeyB, newKeyA, newKeyB)
                        _state.value = ExpertState.WriteResult(result)
                    }
                    ExpertMode.RAW_COMMAND -> {
                        val cmd = ByteUtils.hexToBytes(rawCommandHex.replace(" ", ""))
                        val (_, response) = expertWriter.sendRawCommand(tag, cmd)
                        _state.value = ExpertState.RawCommandResult(response)
                    }
                    else -> _state.value = ExpertState.Error("Ungültiger Modus")
                }
            } catch (e: Exception) {
                _state.value = ExpertState.Error("Fehler: ${e.message}")
            }
        }
    }

    fun addCustomKey(hexKey: String) {
        keyManager.addKeyFromHex(hexKey)
    }

    fun reset() {
        _state.value = ExpertState.Idle
    }
}
