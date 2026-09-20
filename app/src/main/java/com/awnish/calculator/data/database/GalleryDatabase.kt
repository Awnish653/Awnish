package com.awnish.calculator.data.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val mimeType: String,
    val album: String = "All photos",
    val category: String = "Photos",
    val createdAt: Long,
    val sizeBytes: Long,
    val path: String,
    val deletedAt: Long? = null,
    val originalUri: String? = null
)

@Dao
interface PhotoDao {
    @Query("""
        SELECT * FROM photos
        WHERE deletedAt IS NULL
          AND (displayName LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%')
          AND (:category = 'All' OR category = :category)
        ORDER BY createdAt DESC
    """)
    fun observe(query: String, category: String): Flow<List<PhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)

    @Query("UPDATE photos SET deletedAt = :now WHERE id = :id")
    suspend fun trash(id: String, now: Long)

    @Query("UPDATE photos SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Delete
    suspend fun delete(photo: PhotoEntity)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE photos ADD COLUMN category TEXT NOT NULL DEFAULT 'Photos'")
        db.execSQL("ALTER TABLE photos ADD COLUMN originalUri TEXT")
    }
}

@Database(entities = [PhotoEntity::class], version = 2, exportSchema = false)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun photos(): PhotoDao
    companion object {
        val MIGRATION_1_2 = MIGRATION_1_2
    }
}
