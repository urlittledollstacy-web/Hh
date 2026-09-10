package app.hikari.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import app.hikari.core.model.MediaType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_media", primaryKeys = ["id", "type"])
data class CachedMediaEntity(
    val id: Int,
    val type: MediaType,
    val title: String,
    val coverUrl: String?,
    val averageScore: Int?,
    val count: Int?,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface MediaCacheDao {
    @Query("SELECT * FROM cached_media WHERE type = :type ORDER BY updatedAt DESC LIMIT :limit")
    fun observe(type: MediaType, limit: Int): Flow<List<CachedMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(media: List<CachedMediaEntity>)
}

class DatabaseConverters {
    @TypeConverter fun toMediaType(value: String): MediaType = MediaType.valueOf(value)
    @TypeConverter fun fromMediaType(value: MediaType): String = value.name
}

@TypeConverters(DatabaseConverters::class)
@Database(entities = [CachedMediaEntity::class, HikariFavoriteEntity::class], version = 2, exportSchema = false)
abstract class HikariDatabase : RoomDatabase() {
    abstract fun mediaCacheDao(): MediaCacheDao
    abstract fun hikariFavoriteDao(): HikariFavoriteDao
}
