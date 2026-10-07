package com.alfread.alfvision.data.repository

import com.alfread.alfvision.core.RegionRect
import com.alfread.alfvision.data.local.AppDatabase
import com.alfread.alfvision.data.local.RegionPresetEntity
import kotlinx.coroutines.flow.Flow

class RegionRepository(private val database: AppDatabase) {
    fun observe(): Flow<List<RegionPresetEntity>> = database.regionPresetDao().observeAll()

    suspend fun save(name: String, region: RegionRect): Long = database.regionPresetDao().insert(
        RegionPresetEntity(
            name = name,
            x = region.x,
            y = region.y,
            width = region.width,
            height = region.height,
            sourceWidth = region.sourceWidth,
            sourceHeight = region.sourceHeight,
            rotation = region.rotation,
            displayId = region.displayId,
            createdAt = System.currentTimeMillis()
        )
    )

    suspend fun delete(item: RegionPresetEntity) = database.regionPresetDao().delete(item)
    suspend fun update(item: RegionPresetEntity) = database.regionPresetDao().update(item)
    suspend fun deleteAll() = database.regionPresetDao().deleteAll()

    fun toRegion(item: RegionPresetEntity) = RegionRect(
        x = item.x,
        y = item.y,
        width = item.width,
        height = item.height,
        sourceWidth = item.sourceWidth,
        sourceHeight = item.sourceHeight,
        rotation = item.rotation,
        displayId = item.displayId
    )
}
