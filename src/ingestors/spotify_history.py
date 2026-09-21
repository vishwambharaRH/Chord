import json
from pathlib import Path

from src.database.db_manager import DBManager

HISTORY_DIR_NAME = "Spotify Extended Streaming History"


def _iter_records(history_dir: Path):
    for path in sorted(history_dir.glob("Streaming_History_*.json")):
        records = json.loads(path.read_text())
        for record in records:
            yield record


def import_history(data_dir: Path, db: DBManager) -> int:
    """Parse Spotify's Extended Streaming History export and load it into the DB.

    Returns the number of plays inserted (duplicates are skipped via the
    plays table's UNIQUE constraint).
    """
    history_dir = data_dir / HISTORY_DIR_NAME
    if not history_dir.is_dir():
        raise FileNotFoundError(f"Spotify history export not found at {history_dir}")

    imported = 0
    for record in _iter_records(history_dir):
        title = record.get("master_metadata_track_name")
        artist = record.get("master_metadata_album_artist_name")
        if not title or not artist:
            continue

        track_id = db.upsert_track(
            title=title,
            artist=artist,
            album=record.get("master_metadata_album_album_name"),
            external_ids=record.get("spotify_track_uri"),
        )
        db.insert_play(
            track_id=track_id,
            service="spotify",
            played_at=record["ts"],
            ms_played=record.get("ms_played"),
            source="import",
            context=record.get("reason_start"),
        )
        imported += 1

    return imported


if __name__ == "__main__":
    from config.settings import DATA_DIR

    manager = DBManager()
    count = import_history(DATA_DIR, manager)
    print(f"Processed {count} Spotify plays.")
    print(manager.total_stats())
