package com.sxsbsxs.dog

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DownloadUiState(
    val running: Boolean = false,
    val progress: Int = 0,
    val progressKnown: Boolean = false,
    val message: String = "粘贴视频链接即可开始",
    val outputPath: String = "",
    val queuedCount: Int = 0,
    val formatLabel: String = "",
    val lastUrl: String = "",
    val formatSelector: String = "",
    val audioOnly: Boolean = false,
    val queuedLabels: List<String> = emptyList()
)

object DownloadState {
    private val mutableState = MutableStateFlow(DownloadUiState())
    val state = mutableState.asStateFlow()

    fun isRunning(): Boolean = mutableState.value.running

    fun started() {
        started(0)
    }

    fun started(queuedCount: Int) {
        started(queuedCount, "")
    }

    fun started(queuedCount: Int, formatLabel: String) {
        started(queuedCount, formatLabel, "", "", false)
    }

    fun started(
        queuedCount: Int,
        formatLabel: String,
        url: String,
        formatSelector: String,
        audioOnly: Boolean
    ) {
        started(queuedCount, formatLabel, url, formatSelector, audioOnly, emptyList())
    }

    fun started(
        queuedCount: Int,
        formatLabel: String,
        url: String,
        formatSelector: String,
        audioOnly: Boolean,
        queuedLabels: List<String>
    ) {
        mutableState.value = DownloadUiState(
            running = true,
            message = if (queuedCount > 0) "正在准备下载 · 后面还有 $queuedCount 个任务" else "正在准备下载…",
            queuedCount = queuedCount,
            formatLabel = formatLabel,
            lastUrl = url,
            formatSelector = formatSelector,
            audioOnly = audioOnly,
            queuedLabels = queuedLabels
        )
    }

    fun queued(count: Int) {
        queued(count, emptyList())
    }

    fun queued(count: Int, labels: List<String>) {
        mutableState.value = mutableState.value.copy(
            running = true,
            message = "已加入下载队列，前面正在处理当前任务",
            queuedCount = count,
            queuedLabels = labels
        )
    }

    fun queueCleared() {
        mutableState.value = mutableState.value.copy(
            running = true,
            message = "等待队列已清空，当前下载继续进行",
            queuedCount = 0,
            queuedLabels = emptyList()
        )
    }

    fun updateMessage(message: String) {
        mutableState.value = mutableState.value.copy(
            running = true,
            message = message
        )
    }

    fun updateProgress(progress: Int, message: String) {
        mutableState.value = mutableState.value.copy(
            running = true,
            progress = progress.coerceIn(0, 100),
            progressKnown = true,
            message = message
        )
    }

    fun completed(path: String) {
        completed(path, "下载完成")
    }

    fun completed(path: String, message: String) {
        val previous = mutableState.value
        mutableState.value = DownloadUiState(
            progress = 100,
            progressKnown = true,
            message = message,
            outputPath = path,
            queuedCount = 0,
            formatLabel = previous.formatLabel,
            lastUrl = previous.lastUrl,
            formatSelector = previous.formatSelector,
            audioOnly = previous.audioOnly,
            queuedLabels = previous.queuedLabels
        )
    }

    fun failed(message: String) {
        val previous = mutableState.value
        mutableState.value = DownloadUiState(
            message = message,
            formatLabel = previous.formatLabel,
            lastUrl = previous.lastUrl,
            formatSelector = previous.formatSelector,
            audioOnly = previous.audioOnly,
            queuedLabels = previous.queuedLabels
        )
    }

    fun canceled() {
        val previous = mutableState.value
        mutableState.value = DownloadUiState(
            message = "已取消下载",
            formatLabel = previous.formatLabel,
            lastUrl = previous.lastUrl,
            formatSelector = previous.formatSelector,
            audioOnly = previous.audioOnly,
            queuedLabels = emptyList()
        )
    }
}
