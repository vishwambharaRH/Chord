package com.chordtracker.ui.theme

import androidx.compose.ui.graphics.Color

// Mirrors web/style.css so the native chrome and the embedded dashboard
// read as one app rather than two skins stitched together.
val ChordBackground = Color(0xFF121212)
val ChordSurface = Color(0xFF1A1A1A)
val ChordSurfaceAlt = Color(0xFF212121)
val ChordBorder = Color(0xFF2A2A2A)
val ChordText = Color(0xFFF5F5F5)
val ChordMuted = Color(0xFFA0A0A0)
val ChordMutedDim = Color(0xFF6E6E6E)

val ChordPrimary = Color(0xFF1DB954)
val ChordPrimaryDim = Color(0xFF14803B)
val ChordWarn = Color(0xFFE0A030)
val ChordError = Color(0xFFE05252)

val ServiceSpotify = Color(0xFF1DB954)
val ServiceYouTubeMusic = Color(0xFFFF3D3D)
val ServiceYouTube = Color(0xFFFF0000)
val ServiceAppleMusic = Color(0xFFFA586A)

val ServiceColors = mapOf(
    "com.spotify.music" to ServiceSpotify,
    "com.google.android.apps.youtube.music" to ServiceYouTubeMusic,
    "com.apple.android.music" to ServiceAppleMusic,
)
