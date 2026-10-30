package com.angravity.videodownloader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

enum class Status { QUEUED, DOWNLOADING, DONE, FAILED }

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String = "",
    val progress: Float = 0f,
    val status: Status = Status.QUEUED,
    val message: String = "Đang chờ...",
)

/** In-memory state shared between the service and the UI. */
object DownloadStore {
    val tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val engineReady = MutableStateFlow(false)

    fun add(task: DownloadTask) = tasks.update { listOf(task) + it }

    fun edit(id: String, block: (DownloadTask) -> DownloadTask) =
        tasks.update { list -> list.map { if (it.id == id) block(it) else it } }

    fun clearFinished() = tasks.update { list -> list.filter { it.status == Status.QUEUED || it.status == Status.DOWNLOADING } }
}

private val URL_REGEX = Regex("""https?://[^\s"'<>]+""")

/** Shared text from FB/TikTok often contains extra words; pull out the first URL. */
fun extractUrl(text: String?): String? = text?.let { URL_REGEX.find(it)?.value?.trimEnd('.', ',', ')') }
