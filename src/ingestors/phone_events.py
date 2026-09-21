import json
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

from src.database.db_manager import DBManager

PACKAGE_TO_SERVICE = {
    "com.spotify.music": "spotify",
    "com.google.android.apps.youtube.music": "ytmusic",
    "com.apple.android.music": "applemusic",
}

TS_FORMAT = "%Y-%m-%dT%H:%M:%SZ"


def _iso(ms: int) -> str:
    return datetime.fromtimestamp(ms / 1000, tz=timezone.utc).strftime(TS_FORMAT)


def _resolve_artist(db: DBManager, title: str, artist: str) -> str:
    """Android joins every credited artist ("Kanye West, Dwele") while the
    Spotify API/export use the first one. Reuse an existing track's artist
    when it's a prefix of this string so plays land on the same track row;
    otherwise fall back to the first credited name."""
    with db.connect() as conn:
        row = conn.execute(
            "SELECT artist FROM tracks WHERE title = ? AND ? LIKE artist || '%' "
            "ORDER BY LENGTH(artist) DESC LIMIT 1",
            (title, artist),
        ).fetchone()
    if row:
        return row["artist"]
    return artist.split(", ")[0] if artist else "Unknown"


def _find_recent_sync_play(db: DBManager, title: str, start: datetime, listened_ms: int):
    """A play the Spotify recently-played sync already recorded for this
    listen. It stamps plays with their *end* time, so look between the phone's
    start time and start + listened time (plus slack for clock/API lag)."""
    lo = start.strftime(TS_FORMAT)
    hi = (start + timedelta(milliseconds=listened_ms, seconds=180)).strftime(TS_FORMAT)
    with db.connect() as conn:
        return conn.execute(
            """
            SELECT p.id FROM plays p JOIN tracks t ON t.id = p.track_id
            WHERE p.service = 'spotify' AND p.context = 'recently_played'
              AND t.title = ? AND p.played_at BETWEEN ? AND ?
            LIMIT 1
            """,
            (title, lo, hi),
        ).fetchone()


def ingest_file(db: DBManager, path: Path) -> tuple[int, int]:
    added = merged = 0
    for line in path.read_text().splitlines():
        if not line.strip():
            continue
        event = json.loads(line)
        service = PACKAGE_TO_SERVICE.get(event["package"])
        if service is None or not event.get("title"):
            continue

        start = datetime.fromtimestamp(event["ts"] / 1000, tz=timezone.utc)
        listened = int(event["listened_ms"])

        if service == "spotify":
            existing = _find_recent_sync_play(db, event["title"], start, listened)
            if existing:
                with db.connect() as conn:
                    conn.execute("UPDATE plays SET ms_played = ? WHERE id = ?", (listened, existing["id"]))
                merged += 1
                continue

        artist = _resolve_artist(db, event["title"], event.get("artist", ""))
        track_id = db.upsert_track(
            title=event["title"],
            artist=artist,
            album=event.get("album") or None,
            duration_ms=event.get("duration_ms") or None,
        )
        db.insert_play(
            track_id=track_id,
            service=service,
            played_at=_iso(event["ts"]),
            ms_played=listened,
            source="phone",
        )
        added += 1
    return added, merged


def ingest_inbox(db: DBManager, inbox: Path, delete: bool = True) -> tuple[int, int]:
    total_added = total_merged = 0
    for path in sorted(inbox.glob("*.jsonl")):
        added, merged = ingest_file(db, path)
        total_added += added
        total_merged += merged
        if delete:
            path.unlink()
    return total_added, total_merged


if __name__ == "__main__":
    inbox_dir = Path(sys.argv[1])
    added, merged = ingest_inbox(DBManager(), inbox_dir)
    print(f"Phone events: {added} new plays, {merged} merged into existing Spotify plays.")
