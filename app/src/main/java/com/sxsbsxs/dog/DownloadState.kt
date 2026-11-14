package com.sxsbsxs.dog

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DownloadUiState(
    val running: Boolean = false,
    val progress: Int = 0,
    val message: String = "粘贴视频链接即可开始",
    val outputPath: String = ""
)

object DownloadState {
    private val mutableState = MutableStateFlow(DownloadUiState())
    val state = mutableState.asStateFlow()

    fun started() {
        mutableState.value = DownloadUiState(running = true, message = "正在准备下载…")
    }

    fun updateProgress(progress: Int, message: String) {
        mutableState.value = mutableState.value.copy(
            running = true,
            progress = progress.coerceIn(0, 100),
            message = message
        )
    }

    fun completed(path: String) {
        completed(path, "下载完成")
    }

    fun completed(path: String, message: String) {
        mutableState.value = DownloadUiState(
            progress = 100,
            message = message,
            outputPath = path
        )
    }

    fun failed(message: String) {
        mutableState.value = DownloadUiState(message = message)
    }

    fun canceled() {
        mutableState.value = DownloadUiState(message = "已取消下载")
    }
}
