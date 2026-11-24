package com.cinematv.app.data.local.db

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

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val compositeKey: String, // "$mediaId::${episodeId ?: "movie"}"
    val mediaId: String,
    val episodeId: String?,
    val title: String,
    val posterUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "user_list")
data class UserListEntity(
    @PrimaryKey val mediaId: String,
    val providerId: String,
    val isWatchlist: Boolean,
    val isFavorite: Boolean,
    val addedAtEpochMs: Long
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAtEpochMs: Long
)

@Entity(tableName = "authorized_streams")
data class AuthorizedStreamEntity(
    @PrimaryKey val id: String,
    val title: String,
    val arabicTitle: String?,
    val streamUrl: String,
    val mimeType: String,
    val subtitleUrl: String?,
    val createdAtEpochMs: Long
)

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE isCompleted = 0 ORDER BY updatedAtEpochMs DESC")
    fun observeContinueWatching(): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE compositeKey = :key LIMIT 1")
    suspend fun getByKey(key: String): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE compositeKey = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM watch_progress")
    suspend fun clearAll()
}

@Dao
interface UserListDao {
    @Query("SELECT * FROM user_list ORDER BY addedAtEpochMs DESC")
    fun observeAll(): Flow<List<UserListEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserListEntity)

    @Query("DELETE FROM user_list WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: String)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAtEpochMs DESC LIMIT 15")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

@Dao
interface AuthorizedStreamDao {
    @Query("SELECT * FROM authorized_streams ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<AuthorizedStreamEntity>>

    @Query("SELECT * FROM authorized_streams ORDER BY createdAtEpochMs DESC")
    suspend fun getAllOnce(): List<AuthorizedStreamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AuthorizedStreamEntity)

    @Query("DELETE FROM authorized_streams WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [
        WatchProgressEntity::class,
        UserListEntity::class,
        SearchHistoryEntity::class,
        AuthorizedStreamEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CinemaDatabase : RoomDatabase() {
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun userListDao(): UserListDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun authorizedStreamDao(): AuthorizedStreamDao

    companion object {
        fun build(context: Context): CinemaDatabase =
            Room.databaseBuilder(context, CinemaDatabase::class.java, "cinema_tv.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
