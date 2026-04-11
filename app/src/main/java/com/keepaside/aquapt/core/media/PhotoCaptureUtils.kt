package com.keepaside.aquapt.core.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun createTempPhotoCaptureUri(context: Context): Uri? {
    return runCatching {
        val imagesDirectory = File(context.cacheDir, "images").apply {
            if (!exists()) {
                mkdirs()
            }
        }
        val imageFile = File.createTempFile("aquapt_capture_", ".jpg", imagesDirectory)
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }.getOrNull()
}