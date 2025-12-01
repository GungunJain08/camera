package com.example.cameraapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// @Database tells Room this is the main database class.
// 'entities' lists all the tables (we only have one: Photo).
// 'version' is important for migrations later. Start with 1.
@Database(entities = [Photo::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // This function lets the database know about our manager (DAO).
    abstract fun photoDao(): PhotoDao

    // This part ensures we only ever have ONE instance of the database
    // in our whole app, which is very important for performance.
    // This is called a "Singleton".
    companion object {
        // @Volatile means changes to this field are immediately visible to all other threads.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // If an instance already exists, return it.
            // If not, create a new one.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "photo_app_database" // This will be the name of the actual file on the phone.
                ).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}
