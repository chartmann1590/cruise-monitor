package com.cruisewatch.app.ai

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

sealed class DownloadState {
    data class Progress(val downloadedMb: Int, val totalMb: Int) : DownloadState()
    data class Done(val file: File) : DownloadState()
    data class Failed(val message: String) : DownloadState()
}

object ModelDownloadManager {
    private fun modelFile(context: Context, model: LlmModel): File =
        File(File(context.filesDir, "models").apply { mkdirs() }, model.fileName)

    fun localFile(context: Context, model: LlmModel): File? {
        val file = modelFile(context, model)
        return file.takeIf { it.exists() && it.length() > 0 }
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
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastEmitMb = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val downloadedMb = (downloaded / (1024 * 1024)).toInt()
                        if (downloadedMb != lastEmitMb) {
                            lastEmitMb = downloadedMb
                            emit(DownloadState.Progress(downloadedMb, (totalBytes / (1024 * 1024)).toInt()))
                        }
                    }
                }
            }
            tempFile.renameTo(destination)
        }.onSuccess {
            emit(DownloadState.Done(destination))
        }.onFailure { e ->
            tempFile.delete()
            emit(DownloadState.Failed(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)
}
