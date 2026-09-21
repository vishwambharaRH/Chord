import os
from pathlib import Path

from dotenv import load_dotenv

ROOT_DIR = Path(__file__).resolve().parent.parent
load_dotenv(ROOT_DIR / ".env")

DATA_DIR = ROOT_DIR / "data"
DB_PATH = Path(os.getenv("CHORD_DB_PATH", DATA_DIR / "chord.db"))

SPOTIFY_CLIENT_ID = os.getenv("SPOTIFY_CLIENT_ID")
SPOTIFY_CLIENT_SECRET = os.getenv("SPOTIFY_CLIENT_SECRET")
SPOTIFY_REDIRECT_URI = os.getenv("SPOTIFY_REDIRECT_URI")
SPOTIFY_SCOPES = "user-read-currently-playing user-read-playback-state user-read-recently-played"

YTMUSIC_HEADERS_PATH = os.getenv("YTMUSIC_HEADERS_PATH", str(ROOT_DIR / "config" / "ytmusic_headers.json"))

YOUTUBE_API_KEY = os.getenv("YOUTUBE_API_KEY")
