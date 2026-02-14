package com.nfccopy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfccopy.data.repository.CardRepository
import com.nfccopy.model.NfcCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedCardsViewModel(private val repository: CardRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    @OptIn(ExperimentalCoroutinesApi::class)
    val cards: StateFlow<List<NfcCard>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) repository.allCards
        else repository.searchCards(query)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteCard(card: NfcCard) {
        viewModelScope.launch {
            repository.deleteCard(card)
        }
    }
}
