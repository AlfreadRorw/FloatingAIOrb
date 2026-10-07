package com.alfread.alfvision.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alfread.alfvision.data.local.AIProfileEntity
import com.alfread.alfvision.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun profileCanBeInsertedAndRead() {
        val profile = AIProfileEntity(
            name = "Test",
            systemPrompt = "Answer clearly.",
            temperature = 0.7f,
            maxTokens = 512,
            preferredModel = "qwen/qwen3.8-27b",
            createdAt = 1L,
            updatedAt = 1L
        )
        database.aiProfileDao().upsert(profile)
        val stored = database.aiProfileDao().get("Test")
        assertNotNull(stored)
        assertEquals("Answer clearly.", stored?.systemPrompt)
    }
}
