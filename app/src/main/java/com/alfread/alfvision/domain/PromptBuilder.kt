package com.alfread.alfvision.domain

import com.alfread.alfvision.core.ResponseStyle

object PromptBuilder {
    fun styleInstruction(style: ResponseStyle): String = when (style) {
        ResponseStyle.SHORT -> "Keep the answer concise and practical."
        ResponseStyle.NORMAL -> "Give a clear, balanced answer with useful context."
        ResponseStyle.DETAILED -> "Explain the important details and reasoning, but avoid irrelevant filler."
        ResponseStyle.TECHNICAL -> "Use precise technical terminology and mention concrete implementation details when useful."
        ResponseStyle.STEP_BY_STEP -> "Answer as an ordered step-by-step procedure."
    }

    fun quickAction(action: String): String = when (action.lowercase()) {
        "analyze" -> "Analyze this selected screen area. Explain what is happening and identify important visible elements."
        "explain" -> "Explain what is happening in this selected screen area in simple practical terms."
        "read" -> "Read all clearly visible text in this image. Preserve important numbers, names, and labels."
        "translate" -> "Translate the visible text into Indonesian. Preserve names, numbers, and formatting where practical."
        "summarize" -> "Summarize the important information visible in this selected screen area."
        "find error" -> "Look for visible errors, warnings, suspicious states, or likely causes and explain them."
        "extract text" -> "Extract the visible text as clean plain text only. Do not add commentary."
        "describe" -> "Describe the visible interface, objects, states, and important visual details."
        "help me" -> "Based only on what is visible, tell me the most useful next steps to solve or understand the situation."
        else -> action
    }
}
