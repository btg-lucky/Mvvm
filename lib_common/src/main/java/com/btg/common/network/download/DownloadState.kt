package com.btg.common.network.download

import com.btg.common.network.AppException
import java.io.File

/** 下载状态。percent 为 -1 表示总长度未知。 */
sealed interface DownloadState {
    data class Progress(val bytesRead: Long, val total: Long, val percent: Int) : DownloadState
    data class Success(val file: File) : DownloadState
    data class Failed(val error: AppException) : DownloadState
}
