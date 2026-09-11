create directory str as follows

├── .github/
│   └── workflows/
│       └── update-readme-stats.yml    # GitHub Action: Runs cron to regenerate README stats SVG/PNG
├── config/
│   └── settings.py                    # Environment variables, DB paths, and threshold configs
├── data/
│   ├── sample/                        # Place sample JSON/CSV exports here for testing
│   └── .gitkeep                       # Keeps folder tracked (actual SQLite DB will be ignored)
├── src/
│   ├── __init__.py
│   ├── database/                      # Storage Layer
│   │   ├── __init__.py
│   │   ├── schema.sql                 # Table definitions (streams, tracks, album_art_cache)
│   │   └── db_manager.py              # SQLite connection, initializers, and bulk insert helpers
│   ├── ingestors/                     # Extract Layer (Historical & Live Sync)
│   │   ├── __init__.py
│   │   ├── base.py                    # Abstract base ingestor class
│   │   ├── spotify_history.py         # Parses Endsong_X.json (Extended History)
│   │   ├── spotify_api.py             # Pulls live /recently-played via OAuth
│   │   ├── apple_history.py           # Parses Apple Music Play Activity.csv
│   │   ├── ytmusic_history.py         # Parses Google Takeout JSON/CSV dumps
│   │   └── ytmusic_api.py             # Live sync via ytmusicapi (headers auth)
│   ├── processing/                    # Transform Layer
│   │   ├── __init__.py
│   │   ├── matcher.py                 # ISRC matching & rapidfuzz string fallback
│   │   ├── palette.py                 # Pillow/ColorThief color extraction engine
│   │   └── art_fetcher.py             # Spotify API + iTunes public search API cache fetcher
│   ├── analytics/                     # Query & Analytics Engine
│   │   ├── __init__.py
│   │   └── metrics.py                 # SQL queries for Top Artists, Top Albums, Vinyl Shelf, Platform Split
│   └── generator/                     # Headless Publishing Engine
│       ├── __init__.py
│       └── readme_card.py             # Generates SVG/PNG stat cards for GitHub README
├── ui/                                # Presentation Layer (Streamlit Dashboard)
│   ├── app.py                         # Main Streamlit dashboard entry point
│   ├── components/                    # UI Widgets
│   │   ├── vinyl_shelf.py             # CSS/HTML 3D record grid component
│   │   └── color_theme.py             # Applies dynamic album accent colors to UI
│   └── views/                         # Dashboard Pages
│       ├── overview.py                # Overall stats & recent scrobbles
│       ├── historical_import.py       # UI upload runner for new JSON/CSV dumps
│       └── top_charts.py              # Stats.fm style Top Artists/Albums/Tracks filterable by time
├── scripts/
│   ├── sync_now.py                    # CLI script to trigger manual background ingestion
│   └── generate_stats.py              # CLI runner invoked by GitHub Actions
├── .env.example                       # API key & header path templates
├── .gitignore                         # Excludes secrets, *.db, auth JSONs, and local cache
├── requirements.txt                   # Dependency list (streamlit, spotipy, ytmusicapi, rapidfuzz, pillow, etc.)
└── README.md                          # Project docs + dynamically updated stats card