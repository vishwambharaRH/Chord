package com.chordtracker

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = Color(0xFF121212)) {
                    App()
                }
            }
        }
    }
}

private val Muted = Color(0xFFA0A0A0)
private val Green = Color(0xFF1DB954)

private fun formatListened(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("dashboard") }
    var reloadKey by remember { mutableIntStateOf(0) }
    var statsMessage by remember { mutableStateOf<String?>(null) }

    fun refreshStats() {
        scope.launch {
            statsMessage = "Updating…"
            val error = withContext(Dispatchers.IO) { Uploader.fetchStats(context) }
            statsMessage = error
            reloadKey++
        }
    }

    LaunchedEffect(Unit) { refreshStats() }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabButton("Dashboard", tab == "dashboard") { tab = "dashboard" }
            TabButton("Tracker", tab == "tracker") { tab = "tracker" }
            if (tab == "dashboard") {
                Button(onClick = { refreshStats() }) { Text("Update") }
            }
        }
        statsMessage?.let {
            Text(it, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }

        if (tab == "dashboard") Dashboard(reloadKey) else TrackerScreen()
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Green else Color(0xFF2A2A2A),
            contentColor = if (selected) Color(0xFF121212) else Color.White
        )
    ) { Text(label) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Dashboard(reloadKey: Int) {
    val context = LocalContext.current
    val loader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/local/", WebViewAssetLoader.InternalStoragePathHandler(context, AppSettings.statsDir(context)))
            .build()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.parseColor("#121212"))
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)
                }
            }
        },
        update = { view ->
            if (view.tag != reloadKey) {
                view.tag = reloadKey
                view.loadUrl("https://appassets.androidplatform.net/assets/index.html?data=/local/data.json&v=$reloadKey")
            }
        }
    )
}

@Composable
fun TrackerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var granted by remember { mutableStateOf(MediaLog.isNotificationAccessGranted(context)) }
    var entries by remember { mutableStateOf(MediaLog.readEntries(context)) }
    var now by remember { mutableStateOf(MediaLog.readCurrent(context)) }
    var status by remember { mutableStateOf(AppSettings.lastStatus(context)) }

    fun refresh() {
        granted = MediaLog.isNotificationAccessGranted(context)
        entries = MediaLog.readEntries(context)
        now = MediaLog.readCurrent(context)
        status = AppSettings.lastStatus(context)
    }

    // Keep the list live instead of requiring a manual refresh.
    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(2_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (granted) "Notification access: granted" else "Notification access: not granted",
                color = if (granted) Green else Color(0xFFE05252)
            )
            if (!granted) Button(onClick = { MediaLog.openNotificationAccessSettings(context) }) { Text("Grant") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sync: $status", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Button(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { Uploader.sync(context) }
                    refresh()
                }
            }) { Text("Sync now") }
        }

        if (now.isNotEmpty()) {
            Text("Now", color = Green, style = MaterialTheme.typography.labelLarge)
            now.forEach {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("${it.title} — ${it.artist.ifBlank { "unknown" }}", color = Color.White)
                    Text(
                        "${PACKAGE_LABELS[it.packageName] ?: it.packageName} · ${if (it.isPlaying) "playing" else "paused"} · ${formatListened(it.listenedMs)} so far",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Text("${entries.size} finished plays", color = Muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries) { entry ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text("${entry.title} — ${entry.artist.ifBlank { "unknown" }}", color = Color.White)
                    val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.startTs))
                    Text(
                        "${PACKAGE_LABELS[entry.packageName] ?: entry.packageName} · listened ${formatListened(entry.listenedMs)} · $time",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
