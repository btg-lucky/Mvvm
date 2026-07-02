package com.btg.common.network.download

import com.btg.common.network.ExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** 带进度的文件下载器。download 返回冷 Flow，收集时才真正下载，可随协程取消。 */
class FileDownloader(private val client: OkHttpClient) {

    fun download(url: String, dest: File): Flow<DownloadState> = flow {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                throw IOException("HTTP ${response.code}")
            }
            val total = body.contentLength()
            var bytesRead = 0L
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        val percent = if (total > 0) ((bytesRead * 100) / total).toInt() else -1
                        emit(DownloadState.Progress(bytesRead, total, percent))
                    }
                }
            }
            emit(DownloadState.Success(dest))
        }
    }.catch { e ->
        emit(DownloadState.Failed(ExceptionHandler.handle(e)))
    }.flowOn(Dispatchers.IO)
}
