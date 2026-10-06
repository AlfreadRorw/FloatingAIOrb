package com.alfread.alfvision.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        RegionPresetEntity::class,
        AIProfileEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun regionPresetDao(): RegionPresetDao
    abstract fun aiProfileDao(): AIProfileDao
    abstract fun appSettingsDao(): AppSettingsDao
}
