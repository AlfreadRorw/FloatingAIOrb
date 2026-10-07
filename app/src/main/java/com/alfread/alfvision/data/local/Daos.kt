package com.alfread.alfvision.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert suspend fun insert(value: ConversationEntity): Long
    @Update suspend fun update(value: ConversationEntity)
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1") suspend fun get(id: Long): ConversationEntity?
    @Query("DELETE FROM conversations WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM conversations WHERE updatedAt < :cutoff") suspend fun deleteOlderThan(cutoff: Long)
    @Query("DELETE FROM conversations") suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(value: MessageEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(value: List<MessageEntity>)
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC") suspend fun getForConversation(conversationId: Long): List<MessageEntity>
    @Query("DELETE FROM messages WHERE conversationId = :conversationId") suspend fun deleteForConversation(conversationId: Long)
    @Query("DELETE FROM messages") suspend fun deleteAll()
}

@Dao
interface RegionPresetDao {
    @Insert suspend fun insert(value: RegionPresetEntity): Long
    @Update suspend fun update(value: RegionPresetEntity)
    @Delete suspend fun delete(value: RegionPresetEntity)
    @Query("SELECT * FROM region_presets ORDER BY createdAt DESC") fun observeAll(): Flow<List<RegionPresetEntity>>
    @Query("SELECT * FROM region_presets WHERE id = :id LIMIT 1") suspend fun get(id: Long): RegionPresetEntity?
}

@Dao
interface AIProfileDao {
    @Insert suspend fun insert(value: AIProfileEntity): Long
    @Update suspend fun update(value: AIProfileEntity)
    @Delete suspend fun delete(value: AIProfileEntity)
    @Query("SELECT * FROM ai_profiles ORDER BY builtIn DESC, name ASC") fun observeAll(): Flow<List<AIProfileEntity>>
    @Query("SELECT * FROM ai_profiles WHERE id = :id LIMIT 1") suspend fun get(id: Long): AIProfileEntity?
    @Query("SELECT COUNT(*) FROM ai_profiles") suspend fun count(): Int
}

@Dao
interface AppSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(value: AppSettingsEntity)
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1") suspend fun get(): AppSettingsEntity?
}
