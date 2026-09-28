import csv
from datetime import datetime, timedelta, timezone
from pathlib import Path

from src.database.db_manager import DBManager

HISTORY_DIR_NAME = "Apple Music Activity"
DAILY_TRACKS_FILE = "Apple Music - Play History Daily Tracks.csv"


def _split_description(description: str) -> tuple[str, str] | None:
    # Apple's export gives "Artist - Title"; the separator can appear again
    # inside the artist name (e.g. "A$AP Rocky - Praise the Lord"), so only
    # the first " - " is treated as the split point.
    if " - " not in description:
        return None
    artist, title = description.split(" - ", 1)
    artist, title = artist.strip(), title.strip()
    if not artist or not title:
        return None
    return artist, title


def _iter_records(history_dir: Path):
    path = history_dir / DAILY_TRACKS_FILE
    with path.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            play_count = int(row.get("Play Count") or 0)
            if play_count < 1:
                continue  # a session that never crossed the "played" threshold
            parsed = _split_description(row.get("Track Description") or "")
            if parsed is None:
                continue
            yield row, parsed, play_count


def import_history(data_dir: Path, db: DBManager) -> int:
    """Parse Apple Music's "Play History Daily Tracks" export and load it into the DB.

    Each row aggregates a track's plays for one hour of one day, so
    individual plays are spread across that hour (one minute apart) to stay
    distinct under the plays table's UNIQUE(track_id, service, played_at).
    Returns the number of plays inserted.
    """
    history_dir = data_dir / HISTORY_DIR_NAME
    if not history_dir.is_dir():
        raise FileNotFoundError(f"Apple Music history export not found at {history_dir}")

    imported = 0
    for row, (artist, title), play_count in _iter_records(history_dir):
        date_played = row["Date Played"]
        # "Hours" is usually a single hour-of-day, but can list several
        # (e.g. "3, 8") when the day's plays of this track spanned more
        # than one hour; the first is used as an anchor for played_at.
        hours_field = (row.get("Hours") or "0").split(",")[0].strip()
        hour = int(hours_field or 0)
        base = datetime.strptime(date_played, "%Y%m%d").replace(
            hour=hour, tzinfo=timezone.utc
        )

        total_ms = int(row.get("Play Duration Milliseconds") or 0)
        ms_played = total_ms // play_count if play_count else None

        track_id = db.upsert_track(title=title, artist=artist)
        for i in range(play_count):
            played_at = base + timedelta(minutes=i)
            db.insert_play(
                track_id=track_id,
                service="applemusic",
                played_at=played_at.isoformat(),
                ms_played=ms_played,
                source="import",
                context=row.get("End Reason Type"),
            )
            imported += 1

    return imported


if __name__ == "__main__":
    from config.settings import DATA_DIR

    manager = DBManager()
    count = import_history(DATA_DIR, manager)
    print(f"Processed {count} Apple Music plays.")
    print(manager.total_stats())
