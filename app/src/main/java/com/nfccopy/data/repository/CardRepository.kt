package com.nfccopy.data.repository

import com.nfccopy.data.db.NfcCardDao
import com.nfccopy.data.db.NfcCardEntity
import com.nfccopy.model.NfcCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CardRepository(private val dao: NfcCardDao) {

    val allCards: Flow<List<NfcCard>> = dao.getAllCards().map { entities ->
        entities.map { it.toNfcCard() }
    }

    fun searchCards(query: String): Flow<List<NfcCard>> {
        return dao.searchCards(query).map { entities ->
            entities.map { it.toNfcCard() }
        }
    }

    suspend fun getCardById(id: Long): NfcCard? {
        return dao.getCardById(id)?.toNfcCard()
    }

    suspend fun saveCard(card: NfcCard): Long {
        return dao.insertCard(NfcCardEntity.fromNfcCard(card))
    }

    suspend fun updateCard(card: NfcCard) {
        dao.updateCard(NfcCardEntity.fromNfcCard(card))
    }

    suspend fun deleteCard(card: NfcCard) {
        dao.deleteCardById(card.id)
    }
}
