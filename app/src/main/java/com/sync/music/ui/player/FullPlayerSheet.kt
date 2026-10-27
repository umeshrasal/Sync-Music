package com.sync.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sync.music.core.model.MoodChip
import com.sync.music.core.model.PlaybackStatus
import com.sync.music.core.model.PlayerUiState
import com.sync.music.core.model.RepeatState
import com.sync.music.core.model.TimedLyrics
import com.sync.music.ui.theme.YtBackground
import com.sync.music.ui.theme.YtPrimary
import com.sync.music.ui.theme.YtSecondaryText
import com.sync.music.ui.theme.YtSurface
import com.sync.music.ui.theme.YtSurfaceVariant
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    uiState: PlayerUiState,
    lyrics: TimedLyrics?,
    chips: List<MoodChip>,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onApplyMoodChip: (MoodChip) -> Unit,
    onDownloadTrack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = uiState.currentTrack ?: return
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val tabTitles = listOf("UP NEXT", "SONG", "LYRICS")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YtBackground)
            .padding(top = 16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Playing from Queue",
                color = YtSecondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onDownloadTrack) {
                Icon(
                    imageVector = if (track.isOffline) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                    contentDescription = "Download Offline",
                    tint = if (track.isOffline) YtPrimary else Color.White
                )
            }
        }

        // Segmented Tabs
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = Color.White
                )
            },
            divider = {}
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (pagerState.currentPage == index) Color.White else YtSecondaryText
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal Pager for the 3 Tab Views
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> QueueTabView(
                    queue = uiState.queue,
                    currentIndex = uiState.currentQueueIndex,
                    chips = chips,
                    onSelectTrack = onSelectQueueIndex,
                    onRemoveTrack = onRemoveQueueItem,
                    onApplyChip = onApplyMoodChip
                )
                1 -> SongArtTabView(
                    track = track,
                    uiState = uiState,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeekTo = onSeekTo,
                    onToggleRepeat = onToggleRepeat,
                    onToggleShuffle = onToggleShuffle
                )
                2 -> LyricsTabView(
                    lyrics = lyrics,
                    currentPositionMs = uiState.currentPositionMs,
                    onLineClick = { onSeekTo(it.timeMs) }
                )
            }
        }
    }
}

@Composable
private fun SongArtTabView(
    track: com.sync.music.core.model.Track,
    uiState: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit
) {
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val currentMs = if (isDraggingSlider) sliderPosition.toLong() else uiState.currentPositionMs
    val totalMs = uiState.totalDurationMs.coerceAtLeast(1L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // High-Res Artwork
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(YtSurface)
        )

        // Metadata Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = track.artist,
                color = YtSecondaryText,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Progress Slider & Timestamps
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f),
                onValueChange = {
                    isDraggingSlider = true
                    sliderPosition = it * totalMs
                },
                onValueChangeFinished = {
                    isDraggingSlider = false
                    onSeekTo(sliderPosition.toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTime(currentMs), color = YtSecondaryText, fontSize = 12.sp)
                Text(text = formatTime(totalMs), color = YtSecondaryText, fontSize = 12.sp)
            }
        }

        // Primary Playback Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (uiState.isShuffleOn) YtPrimary else YtSecondaryText,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Previous
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            // Play / Pause Circle
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onTogglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                if (uiState.playbackStatus == PlaybackStatus.BUFFERING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.Black,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Next
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            // Repeat (Off, All, One)
            IconButton(onClick = onToggleRepeat) {
                val (icon, tint) = when (uiState.repeatState) {
                    RepeatState.OFF -> Icons.Rounded.Repeat to YtSecondaryText
                    RepeatState.ALL -> Icons.Rounded.Repeat to YtPrimary
                    RepeatState.ONE -> Icons.Rounded.RepeatOne to YtPrimary
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Repeat",
                    tint = tint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun QueueTabView(
    queue: List<com.sync.music.core.model.Track>,
    currentIndex: Int,
    chips: List<MoodChip>,
    onSelectTrack: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onApplyChip: (MoodChip) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Mood chips row
        if (chips.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chips) { chip ->
                    FilterChip(
                        selected = chip.isSelected,
                        onClick = { onApplyChip(chip) },
                        label = { Text(text = chip.title, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = YtSurfaceVariant,
                            labelColor = Color.White,
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }
        }

        // Queue list items
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            itemsIndexed(queue) { index, item ->
                val isCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCurrent) YtSurfaceVariant.copy(alpha = 0.6f) else Color.Transparent)
                        .clickable { onSelectTrack(index) }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = if (isCurrent) YtPrimary else Color.White,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.artist,
                            color = YtSecondaryText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (queue.size > 1 && !isCurrent) {
                        IconButton(onClick = { onRemoveTrack(index) }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove",
                                tint = YtSecondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsTabView(
    lyrics: TimedLyrics?,
    currentPositionMs: Long,
    onLineClick: (com.sync.music.core.model.LyricLine) -> Unit
) {
    if (lyrics == null || lyrics.lines.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No lyrics found for this song",
                color = YtSecondaryText,
                fontSize = 16.sp
            )
        }
        return
    }

    val listState = rememberLazyListState()

    // Find active lyric index
    val activeIndex = lyrics.lines.indexOfLast { currentPositionMs >= it.timeMs }.coerceAtLeast(0)

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyrics.isSynced) {
            listState.animateScrollToItem(
                (activeIndex - 2).coerceAtLeast(0)
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isCurrent = index == activeIndex && lyrics.isSynced
            val textColor by animateColorAsState(
                targetValue = if (isCurrent) Color.White else Color.White.copy(alpha = 0.35f),
                label = "lyricTextColor"
            )

            Text(
                text = line.text,
                color = textColor,
                fontSize = if (isCurrent) 24.sp else 18.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLineClick(line) }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
