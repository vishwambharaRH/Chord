package com.chordtracker

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

// Reads currently-playing state via MediaSessionManager (structured
// title/artist/album/playback state that every media app exposes the same
// way) and turns it into finished "plays": the time playback started plus
// how long it was actually playing, excluding pauses.
class MediaListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "ChordMediaListener"
        const val PLAYS_FILE_NAME = "plays.jsonl"
        const val CURRENT_FILE_NAME = "current.json"

        // Ignore blips (skips, the paused-at-0 flicker on track change).
        private const val MIN_LISTENED_MS = 3_000L

        // A paused session is finalized after this long, so a track left
        // paused overnight still gets logged rather than staying open.
        private const val PAUSE_FINALIZE_MS = 5 * 60_000L

        private val TRACKED_PACKAGES = setOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.apple.android.music"
        )
    }

    private class Session(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long
    ) {
        var startTs: Long? = null
        var playingSince: Long? = null
        var accumulatedMs = 0L

        val key get() = "$title|$artist|$album"
    }

    private lateinit var mediaSessionManager: MediaSessionManager
    private val handler = Handler(Looper.getMainLooper())
    private val activeControllers = mutableMapOf<String, MediaController>()
    private val controllerCallbacks = mutableMapOf<String, MediaController.Callback>()
    private val sessions = mutableMapOf<String, Session>()
    private val idleFinalizers = mutableMapOf<String, Runnable>()

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attachControllers(controllers ?: emptyList())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
        mediaSessionManager = getSystemService(MediaSessionManager::class.java)
        val componentName = ComponentName(this, MediaListenerService::class.java)
        mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, componentName)
        attachControllers(mediaSessionManager.getActiveSessions(componentName))
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        detachAllControllers()
        sessions.keys.toList().forEach { finalizeSession(it) }
    }

    private fun attachControllers(controllers: List<MediaController>) {
        val current = controllers.filter { it.packageName in TRACKED_PACKAGES }
        val currentPackages = current.map { it.packageName }.toSet()

        activeControllers.keys.filter { it !in currentPackages }.forEach { pkg ->
            controllerCallbacks[pkg]?.let { activeControllers[pkg]?.unregisterCallback(it) }
            activeControllers.remove(pkg)
            controllerCallbacks.remove(pkg)
            finalizeSession(pkg)
        }

        current.forEach { controller ->
            val pkg = controller.packageName
            if (activeControllers.containsKey(pkg)) return@forEach

            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    onState(pkg, metadata, controller.playbackState)
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    onState(pkg, controller.metadata, state)
                }

                override fun onSessionDestroyed() {
                    activeControllers.remove(pkg)
                    controllerCallbacks.remove(pkg)
                    finalizeSession(pkg)
                }
            }
            controller.registerCallback(callback)
            activeControllers[pkg] = controller
            controllerCallbacks[pkg] = callback

            onState(pkg, controller.metadata, controller.playbackState)
        }
    }

    private fun detachAllControllers() {
        activeControllers.forEach { (pkg, controller) ->
            controllerCallbacks[pkg]?.let { controller.unregisterCallback(it) }
        }
        activeControllers.clear()
        controllerCallbacks.clear()
    }

    private fun onState(pkg: String, metadata: MediaMetadata?, state: PlaybackState?) {
        if (metadata == null) return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (title.isNullOrBlank()) return

        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val now = System.currentTimeMillis()

        val existing = sessions[pkg]
        if (existing != null && existing.key != "$title|$artist|$album") {
            finalizeSession(pkg)
        }
        val session = sessions.getOrPut(pkg) { Session(title, artist, album, duration) }

        idleFinalizers.remove(pkg)?.let { handler.removeCallbacks(it) }

        if (isPlaying && session.playingSince == null) {
            session.playingSince = now
            if (session.startTs == null) session.startTs = now
        } else if (!isPlaying && session.playingSince != null) {
            session.accumulatedMs += now - session.playingSince!!
            session.playingSince = null
        }

        if (!isPlaying) {
            val finalizer = Runnable { finalizeSession(pkg) }
            idleFinalizers[pkg] = finalizer
            handler.postDelayed(finalizer, PAUSE_FINALIZE_MS)
        }
        writeCurrent()
    }

    private fun finalizeSession(pkg: String) {
        idleFinalizers.remove(pkg)?.let { handler.removeCallbacks(it) }
        val session = sessions.remove(pkg) ?: return
        writeCurrent()

        session.playingSince?.let {
            session.accumulatedMs += System.currentTimeMillis() - it
        }
        val start = session.startTs ?: return
        var listened = session.accumulatedMs
        if (session.durationMs > 0) listened = minOf(listened, session.durationMs)
        if (listened < MIN_LISTENED_MS) return

        val entry = JSONObject().apply {
            put("ts", start)
            put("package", pkg)
            put("title", session.title)
            put("artist", session.artist)
            put("album", session.album)
            put("duration_ms", session.durationMs)
            put("listened_ms", listened)
        }
        try {
            FileWriter(File(filesDir, PLAYS_FILE_NAME), true).use { it.appendLine(entry.toString()) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write play", e)
        }
    }

    // Open (not yet finished) sessions, so the app can show what's playing
    // now instead of an empty list until the track ends.
    private fun writeCurrent() {
        val now = System.currentTimeMillis()
        val array = org.json.JSONArray()
        sessions.forEach { (pkg, session) ->
            val live = session.playingSince?.let { now - it } ?: 0L
            array.put(JSONObject().apply {
                put("package", pkg)
                put("title", session.title)
                put("artist", session.artist)
                put("is_playing", session.playingSince != null)
                put("listened_ms", session.accumulatedMs + live)
            })
        }
        try {
            File(filesDir, CURRENT_FILE_NAME).writeText(array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write current sessions", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
