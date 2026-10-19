package com.kaskita.lokal.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Reads and writes backup text through a Storage Access Framework [Uri]; never touches filesystem paths directly. */
class BackupFileIo(private val context: Context) {

    suspend fun write(uri: Uri, json: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Tidak bisa membuka file tujuan")
        stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Tidak bisa membuka file backup")
        stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_BACKUP_BYTES) throw IOException("File backup terlalu besar")
            }
            out.toString(Charsets.UTF_8.name())
        }
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 100 * 1024 * 1024
    }
}
