package com.motorista.radar

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * FileProvider 100% nativo sem depender do AndroidX.
 * Permite que o instalador do sistema (PackageInstaller) leia o APK baixado com segurança via Content URI.
 */
class GenericFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = getFileForUri(uri)
        val proj = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(proj, 1)
        val row = cursor.newRow()
        for (col in proj) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> row.add(file.name)
                OpenableColumns.SIZE -> row.add(file.length())
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        val file = getFileForUri(uri)
        val lastDot = file.name.lastIndexOf('.')
        if (lastDot >= 0) {
            val ext = file.name.substring(lastDot + 1)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (mime != null) return mime
        }
        return "application/octet-stream"
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = getFileForUri(uri)
        val fileMode = when (mode) {
            "r" -> ParcelFileDescriptor.MODE_READ_ONLY
            "w", "wa" -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE
            "rw" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
            else -> throw IllegalArgumentException("Modo inválido: $mode")
        }
        return ParcelFileDescriptor.open(file, fileMode)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun getFileForUri(uri: Uri): File {
        val path = uri.encodedPath ?: throw FileNotFoundException("URI path vazio")
        val decodedPath = Uri.decode(path)
        val cleanPath = if (decodedPath.startsWith("/")) decodedPath.substring(1) else decodedPath
        val ctx = context ?: throw FileNotFoundException("Contexto nulo")
        val baseDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val file = File(baseDir, cleanPath)
        if (!file.exists()) {
            throw FileNotFoundException("Arquivo não encontrado: ${file.absolutePath}")
        }
        return file
    }

    companion object {
        fun getUriForFile(context: Context, authority: String, file: File): Uri {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val relPath = file.absolutePath.removePrefix(baseDir.absolutePath).trimStart('/', '\\')
            return Uri.Builder()
                .scheme("content")
                .authority(authority)
                .encodedPath(Uri.encode(relPath))
                .build()
        }
    }
}
