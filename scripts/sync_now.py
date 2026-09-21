import argparse
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT_DIR))

from src.database.db_manager import DBManager
from src.ingestors.spotify_api import check_once, poll


def main():
    parser = argparse.ArgumentParser(description="Poll Spotify for what's currently playing.")
    parser.add_argument("--interval", type=int, default=30, help="Seconds between polls (default: 30)")
    parser.add_argument(
        "--once",
        action="store_true",
        help="Run a single check-and-exit cycle instead of looping (for cron)",
    )
    args = parser.parse_args()

    if args.once:
        check_once(DBManager())
    else:
        poll(DBManager(), interval_seconds=args.interval)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nStopped.")
