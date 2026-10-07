package com.alfread.alfvision.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
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
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun regionPresetDao(): RegionPresetDao
    abstract fun aiProfileDao(): AIProfileDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "alf_vision.db")
                .addCallback(object : RoomDatabase.Callback() {})
                .build()
    }
}
