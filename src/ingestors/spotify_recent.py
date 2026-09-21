from datetime import datetime, timedelta, timezone

from src.database.db_manager import DBManager
from src.ingestors.spotify_api import get_client


def _normalize_ts(played_at: str) -> str:
    # The history export uses second-precision "...Z" timestamps; matching
    # that format lets the plays UNIQUE constraint dedupe overlap between
    # the export and this endpoint.
    dt = datetime.fromisoformat(played_at.replace("Z", "+00:00")).astimezone(timezone.utc)
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def _phone_already_has(db: DBManager, title: str, end_ts: str, duration_ms: int) -> bool:
    """The phone app stamps plays with their *start* time, this endpoint with
    the end; treat a same-title phone play starting within one track length
    (plus slack for pauses) before this end time as the same listen."""
    end = datetime.strptime(end_ts, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    lo = (end - timedelta(milliseconds=duration_ms, minutes=10)).strftime("%Y-%m-%dT%H:%M:%SZ")
    with db.connect() as conn:
        return conn.execute(
            """
            SELECT 1 FROM plays p JOIN tracks t ON t.id = p.track_id
            WHERE p.service = 'spotify' AND p.source = 'phone'
              AND t.title = ? AND p.played_at BETWEEN ? AND ?
            LIMIT 1
            """,
            (title, lo, end_ts),
        ).fetchone() is not None


def sync_recent(db: DBManager, interactive: bool = False) -> int:
    """Insert the last 50 plays from Spotify's recently-played endpoint.

    Stateless: safe to run on any schedule, since re-fetched plays are
    ignored via the UNIQUE(track_id, service, played_at) constraint. ms_played
    isn't provided by this endpoint, so it's left NULL.
    """
    sp = get_client(interactive=interactive)
    items = sp.current_user_recently_played(limit=50).get("items", [])

    before = db.total_stats()["total_plays"]
    for item in items:
        track = item["track"]
        if track.get("type") != "track":
            continue
        played_at = _normalize_ts(item["played_at"])
        if _phone_already_has(db, track["name"], played_at, track.get("duration_ms") or 0):
            continue
        images = track.get("album", {}).get("images") or []
        track_id = db.upsert_track(
            title=track["name"],
            artist=track["artists"][0]["name"] if track.get("artists") else "Unknown",
            album=track.get("album", {}).get("name"),
            duration_ms=track.get("duration_ms"),
            art_url=images[0]["url"] if images else None,
            external_ids=track.get("uri"),
        )
        db.insert_play(
            track_id=track_id,
            service="spotify",
            played_at=played_at,
            source="live",
            context="recently_played",
        )
    return db.total_stats()["total_plays"] - before


if __name__ == "__main__":
    added = sync_recent(DBManager())
    print(f"Added {added} new Spotify plays.")
