package com.alfread.alfvision.vision

import com.alfread.alfvision.core.model.CaptureRequest
import com.alfread.alfvision.core.model.CaptureResult
import com.alfread.alfvision.core.model.Region
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

class CaptureCoordinator {
    private val _requests = MutableSharedFlow<CaptureRequest>(extraBufferCapacity = 8)
    val requests: SharedFlow<CaptureRequest> = _requests
    private val pending = LinkedHashMap<String, CompletableDeferred<CaptureResult>>()
    private val lock = Any()

    suspend fun request(region: Region?, quality: Int, maxBytes: Int, fullScreen: Boolean = region == null): CaptureResult {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<CaptureResult>()
        synchronized(lock) { pending[id] = deferred }
        _requests.emit(CaptureRequest(id, region, fullScreen, quality, maxBytes))
        return try {
            deferred.await()
        } finally {
            synchronized(lock) { pending.remove(id) }
        }
    }

    fun complete(result: CaptureResult) {
        synchronized(lock) { pending.remove(result.requestId)?.complete(result) }
    }

    fun fail(requestId: String, throwable: Throwable) {
        synchronized(lock) { pending.remove(requestId)?.completeExceptionally(throwable) }
    }
}
