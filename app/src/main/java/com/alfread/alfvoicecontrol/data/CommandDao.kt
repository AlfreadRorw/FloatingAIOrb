package com.alfread.alfvoicecontrol.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandDao {

    @Query("SELECT * FROM voice_commands ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<VoiceCommand>>

    @Query("SELECT * FROM voice_commands WHERE enabled = 1")
    suspend fun getAllEnabled(): List<VoiceCommand>

    @Query("SELECT * FROM voice_commands WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VoiceCommand?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(command: VoiceCommand)

    @Update
    suspend fun update(command: VoiceCommand)

    @Delete
    suspend fun delete(command: VoiceCommand)

    @Query("DELETE FROM voice_commands WHERE id = :id")
    suspend fun deleteById(id: String)
}
