package com.nfccopy.ui.viewmodel

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfccopy.data.repository.CardRepository
import com.nfccopy.model.NfcCard
import com.nfccopy.nfc.NfcWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WriteViewModel(
    private val nfcWriter: NfcWriter,
    private val repository: CardRepository
) : ViewModel() {

    sealed class WriteState {
        data object Idle : WriteState()
        data object SelectSource : WriteState()
        data class WaitingForTarget(val sourceCard: NfcCard) : WriteState()
        data object Writing : WriteState()
        data class Success(val message: String) : WriteState()
        data class Error(val message: String) : WriteState()
    }

    private val _state = MutableStateFlow<WriteState>(WriteState.Idle)
    val state: StateFlow<WriteState> = _state.asStateFlow()

    val savedCards: StateFlow<List<NfcCard>> = repository.allCards
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun selectSource() {
        _state.value = WriteState.SelectSource
    }

    fun onSourceSelected(card: NfcCard) {
        _state.value = WriteState.WaitingForTarget(card)
    }

    fun onTagDiscovered(tag: Tag) {
        val currentState = _state.value
        if (currentState !is WriteState.WaitingForTarget) return

        _state.value = WriteState.Writing
        viewModelScope.launch {
            when (val result = nfcWriter.writeToTag(tag, currentState.sourceCard)) {
                is NfcWriter.WriteResult.Success -> _state.value = WriteState.Success("Erfolgreich geschrieben!")
                is NfcWriter.WriteResult.Partial -> _state.value = WriteState.Success("${result.sectorsWritten}/${result.totalSectors} Sektoren geschrieben")
                is NfcWriter.WriteResult.Error -> _state.value = WriteState.Error(result.message)
            }
        }
    }

    fun reset() {
        _state.value = WriteState.Idle
    }
}
