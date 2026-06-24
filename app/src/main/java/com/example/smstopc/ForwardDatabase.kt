package com.example.smstopc

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "forward_records")
data class ForwardRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val content: String,
    val code: String,
    val success: Boolean = false,
    val errorMsg: String = "",
    val pending: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ForwardDao {
    @Query("SELECT * FROM forward_records ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecent(): List<ForwardRecord>

    @Query("SELECT * FROM forward_records ORDER BY timestamp DESC LIMIT 50")
    fun getRecentFlow(): Flow<List<ForwardRecord>>

    @Insert
    suspend fun insert(record: ForwardRecord): Long

    @Query("UPDATE forward_records SET success = :success, errorMsg = :errorMsg, pending = 0 WHERE id = :id")
    suspend fun updateResult(id: Long, success: Boolean, errorMsg: String)

    @Query("SELECT * FROM forward_records WHERE pending = 1 ORDER BY timestamp ASC")
    suspend fun getPending(): List<ForwardRecord>

    @Query("DELETE FROM forward_records WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM forward_records WHERE success = 1")
    suspend fun countSuccess(): Int
}

@Database(entities = [ForwardRecord::class], version = 1, exportSchema = false)
abstract class ForwardDatabase : RoomDatabase() {
    abstract fun forwardDao(): ForwardDao

    companion object {
        @Volatile
        private var INSTANCE: ForwardDatabase? = null

        fun getInstance(): ForwardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    App.context,
                    ForwardDatabase::class.java,
                    "forward_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
