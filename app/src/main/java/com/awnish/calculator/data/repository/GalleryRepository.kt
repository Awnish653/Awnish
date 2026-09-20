package com.awnish.calculator.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.room.Room
import com.awnish.calculator.data.crypto.GalleryCipher
import com.awnish.calculator.data.database.GalleryDatabase
import com.awnish.calculator.data.database.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class GalleryRepository(private val context: Context) {
    private val database = Room.databaseBuilder(
        context,
        GalleryDatabase::class.java,
        "gallery.db"
    ).addMigrations(GalleryDatabase.MIGRATION_1_2).build()

    private val dao = database.photos()
    private val cipher = GalleryCipher()
    private val directory = File(context.filesDir, "private_gallery").apply { mkdirs() }

    fun photos(query: String, category: String = "All"): Flow<List<PhotoEntity>> =
        dao.observe(query, category)

    suspend fun import(uri: Uri, removeOriginal: Boolean = true) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Private file"

        val type = resolver.getType(uri) ?: mimeFromName(name)
        val category = categoryFor(type, name)
        val raw = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read selected file")

        val id = UUID.randomUUID().toString()
        val file = File(directory, "$id.awn")

        try {
            file.writeBytes(cipher.encrypt(raw))
            dao.insert(
                PhotoEntity(
                    id = id,
                    displayName = name,
                    mimeType = type,
                    album = category,
                    category = category,
                    createdAt = System.currentTimeMillis(),
                    sizeBytes = raw.size.toLong(),
                    path = file.name,
                    originalUri = uri.toString()
                )
            )

            var originalRemoved = false
            if (removeOriginal) {
                originalRemoved = runCatching { deleteOriginal(uri) }.getOrDefault(false)
            }
            originalRemoved
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    suspend fun bytes(item: PhotoEntity): ByteArray =
        withContext(Dispatchers.IO) {
            cipher.decrypt(File(directory, item.path).readBytes())
        }

    fun fileUri(item: PhotoEntity): Uri =
        FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            File(directory, item.path)
        )

    suspend fun export(item: PhotoEntity): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, item.displayName)
            put(MediaStore.Downloads.MIME_TYPE, item.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/AWNISH Vault")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val uri = resolver.insert(collection, values) ?: error("Unable to create export")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes(item)) }
                ?: error("Unable to export file")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    suspend fun trash(item: PhotoEntity) =
        dao.trash(item.id, System.currentTimeMillis())

    suspend fun permanentlyDelete(item: PhotoEntity) =
        withContext(Dispatchers.IO) {
            File(directory, item.path).delete()
            dao.delete(item)
        }

    private fun deleteOriginal(uri: Uri): Boolean {
        return runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                context.contentResolver.delete(uri, null, null) > 0
            }
        }.getOrDefault(false)
    }

    private fun categoryFor(type: String, name: String): String {
        val lower = name.lowercase()
        return when {
            type.startsWith("image/") -> "Photos"
            type.startsWith("video/") -> "Videos"
            type == "application/vnd.android.package-archive" || lower.endsWith(".apk") -> "Apps & Games"
            type.startsWith("text/") ||
                type.contains("pdf") ||
                type.contains("document") ||
                type.contains("word") ||
                type.contains("sheet") ||
                type.contains("presentation") ||
                lower.endsWith(".doc") ||
                lower.endsWith(".docx") ||
                lower.endsWith(".pdf") ||
                lower.endsWith(".txt") ||
                lower.endsWith(".xls") ||
                lower.endsWith(".xlsx") ||
                lower.endsWith(".ppt") ||
                lower.endsWith(".pptx") -> "Documents"
            else -> "Others"
        }
    }

    private fun mimeFromName(name: String): String {
        return when {
            name.endsWith(".pdf", true) -> "application/pdf"
            name.endsWith(".apk", true) -> "application/vnd.android.package-archive"
            name.endsWith(".mp4", true) -> "video/mp4"
            name.endsWith(".mkv", true) -> "video/x-matroska"
            name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
            name.endsWith(".png", true) -> "image/png"
            name.endsWith(".webp", true) -> "image/webp"
            name.endsWith(".txt", true) -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
