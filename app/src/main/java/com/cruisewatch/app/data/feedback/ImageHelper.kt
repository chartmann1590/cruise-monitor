package com.cruisewatch.app.data.feedback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val MAX_IMAGE_BYTES = 6L * 1024 * 1024

/** Result of converting a picked image to a Worker upload payload. */
sealed interface ImagePayload {
    data class Ready(val fileName: String, val contentBase64: String) : ImagePayload
    data class Error(val message: String) : ImagePayload
}

/** Reads the image at [uri] and returns its Base64 form (Base64.NO_WRAP). */
fun uriToBase64(context: Context, uri: Uri, prefix: String = "issue"): ImagePayload {
    return try {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        val extension = when {
            mime.contains("png", ignoreCase = true) -> "png"
            mime.contains("webp", ignoreCase = true) -> "webp"
            mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> "jpg"
            else -> uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        }.takeIf { it == "png" || it == "jpg" || it == "jpeg" || it == "webp" } ?: "png"

        val size = runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getLong(idx) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
        if (size > MAX_IMAGE_BYTES) {
            return ImagePayload.Error("Image is too large (max 6 MB).")
        }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ImagePayload.Error("Unable to read the selected image.")
        if (bytes.size > MAX_IMAGE_BYTES) {
            return ImagePayload.Error("Image is too large (max 6 MB).")
        }
        ImagePayload.Ready(
            fileName = uniqueFileName(prefix, extension),
            contentBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
    } catch (e: Exception) {
        ImagePayload.Error("Unable to read the selected image.")
    }
}

/** Decodes a downsampled preview bitmap for the picked image. */
fun uriToPreviewBitmap(context: Context, uri: Uri, maxDimension: Int = 1024): Bitmap? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val longest = maxOf(w, h)
                if (longest > maxDimension) {
                    val scale = maxDimension.toDouble() / longest
                    decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()
}

fun uniqueFileName(prefix: String, extension: String): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val random = UUID.randomUUID().toString().take(4)
    val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9_-]"), "").take(24).ifEmpty { "issue" }
    return "$safePrefix-$stamp-$random.$extension"
}
