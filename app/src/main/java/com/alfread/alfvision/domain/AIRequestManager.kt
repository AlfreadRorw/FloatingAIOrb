package com.alfread.alfvision.domain

import android.graphics.Bitmap
import com.alfread.alfvision.core.ResponseStyle
import com.alfread.alfvision.data.repository.GroqRepository
import com.alfread.alfvision.data.repository.GroqResultWithMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AIRequestManager(
    private val repository: GroqRepository,
    private val scope: CoroutineScope
) {
    private var activeJob: Job? = null

    fun start(
        prompt: String,
        systemPrompt: String? = null,
        images: List<Bitmap> = emptyList(),
        model: String? = null,
        style: ResponseStyle? = null,
        onResult: (Result<GroqResultWithMetadata>) -> Unit
    ) {
        activeJob?.cancel()
        activeJob = scope.launch(Dispatchers.IO) {
            val result = repository.chat(
                prompt = prompt,
                systemPrompt = systemPrompt,
                images = images,
                model = model,
                style = style
            )
            launch(Dispatchers.Main.immediate) { onResult(result) }
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }
}
