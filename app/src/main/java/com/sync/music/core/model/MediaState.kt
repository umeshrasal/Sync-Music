package com.sync.music.core.model

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR
}

enum class RepeatState {
    OFF,
    ALL,
    ONE
}

data class PlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val playbackStatus: PlaybackStatus = PlaybackStatus.IDLE,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val repeatState: RepeatState = RepeatState.OFF,
    val isShuffleOn: Boolean = false,
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = -1,
    val errorMessage: String? = null
)
