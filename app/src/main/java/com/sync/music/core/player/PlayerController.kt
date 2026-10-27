package com.sync.music.core.player

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sync.music.core.model.PlaybackStatus
import com.sync.music.core.model.PlayerUiState
import com.sync.music.core.model.RepeatState
import com.sync.music.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    val queueManager: QueueManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        initializeController()
        startProgressPolling()
        observeQueue()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupPlayerListener()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        val controller = mediaController ?: return
        controller.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val status = when (playbackState) {
                    Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
                    Player.STATE_READY -> if (controller.isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED
                    Player.STATE_ENDED -> PlaybackStatus.ENDED
                    Player.STATE_IDLE -> PlaybackStatus.IDLE
                    else -> PlaybackStatus.IDLE
                }

                _uiState.value = _uiState.value.copy(
                    playbackStatus = status,
                    isPlaying = controller.isPlaying,
                    totalDurationMs = controller.duration.coerceAtLeast(0L)
                )

                if (playbackState == Player.STATE_ENDED) {
                    playNext()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(
                    isPlaying = isPlaying,
                    playbackStatus = if (isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _uiState.value = _uiState.value.copy(
                    totalDurationMs = controller.duration.coerceAtLeast(0L),
                    currentPositionMs = controller.currentPosition.coerceAtLeast(0L)
                )
            }
        })
    }

    private fun observeQueue() {
        scope.launch {
            queueManager.queue.collect { list ->
                _uiState.value = _uiState.value.copy(
                    queue = list,
                    currentTrack = queueManager.currentTrack(),
                    currentQueueIndex = queueManager.currentIndex.value
                )
            }
        }
        scope.launch {
            queueManager.currentIndex.collect { idx ->
                _uiState.value = _uiState.value.copy(
                    currentTrack = queueManager.currentTrack(),
                    currentQueueIndex = idx
                )
            }
        }
    }

    private fun startProgressPolling() {
        scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.coerceAtLeast(0L)
                    val buf = controller.bufferedPosition.coerceAtLeast(0L)

                    _uiState.value = _uiState.value.copy(
                        currentPositionMs = pos,
                        totalDurationMs = dur,
                        bufferedPositionMs = buf
                    )
                }
                delay(300)
            }
        }
    }

    fun playTrack(track: Track) {
        queueManager.startRadio(track)
        playCurrentQueueItem()
    }

    fun playQueueIndex(index: Int) {
        queueManager.setIndex(index)
        playCurrentQueueItem()
    }

    private fun playCurrentQueueItem() {
        val track = queueManager.currentTrack() ?: return
        val controller = mediaController ?: return

        val mediaItem = track.toMediaItem()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()

        _uiState.value = _uiState.value.copy(
            currentTrack = track,
            isPlaying = true,
            playbackStatus = PlaybackStatus.BUFFERING
        )
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && queueManager.currentTrack() != null) {
                playCurrentQueueItem()
            } else {
                controller.play()
            }
        }
    }

    fun playNext() {
        val next = queueManager.next()
        if (next != null) {
            playCurrentQueueItem()
        }
    }

    fun playPrevious() {
        val controller = mediaController ?: return
        // If current position > 3 seconds, replay track from beginning
        if (controller.currentPosition > 3000) {
            controller.seekTo(0)
        } else {
            val prev = queueManager.previous()
            if (prev != null) {
                playCurrentQueueItem()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(currentPositionMs = positionMs)
    }

    fun toggleRepeat() {
        val controller = mediaController ?: return
        val nextState = when (_uiState.value.repeatState) {
            RepeatState.OFF -> RepeatState.ALL
            RepeatState.ALL -> RepeatState.ONE
            RepeatState.ONE -> RepeatState.OFF
        }
        controller.repeatMode = when (nextState) {
            RepeatState.OFF -> Player.REPEAT_MODE_OFF
            RepeatState.ALL -> Player.REPEAT_MODE_ALL
            RepeatState.ONE -> Player.REPEAT_MODE_ONE
        }
        _uiState.value = _uiState.value.copy(repeatState = nextState)
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val newShuffle = !_uiState.value.isShuffleOn
        controller.shuffleModeEnabled = newShuffle
        _uiState.value = _uiState.value.copy(isShuffleOn = newShuffle)
    }
}
