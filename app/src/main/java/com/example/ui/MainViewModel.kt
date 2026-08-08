package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.repository.SongRepository
import com.example.model.Playlist
import com.example.model.PlaylistWithSongs
import com.example.model.Song
import com.example.playback.GestureFeedbackEvent
import com.example.playback.MusicPlayerManager
import com.example.playback.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SongRepository

    val searchQuery = MutableStateFlow("")
    val selectedTab = MutableStateFlow(0) // 0: All Songs, 1: Playlists, 2: Favorites

    val currentSong = MusicPlayerManager.currentSong
    val isPlaying = MusicPlayerManager.isPlaying
    val currentPositionMs = MusicPlayerManager.currentPositionMs
    val durationMs = MusicPlayerManager.durationMs
    val isShuffle = MusicPlayerManager.isShuffle
    val repeatMode = MusicPlayerManager.repeatMode
    val gestureFeedback = MusicPlayerManager.gestureFeedback

    val isFullPlayerExpanded = MutableStateFlow(false)
    val selectedPlaylistId = MutableStateFlow<Long?>(null)

    val scanMessage = MutableStateFlow<String?>(null)

    init {
        val database = AppDatabase.getInstance(application)
        repository = SongRepository(application, database.playlistDao())
        MusicPlayerManager.init(application)

        viewModelScope.launch {
            repository.initDefaultSongsAndPlaylists()
        }
    }

    val songs: StateFlow<List<Song>> = combine(repository.allSongs, searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val playlists: StateFlow<List<PlaylistWithSongs>> = repository.playlists.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val selectedPlaylistWithSongs: StateFlow<PlaylistWithSongs?> = selectedPlaylistId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else repository.getPlaylistWithSongs(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun playSong(song: Song, playlist: List<Song> = emptyList()) {
        val queue = playlist.ifEmpty { songs.value }
        MusicPlayerManager.playSong(song, queue)
    }

    fun togglePlayPause() = MusicPlayerManager.togglePlayPause()
    fun skipNext(fromGesture: Boolean = false) = MusicPlayerManager.skipNext(fromGesture)
    fun skipPrevious(fromGesture: Boolean = false) = MusicPlayerManager.skipPrevious(fromGesture)
    fun seekTo(positionMs: Long) = MusicPlayerManager.seekTo(positionMs)
    fun fastForward(fromGesture: Boolean = true) = MusicPlayerManager.fastForward(10000, fromGesture)
    fun rewind(fromGesture: Boolean = true) = MusicPlayerManager.rewind(10000, fromGesture)
    fun toggleShuffle() = MusicPlayerManager.toggleShuffle()
    fun toggleRepeat() = MusicPlayerManager.toggleRepeat()

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song)
        }
    }

    fun scanLocalStorage() {
        viewModelScope.launch {
            val count = repository.scanDeviceLocalAudio()
            scanMessage.value = if (count > 0) "Discovered $count local tracks!" else "No new audio files found"
        }
    }

    fun createPlaylist(name: String, description: String, gradientIndex: Int) {
        viewModelScope.launch {
            repository.createPlaylist(name, description, gradientIndex)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            if (selectedPlaylistId.value == playlist.id) {
                selectedPlaylistId.value = null
            }
            repository.deletePlaylist(playlist)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun clearScanMessage() {
        scanMessage.value = null
    }
}
