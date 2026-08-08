package com.example.playback

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import com.example.model.Song
import com.example.service.MusicPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RepeatMode {
    OFF, ALL, ONE
}

sealed class GestureFeedbackEvent {
    data class SkippedNext(val songTitle: String) : GestureFeedbackEvent()
    data class SkippedPrevious(val songTitle: String) : GestureFeedbackEvent()
    data class FastForwarded(val seconds: Int) : GestureFeedbackEvent()
    data class Rewound(val seconds: Int) : GestureFeedbackEvent()
}

object MusicPlayerManager : MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private var mediaPlayer: MediaPlayer? = null
    private var applicationContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _gestureFeedback = MutableSharedFlow<GestureFeedbackEvent>()
    val gestureFeedback: SharedFlow<GestureFeedbackEvent> = _gestureFeedback.asSharedFlow()

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    fun playSong(song: Song, playlistQueue: List<Song> = emptyList()) {
        val context = applicationContext ?: return

        if (playlistQueue.isNotEmpty()) {
            _queue.value = playlistQueue
        } else if (!_queue.value.contains(song)) {
            _queue.value = listOf(song)
        }

        _currentSong.value = song

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, Uri.parse(song.contentUri))
                setOnCompletionListener(this@MusicPlayerManager)
                setOnErrorListener(this@MusicPlayerManager)
                prepare()
                start()
            }

            mediaPlayer = player
            _isPlaying.value = true
            _durationMs.value = player.duration.toLong()
            startProgressTracker()

            // Start background service
            startPlaybackService(context)

        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: run {
            _currentSong.value?.let { playSong(it) }
            return
        }

        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
            stopProgressTracker()
        } else {
            player.start()
            _isPlaying.value = true
            startProgressTracker()
            applicationContext?.let { startPlaybackService(it) }
        }
    }

    fun skipNext(fromGesture: Boolean = false) {
        val currentList = _queue.value
        if (currentList.isEmpty()) return

        val current = _currentSong.value
        val currentIndex = currentList.indexOf(current)

        val nextIndex = if (_isShuffle.value) {
            (0 until currentList.size).random()
        } else {
            if (currentIndex == -1 || currentIndex >= currentList.size - 1) 0 else currentIndex + 1
        }

        val nextSong = currentList[nextIndex]
        playSong(nextSong)

        if (fromGesture) {
            scope.launch {
                _gestureFeedback.emit(GestureFeedbackEvent.SkippedNext(nextSong.title))
            }
        }
    }

    fun skipPrevious(fromGesture: Boolean = false) {
        val currentList = _queue.value
        if (currentList.isEmpty()) return

        val player = mediaPlayer
        if (player != null && player.currentPosition > 3000) {
            // Seek to start if already playing past 3 seconds
            player.seekTo(0)
            _currentPositionMs.value = 0
            return
        }

        val current = _currentSong.value
        val currentIndex = currentList.indexOf(current)

        val prevIndex = if (currentIndex <= 0) currentList.size - 1 else currentIndex - 1
        val prevSong = currentList[prevIndex]
        playSong(prevSong)

        if (fromGesture) {
            scope.launch {
                _gestureFeedback.emit(GestureFeedbackEvent.SkippedPrevious(prevSong.title))
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let {
            it.seekTo(positionMs.toInt())
            _currentPositionMs.value = positionMs
        }
    }

    fun fastForward(ms: Long = 10000, fromGesture: Boolean = false) {
        val player = mediaPlayer ?: return
        val newPos = (player.currentPosition + ms).coerceAtMost(player.duration.toLong())
        player.seekTo(newPos.toInt())
        _currentPositionMs.value = newPos

        if (fromGesture) {
            scope.launch {
                _gestureFeedback.emit(GestureFeedbackEvent.FastForwarded((ms / 1000).toInt()))
            }
        }
    }

    fun rewind(ms: Long = 10000, fromGesture: Boolean = false) {
        val player = mediaPlayer ?: return
        val newPos = (player.currentPosition - ms).coerceAtLeast(0)
        player.seekTo(newPos.toInt())
        _currentPositionMs.value = newPos

        if (fromGesture) {
            scope.launch {
                _gestureFeedback.emit(GestureFeedbackEvent.Rewound((ms / 1000).toInt()))
            }
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    override fun onCompletion(mp: MediaPlayer?) {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _currentSong.value?.let { playSong(it) }
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                skipNext()
            }
        }
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        _isPlaying.value = false
        stopProgressTracker()
        return true
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (_isPlaying.value) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        _currentPositionMs.value = it.currentPosition.toLong()
                        _durationMs.value = it.duration.toLong()
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun startPlaybackService(context: Context) {
        try {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        stopProgressTracker()
    }
}
