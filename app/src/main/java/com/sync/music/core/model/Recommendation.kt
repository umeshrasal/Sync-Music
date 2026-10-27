package com.sync.music.core.model

data class MoodChip(
    val id: String,
    val title: String,
    val params: String,
    val isSelected: Boolean = false
)

data class MixCategory(
    val title: String,
    val subtitle: String? = null,
    val tracks: List<Track>
)

data class RadioQueueResult(
    val tracks: List<Track>,
    val continuationToken: String?,
    val chips: List<MoodChip>
)
