package com.chordtracker

import android.content.Context
import android.content.Intent
import android.provider.Settings
import org.json.JSONObject
import java.io.File

data class PlayEntry(
    val startTs: Long,
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val listenedMs: Long
)

data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val listenedMs: Long
)

val PACKAGE_LABELS = mapOf(
    "com.spotify.music" to "Spotify",
    "com.google.android.apps.youtube.music" to "YouTube Music",
    "com.apple.android.music" to "Apple Music"
)

object MediaLog {

    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        return enabled.contains(context.packageName)
    }

    fun openNotificationAccessSettings(context: Context) {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun readPlayLines(context: Context): List<String> {
        val file = File(context.filesDir, MediaListenerService.PLAYS_FILE_NAME)
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }
    }

    fun readCurrent(context: Context): List<NowPlaying> {
        val file = File(context.filesDir, MediaListenerService.CURRENT_FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val array = org.json.JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                NowPlaying(
                    packageName = obj.optString("package"),
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    isPlaying = obj.optBoolean("is_playing"),
                    listenedMs = obj.optLong("listened_ms")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun readEntries(context: Context): List<PlayEntry> =
        readPlayLines(context).mapNotNull { line ->
            try {
                val obj = JSONObject(line)
                PlayEntry(
                    startTs = obj.optLong("ts"),
                    packageName = obj.optString("package"),
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    album = obj.optString("album"),
                    durationMs = obj.optLong("duration_ms"),
                    listenedMs = obj.optLong("listened_ms")
                )
            } catch (e: Exception) {
                null
            }
        }.reversed()
}
