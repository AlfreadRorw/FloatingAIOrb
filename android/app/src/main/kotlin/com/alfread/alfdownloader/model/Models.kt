package com.alfread.alfdownloader.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val ok: Boolean = false, val service: String = "", val version: String? = null)

@Serializable
data class MediaInfo(
    val id: String = "",
    val title: String = "",
    val uploader: String = "",
    val duration: Double? = null,
    val thumbnail: String? = null,
    val webpageUrl: String = ""
)

@Serializable
data class CreateJobRequest(val url: String, val quality: String = "best")

@Serializable
data class Job(
    val id: String = "",
    val url: String = "",
    val title: String? = null,
    val status: String = "queued",
    val progress: Double = 0.0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speed: String? = null,
    val eta: String? = null,
    val filename: String? = null,
    val error: String? = null,
    val createdAt: String = ""
)

@Serializable
data class CreateJobResponse(val id: String)
