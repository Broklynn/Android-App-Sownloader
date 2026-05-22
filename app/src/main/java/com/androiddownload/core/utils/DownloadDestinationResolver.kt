package com.androiddownload.core.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.androiddownload.R
import java.io.File
import java.io.IOException
import java.util.Locale

object DownloadDestinationResolver {
    private const val PREFS_NAME = "aio_downloader_settings"
    private const val PREF_CUSTOM_DOWNLOAD_TREE_URI = "custom_download_tree_uri"
    private const val DEFAULT_PUBLIC_DIRECTORY_LABEL = "Downloads/DarkWave"
    private const val DEFAULT_PUBLIC_SUBDIRECTORY = "DarkWave"

    data class SavedFile(
        val fileName: String,
        val uri: Uri,
        val bytes: Long
    )

    class DestinationException(message: String, cause: Throwable? = null) : IOException(message, cause)

    fun customTreeUri(context: Context): Uri? {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_CUSTOM_DOWNLOAD_TREE_URI, null)
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?: return null
        return Uri.parse(raw)
    }

    fun setCustomTreeUri(context: Context, uri: Uri) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_CUSTOM_DOWNLOAD_TREE_URI, uri.toString())
            .apply()
    }

    fun clearCustomTreeUri(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_CUSTOM_DOWNLOAD_TREE_URI)
            .apply()
    }

    fun describeCurrentDestination(context: Context): String {
        return customTreeUri(context)?.let { summarizeUri(it) }
            ?: defaultDestinationLabel()
    }

    fun saveToDestination(
        context: Context,
        sourceFile: File,
        preferredName: String,
        mimeType: String?,
        preserveName: Boolean = false,
        destinationSubfolder: String? = null
    ): SavedFile {
        if (!sourceFile.exists() || !sourceFile.isFile || sourceFile.length() <= 0L) {
            throw DestinationException(context.getString(R.string.download_final_file_invalid))
        }
        val treeUri = customTreeUri(context)
        return if (treeUri != null) {
            saveToTree(context, treeUri, sourceFile, preferredName, mimeType)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(context, sourceFile, preferredName, mimeType, destinationSubfolder)
        } else {
            saveToDefaultDirectory(context, sourceFile, preferredName, mimeType, preserveName, destinationSubfolder)
        }
    }

    fun defaultDestinationLabel(): String = DEFAULT_PUBLIC_DIRECTORY_LABEL

    fun defaultDirectory(context: Context): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
    }

    fun summarizeUri(uri: Uri): String {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        return documentId
            ?.substringAfter(':', documentId)
            ?.takeIf { it.isNotBlank() }
            ?: uri.toString()
    }

    private fun saveToDefaultDirectory(
        context: Context,
        sourceFile: File,
        preferredName: String,
        mimeType: String?,
        preserveName: Boolean,
        destinationSubfolder: String?
    ): SavedFile {
        val finalFile = resolveDefaultFile(context, preferredName, mimeType, preserveName, destinationSubfolder)
        finalFile.parentFile?.mkdirs()
        if (!sourceFile.renameTo(finalFile)) {
            sourceFile.inputStream().use { input ->
                finalFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            sourceFile.delete()
        }
        return SavedFile(
            fileName = finalFile.name,
            uri = Uri.fromFile(finalFile),
            bytes = finalFile.length()
        )
    }

    private fun saveToMediaStore(
        context: Context,
        sourceFile: File,
        preferredName: String,
        mimeType: String?,
        destinationSubfolder: String?
    ): SavedFile {
        val resolver = context.contentResolver
        val documentMimeType = normalizeMimeType(mimeType)
            ?: mimeTypeForName(preferredName)
            ?: "application/octet-stream"
        val relativePath = publicRelativePath(destinationSubfolder)
        val fileName = uniqueMediaStoreFileName(
            resolver = resolver,
            preferredName = FileNameUtils.ensureExtension(preferredName, documentMimeType),
            relativePath = mediaStoreQueryRelativePath(destinationSubfolder)
        )
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, documentMimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val targetUri = resolver.insert(collection, values)
            ?: throw DestinationException(context.getString(R.string.download_save_error))

        try {
            resolver.openOutputStream(targetUri, "w")?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw DestinationException(context.getString(R.string.download_save_error))
            ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }.also { resolver.update(targetUri, it, null, null) }
        } catch (exception: Exception) {
            runCatching { resolver.delete(targetUri, null, null) }
            throw DestinationException(context.getString(R.string.download_save_error), exception)
        }

        val bytes = sourceFile.length()
        sourceFile.delete()
        YtDlpDiagnostics.record(
            context = context,
            url = "app",
            option = "destino",
            attempt = "salvar arquivo",
            result = "salvando em $relativePath",
            error = targetUri.toString()
        )
        return SavedFile(fileName = fileName, uri = targetUri, bytes = bytes)
    }

    private fun saveToTree(
        context: Context,
        treeUri: Uri,
        sourceFile: File,
        preferredName: String,
        mimeType: String?
    ): SavedFile {
        val resolver = context.contentResolver
        val documentMimeType = normalizeMimeType(mimeType)
            ?: mimeTypeForName(preferredName)
            ?: "application/octet-stream"
        val fileName = uniqueTreeFileName(resolver, treeUri, FileNameUtils.ensureExtension(preferredName, documentMimeType))
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val targetUri = runCatching {
            DocumentsContract.createDocument(resolver, parentUri, documentMimeType, fileName)
        }.getOrNull() ?: throw DestinationException(context.getString(R.string.download_custom_folder_access_error))

        try {
            resolver.openOutputStream(targetUri, "w")?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw DestinationException(context.getString(R.string.download_save_error))
        } catch (exception: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, targetUri) }
            throw DestinationException(context.getString(R.string.download_save_error), exception)
        }

        val bytes = sourceFile.length()
        sourceFile.delete()
        YtDlpDiagnostics.record(
            context = context,
            url = "app",
            option = "destino",
            attempt = "salvar arquivo",
            result = "arquivo salvo em pasta customizada",
            error = summarizeUri(treeUri)
        )
        return SavedFile(fileName = fileName, uri = targetUri, bytes = bytes)
    }

    private fun resolveDefaultFile(
        context: Context,
        preferredName: String,
        mimeType: String?,
        preserveName: Boolean,
        destinationSubfolder: String?
    ): File {
        val directory = File(defaultDirectory(context), cleanDestinationSubfolder(destinationSubfolder))
        directory.mkdirs()
        val cleanName = FileNameUtils.ensureExtension(FileNameUtils.sanitize(preferredName), mimeType)
        if (preserveName) return File(directory, cleanName)

        val name = cleanName.substringBeforeLast('.', cleanName)
        val extension = cleanName.substringAfterLast('.', missingDelimiterValue = "")
        var candidate = File(directory, cleanName)
        var index = 1
        while (candidate.exists()) {
            val nextName = if (extension.isBlank()) {
                "$name ($index)"
            } else {
                "$name ($index).$extension"
            }
            candidate = File(directory, nextName)
            index++
        }
        return candidate
    }

    private fun uniqueTreeFileName(
        resolver: ContentResolver,
        treeUri: Uri,
        preferredName: String
    ): String {
        val existingNames = queryTreeFileNames(resolver, treeUri)
        if (preferredName !in existingNames) return preferredName
        val name = preferredName.substringBeforeLast('.', preferredName)
        val extension = preferredName.substringAfterLast('.', missingDelimiterValue = "")
        var index = 1
        while (true) {
            val candidate = if (extension.isBlank()) {
                "$name ($index)"
            } else {
                "$name ($index).$extension"
            }
            if (candidate !in existingNames) return candidate
            index++
        }
    }

    private fun uniqueMediaStoreFileName(
        resolver: ContentResolver,
        preferredName: String,
        relativePath: String
    ): String {
        val existingNames = queryMediaStoreFileNames(resolver, relativePath)
        if (preferredName !in existingNames) return preferredName
        val name = preferredName.substringBeforeLast('.', preferredName)
        val extension = preferredName.substringAfterLast('.', missingDelimiterValue = "")
        var index = 1
        while (true) {
            val candidate = if (extension.isBlank()) {
                "$name ($index)"
            } else {
                "$name ($index).$extension"
            }
            if (candidate !in existingNames) return candidate
            index++
        }
    }

    private fun queryMediaStoreFileNames(resolver: ContentResolver, relativePath: String): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptySet()
        val names = mutableSetOf<String>()
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                "${MediaStore.Downloads.RELATIVE_PATH}=?",
                arrayOf(relativePath),
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                if (nameIndex < 0) return@use
                while (cursor.moveToNext()) {
                    cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let(names::add)
                }
            }
        }
        return names
    }

    internal fun publicRelativePath(destinationSubfolder: String?): String {
        return "$publicDownloadsDirectory/$DEFAULT_PUBLIC_SUBDIRECTORY/${cleanDestinationSubfolder(destinationSubfolder)}"
    }

    internal fun mediaStoreQueryRelativePath(destinationSubfolder: String?): String {
        return "${publicRelativePath(destinationSubfolder)}/"
    }

    private fun cleanDestinationSubfolder(destinationSubfolder: String?): String {
        return destinationSubfolder
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { FileNameUtils.sanitize(it) }
            ?.takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: DEFAULT_FILES_SUBDIRECTORY
    }

    private fun queryTreeFileNames(resolver: ContentResolver, treeUri: Uri): Set<String> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val names = mutableSetOf<String>()
        runCatching {
            resolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (nameIndex < 0) return@use
                while (cursor.moveToNext()) {
                    cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let(names::add)
                }
            }
        }
        return names
    }

    private fun normalizeMimeType(mimeType: String?): String? {
        return mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.contains('/') }
    }

    private fun mimeTypeForName(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
            .takeIf { it.isNotBlank() }
            ?: return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private val publicDownloadsDirectory: String
        get() = Environment.DIRECTORY_DOWNLOADS ?: "Download"

    private const val DEFAULT_FILES_SUBDIRECTORY = "Arquivos"
}
