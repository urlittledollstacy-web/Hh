package app.hikari.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.hikari.core.model.MediaType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "hikari_favorites", primaryKeys = ["mediaId", "type"])
data class HikariFavoriteEntity(
    val mediaId: Int,
    val type: MediaType,
    val title: String,
    val coverUrl: String?,
    val averageScore: Int?,
    val episodesOrChapters: Int?,
    val addedAt: Long = System.currentTimeMillis(),
)

@Dao
interface HikariFavoriteDao {
    @Query("SELECT EXISTS(SELECT 1 FROM hikari_favorites WHERE mediaId = :mediaId AND type = :type)")
    fun observeIsFavorite(mediaId: Int, type: MediaType): Flow<Boolean>

    @Query("SELECT * FROM hikari_favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<HikariFavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: HikariFavoriteEntity)

    @Query("DELETE FROM hikari_favorites WHERE mediaId = :mediaId AND type = :type")
    suspend fun remove(mediaId: Int, type: MediaType)
}
