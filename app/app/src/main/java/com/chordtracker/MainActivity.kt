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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.chordtracker.ui.theme.ChordMuted
import com.chordtracker.ui.theme.ChordMutedDim
import com.chordtracker.ui.theme.ChordPrimary
import com.chordtracker.ui.theme.ChordSurface
import com.chordtracker.ui.theme.ChordSurfaceAlt
import com.chordtracker.ui.theme.ChordTheme
import com.chordtracker.ui.theme.ChordWarn
import com.chordtracker.ui.theme.ServiceColors
import com.chordtracker.ui.theme.WordmarkStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChordTheme { App() } }
    }
}

private fun formatListened(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun serviceColor(pkg: String): Color = ServiceColors[pkg] ?: ChordMuted

private enum class Tab(val label: String) { DASHBOARD("Dashboard"), TRACKER("Tracker") }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var statsMessage by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    fun refreshStats() {
        scope.launch {
            refreshing = true
            statsMessage = null
            statsMessage = withContext(Dispatchers.IO) { Uploader.fetchStats(context) }
            reloadKey++
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { refreshStats() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Chord", style = WordmarkStyle, color = ChordPrimary) },
                    actions = {
                        if (tab == Tab.DASHBOARD) {
                            IconButton(onClick = { refreshStats() }, enabled = !refreshing) {
                                if (refreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = ChordPrimary,
                                    )
                                } else {
                                    Icon(Icons.Filled.CloudSync, contentDescription = "Update", tint = ChordMuted)
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = ChordPrimary,
                    ),
                    modifier = Modifier.statusBarsPadding(),
                )
                AnimatedVisibility(visible = tab == Tab.DASHBOARD && statsMessage != null) {
                    Text(
                        statsMessage ?: "",
                        color = ChordMutedDim,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = ChordSurface,
                modifier = Modifier.navigationBarsPadding(),
            ) {
                NavigationBarItem(
                    selected = tab == Tab.DASHBOARD,
                    onClick = { tab = Tab.DASHBOARD },
                    icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                    label = { Text("Dashboard") },
                    colors = navItemColors(),
                )
                NavigationBarItem(
                    selected = tab == Tab.TRACKER,
                    onClick = { tab = Tab.TRACKER },
                    icon = { Icon(Icons.Filled.Headphones, contentDescription = null) },
                    label = { Text("Tracker") },
                    colors = navItemColors(),
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Crossfade(targetState = tab, label = "tab") { current ->
                if (current == Tab.DASHBOARD) Dashboard(reloadKey) else TrackerScreen()
            }
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.background,
    selectedTextColor = ChordPrimary,
    indicatorColor = ChordPrimary,
    unselectedIconColor = ChordMuted,
    unselectedTextColor = ChordMuted,
)

// Mirrors the web dashboard's tabs (web/index.html), driven natively instead
// of through its own in-page nav — see body.embedded in web/style.css and
// window.chordShowTab in web/app.js.
private enum class DashTab(val id: String, val label: String, val icon: ImageVector) {
    OVERVIEW("overview", "Overview", Icons.Filled.Home),
    TRACKS("tracks", "Top Tracks", Icons.Filled.MusicNote),
    ARTISTS("artists", "Top Artists", Icons.Filled.Mic),
    ACTIVITY("activity", "Activity", Icons.AutoMirrored.Filled.ShowChart),
    ALBUMS("vinyl", "Top Albums", Icons.Filled.Album),
}

@Composable
private fun DashboardTabBar(selected: DashTab, onSelect: (DashTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(ChordSurface).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        DashTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) ChordPrimary.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    tint = if (active) ChordPrimary else ChordMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var selectedTab by remember { mutableStateOf(DashTab.OVERVIEW) }

    fun applyTab(tab: DashTab) {
        webViewRef?.evaluateJavascript("window.chordShowTab && window.chordShowTab('${tab.id}');", null)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DashboardTabBar(selected = selectedTab) { tab ->
            selectedTab = tab
            applyTab(tab)
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

                        override fun onPageFinished(view: WebView, url: String?) {
                            applyTab(selectedTab)
                        }
                    }
                    webViewRef = this
                }
            },
            update = { view ->
                if (view.tag != reloadKey) {
                    view.tag = reloadKey
                    view.loadUrl("https://appassets.androidplatform.net/assets/index.html?data=/local/data.json&v=$reloadKey&embed=1")
                }
            },
        )
    }
}

@Composable
fun TrackerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var granted by remember { mutableStateOf(MediaLog.isNotificationAccessGranted(context)) }
    var entries by remember { mutableStateOf(MediaLog.readEntries(context)) }
    var now by remember { mutableStateOf(MediaLog.readCurrent(context)) }
    var status by remember { mutableStateOf(AppSettings.lastStatus(context)) }
    var syncing by remember { mutableStateOf(false) }

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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            AnimatedVisibility(
                visible = !granted,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ChordWarn.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = ChordWarn)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notification access needed", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Chord reads playback state to track your listens.", color = ChordMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { MediaLog.openNotificationAccessSettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = ChordWarn, contentColor = Color(0xFF121212)),
                        ) { Text("Grant") }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ChordSurface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SYNC", color = ChordMutedDim, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(status, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                syncing = true
                                withContext(Dispatchers.IO) { Uploader.sync(context) }
                                refresh()
                                syncing = false
                            }
                        },
                        enabled = !syncing,
                        colors = ButtonDefaults.buttonColors(containerColor = ChordPrimary, contentColor = Color(0xFF121212)),
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF121212))
                        } else {
                            Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync now")
                        }
                    }
                }
            }
        }

        if (now.isNotEmpty()) {
            item {
                SectionHeader(title = "Now playing", live = true)
            }
            items(now) { item -> NowPlayingCard(item) }
        }

        item {
            SectionHeader(title = "${entries.size} finished plays", live = false)
        }
        items(entries) { entry -> PlayRow(entry) }
    }
}

@Composable
private fun SectionHeader(title: String, live: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        if (live) {
            PulsingDot(color = ChordPrimary)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            title.uppercase(),
            color = ChordMutedDim,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

@Composable
private fun EqualizerBars(color: Color, playing: Boolean) {
    val transition = rememberInfiniteTransition(label = "eq")
    @Composable
    fun bar(delayMs: Int): Float {
        val f by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(500, delayMillis = delayMs, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "bar",
        )
        return f
    }

    val h1 = bar(0)
    val h2 = bar(150)
    val h3 = bar(300)
    val heights = if (playing) listOf(h1, h2, h3) else listOf(0.4f, 0.7f, 0.4f)

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .graphicsLayer { scaleY = fraction; transformOrigin = TransformOrigin(0.5f, 1f) }
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun NowPlayingCard(item: NowPlaying) {
    val accent = serviceColor(item.packageName)
    Card(
        colors = CardDefaults.cardColors(containerColor = ChordSurfaceAlt),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(accent))
            Row(
                modifier = Modifier.weight(1f).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    Text(
                        item.artist.ifBlank { "Unknown artist" },
                        color = ChordMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${PACKAGE_LABELS[item.packageName] ?: item.packageName} · ${formatListened(item.listenedMs)}",
                        color = ChordMutedDim,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (item.isPlaying) {
                    EqualizerBars(color = accent, playing = true)
                } else {
                    Icon(Icons.Filled.Pause, contentDescription = "Paused", tint = ChordMutedDim, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayRow(entry: PlayEntry) {
    val accent = serviceColor(entry.packageName)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(entry.artist.ifBlank { "Unknown artist" }, color = ChordMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatListened(entry.listenedMs), color = ChordMutedDim, style = MaterialTheme.typography.bodySmall)
            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.startTs))
            Text(time, color = ChordMutedDim, style = MaterialTheme.typography.labelSmall)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}
