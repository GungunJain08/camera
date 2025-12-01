package com.example.cameraapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// This tells Room to create a table named "photos_table" for this class.
@Entity(tableName = "photos_table")
data class Photo(
    // This is the unique ID for each photo entry. It's a "PrimaryKey".
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // The path to the image file on the phone (e.g., "content://...")
    val imageUri: String,

    // The location where the photo was taken
    val latitude: Double,
    val longitude: Double,

    // The time when the photo was taken
    val timestamp: Long
)