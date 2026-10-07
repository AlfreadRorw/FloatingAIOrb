package com.alfread.alfvision.data.repository

import android.content.Context
import com.alfread.alfvision.core.AutoDeletePeriod
import com.alfread.alfvision.data.local.AppDatabase
import com.alfread.alfvision.data.local.ConversationEntity
import com.alfread.alfvision.data.local.MessageEntity
import com.alfread.alfvision.data.prefs.AppPreferences
import kotlinx.coroutines.flow.Flow
import java.io.File

class HistoryRepository(
    private val database: AppDatabase,
    private val preferences: AppPreferences
) {
    fun observeConversations(): Flow<List<ConversationEntity>> = database.conversationDao().observeAll()

    suspend fun getMessages(conversationId: Long): List<MessageEntity> = database.messageDao().getForConversation(conversationId)

    suspend fun ensureConversation(title: String, profile: String, id: Long = System.currentTimeMillis()): Long {
        val existing = database.conversationDao().get(id)
        if (existing == null) {
            database.conversationDao().upsert(ConversationEntity(id, title.take(80), System.currentTimeMillis(), System.currentTimeMillis(), profile))
        }
        return id
    }

    suspend fun addMessage(
        conversationId: Long,
        role: String,
        content: String,
        model: String,
        imagePath: String? = null,
        regionJson: String? = null,
        inputTokens: Int? = null,
        outputTokens: Int? = null
    ) {
        val now = System.currentTimeMillis()
        database.messageDao().insert(
            MessageEntity(
                id = now + (0..999).random(),
                conversationId = conversationId,
                role = role,
                content = content,
                timestamp = now,
                model = model,
                imagePath = imagePath,
                regionJson = regionJson,
                tokenInput = inputTokens,
                tokenOutput = outputTokens
            )
        )
        val conversation = database.conversationDao().get(conversationId)
        if (conversation != null) database.conversationDao().upsert(conversation.copy(updatedAt = now))
    }

    suspend fun renameConversation(value: ConversationEntity, name: String) {
        database.conversationDao().upsert(value.copy(title = name.take(80), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(value: ConversationEntity) {
        value.let {
            database.messageDao().deleteForConversation(it.id)
            database.conversationDao().delete(it)
        }
    }

    suspend fun deleteOlderThan(before: Long) {
        database.conversationDao().deleteOlderThan(before)
        // Message rows are removed by a database-level foreign key only in future schemas;
        // current schema stores messages independently, so clear old messages through the same timestamp policy.
        database.messageDao().deleteOlderThan(before)
    }

    suspend fun deleteAll() {
        database.messageDao().deleteAll()
        database.conversationDao().deleteAll()
    }

    suspend fun saveScreenshot(context: Context, imageBytes: ByteArray, name: String): String? {
        if (!preferences.snapshot().saveScreenshots) return null
        val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
        val file = File(dir, "${name.take(40).replace(Regex("[^A-Za-z0-9_-]"), "_")}_${System.currentTimeMillis()}.jpg")
        file.writeBytes(imageBytes)
        return file.absolutePath
    }

    companion object {
        private var repository: HistoryRepository? = null

        fun register(instance: HistoryRepository) { repository = instance }

        fun cleanupExpiredScreenshots(context: Context, preferences: AppPreferences) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
                val period = preferences.snapshot().autoDelete
                if (period == AutoDeletePeriod.NEVER) return@launch
                val age = when (period) {
                    AutoDeletePeriod.DAY_1 -> 24L * 60 * 60 * 1000
                    AutoDeletePeriod.DAYS_7 -> 7L * 24 * 60 * 60 * 1000
                    AutoDeletePeriod.DAYS_30 -> 30L * 24 * 60 * 60 * 1000
                    AutoDeletePeriod.NEVER -> Long.MAX_VALUE
                }
                val before = System.currentTimeMillis() - age
                repository?.deleteOlderThan(before)
                val dir = File(context.filesDir, "screenshots")
                dir.listFiles()?.forEach { file ->
                    if (file.lastModified() < before) file.delete()
                }
            }
        }
    }

}
