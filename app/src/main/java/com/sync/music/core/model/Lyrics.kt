package com.sync.music.core.model

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class TimedLyrics(
    val videoId: String,
    val lines: List<LyricLine>,
    val isSynced: Boolean
) {
    companion object {
        private val LRC_REGEX = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

        fun parseLrc(videoId: String, lrcContent: String): TimedLyrics {
            val lines = mutableListOf<LyricLine>()
            val rawLines = lrcContent.lines()

            for (line in rawLines) {
                val match = LRC_REGEX.find(line.trim())
                if (match != null) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fraction = match.groupValues[3]
                    val millis = if (fraction.length == 2) {
                        fraction.toLongOrNull()?.times(10) ?: 0L
                    } else {
                        fraction.toLongOrNull() ?: 0L
                    }
                    val timeMs = (minutes * 60 + seconds) * 1000 + millis
                    val text = match.groupValues[4].trim()
                    if (text.isNotEmpty()) {
                        lines.add(LyricLine(timeMs, text))
                    }
                }
            }

            lines.sortBy { it.timeMs }
            return TimedLyrics(
                videoId = videoId,
                lines = lines,
                isSynced = lines.isNotEmpty()
            )
        }
    }
}
