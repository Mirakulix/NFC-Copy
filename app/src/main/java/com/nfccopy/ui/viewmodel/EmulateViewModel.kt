package com.nfccopy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfccopy.data.repository.CardRepository
import com.nfccopy.model.NfcCard
import com.nfccopy.nfc.CardEmulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class EmulateViewModel(
    private val cardEmulator: CardEmulator,
    private val repository: CardRepository
) : ViewModel() {

    sealed class EmulateState {
        data object Idle : EmulateState()
        data class Emulating(val card: NfcCard) : EmulateState()
    }

    private val _state = MutableStateFlow<EmulateState>(EmulateState.Idle)
    val state: StateFlow<EmulateState> = _state.asStateFlow()

    val savedCards: StateFlow<List<NfcCard>> = repository.allCards
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun startEmulation(card: NfcCard) {
        cardEmulator.startEmulation(card)
        _state.value = EmulateState.Emulating(card)
    }

    fun stopEmulation() {
        cardEmulator.stopEmulation()
        _state.value = EmulateState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        if (cardEmulator.isEmulating) {
            cardEmulator.stopEmulation()
        }
    }
}
