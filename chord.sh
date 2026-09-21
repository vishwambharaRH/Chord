#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [ -x "/Users/vishwam/VSCode/.venv/bin/python3" ]; then
    PYTHON="/Users/vishwam/VSCode/.venv/bin/python3"
else
    PYTHON="python3"
fi

usage() {
    cat <<EOF
Usage: ./chord.sh <command>

Commands:
  import-spotify   Import Spotify Extended Streaming History from data/
  import-ytmusic   Import YouTube Music history from data/Takeout/
  fetch-durations  Backfill YouTube/YT Music play durations via the Data API
  fetch-art        Backfill Spotify album art (and duration) via the Web API
  sync             Poll Spotify for what's currently playing (Ctrl+C to stop)
  stats            Regenerate web/data.json from the database
  serve            Serve web/ locally at http://localhost:8080
  refresh          stats + serve, in one go
EOF
}

case "${1:-}" in
    import-spotify)
        "$PYTHON" -m src.ingestors.spotify_history
        ;;
    import-ytmusic)
        "$PYTHON" -m src.ingestors.ytmusic_history
        ;;
    fetch-durations)
        "$PYTHON" -m src.ingestors.youtube_durations
        ;;
    fetch-art)
        "$PYTHON" -m src.processing.art_fetcher
        ;;
    sync)
        "$PYTHON" scripts/sync_now.py "${@:2}"
        ;;
    stats)
        "$PYTHON" scripts/generate_stats.py
        ;;
    serve)
        cd web && "$PYTHON" -m http.server 8080
        ;;
    refresh)
        "$PYTHON" scripts/generate_stats.py
        cd web && "$PYTHON" -m http.server 8080
        ;;
    *)
        usage
        exit 1
        ;;
esac
