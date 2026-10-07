package com.alfread.alfvision.data.repository

import com.alfread.alfvision.core.model.Region
import com.alfread.alfvision.data.local.RegionPresetDao
import com.alfread.alfvision.data.local.RegionPresetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RegionRepository(private val dao: RegionPresetDao) {
    fun observe(): Flow<List<RegionPresetEntity>> = dao.observeAll()

    suspend fun save(name: String, region: Region): Long = dao.insert(
        RegionPresetEntity(
            name = name,
            x = region.x,
            y = region.y,
            width = region.width,
            height = region.height,
            screenWidth = region.screenWidth,
            screenHeight = region.screenHeight,
            displayId = region.displayId,
            rotation = region.rotation,
            createdAt = System.currentTimeMillis()
        )
    )

    suspend fun delete(item: RegionPresetEntity) = dao.delete(item)

    suspend fun update(item: RegionPresetEntity) = dao.update(item)

    fun toRegion(item: RegionPresetEntity): Region = Region(item.x, item.y, item.width, item.height, item.screenWidth, item.screenHeight, item.displayId, item.rotation)
}
