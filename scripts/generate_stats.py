import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT_DIR))

from src.analytics import metrics
from src.database.db_manager import DBManager

OUTPUT_PATH = Path(os.getenv("CHORD_STATS_PATH", ROOT_DIR / "web" / "data.json"))


def build_payload(db: DBManager, service: str | None = None) -> dict:
    return {
        "totals": db.total_stats(service=service),
        "top_tracks": [dict(r) for r in db.fetch_top_tracks(limit=50, service=service)],
        "top_artists": metrics.top_artists(db, limit=50, service=service),
        "top_albums": metrics.top_albums(db, limit=50, service=service),
        "by_month": metrics.plays_by_month(db, service=service),
        "by_weekday": metrics.plays_by_weekday(db, service=service),
        "by_hour": metrics.plays_by_hour(db, service=service),
        "recent_plays": [dict(r) for r in db.fetch_recent_plays(limit=50, service=service)],
    }


def main():
    db = DBManager()
    services = db.list_services()

    filters = {"all": build_payload(db)}
    for service in services:
        filters[service] = build_payload(db, service=service)

    payload = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "services": services,
        "by_service": metrics.plays_by_service(db),
        "filters": filters,
    }

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(json.dumps(payload, indent=2))
    print(f"Wrote {OUTPUT_PATH} ({payload['filters']['all']['totals']['total_plays']} plays, services: {services})")


if __name__ == "__main__":
    main()
