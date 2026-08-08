package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val artworkResName: String? = null,
    val isFavorite: Boolean = false,
    val source: String = SOURCE_DEMO,
    val dateAdded: Long = System.currentTimeMillis()
) {
    companion object {
        const val SOURCE_LOCAL = "LOCAL"
        const val SOURCE_DEMO = "DEMO"
    }

    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}
