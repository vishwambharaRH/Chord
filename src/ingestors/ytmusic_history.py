import json
import re
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from src.database.db_manager import DBManager

TAKEOUT_HISTORY_PATH = Path("Takeout") / "YouTube and YouTube Music" / "history" / "watch-history.json"
WATCHED_PREFIX = "Watched "
TOPIC_SUFFIX = " - Topic"
SHORTS_HASHTAG_RE = re.compile(r"#shorts?\b", re.IGNORECASE)
HASHTAG_RE = re.compile(r"#\w+")
HEAVY_HASHTAG_THRESHOLD = 3  # 3+ hashtags in a title is a strong Shorts/Reels-clip signal

# Takeout's "header" field tells us which product the watch happened in.
# Everything else in watch-history.json (regular long-form YouTube) is
# tagged "YouTube"; actual YouTube Music plays are tagged "YouTube Music".
HEADER_TO_SERVICE = {
    "YouTube Music": "ytmusic",
    "YouTube": "youtube",
}


def _clean_title(raw_title: str) -> str:
    if raw_title.startswith(WATCHED_PREFIX):
        return raw_title[len(WATCHED_PREFIX):]
    return raw_title


def _clean_artist(channel_name: str) -> str:
    if channel_name.endswith(TOPIC_SUFFIX):
        return channel_name[: -len(TOPIC_SUFFIX)]
    return channel_name


def _extract_video_id(title_url: str) -> str | None:
    query = parse_qs(urlparse(title_url).query)
    values = query.get("v")
    return values[0] if values else None


def _is_short(title: str) -> bool:
    # Takeout doesn't flag Shorts with a distinct header, URL pattern, or
    # duration field (titleUrl is identical to a regular watch), so this is
    # a heuristic on the title text: an explicit #shorts-style hashtag, or
    # heavy hashtag-stuffing (a common pattern for viral short-form clips).
    # It will still miss Shorts with a plain, hashtag-free title — there's
    # no signal in the export to catch those.
    if SHORTS_HASHTAG_RE.search(title):
        return True
    return len(HASHTAG_RE.findall(title)) >= HEAVY_HASHTAG_THRESHOLD


def _iter_records(history_path: Path):
    records = json.loads(history_path.read_text())
    for record in records:
        service = HEADER_TO_SERVICE.get(record.get("header"))
        if service is None:
            continue
        title = record.get("title")
        title_url = record.get("titleUrl")
        if not title or not record.get("time") or not title_url:
            continue
        if "/post/" in title_url:
            continue  # a community post view, not a video watch
        if _is_short(title):
            continue
        yield service, record


def import_history(data_dir: Path, db: DBManager) -> int:
    history_path = data_dir / TAKEOUT_HISTORY_PATH
    if not history_path.is_file():
        raise FileNotFoundError(f"YouTube history export not found at {history_path}")

    imported = 0
    for service, record in _iter_records(history_path):
        title = _clean_title(record["title"])
        subtitles = record.get("subtitles") or []
        artist = _clean_artist(subtitles[0]["name"]) if subtitles else "Unknown"

        track_id = db.upsert_track(title=title, artist=artist)
        db.insert_play(
            track_id=track_id,
            service=service,
            played_at=record["time"],
            source="import",
            context=_extract_video_id(record["titleUrl"]),
        )
        imported += 1

    return imported


if __name__ == "__main__":
    from config.settings import DATA_DIR

    manager = DBManager()
    count = import_history(DATA_DIR, manager)
    print(f"Processed {count} YouTube plays.")
    print(manager.total_stats())
