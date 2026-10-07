package com.alfread.alfvision.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: ConversationEntity)

    @Delete
    suspend fun delete(value: ConversationEntity)

    @Query("DELETE FROM conversations WHERE updatedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getForConversation(conversationId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(value: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: Long)

    @Query("DELETE FROM messages WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface RegionPresetDao {
    @Query("SELECT * FROM region_presets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RegionPresetEntity>>

    @Insert
    suspend fun insert(value: RegionPresetEntity): Long

    @Update
    suspend fun update(value: RegionPresetEntity)

    @Delete
    suspend fun delete(value: RegionPresetEntity)

    @Query("DELETE FROM region_presets")
    suspend fun deleteAll()
}

@Dao
interface AIProfileDao {
    @Query("SELECT * FROM ai_profiles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AIProfileEntity>>

    @Query("SELECT * FROM ai_profiles WHERE name = :name LIMIT 1")
    suspend fun get(name: String): AIProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: AIProfileEntity)

    @Query("DELETE FROM ai_profiles WHERE name = :name")
    suspend fun delete(name: String)
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: AppSettingsEntity)
}
