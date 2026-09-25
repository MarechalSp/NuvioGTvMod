package com.nuvio.tv.data.repository.epg

import androidx.compose.runtime.Immutable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

@Immutable
data class EpgProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val category: String? = null,
    val iconUrl: String? = null,
) {
    fun isLive(nowMs: Long): Boolean {
        return nowMs in startEpochMs until endEpochMs
    }

    fun intersects(windowStartMs: Long, windowEndMs: Long): Boolean {
        return endEpochMs > windowStartMs && startEpochMs < windowEndMs
    }

    fun progress(nowMs: Long): Float {
        if (nowMs <= startEpochMs) return 0f
        if (nowMs >= endEpochMs) return 1f
        val duration = endEpochMs - startEpochMs
        if (duration <= 0L) return 0f
        return ((nowMs - startEpochMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    val durationMinutes: Int
        get() = max(1, ((endEpochMs - startEpochMs) / 60000L).toInt())

    fun remainingMinutes(nowMs: Long): Int {
        if (nowMs >= endEpochMs) return 0
        return max(0, ((endEpochMs - nowMs) / 60000L).toInt())
    }

    val formattedTimeRange: String
        get() {
            val startStr = formatEpochToTime(startEpochMs)
            val endStr = formatEpochToTime(endEpochMs)
            return "$startStr - $endStr"
        }
}

@Immutable
data class ChannelEpgInfo(
    val channelKey: String,
    val channelId: String? = null,
    val nowProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null,
    val todayPrograms: List<EpgProgram> = emptyList(),
) {
    val hasPrograms: Boolean
        get() = nowProgram != null || todayPrograms.isNotEmpty()

    fun programsForWindow(fromMs: Long, toMs: Long): List<EpgProgram> {
        if (todayPrograms.isEmpty()) return emptyList()
        return todayPrograms.filter { it.intersects(fromMs, toMs) }
    }
}

@Immutable
data class EpgSourceConfig(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val lastUpdatedEpochMs: Long = 0L,
    val programCount: Int = 0,
)

@Immutable
data class TimelineSlot(
    val label: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val isCurrent: Boolean,
)

private fun formatEpochToTime(epochMs: Long): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        sdf.format(Date(epochMs))
    } catch (_: Exception) {
        "--:--"
    }
}
