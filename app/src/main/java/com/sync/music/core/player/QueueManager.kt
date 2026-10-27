package com.sync.music.core.player

import com.sync.music.core.database.dao.TrackDao
import com.sync.music.core.database.entity.TrackEntity
import com.sync.music.core.model.MoodChip
import com.sync.music.core.model.Track
import com.sync.music.core.network.RecommendationApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor(
    private val recommendationApi: RecommendationApi,
    private val trackDao: TrackDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _availableChips = MutableStateFlow<List<MoodChip>>(emptyList())
    val availableChips: StateFlow<List<MoodChip>> = _availableChips.asStateFlow()

    private var currentSeedVideoId: String? = null
    private var continuationToken: String? = null
    private var isLoadingMore = false

    /**
     * Seeds the queue with an initial track and fetches YouTube's algorithmic "Up Next" radio queue.
     */
    fun startRadio(seedTrack: Track) {
        currentSeedVideoId = seedTrack.id
        continuationToken = null
        _queue.value = listOf(seedTrack)
        _currentIndex.value = 0

        scope.launch {
            // Cache seed track metadata
            trackDao.insertOrUpdateTrack(TrackEntity.fromTrack(seedTrack))

            // Fetch dynamic recommendation queue based on RDAMVM<seedTrack.id>
            val result = recommendationApi.getRadioQueue(seedTrack.id)
            if (result.isSuccess) {
                val data = result.getOrThrow()
                continuationToken = data.continuationToken
                _availableChips.value = data.chips

                // Filter out duplicated seed track if present in recommendations
                val filtered = data.tracks.filter { it.id != seedTrack.id }
                _queue.value = listOf(seedTrack) + filtered

                // Cache upcoming tracks
                trackDao.insertOrUpdateTracks(filtered.map { TrackEntity.fromTrack(it) })
            }
        }
    }

    /**
     * Appends next set of algorithmic suggestions seamlessly when approaching end of queue.
     */
    fun checkAndFetchMore(upcomingThreshold: Int = 3) {
        val current = _currentIndex.value
        val total = _queue.value.size
        if (total - current <= upcomingThreshold && !isLoadingMore && continuationToken != null) {
            fetchContinuation()
        }
    }

    private fun fetchContinuation() {
        val seed = currentSeedVideoId ?: return
        val token = continuationToken ?: return
        isLoadingMore = true

        scope.launch {
            try {
                val result = recommendationApi.getRadioQueue(seed, continuationToken = token)
                if (result.isSuccess) {
                    val data = result.getOrThrow()
                    continuationToken = data.continuationToken

                    val currentTracks = _queue.value
                    val existingIds = currentTracks.map { it.id }.toSet()
                    val newTracks = data.tracks.filter { it.id !in existingIds }

                    _queue.value = currentTracks + newTracks
                    trackDao.insertOrUpdateTracks(newTracks.map { TrackEntity.fromTrack(it) })
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    /**
     * Re-filters active radio recommendations by selecting a mood/vibe chip.
     */
    fun applyMoodChip(chip: MoodChip) {
        val seed = currentSeedVideoId ?: return
        scope.launch {
            val result = recommendationApi.getRadioQueue(seed, chipParams = chip.params)
            if (result.isSuccess) {
                val data = result.getOrThrow()
                continuationToken = data.continuationToken
                _availableChips.value = data.chips.map {
                    if (it.id == chip.id) it.copy(isSelected = true) else it.copy(isSelected = false)
                }

                val currentTrack = currentTrack()
                val list = if (currentTrack != null) {
                    listOf(currentTrack) + data.tracks.filter { it.id != currentTrack.id }
                } else {
                    data.tracks
                }
                _queue.value = list
                _currentIndex.value = 0
            }
        }
    }

    fun currentTrack(): Track? {
        val idx = _currentIndex.value
        val list = _queue.value
        return if (idx in list.indices) list[idx] else null
    }

    fun next(): Track? {
        val nextIdx = _currentIndex.value + 1
        val list = _queue.value
        return if (nextIdx in list.indices) {
            _currentIndex.value = nextIdx
            checkAndFetchMore()
            list[nextIdx]
        } else {
            null
        }
    }

    fun previous(): Track? {
        val prevIdx = _currentIndex.value - 1
        val list = _queue.value
        return if (prevIdx >= 0 && list.isNotEmpty()) {
            _currentIndex.value = prevIdx
            list[prevIdx]
        } else {
            null
        }
    }

    fun setIndex(index: Int) {
        if (index in _queue.value.indices) {
            _currentIndex.value = index
            checkAndFetchMore()
        }
    }

    fun removeAt(index: Int) {
        val list = _queue.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _queue.value = list
            if (_currentIndex.value >= index && _currentIndex.value > 0) {
                _currentIndex.value -= 1
            }
        }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        val list = _queue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _queue.value = list
        }
    }
}
