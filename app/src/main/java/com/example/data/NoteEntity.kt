package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val sourceType: String, // "YouTube", "Audio File", "Video File", "Voice Recording", "Manual"
    val sourceUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val tags: String = "" // comma separated tags
)
