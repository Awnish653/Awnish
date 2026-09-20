package com.awnish.calculator.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.awnish.calculator.data.crypto.GalleryCipher
import com.awnish.calculator.data.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class GalleryRepository(private val context: Context) {
    private val dao = Room.databaseBuilder(context, GalleryDatabase::class.java, "gallery.db").build().photos()
    private val cipher = GalleryCipher(); private val directory = File(context.filesDir, "private_gallery").apply { mkdirs() }
    fun photos(query: String): Flow<List<PhotoEntity>> = dao.observe(query)
    suspend fun import(uri: Uri, album: String = "All photos") = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver; val name: String = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "Private image"
        val type = resolver.getType(uri) ?: "image/*"; require(type.startsWith("image/")) { "Choose an image file" }
        val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read selected image")
        val id = UUID.randomUUID().toString(); val file = File(directory, "$id.awn")
        try { file.writeBytes(cipher.encrypt(raw)); dao.insert(PhotoEntity(id, name, type, album, System.currentTimeMillis(), raw.size.toLong(), file.name)) } catch (e: Exception) { file.delete(); throw e }
    }
    suspend fun bytes(photo: PhotoEntity) = withContext(Dispatchers.IO) { cipher.decrypt(File(directory, photo.path).readBytes()) }
    suspend fun trash(photo: PhotoEntity) = dao.trash(photo.id, System.currentTimeMillis())
    suspend fun permanentlyDelete(photo: PhotoEntity) { withContext(Dispatchers.IO) { File(directory, photo.path).delete(); dao.delete(photo) } }
    suspend fun export(photo: PhotoEntity): Uri = withContext(Dispatchers.IO) { val values = android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, photo.displayName); put(android.provider.MediaStore.Images.Media.MIME_TYPE, photo.mimeType); put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AWNISH Exports") }; val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Unable to create export"); context.contentResolver.openOutputStream(uri)?.use { it.write(bytes(photo)) } ?: error("Unable to export image"); uri }
}
