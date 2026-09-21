import sqlite3
from contextlib import contextmanager
from pathlib import Path

from config.settings import DB_PATH

SCHEMA_PATH = Path(__file__).resolve().parent / "schema.sql"


class DBManager:
    def __init__(self, db_path: Path = DB_PATH):
        self.db_path = db_path
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._init_schema()

    @contextmanager
    def connect(self):
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON")
        try:
            yield conn
            conn.commit()
        finally:
            conn.close()

    def _init_schema(self):
        with self.connect() as conn:
            conn.executescript(SCHEMA_PATH.read_text())

    def upsert_track(self, title: str, artist: str, album: str | None = None,
                      duration_ms: int | None = None, art_url: str | None = None,
                      external_ids: str | None = None) -> int:
        # SQLite's UNIQUE constraint treats every NULL as distinct from every
        # other NULL, so an unknown album must be normalized to '' here —
        # otherwise repeated plays of the same NULL-album track (e.g. from
        # YouTube Music imports, which have no album data) would each insert
        # a new track row instead of matching the existing one.
        album = album or ""
        with self.connect() as conn:
            conn.execute(
                """
                INSERT INTO tracks (title, artist, album, duration_ms, art_url, external_ids)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (title, artist, album) DO UPDATE SET
                    duration_ms = COALESCE(excluded.duration_ms, tracks.duration_ms),
                    art_url = COALESCE(excluded.art_url, tracks.art_url),
                    external_ids = COALESCE(excluded.external_ids, tracks.external_ids)
                """,
                (title, artist, album, duration_ms, art_url, external_ids),
            )
            row = conn.execute(
                "SELECT id FROM tracks WHERE title = ? AND artist = ? AND album = ?",
                (title, artist, album),
            ).fetchone()
            return row["id"]

    def insert_play(self, track_id: int, service: str, played_at: str,
                     ms_played: int | None = None, source: str = "import",
                     context: str | None = None) -> None:
        with self.connect() as conn:
            conn.execute(
                """
                INSERT OR IGNORE INTO plays (track_id, service, played_at, ms_played, source, context)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (track_id, service, played_at, ms_played, source, context),
            )

    def list_services(self):
        with self.connect() as conn:
            return [r["service"] for r in conn.execute(
                "SELECT DISTINCT service FROM plays ORDER BY service"
            ).fetchall()]

    def fetch_top_tracks(self, limit: int = 20, service: str | None = None):
        where = "WHERE p.service = ?" if service else ""
        params = (service, limit) if service else (limit,)
        with self.connect() as conn:
            return conn.execute(
                f"""
                SELECT t.title, t.artist, t.album, t.art_url,
                       COUNT(*) AS play_count,
                       SUM(p.ms_played) AS total_ms_played
                FROM plays p
                JOIN tracks t ON t.id = p.track_id
                {where}
                GROUP BY p.track_id
                ORDER BY play_count DESC
                LIMIT ?
                """,
                params,
            ).fetchall()

    def fetch_recent_plays(self, limit: int = 50, service: str | None = None):
        where = "WHERE p.service = ?" if service else ""
        params = (service, limit) if service else (limit,)
        with self.connect() as conn:
            return conn.execute(
                f"""
                SELECT t.title, t.artist, t.album, p.service, p.played_at
                FROM plays p
                JOIN tracks t ON t.id = p.track_id
                {where}
                ORDER BY p.played_at DESC
                LIMIT ?
                """,
                params,
            ).fetchall()

    def total_stats(self, service: str | None = None):
        where = "WHERE service = ?" if service else ""
        params = (service,) if service else ()
        with self.connect() as conn:
            row = conn.execute(
                f"""
                SELECT COUNT(*) AS total_plays,
                       COUNT(DISTINCT track_id) AS unique_tracks,
                       SUM(ms_played) AS total_ms_played
                FROM plays
                {where}
                """,
                params,
            ).fetchone()
            return dict(row)
