package com.hunter.stockfarmer.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** Puts a farm picture where the player will find it: in the Pictures folder of the gallery. */
object PhotoSaver {
    /** Saves the PNG and says whether it worked. */
    fun save(
        context: Context,
        png: ByteArray,
    ): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToGallery(context, png)
            } else {
                saveToAppFolder(context, png)
            }
        }.isSuccess

    private fun saveToGallery(
        context: Context,
        png: ByteArray,
    ) {
        val nowSeconds = System.currentTimeMillis() / 1000
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Stock Farmer.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Stock Farmer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(MediaStore.Images.Media.DATE_ADDED, nowSeconds)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            }
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { it.write(png) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    /** Before Android 10 writing to the shared gallery needs a storage permission, so the picture goes to the app's folder. */
    private fun saveToAppFolder(
        context: Context,
        png: ByteArray,
    ) {
        val folder = context.getExternalFilesDir("Pictures") ?: File(context.filesDir, "Pictures")
        folder.mkdirs()
        File(folder, "Stock Farmer.png").writeBytes(png)
    }
}
