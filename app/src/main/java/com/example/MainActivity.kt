package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Song
import com.example.ui.MainViewModel
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.FullPlayerSheet
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.theme.PulseMusicTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PulseMusicTheme {
                PulseMusicApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun PulseMusicApp(viewModel: MainViewModel) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()

    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isFullPlayerExpanded by viewModel.isFullPlayerExpanded.collectAsStateWithLifecycle()
    val selectedPlaylistWithSongs by viewModel.selectedPlaylistWithSongs.collectAsStateWithLifecycle()
    val scanMessage by viewModel.scanMessage.collectAsStateWithLifecycle()

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main view switching between Home and Playlist Detail
            val playlistDetail = selectedPlaylistWithSongs
            if (playlistDetail != null) {
                PlaylistDetailScreen(
                    playlistWithSongs = playlistDetail,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onBackClick = { viewModel.selectedPlaylistId.value = null },
                    onPlaySong = { song, queue -> viewModel.playSong(song, queue) },
                    onPlayAll = { queue -> viewModel.playSong(queue.first(), queue) },
                    onRemoveSongFromPlaylist = { playlistId, songId ->
                        viewModel.removeSongFromPlaylist(playlistId, songId)
                    },
                    onDeletePlaylist = {
                        viewModel.deletePlaylist(playlistDetail.playlist)
                    }
                )
            } else {
                HomeScreen(
                    songs = songs,
                    playlists = playlists,
                    favoriteSongs = favoriteSongs,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    searchQuery = searchQuery,
                    selectedTab = selectedTab,
                    scanMessage = scanMessage,
                    onSearchQueryChange = { viewModel.searchQuery.value = it },
                    onTabSelected = { viewModel.selectedTab.value = it },
                    onPlaySong = { song, queue -> viewModel.playSong(song, queue) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onScanStorage = { viewModel.scanLocalStorage() },
                    onSelectPlaylist = { viewModel.selectedPlaylistId.value = it },
                    onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                    onAddToPlaylistClick = { songToAddToPlaylist = it },
                    onClearScanMessage = { viewModel.clearScanMessage() }
                )
            }

            // Bottom Mini Player
            if (currentSong != null && !isFullPlayerExpanded) {
                MiniPlayerBar(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    onPlayPauseToggle = { viewModel.togglePlayPause() },
                    onSkipNext = { viewModel.skipNext(fromGesture = false) },
                    onExpandPlayer = { viewModel.isFullPlayerExpanded.value = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Animated Full Player Sheet
            AnimatedVisibility(
                visible = isFullPlayerExpanded && currentSong != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                currentSong?.let { song ->
                    FullPlayerSheet(
                        song = song,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        gestureFeedbackFlow = viewModel.gestureFeedback,
                        onPlayPauseToggle = { viewModel.togglePlayPause() },
                        onSkipNext = { viewModel.skipNext(it) },
                        onSkipPrevious = { viewModel.skipPrevious(it) },
                        onSeekTo = { viewModel.seekTo(it) },
                        onFastForward = { viewModel.fastForward(true) },
                        onRewind = { viewModel.rewind(true) },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onToggleRepeat = { viewModel.toggleRepeat() },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onAddToPlaylistClick = { songToAddToPlaylist = it },
                        onCollapse = { viewModel.isFullPlayerExpanded.value = false }
                    )
                }
            }

            // Dialogs
            if (showCreatePlaylistDialog) {
                CreatePlaylistDialog(
                    onDismiss = { showCreatePlaylistDialog = false },
                    onCreate = { name, description, gradientIndex ->
                        viewModel.createPlaylist(name, description, gradientIndex)
                    }
                )
            }

            songToAddToPlaylist?.let { targetSong ->
                AddToPlaylistDialog(
                    song = targetSong,
                    playlists = playlists,
                    onDismiss = { songToAddToPlaylist = null },
                    onSelectPlaylist = { playlistId ->
                        viewModel.addSongToPlaylist(playlistId, targetSong.id)
                    },
                    onCreateNewPlaylistClick = {
                        showCreatePlaylistDialog = true
                    }
                )
            }
        }
    }
}
