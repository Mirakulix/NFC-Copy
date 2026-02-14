package com.nfccopy.ui.viewmodel

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfccopy.data.repository.CardRepository
import com.nfccopy.model.NfcCard
import com.nfccopy.nfc.NfcReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReadViewModel(
    private val nfcReader: NfcReader,
    private val repository: CardRepository
) : ViewModel() {

    sealed class ReadState {
        data object Idle : ReadState()
        data object Waiting : ReadState()
        data object Reading : ReadState()
        data class Success(val card: NfcCard, val savedId: Long? = null) : ReadState()
        data class Error(val message: String) : ReadState()
    }

    private val _state = MutableStateFlow<ReadState>(ReadState.Idle)
    val state: StateFlow<ReadState> = _state.asStateFlow()

    fun startWaiting() {
        _state.value = ReadState.Waiting
    }

    fun stopWaiting() {
        _state.value = ReadState.Idle
    }

    fun onTagDiscovered(tag: Tag) {
        _state.value = ReadState.Reading
        viewModelScope.launch {
            try {
                val card = nfcReader.readTag(tag)
                _state.value = ReadState.Success(card)
            } catch (e: Exception) {
                _state.value = ReadState.Error(e.message ?: "Unbekannter Fehler")
            }
        }
    }

    fun saveCard(card: NfcCard, label: String = "") {
        viewModelScope.launch {
            val cardToSave = card.copy(label = label.ifBlank { card.type.displayName })
            val id = repository.saveCard(cardToSave)
            _state.value = ReadState.Success(cardToSave, savedId = id)
        }
    }

    fun reset() {
        _state.value = ReadState.Idle
    }
}
