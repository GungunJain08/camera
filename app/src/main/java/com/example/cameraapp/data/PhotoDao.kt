package com.example.cameraapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// @Dao tells Room that this is our manager interface.
@Dao
interface PhotoDao {

    // This is the "add new entry" function.
    // OnConflictStrategy.IGNORE means if we try to insert a photo that
    // already exists with the same ID, just ignore the new one.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(photo: Photo) // 'suspend' means it must be called from a background thread.

    // This is the "read all entries" function.
    // The @Query contains the SQL command to get all rows from our table,
    // ordered by the newest first (based on the timestamp).
    @Query("SELECT * FROM photos_table ORDER BY timestamp DESC")
    suspend fun getAllPhotos(): List<Photo>
}