package com.yourname.nib.core.update

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

enum class DownloadPhase { Idle, Running, Done, Failed }

data class DownloadState(
    val phase: DownloadPhase = DownloadPhase.Idle,
    val version: String = "",
    val url: String = "",
    val received: Long = 0,
    val total: Long = 0,
    val file: File? = null,
    val message: String? = null,
) {
    val fraction: Float
        get() = if (phase == DownloadPhase.Running && total > 0) {
            (received.toFloat() / total).coerceIn(0f, 1f)
        } else {
            0f
        }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

object UpdateCenter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(DownloadState())
    private var job: Job? = null
    private var generation = 0

    val download: StateFlow<DownloadState> = state.asStateFlow()

    fun start(context: Context, version: String, url: String) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        val mine = ++generation
        state.value = DownloadState(DownloadPhase.Running, version, url)
        job = scope.launch {
            runCatching {
                val dir = File(app.cacheDir, "updates")
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, "nib-$version.apk")
                fetch(url, target) { received, total ->
                    if (mine == generation) {
                        state.value = DownloadState(DownloadPhase.Running, version, url, received, total)
                    }
                }
                target
            }.onSuccess {
                if (mine == generation) {
                    state.value = DownloadState(DownloadPhase.Done, version, url, total = it.length(), file = it)
                }
            }.onFailure {
                if (mine == generation) {
                    state.value = if (it is CancellationException) {
                        DownloadState()
                    } else {
                        DownloadState(DownloadPhase.Failed, version, url, message = it.message ?: "下载失败")
                    }
                }
            }
        }
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        state.value = DownloadState()
    }

    fun reset() {
        generation++
        job = null
        state.value = DownloadState()
    }

    private suspend fun fetch(url: String, target: File, onProgress: (Long, Long) -> Unit) {
        val connection = openConnection(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val total = connection.contentLengthLong
            var received = 0L
            var reported = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        if (received - reported >= 131_072 || (total > 0 && received >= total)) {
                            reported = received
                            onProgress(received, total)
                        }
                    }
                }
            }
            if (received == 0L) throw IOException("安装包为空")
            if (total > 0 && received != total) throw IOException("下载不完整")
            onProgress(received, if (total > 0) total else received)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        var current = url
        repeat(6) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("User-Agent", "Nib-Android")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw IOException("HTTP $code")
                current = URL(URL(current), location).toString()
            } else {
                return connection
            }
        }
        throw IOException("重定向次数过多")
    }
}
