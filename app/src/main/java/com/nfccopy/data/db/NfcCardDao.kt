package com.nfccopy.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NfcCardDao {
    @Query("SELECT * FROM nfc_cards ORDER BY timestamp DESC")
    fun getAllCards(): Flow<List<NfcCardEntity>>

    @Query("SELECT * FROM nfc_cards WHERE id = :id")
    suspend fun getCardById(id: Long): NfcCardEntity?

    @Query("SELECT * FROM nfc_cards WHERE uid LIKE '%' || :query || '%' OR label LIKE '%' || :query || '%' OR technology LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchCards(query: String): Flow<List<NfcCardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: NfcCardEntity): Long

    @Update
    suspend fun updateCard(card: NfcCardEntity)

    @Delete
    suspend fun deleteCard(card: NfcCardEntity)

    @Query("DELETE FROM nfc_cards WHERE id = :id")
    suspend fun deleteCardById(id: Long)
}
