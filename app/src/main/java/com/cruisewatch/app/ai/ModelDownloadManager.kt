package com.cruisewatch.app.ai

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

sealed class DownloadState {
    data class Progress(val downloadedMb: Int, val totalMb: Int) : DownloadState()
    data class Done(val file: File) : DownloadState()
    data class Failed(val message: String) : DownloadState()
}

/**
 * Downloads the on-device LLM model files from Hugging Face and verifies
 * their integrity FAIL-CLOSED: a download is only moved into place (and a
 * previously downloaded file only trusted) when its exact byte size and
 * SHA-256 match the values pinned in [LlmModelCatalog]. A file that fails
 * verification is deleted, never loaded.
 */
object ModelDownloadManager {
    private fun modelFile(context: Context, model: LlmModel): File =
        File(File(context.filesDir, "models").apply { mkdirs() }, model.fileName)

    /**
     * The already-downloaded model file, or null. Cheap synchronous check:
     * the file must exist with exactly the pinned size. Full SHA-256
     * verification happens in [verifyIntegrity] before the engine loads it.
     */
    fun localFile(context: Context, model: LlmModel): File? {
        val file = modelFile(context, model)
        return file.takeIf { it.exists() && it.length() == model.expectedSizeBytes }
    }

    /** Streams [file] through SHA-256 and compares it to the pinned hash. */
    private fun sha256Matches(file: File, expectedSha256: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    /**
     * Full integrity check of the on-disk model file (size + SHA-256).
     * Deletes the file and returns false if it does not match the pin —
     * a corrupt or tampered model is never handed to the engine.
     */
    suspend fun verifyIntegrity(context: Context, model: LlmModel): Boolean =
        withContext(Dispatchers.IO) {
            val file = modelFile(context, model)
            val ok = file.exists() &&
                file.length() == model.expectedSizeBytes &&
                runCatching { sha256Matches(file, model.expectedSha256) }.getOrDefault(false)
            if (!ok) file.delete()
            ok
        }

    fun download(context: Context, model: LlmModel): Flow<DownloadState> = flow {
        val destination = modelFile(context, model)
        val tempFile = File(destination.parentFile, "${destination.name}.part")
        runCatching {
            val connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connect()
            }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: (model.approxSizeMb.toLong() * 1024 * 1024)
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastEmitMb = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        val downloadedMb = (downloaded / (1024 * 1024)).toInt()
                        if (downloadedMb != lastEmitMb) {
                            lastEmitMb = downloadedMb
                            emit(DownloadState.Progress(downloadedMb, (totalBytes / (1024 * 1024)).toInt()))
                        }
                    }
                }
            }
            // Fail closed: only a byte-exact, hash-exact download is accepted.
            check(tempFile.length() == model.expectedSizeBytes) {
                "Downloaded model failed its integrity check (unexpected size)"
            }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            check(actualSha256.equals(model.expectedSha256, ignoreCase = true)) {
                "Downloaded model failed its integrity check (checksum mismatch)"
            }
            check(tempFile.renameTo(destination)) { "Could not move the downloaded model into place" }
        }.onSuccess {
            emit(DownloadState.Done(destination))
        }.onFailure { e ->
            tempFile.delete()
            emit(DownloadState.Failed(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)
}
