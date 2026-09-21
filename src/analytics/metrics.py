from src.database.db_manager import DBManager


def top_artists(db: DBManager, limit: int = 20, service: str | None = None):
    where = "WHERE p.service = ?" if service else ""
    params = (service, limit) if service else (limit,)
    with db.connect() as conn:
        rows = conn.execute(
            f"""
            SELECT t.artist,
                   COUNT(*) AS play_count,
                   SUM(p.ms_played) AS total_ms_played
            FROM plays p
            JOIN tracks t ON t.id = p.track_id
            {where}
            GROUP BY t.artist
            ORDER BY play_count DESC
            LIMIT ?
            """,
            params,
        ).fetchall()
        return [dict(r) for r in rows]


def top_albums(db: DBManager, limit: int = 20, service: str | None = None):
    conditions = ["t.album IS NOT NULL", "t.album != ''"]
    params = []
    if service:
        conditions.append("p.service = ?")
        params.append(service)
    where = "WHERE " + " AND ".join(conditions)
    params.append(limit)
    with db.connect() as conn:
        rows = conn.execute(
            f"""
            SELECT t.album, t.artist,
                   COUNT(*) AS play_count,
                   SUM(p.ms_played) AS total_ms_played,
                   MAX(t.art_url) AS art_url,
                   COUNT(DISTINCT p.service) AS service_count
            FROM plays p
            JOIN tracks t ON t.id = p.track_id
            {where}
            GROUP BY t.album, t.artist
            ORDER BY play_count DESC
            LIMIT ?
            """,
            params,
        ).fetchall()
        return [dict(r) for r in rows]


def plays_by_month(db: DBManager, service: str | None = None):
    where = "WHERE service = ?" if service else ""
    params = (service,) if service else ()
    with db.connect() as conn:
        rows = conn.execute(
            f"""
            SELECT strftime('%Y-%m', played_at) AS month,
                   COUNT(*) AS play_count,
                   SUM(ms_played) AS total_ms_played
            FROM plays
            {where}
            GROUP BY month
            ORDER BY month
            """,
            params,
        ).fetchall()
        return [dict(r) for r in rows]


def plays_by_weekday(db: DBManager, service: str | None = None):
    where = "WHERE service = ?" if service else ""
    params = (service,) if service else ()
    with db.connect() as conn:
        rows = conn.execute(
            f"""
            SELECT CAST(strftime('%w', played_at) AS INTEGER) AS weekday,
                   COUNT(*) AS play_count
            FROM plays
            {where}
            GROUP BY weekday
            ORDER BY weekday
            """,
            params,
        ).fetchall()
        return [dict(r) for r in rows]


def plays_by_hour(db: DBManager, service: str | None = None):
    where = "WHERE service = ?" if service else ""
    params = (service,) if service else ()
    with db.connect() as conn:
        rows = conn.execute(
            f"""
            SELECT CAST(strftime('%H', played_at) AS INTEGER) AS hour,
                   COUNT(*) AS play_count
            FROM plays
            {where}
            GROUP BY hour
            ORDER BY hour
            """,
            params,
        ).fetchall()
        return [dict(r) for r in rows]


def plays_by_service(db: DBManager):
    with db.connect() as conn:
        rows = conn.execute(
            """
            SELECT service, COUNT(*) AS play_count
            FROM plays
            GROUP BY service
            ORDER BY play_count DESC
            """
        ).fetchall()
        return [dict(r) for r in rows]
