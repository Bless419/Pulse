package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.data.db.PlaylistDao
import com.example.data.synth.AudioSynthGenerator
import com.example.model.Playlist
import com.example.model.PlaylistSongCrossRef
import com.example.model.PlaylistWithSongs
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SongRepository(
    private val context: Context,
    private val playlistDao: PlaylistDao
) {

    val allSongs: Flow<List<Song>> = playlistDao.getAllSongs()
    val favoriteSongs: Flow<List<Song>> = playlistDao.getFavoriteSongs()
    val playlists: Flow<List<PlaylistWithSongs>> = playlistDao.getAllPlaylistsWithSongs()

    suspend fun initDefaultSongsAndPlaylists() = withContext(Dispatchers.IO) {
        val uri1 = AudioSynthGenerator.getOrCreateDemoAudioUri(context, "synthwave_pulse.wav", 440.0f, true)
        val uri2 = AudioSynthGenerator.getOrCreateDemoAudioUri(context, "lofi_sunset.wav", 330.0f, true)
        val uri3 = AudioSynthGenerator.getOrCreateDemoAudioUri(context, "cyber_ambient.wav", 523.25f, false)
        val uri4 = AudioSynthGenerator.getOrCreateDemoAudioUri(context, "midnight_groove.wav", 293.66f, true)

        val demoSongs = listOf(
            Song(
                id = "demo_1",
                title = "Synthwave Pulse",
                artist = "Aura Electronic",
                album = "Neon Highways",
                durationMs = 30000,
                contentUri = uri1,
                artworkResName = "img_album_cover1_1786222660381",
                source = Song.SOURCE_DEMO
            ),
            Song(
                id = "demo_2",
                title = "Midnight Chill",
                artist = "Lofi Dreams",
                album = "Quiet Hours",
                durationMs = 30000,
                contentUri = uri2,
                artworkResName = "img_album_cover2_1786222671906",
                source = Song.SOURCE_DEMO
            ),
            Song(
                id = "demo_3",
                title = "Cyber Ambient Flow",
                artist = "Pixel Echo",
                album = "Digital Horizons",
                durationMs = 30000,
                contentUri = uri3,
                artworkResName = "img_album_cover3_1786222683717",
                source = Song.SOURCE_DEMO
            ),
            Song(
                id = "demo_4",
                title = "Neon Sunset Groove",
                artist = "Aura Electronic",
                album = "Neon Highways",
                durationMs = 30000,
                contentUri = uri4,
                artworkResName = "img_album_cover1_1786222660381",
                source = Song.SOURCE_DEMO
            )
        )

        playlistDao.insertSongs(demoSongs)

        // Seed default playlists if none exist
        val defaultPlaylists = listOf(
            Playlist(name = "Chill Night Beats", description = "Relaxing synth and lofi tracks for late night study", gradientIndex = 0),
            Playlist(name = "Cyber Energy", description = "High tempo electronic synthwave driving tracks", gradientIndex = 1)
        )

        defaultPlaylists.forEach { pl ->
            val id = playlistDao.insertPlaylist(pl)
            if (id > 0) {
                if (pl.name.contains("Chill")) {
                    playlistDao.addSongToPlaylist(PlaylistSongCrossRef(id, "demo_2"))
                    playlistDao.addSongToPlaylist(PlaylistSongCrossRef(id, "demo_3"))
                } else {
                    playlistDao.addSongToPlaylist(PlaylistSongCrossRef(id, "demo_1"))
                    playlistDao.addSongToPlaylist(PlaylistSongCrossRef(id, "demo_4"))
                }
            }
        }
    }

    /**
     * Scans user's device local storage for offline audio files (MP3, AAC, WAV, M4A, FLAC) via MediaStore.
     */
    suspend fun scanDeviceLocalAudio(): Int = withContext(Dispatchers.IO) {
        val localSongs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val title = c.getString(titleColumn) ?: "Unknown Track"
                    val artist = c.getString(artistColumn) ?: "Unknown Artist"
                    val album = c.getString(albumColumn) ?: "Local Music"
                    val duration = c.getLong(durationColumn)

                    if (duration > 2000) { // filter out short notification ringtones
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        ).toString()

                        localSongs.add(
                            Song(
                                id = "local_$id",
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = duration,
                                contentUri = contentUri,
                                artworkResName = null,
                                source = Song.SOURCE_LOCAL
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (localSongs.isNotEmpty()) {
            playlistDao.insertSongs(localSongs)
        }

        return@withContext localSongs.size
    }

    suspend fun toggleFavorite(song: Song) = withContext(Dispatchers.IO) {
        playlistDao.setFavorite(song.id, !song.isFavorite)
    }

    suspend fun createPlaylist(name: String, description: String, gradientIndex: Int): Long = withContext(Dispatchers.IO) {
        val playlist = Playlist(name = name, description = description, gradientIndex = gradientIndex)
        return@withContext playlistDao.insertPlaylist(playlist)
    }

    suspend fun deletePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: String) = withContext(Dispatchers.IO) {
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?> {
        return playlistDao.getPlaylistWithSongs(playlistId)
    }
}
