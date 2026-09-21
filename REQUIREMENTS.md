# Chord — Requirements

Draft requirements derived from the README and the existing scaffold (`src/ingestors`, `src/database`, `src/processing`, `src/analytics`, `src/generator`, `ui/`). Fill in / strike out as you go; this is a starting point, not a spec set in stone.

## 1. Data sources

### 1.1 Spotify
- **Live "now playing"**: Spotify Web API, `GET /me/player/currently-playing`.
- **Historical import**: `Extended Streaming History` JSON export, requested from Spotify's privacy page (spotify.com/account/privacy) — takes days to arrive, so request it early.
- **Auth**: OAuth 2.0 (Authorization Code flow). Needs a Spotify Developer app (client ID + secret) registered at developer.spotify.com/dashboard, plus a redirect URI for the local auth callback.
- **Scopes needed**: `user-read-currently-playing`, `user-read-playback-state`, `user-read-recently-played`.
- **Rate limits**: generous for personal use, but poll on an interval (e.g. every 30–60s) rather than continuously.
- **Maps to**: `src/ingestors/spotify_api.py` (live), `src/ingestors/spotify_history.py` (bulk import).

### 1.2 Apple Music
- **Live "now playing"**: Apple Music API (MusicKit), or — since Apple Music has no personal "currently playing" endpoint comparable to Spotify's — consider the local `Music.app`/`Music` app scripting bridge on macOS (AppleScript/JXA via `osascript`) as the practical source of truth if you're primarily listening on a Mac.
- **Historical import**: Apple's "Get a copy of your data" export (privacy.apple.com) includes Apple Music Play Activity as CSV.
- **Auth**: MusicKit requires a paid Apple Developer account, a MusicKit private key (.p8), Team ID, and Key ID to mint developer tokens; a user token additionally requires Sign in with Apple Music from a frontend (JS `MusicKit.configure` + authorization flow) — there's no server-only OAuth flow like Spotify's.
- **Maps to**: `src/ingestors/apple_history.py` (import); live-polling ingestor not yet scaffolded — decide MusicKit vs. local scripting before building it.

### 1.3 YouTube Music
- **No official "now playing" API.** Options to evaluate:
  - Unofficial `ytmusicapi` (Python) using browser-cookie auth — read-only, fragile to Google changes, but no OAuth app needed.
  - Browser extension / userscript that scrobbles from the YT Music web player to a local webhook (the README's "webapp scrobbler" idea).
  - YouTube Data API v3 only exposes playlists/history in limited ways — not real-time playback.
- **Historical import**: Google Takeout → YouTube and YouTube Music → history (JSON/HTML watch history).
- **Auth**: depends on chosen approach — `ytmusicapi` needs your browser's request headers/cookies exported; Data API v3 needs a Google Cloud project + OAuth client.
- **Maps to**: `src/ingestors/ytmusic_api.py` (live), `src/ingestors/ytmusic_history.py` (import).

### 1.4 Bandcamp
- Explicitly TBD per README — no API; likely scraping or manual purchase-history export later. Not required for v1.

## 2. Storage
- **Engine**: SQLite (per README's preference for unhosted/private) — `src/database/schema.sql` + `db_manager.py` are stubbed for this.
- **Core tables to design**:
  - `tracks` (canonical track identity — title, artist, album, ISRC/external IDs if available)
  - `plays` / `scrobbles` (timestamp, source service, track ref, ms played, context e.g. playlist)
  - `sources` (which service + which ingestion method: live poll vs. historical import, to dedupe overlap)
- **Cross-service matching**: `src/processing/matcher.py` implies you'll need to reconcile the same song appearing across Spotify/Apple/YT with different IDs/naming — fuzzy match on artist+title (+ duration) is the usual approach.

## 3. Processing & analytics
- `src/processing/art_fetcher.py` — album art (Spotify/Apple/YT provide art URLs; cache locally).
- `src/processing/palette.py` — likely extracting a dominant color palette from art for UI theming.
- `src/analytics/metrics.py` — define the actual stats you want first: top artists/tracks/albums (by period), total listening time, streaks, service breakdown, time-of-day patterns, etc.

## 4. Output / presentation
- `src/generator/readme_card.py` — generates an image/SVG card (à la GitHub readme stats cards) for embedding in your GitHub profile README or portfolio.
- `ui/` (app.py, views: overview, top_charts, historical_import, vinyl_shelf component) — looks like a local dashboard app (Streamlit-shaped, given `app.py` + `views/` naming, but confirm your intended framework — Streamlit, Flask+HTMX, FastAPI+frontend, etc. — since that choice drives a lot of the `ui/` structure).

## 5. Automation
- GitHub Actions workflow (`.github/` exists — check what's in there) to run `scripts/sync_now.py` and `scripts/generate_stats.py` on a schedule, then commit/push the updated card.
- Since this touches personal API tokens, secrets need to live in GitHub Actions secrets, not the repo — `.env` is already gitignored, confirm the same discipline in CI.

## 6. Open decisions before implementation
1. **UI framework** for `ui/app.py` — Streamlit vs. something else.
2. **Apple Music live tracking** — MusicKit (needs paid dev account) vs. local `osascript` polling of Music.app.
3. **YouTube Music approach** — `ytmusicapi` unofficial client vs. custom scrobbler.
4. **Polling vs. webhook** model for live "now playing" — a long-running poller script, or triggered checks.
5. **Track identity/matching strategy** across services for `matcher.py`.

## 7. What to line up before full implementation starts
- [ ] Spotify Developer app (client ID/secret, redirect URI) → scopes above
- [ ] Spotify Extended Streaming History export requested (long lead time)
- [ ] Apple Developer account status (needed only if going MusicKit route)
- [ ] Apple "Get a copy of your data" export requested
- [ ] Google Takeout export for YouTube Music history
- [ ] Decision on YT Music live-tracking approach (affects whether a Google Cloud project is needed)
- [ ] `.env` populated with the above credentials (keys only, referenced from `config/settings.py`)
