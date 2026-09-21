import sys
import time

import spotipy

from src.database.db_manager import DBManager
from src.ingestors.spotify_api import get_client

# Spotify's rate limit is a rolling 30s window (undocumented exact size).
# A prior run at 0.05s pacing (~20 req/s) tripped a 24h app-wide lockout,
# so this stays well under that: ~2 req/s.
PAUSE_SECONDS = 0.5
TOP_ALBUMS = 50


def _pending_representative_tracks(db: DBManager, limit: int = TOP_ALBUMS):
    """One track per top-N album (by play count) that still needs art.

    Art is really an album-level property, and top_albums() aggregates with
    MAX(art_url) across an album's tracks, so a single successful lookup per
    album is all the Vinyl Shelf needs — no reason to fetch every track.
    """
    with db.connect() as conn:
        top_albums = conn.execute(
            """
            SELECT t.album, t.artist
            FROM plays p
            JOIN tracks t ON t.id = p.track_id
            WHERE p.service = 'spotify' AND t.album != ''
            GROUP BY t.album, t.artist
            ORDER BY COUNT(*) DESC
            LIMIT ?
            """,
            (limit,),
        ).fetchall()

        pending = []
        for row in top_albums:
            track = conn.execute(
                """
                SELECT id, external_ids
                FROM tracks
                WHERE album = ? AND artist = ?
                  AND external_ids LIKE 'spotify:track:%'
                  AND art_url IS NULL
                LIMIT 1
                """,
                (row["album"], row["artist"]),
            ).fetchone()
            if track:
                pending.append(track)
        return pending


def fetch_art(db: DBManager) -> int:
    """Backfill album art (and track duration) for the top albums using the
    track URIs captured during history import.

    Spotify's batch "Get Several Tracks" endpoint returns 403 for this app
    (likely a Development Mode restriction), even though the single-track
    endpoint works fine — so this fetches one track at a time instead.
    """
    sp = get_client()
    pending = _pending_representative_tracks(db)
    print(f"{len(pending)} albums still need art (1 API call each).")

    updated = 0
    for i, row in enumerate(pending, start=1):
        try:
            track = sp.track(row["external_ids"])
        except spotipy.SpotifyException as exc:
            if exc.http_status == 429:
                print(f"  rate limited at track {i}/{len(pending)} — stopping here, "
                      f"resume later (already-fetched albums won't be re-requested).")
                sys.exit(1)
            print(f"  track {i}/{len(pending)} ({row['external_ids']}) failed: {exc}")
            time.sleep(PAUSE_SECONDS)
            continue
        except Exception as exc:
            print(f"  track {i}/{len(pending)} ({row['external_ids']}) failed: {exc}")
            time.sleep(PAUSE_SECONDS)
            continue

        images = track.get("album", {}).get("images") or []
        art_url = images[0]["url"] if images else None
        with db.connect() as conn:
            conn.execute(
                """
                UPDATE tracks
                SET art_url = COALESCE(?, art_url),
                    duration_ms = COALESCE(duration_ms, ?)
                WHERE id = ?
                """,
                (art_url, track.get("duration_ms"), row["id"]),
            )
        if art_url:
            updated += 1
        print(f"  {i}/{len(pending)}: {'ok' if art_url else 'no art found'}")
        time.sleep(PAUSE_SECONDS)

    return updated


if __name__ == "__main__":
    manager = DBManager()
    count = fetch_art(manager)
    print(f"Backfilled art for {count} albums.")
