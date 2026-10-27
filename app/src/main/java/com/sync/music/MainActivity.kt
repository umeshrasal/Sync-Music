package com.sync.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.sync.music.ui.MainViewModel
import com.sync.music.ui.home.HomeScreen
import com.sync.music.ui.player.FullPlayerSheet
import com.sync.music.ui.player.MiniPlayer
import com.sync.music.ui.theme.SyncTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkNotificationPermission()

        setContent {
            SyncTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val playerUiState by viewModel.playerUiState.collectAsState()
    val lyrics by viewModel.lyricsState.collectAsState()
    val chips by viewModel.availableChips.collectAsState()
    val offlineTracks by viewModel.offlineTracks.collectAsState()

    var isFullPlayerExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Home Screen Content
        HomeScreen(
            offlineTracks = offlineTracks,
            onPlayTrack = { track -> viewModel.playTrack(track) }
        )

        // Persistent MiniPlayer at Bottom
        if (playerUiState.currentTrack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                MiniPlayer(
                    uiState = playerUiState,
                    onExpand = { isFullPlayerExpanded = true },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.playNext() }
                )
            }
        }

        // Expandable Full-Screen Player Modal Sheet
        if (isFullPlayerExpanded && playerUiState.currentTrack != null) {
            ModalBottomSheet(
                onDismissRequest = { isFullPlayerExpanded = false },
                sheetState = sheetState,
                dragHandle = null
            ) {
                FullPlayerSheet(
                    uiState = playerUiState,
                    lyrics = lyrics,
                    chips = chips,
                    onCollapse = { isFullPlayerExpanded = false },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.playNext() },
                    onPrevious = { viewModel.playPrevious() },
                    onSeekTo = { pos -> viewModel.seekTo(pos) },
                    onToggleRepeat = { viewModel.toggleRepeat() },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onSelectQueueIndex = { idx -> viewModel.playQueueIndex(idx) },
                    onRemoveQueueItem = { idx -> viewModel.removeQueueItem(idx) },
                    onApplyMoodChip = { chip -> viewModel.applyMoodChip(chip) },
                    onDownloadTrack = { viewModel.downloadCurrentTrack() }
                )
            }
        }
    }
}
