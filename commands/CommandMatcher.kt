package com.alfread.alfvoicecontrol.commands

import com.alfread.alfvoicecontrol.model.VoiceCommand
import java.text.Normalizer
import kotlin.math.max

object CommandMatcher {
    data class Match(val command: VoiceCommand, val score: Float)

    fun match(transcript: String, commands: List<VoiceCommand>, threshold: Float): Match? {
        val input = normalize(transcript)
        if (input.isBlank()) return null
        return commands
            .asSequence()
            .filter { it.enabled && it.triggerPhrase.isNotBlank() }
            .map { it to score(input, normalize(it.triggerPhrase)) }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= threshold }
            ?.let { Match(it.first, it.second) }
    }

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun score(input: String, target: String): Float {
        if (input == target) return 1f
        if (input.contains(target) || target.contains(input)) {
            return max(0.80f, minOf(input.length, target.length).toFloat() / max(input.length, target.length))
        }
        val a = input.split(' ').filter { it.isNotBlank() }.toSet()
        val b = target.split(' ').filter { it.isNotBlank() }.toSet()
        val overlap = if (a.isEmpty() || b.isEmpty()) 0f else a.intersect(b).size.toFloat() / max(a.size, b.size)
        val distance = levenshtein(input, target)
        val editScore = 1f - distance.toFloat() / max(input.length, target.length).coerceAtLeast(1)
        return (overlap * 0.55f + editScore * 0.45f).coerceIn(0f, 1f)
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            previous = current
        }
        return previous[b.length]
    }
}
