package com.alfread.alfvoicecontrol.voice

import com.alfread.alfvoicecontrol.data.VoiceCommand
import kotlin.math.max

data class MatchResult(
    val command: VoiceCommand?,
    val confidence: Float,
    val recognizedText: String
)

/**
 * Normalizes recognized speech and fuzzy-matches it against the stored
 * trigger phrases. Matching is intentionally forgiving of small variations
 * (case, punctuation, spacing) but not so aggressive that unrelated phrases
 * get confused with each other.
 */
object CommandMatcher {

    fun normalize(text: String): String {
        return text
            .lowercase()
            .trim()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Returns similarity in [0f, 1f] between two normalized strings, based on
     * Levenshtein edit distance relative to the longer string's length.
     */
    fun similarity(a: String, b: String): Float {
        if (a.isEmpty() && b.isEmpty()) return 1f
        val distance = levenshtein(a, b)
        val longest = max(a.length, b.length)
        if (longest == 0) return 1f
        return 1f - (distance.toFloat() / longest.toFloat())
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }

    /**
     * Finds the best matching enabled command for [recognizedText], if its
     * similarity clears [confidenceThreshold].
     */
    fun findBestMatch(
        recognizedText: String,
        candidates: List<VoiceCommand>,
        confidenceThreshold: Float
    ): MatchResult {
        val normalizedInput = normalize(recognizedText)
        var best: VoiceCommand? = null
        var bestScore = 0f

        for (candidate in candidates) {
            if (!candidate.enabled) continue
            val score = similarity(normalizedInput, normalize(candidate.triggerPhrase))
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }

        return if (best != null && bestScore >= confidenceThreshold) {
            MatchResult(best, bestScore, recognizedText)
        } else {
            MatchResult(null, bestScore, recognizedText)
        }
    }

    /** Simple containment-aware check used for quick wake-word detection. */
    fun containsPhrase(recognizedText: String, phrase: String): Boolean {
        val normInput = normalize(recognizedText)
        val normPhrase = normalize(phrase)
        if (normPhrase.isEmpty()) return false
        if (normInput.contains(normPhrase)) return true
        return similarity(normInput, normPhrase) >= 0.8f
    }
}
