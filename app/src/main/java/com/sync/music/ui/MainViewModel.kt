package com.sync.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sync.music.core.database.dao.TrackDao
import com.sync.music.core.download.DownloadState
import com.sync.music.core.download.OfflineDownloadManager
import com.sync.music.core.model.MoodChip
import com.sync.music.core.model.PlayerUiState
import com.sync.music.core.model.TimedLyrics
import com.sync.music.core.model.Track
import com.sync.music.core.network.LyricsService
import com.sync.music.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val lyricsService: LyricsService,
    private val trackDao: TrackDao
) : ViewModel() {

    val playerUiState: StateFlow<PlayerUiState> = playerController.uiState

    val downloadState: StateFlow<DownloadState> = offlineDownloadManager.downloadState

    val availableChips: StateFlow<List<MoodChip>> = playerController.queueManager.availableChips

    val offlineTracks = trackDao.observeOfflineTracks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _lyricsState = MutableStateFlow<TimedLyrics?>(null)
    val lyricsState: StateFlow<TimedLyrics?> = _lyricsState.asStateFlow()

    private var currentLyricsTrackId: String? = null

    init {
        // Observe track changes to fetch synchronized lyrics
        viewModelScope.launch {
            playerUiState.collect { state ->
                val track = state.currentTrack
                if (track != null && track.id != currentLyricsTrackId) {
                    currentLyricsTrackId = track.id
                    loadLyrics(track)
                }
            }
        }
    }

    private fun loadLyrics(track: Track) {
        viewModelScope.launch {
            _lyricsState.value = null
            val lyrics = lyricsService.getLyrics(
                videoId = track.id,
                trackName = track.title,
                artistName = track.artist,
                durationSeconds = track.durationMs / 1000
            )
            _lyricsState.value = lyrics
        }
    }

    fun playTrack(track: Track) = playerController.playTrack(track)
    fun playQueueIndex(index: Int) = playerController.playQueueIndex(index)
    fun togglePlayPause() = playerController.togglePlayPause()
    fun playNext() = playerController.playNext()
    fun playPrevious() = playerController.playPrevious()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun toggleRepeat() = playerController.toggleRepeat()
    fun toggleShuffle() = playerController.toggleShuffle()
    fun applyMoodChip(chip: MoodChip) = playerController.queueManager.applyMoodChip(chip)
    fun removeQueueItem(index: Int) = playerController.queueManager.removeAt(index)

    fun downloadCurrentTrack() {
        val track = playerUiState.value.currentTrack ?: return
        viewModelScope.launch {
            offlineDownloadManager.downloadTrack(track)
        }
    }
}
