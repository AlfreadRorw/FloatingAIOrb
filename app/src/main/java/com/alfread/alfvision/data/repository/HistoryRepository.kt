package com.alfread.alfvision.data.repository

import com.alfread.alfvision.core.model.ChatLine
import com.alfread.alfvision.core.model.Role
import com.alfread.alfvision.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

class HistoryRepository(private val db: AppDatabase) {
    fun observeConversations(): Flow<List<ConversationEntity>> = db.conversationDao().observeAll()

    suspend fun createConversation(title: String, profileId: Long): Long = db.conversationDao().insert(
        ConversationEntity(title = title, profileId = profileId, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
    )

    suspend fun addMessage(conversationId: Long, line: ChatLine): Long = db.messageDao().insert(
        MessageEntity(
            conversationId = conversationId,
            role = line.role.name,
            content = line.content,
            timestamp = line.timestamp,
            imagePath = line.imagePath,
            model = line.model,
            tokenUsage = line.tokenUsage
        )
    )

    suspend fun getMessages(conversationId: Long): List<ChatLine> = db.messageDao().getForConversation(conversationId).map {
        ChatLine(
            id = it.id,
            role = runCatching { Role.valueOf(it.role) }.getOrDefault(Role.USER),
            content = it.content,
            timestamp = it.timestamp,
            imagePath = it.imagePath,
            model = it.model,
            tokenUsage = it.tokenUsage
        )
    }

    suspend fun deleteConversation(id: Long) {
        db.messageDao().deleteForConversation(id)
        db.conversationDao().delete(id)
    }

    suspend fun deleteOlderThan(cutoff: Long) {
        val ids = db.conversationDao().observeAll().first().filter { it.updatedAt < cutoff }.map { it.id }
        ids.forEach { db.messageDao().deleteForConversation(it) }
        db.conversationDao().deleteOlderThan(cutoff)
    }

    suspend fun deleteAll() {
        db.messageDao().deleteAll()
        db.conversationDao().deleteAll()
    }
}
