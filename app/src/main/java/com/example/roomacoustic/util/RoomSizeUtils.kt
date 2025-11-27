package com.example.roomacoustic.util

import com.example.roomacoustic.screens.components.RoomSize
import com.example.roomacoustic.viewmodel.RoomViewModel

private fun normalizeLabel(s: String): String =
    s.lowercase()
        .replace("\\s+".toRegex(), "")
        .replace("[()\\[\\]{}:：=~_\\-]".toRegex(), "")

private val W_KEYS = setOf("w", "width", "가로", "폭", "넓이")
private val D_KEYS = setOf("d", "depth", "세로", "길이", "방길이", "방깊이", "전장", "장변")
private val H_KEYS = setOf("h", "height", "높이", "천장", "층고")

fun inferRoomSizeFromLabels(
    labeled: List<RoomViewModel.LabeledMeasure>
): RoomSize? {
    if (labeled.isEmpty()) return null

    fun pick(keys: Set<String>): Float? =
        labeled.firstOrNull { m ->
            val norm = normalizeLabel(m.label)
            keys.any { k -> norm.contains(k) || k.contains(norm) }
        }?.meters

    val w = pick(W_KEYS)
    val d = pick(D_KEYS)
    val h = pick(H_KEYS)

    return if (w != null && d != null && h != null) RoomSize(w, d, h) else null
}
