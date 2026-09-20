package com.awnish.calculator.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "photos")
data class PhotoEntity(@PrimaryKey val id: String, val displayName: String, val mimeType: String, val album: String = "All photos", val createdAt: Long, val sizeBytes: Long, val path: String, val deletedAt: Long? = null)

@Dao interface PhotoDao {
    @Query("SELECT * FROM photos WHERE deletedAt IS NULL AND displayName LIKE '%' || :query || '%' ORDER BY createdAt DESC") fun observe(query: String): Flow<List<PhotoEntity>>
    @Query("SELECT * FROM photos WHERE deletedAt IS NULL ORDER BY createdAt DESC") suspend fun all(): List<PhotoEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(photo: PhotoEntity)
    @Query("UPDATE photos SET deletedAt = :now WHERE id = :id") suspend fun trash(id: String, now: Long)
    @Query("UPDATE photos SET deletedAt = NULL WHERE id = :id") suspend fun restore(id: String)
    @Delete suspend fun delete(photo: PhotoEntity)
}
@Database(entities = [PhotoEntity::class], version = 1, exportSchema = false)
abstract class GalleryDatabase : RoomDatabase() { abstract fun photos(): PhotoDao }
