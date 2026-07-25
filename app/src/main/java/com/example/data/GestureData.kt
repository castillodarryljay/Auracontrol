package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "gesture_mappings")
data class GestureMapping(
    @PrimaryKey val gestureId: String, // SWIPE_LEFT, SWIPE_RIGHT, SWIPE_UP, SWIPE_DOWN, WAVE, HOVER
    val gestureName: String,           // User-friendly display name
    val actionId: String,              // BACK, HOME, RECENTS, PLAY_PAUSE, NEXT_TRACK, PREVIOUS_TRACK, VOLUME_UP, VOLUME_DOWN, TOGGLE_FLASHLIGHT, SCROLL_UP, SCROLL_DOWN, NONE
    val actionName: String             // User-friendly description of action
)

@Dao
interface GestureMappingDao {
    @Query("SELECT * FROM gesture_mappings")
    fun getAllMappings(): Flow<List<GestureMapping>>

    @Query("SELECT COUNT(*) FROM gesture_mappings")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: GestureMapping)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<GestureMapping>)

    @Query("UPDATE gesture_mappings SET actionId = :actionId, actionName = :actionName WHERE gestureId = :gestureId")
    suspend fun updateMapping(gestureId: String, actionId: String, actionName: String)
}

@Database(entities = [GestureMapping::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gestureMappingDao(): GestureMappingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gesture_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class GestureRepository(private val dao: GestureMappingDao) {
    val allMappings: Flow<List<GestureMapping>> = dao.getAllMappings()

    suspend fun updateMapping(gestureId: String, actionId: String, actionName: String) {
        dao.updateMapping(gestureId, actionId, actionName)
    }

    suspend fun populateDefaultsIfNeeded() {
        if (dao.getCount() == 0) {
            val defaults = listOf(
                // Open Hand Category
                GestureMapping("OPEN_PALM", "Open Hand (Palm)", "PLAY_PAUSE", "Play/Pause Media"),
                GestureMapping("WAVE", "Wave Hand", "NONE", "No Action"),
                GestureMapping("SWIPE_LEFT", "Swipe Left", "NEXT_TRACK", "Next Track"),
                GestureMapping("SWIPE_RIGHT", "Swipe Right", "PREVIOUS_TRACK", "Previous Track"),
                GestureMapping("SWIPE_UP", "Swipe Up", "VOLUME_UP", "Volume Up"),
                GestureMapping("SWIPE_DOWN", "Swipe Down", "VOLUME_DOWN", "Volume Down"),

                // Closed Hand Category
                GestureMapping("FIST", "Fist (Closed)", "NONE", "No Action"),
                GestureMapping("PEACE_SIGN", "Peace Sign", "HOME", "Go Home"),
                GestureMapping("ROCK_ON", "Rock On", "TOGGLE_FLASHLIGHT", "Toggle Flashlight"),
                GestureMapping("THUMBS_UP", "Thumbs Up", "SCROLL_UP", "Scroll Up"),
                GestureMapping("THUMBS_DOWN", "Thumbs Down", "SCROLL_DOWN", "Scroll Down"),

                // Finger Raise Category
                GestureMapping("INDEX_RAISED", "Pointer Finger", "NONE", "No Action"),
                GestureMapping("MIDDLE_RAISED", "Middle Finger", "NONE", "No Action"),
                GestureMapping("PINKY_RAISED", "Pinky Finger", "BACK", "Go Back"),
                GestureMapping("INDEX_MIDDLE_RAISED", "Pointer & Middle", "NONE", "No Action"),

                // Pinch Category
                GestureMapping("INDEX_PINCH", "Index Pinch", "NONE", "No Action"),
                GestureMapping("MIDDLE_PINCH", "Middle Pinch", "NONE", "No Action"),
                GestureMapping("RING_PINCH", "Ring Pinch", "NONE", "No Action"),
                GestureMapping("PINKY_PINCH", "Pinky Pinch", "NONE", "No Action"),

                // Combinations Category
                GestureMapping("COMBO_FIST_OPEN", "Fist to Open Hand", "RECENTS", "Recent Apps"),
                GestureMapping("COMBO_PINCH_SWIPE", "Pinch to Swipe Up", "SCREENSHOT", "Take Screenshot"),

                // Proximity Sensor Category
                GestureMapping("PROXIMITY_WAVE", "Proximity Wave", "NONE", "No Action"),
                GestureMapping("PROXIMITY_HOVER", "Proximity Hover", "NONE", "No Action")
            )
            dao.insertAll(defaults)
        }
    }
}
