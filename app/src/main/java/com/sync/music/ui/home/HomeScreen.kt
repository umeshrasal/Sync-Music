package com.sync.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sync.music.core.database.dao.OfflineTrackRecord
import com.sync.music.core.model.Track
import com.sync.music.ui.theme.YtBackground
import com.sync.music.ui.theme.YtPrimary
import com.sync.music.ui.theme.YtSecondaryText
import com.sync.music.ui.theme.YtSurfaceVariant

@Composable
fun HomeScreen(
    offlineTracks: List<OfflineTrackRecord>,
    onPlayTrack: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val quickPicks = remember {
        listOf(
            Track(
                id = "kJQP7kiw5Fk",
                title = "Despacito",
                artist = "Luis Fonsi ft. Daddy Yankee",
                thumbnailUrl = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
                durationMs = 282000L
            ),
            Track(
                id = "JGwWNGJdvx8",
                title = "Shape of You",
                artist = "Ed Sheeran",
                thumbnailUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg",
                durationMs = 233000L
            ),
            Track(
                id = "RgKAFK5djSk",
                title = "See You Again",
                artist = "Wiz Khalifa ft. Charlie Puth",
                thumbnailUrl = "https://i.ytimg.com/vi/RgKAFK5djSk/hqdefault.jpg",
                durationMs = 230000L
            ),
            Track(
                id = "fJ9rUzIMcZQ",
                title = "Bohemian Rhapsody",
                artist = "Queen",
                thumbnailUrl = "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg",
                durationMs = 359000L
            )
        )
    }

    val moodCategories = listOf("Energize", "Workout", "Relax", "Focus", "Commute")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(YtBackground),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // App Bar Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = " Music",
                    color = YtPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Mood filters
        item {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(moodCategories) { cat ->
                    AssistChip(
                        onClick = { /* filter recommendation stream */ },
                        label = { Text(text = cat, color = Color.White, fontSize = 13.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = YtSurfaceVariant),
                        border = null
                    )
                }
            }
        }

        // Quick Picks Section
        item {
            SectionHeader(title = "Quick picks", subtitle = "START RADIO FROM A SONG")
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(quickPicks) { track ->
                    QuickPickCard(track = track, onClick = { onPlayTrack(track) })
                }
            }
        }

        // Offline Library Section
        if (offlineTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(title = "Downloaded Vault", subtitle = "AES-256 ENCRYPTED OFFLINE TRACKS")
            }
            items(offlineTracks) { record ->
                val track = Track(
                    id = record.id,
                    title = record.title,
                    artist = record.artist,
                    album = record.album,
                    durationMs = record.durationMs,
                    thumbnailUrl = record.thumbnailUrl,
                    loudnessDb = record.loudnessDb,
                    isOffline = true,
                    offlineUri = "sync-offline://${record.id}"
                )
                OfflineTrackItem(record = record, onClick = { onPlayTrack(track) })
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = subtitle,
            color = YtSecondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuickPickCard(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(YtSurfaceVariant)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            color = YtSecondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OfflineTrackItem(
    record: OfflineTrackRecord,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = record.thumbnailUrl,
            contentDescription = record.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = record.artist,
                color = YtSecondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Rounded.DownloadDone,
            contentDescription = "Saved Offline",
            tint = YtPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}
