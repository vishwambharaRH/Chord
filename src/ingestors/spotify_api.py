import json
import time
from datetime import datetime, timezone

import spotipy
from spotipy.oauth2 import SpotifyOAuth

from config.settings import (
    DATA_DIR,
    ROOT_DIR,
    SPOTIFY_CLIENT_ID,
    SPOTIFY_CLIENT_SECRET,
    SPOTIFY_REDIRECT_URI,
    SPOTIFY_SCOPES,
)
from src.database.db_manager import DBManager

CACHE_PATH = ROOT_DIR / "config" / ".spotify_token_cache"
STATE_PATH = DATA_DIR / ".spotify_live_state.json"


def get_client(interactive: bool = True) -> spotipy.Spotify:
    auth_manager = SpotifyOAuth(
        client_id=SPOTIFY_CLIENT_ID,
        client_secret=SPOTIFY_CLIENT_SECRET,
        redirect_uri=SPOTIFY_REDIRECT_URI,
        scope=SPOTIFY_SCOPES,
        cache_path=str(CACHE_PATH),
        open_browser=interactive,
    )
    if not interactive and not auth_manager.validate_token(auth_manager.cache_handler.get_cached_token()):
        raise SystemExit("No valid cached Spotify token; re-authenticate locally and refresh the secret.")
    # spotipy's default retry behavior sleeps through whatever Retry-After
    # a 429 response carries, which can be enormous (Spotify has returned
    # values over 24h for this app after a burst of requests). retries=0
    # makes a rate-limited call fail immediately instead of hanging, so
    # callers can decide how to handle it rather than blocking for a day.
    return spotipy.Spotify(auth_manager=auth_manager, retries=0, requests_timeout=10)


def fetch_currently_playing(sp: spotipy.Spotify):
    result = sp.current_user_playing_track()
    if not result or not result.get("item") or result["item"].get("type") != "track":
        return None

    item = result["item"]
    images = item.get("album", {}).get("images") or []
    return {
        "uri": item["uri"],
        "title": item["name"],
        "artist": item["artists"][0]["name"] if item.get("artists") else "Unknown",
        "album": item.get("album", {}).get("name"),
        "art_url": images[0]["url"] if images else None,
        "duration_ms": item.get("duration_ms"),
        "progress_ms": result.get("progress_ms"),
        "is_playing": result.get("is_playing", False),
    }


def _record_play(db: DBManager, track: dict, started_at: datetime):
    track_id = db.upsert_track(
        title=track["title"],
        artist=track["artist"],
        album=track["album"],
        duration_ms=track["duration_ms"],
        art_url=track["art_url"],
    )
    db.insert_play(
        track_id=track_id,
        service="spotify",
        played_at=started_at.isoformat(),
        ms_played=track["progress_ms"],
        source="live",
    )
    print(f"Logged: {track['title']} — {track['artist']}")


def _step(db: DBManager, sp: spotipy.Spotify, state: dict | None) -> dict | None:
    """Run one poll cycle against `state` (None, or {"track": {...}, "started_at": iso-str}).

    When the playing track changes (or playback stops), the previous track
    is written to the DB with how far into it playback had reached — an
    approximation of ms_played, since the API only reports progress, not a
    true "listened for N ms" figure like the history export gives us.

    Returns the state to carry into the next cycle.
    """
    playing = fetch_currently_playing(sp)
    now_uri = playing["uri"] if playing and playing["is_playing"] else None
    current_uri = state["track"]["uri"] if state else None

    if now_uri != current_uri:
        if state:
            _record_play(db, state["track"], datetime.fromisoformat(state["started_at"]))
        if playing and playing["is_playing"]:
            print(f"Now playing: {playing['title']} — {playing['artist']}")
            return {"track": playing, "started_at": datetime.now(timezone.utc).isoformat()}
        return None

    if playing and playing["is_playing"]:
        return {"track": playing, "started_at": state["started_at"]}

    return state


def poll(db: DBManager, interval_seconds: int = 30):
    """Continuously poll the Spotify Web API for what's currently playing."""
    sp = get_client()
    state = None

    print(f"Polling Spotify every {interval_seconds}s. Ctrl+C to stop.")
    while True:
        try:
            state = _step(db, sp, state)
        except Exception as exc:
            print(f"Spotify API error: {exc}")
        time.sleep(interval_seconds)


def _load_state() -> dict | None:
    if STATE_PATH.exists():
        return json.loads(STATE_PATH.read_text())
    return None


def _save_state(state: dict | None):
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    if state is None:
        STATE_PATH.unlink(missing_ok=True)
    else:
        STATE_PATH.write_text(json.dumps(state))


def check_once(db: DBManager):
    """One poll-and-record cycle, with state persisted to disk between calls.

    Meant to be invoked by cron (or any scheduler) instead of running as a
    long-lived process — each invocation loads state, checks Spotify once,
    saves state, and exits.
    """
    sp = get_client()
    state = _load_state()
    try:
        state = _step(db, sp, state)
    except Exception as exc:
        print(f"Spotify API error: {exc}")
        return
    _save_state(state)


if __name__ == "__main__":
    poll(DBManager())
