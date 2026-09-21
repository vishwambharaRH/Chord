import re
import time
from pathlib import Path

import requests

from src.database.db_manager import DBManager

API_URL = "https://www.googleapis.com/youtube/v3/videos"
BATCH_SIZE = 50  # videos.list accepts at most 50 ids per call
PAUSE_SECONDS = 0.2

ISO8601_DURATION_RE = re.compile(
    r"P(?:(?P<days>\d+)D)?T?(?:(?P<hours>\d+)H)?(?:(?P<minutes>\d+)M)?(?:(?P<seconds>\d+)S)?"
)


def _parse_duration_ms(iso_duration: str) -> int | None:
    match = ISO8601_DURATION_RE.fullmatch(iso_duration)
    if not match:
        return None
    parts = {key: int(value) if value else 0 for key, value in match.groupdict().items()}
    seconds = parts["days"] * 86400 + parts["hours"] * 3600 + parts["minutes"] * 60 + parts["seconds"]
    return seconds * 1000


def _fetch_batch(video_ids: list[str], api_key: str) -> dict[str, int]:
    resp = requests.get(
        API_URL,
        params={"part": "contentDetails", "id": ",".join(video_ids), "key": api_key},
        timeout=15,
    )
    resp.raise_for_status()
    durations = {}
    for item in resp.json().get("items", []):
        ms = _parse_duration_ms(item["contentDetails"]["duration"])
        if ms is not None:
            durations[item["id"]] = ms
    return durations


def _pending_video_ids(db: DBManager) -> list[str]:
    with db.connect() as conn:
        rows = conn.execute(
            """
            SELECT DISTINCT context
            FROM plays
            WHERE service IN ('youtube', 'ytmusic')
              AND context IS NOT NULL
              AND ms_played IS NULL
            """
        ).fetchall()
    return [row["context"] for row in rows]


def fetch_durations(db: DBManager, api_key: str) -> int:
    """Backfill ms_played for YouTube/YouTube Music plays using each video's
    total duration from the YouTube Data API — Takeout gives no watch-time
    data, so this is a proxy (assumes a full watch) rather than a true
    "listened for N ms" figure.
    """
    video_ids = _pending_video_ids(db)
    total_batches = -(-len(video_ids) // BATCH_SIZE)
    print(f"{len(video_ids)} videos need durations ({total_batches} API calls).")

    updated = 0
    for i in range(0, len(video_ids), BATCH_SIZE):
        batch = video_ids[i : i + BATCH_SIZE]
        batch_num = i // BATCH_SIZE + 1
        try:
            durations = _fetch_batch(batch, api_key)
        except requests.RequestException as exc:
            print(f"  batch {batch_num}/{total_batches} failed: {exc}")
            time.sleep(PAUSE_SECONDS)
            continue

        with db.connect() as conn:
            for video_id, ms in durations.items():
                conn.execute(
                    "UPDATE plays SET ms_played = ? WHERE context = ? AND ms_played IS NULL",
                    (ms, video_id),
                )
        updated += len(durations)

        print(f"  batch {batch_num}/{total_batches}: {len(durations)}/{len(batch)} durations found "
              f"(videos removed, private, or age-restricted won't resolve)")
        time.sleep(PAUSE_SECONDS)

    return updated


if __name__ == "__main__":
    from config.settings import YOUTUBE_API_KEY

    if not YOUTUBE_API_KEY:
        raise SystemExit("Set YOUTUBE_API_KEY in .env first.")

    manager = DBManager()
    count = fetch_durations(manager, YOUTUBE_API_KEY)
    print(f"Backfilled durations for {count} videos.")
    print(manager.total_stats())
