package com.yourname.nib.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val PROJECT_URL = "https://github.com/yourname/nib"
const val ISSUE_URL = "https://github.com/yourname/nib/issues"

private const val RELEASES_API = "https://api.github.com/repos/yourname/nib/releases/latest"

data class UpdateInfo(
    val version: String,
    val notes: String,
    val downloadUrl: String?,
    val pageUrl: String,
)

fun isNewerVersion(remote: String, local: String): Boolean {
    val remoteParts = remote.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
    val localParts = local.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
    for (index in 0 until maxOf(remoteParts.size, localParts.size)) {
        val a = remoteParts.getOrElse(index) { 0 }
        val b = localParts.getOrElse(index) { 0 }
        if (a != b) return a > b
    }
    return false
}

object UpdateChecker {
    suspend fun latest(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Nib-Android")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                for (index in 0 until (assets?.length() ?: 0)) {
                    val asset = assets!!.getJSONObject(index)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                UpdateInfo(
                    version = json.optString("tag_name").removePrefix("v").ifEmpty { "未知" },
                    notes = json.optString("body").ifBlank { "该版本没有填写更新说明" },
                    downloadUrl = apkUrl,
                    pageUrl = json.optString("html_url").ifEmpty { PROJECT_URL },
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}
