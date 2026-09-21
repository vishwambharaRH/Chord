"""One-off migration: merge track rows that only differ because SQLite
treated a NULL album as distinct from every other NULL album (fixed in
db_manager.upsert_track, which now normalizes album to ''). Any tracks
already duplicated before that fix are merged here, moving their plays
onto a single canonical track row.
"""

import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT_DIR))

from src.database.db_manager import DBManager


def dedupe(db: DBManager) -> int:
    merged = 0
    with db.connect() as conn:
        # Normalize NULL to '' up front (matches upsert_track's convention)
        # so a merged group's surviving row doesn't stay NULL and cause the
        # next import to treat it as a fresh, unmatched track again.
        conn.execute("UPDATE tracks SET album = '' WHERE album IS NULL")

        groups = conn.execute(
            """
            SELECT title, artist, COALESCE(album, '') AS album_key
            FROM tracks
            GROUP BY title, artist, album_key
            HAVING COUNT(*) > 1
            """
        ).fetchall()

        for group in groups:
            ids = [
                row["id"]
                for row in conn.execute(
                    "SELECT id FROM tracks WHERE title = ? AND artist = ? AND COALESCE(album, '') = ?",
                    (group["title"], group["artist"], group["album_key"]),
                ).fetchall()
            ]
            canonical_id, *duplicate_ids = sorted(ids)
            for dup_id in duplicate_ids:
                conn.execute(
                    "UPDATE OR IGNORE plays SET track_id = ? WHERE track_id = ?",
                    (canonical_id, dup_id),
                )
                conn.execute("DELETE FROM plays WHERE track_id = ?", (dup_id,))
                conn.execute("DELETE FROM tracks WHERE id = ?", (dup_id,))
                merged += 1

    return merged


if __name__ == "__main__":
    db = DBManager()
    count = dedupe(db)
    print(f"Merged {count} duplicate track rows.")
    print(db.total_stats())
