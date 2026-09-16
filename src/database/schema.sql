CREATE TABLE IF NOT EXISTS tracks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    album TEXT,
    duration_ms INTEGER,
    art_url TEXT,
    external_ids TEXT,
    UNIQUE (title, artist, album)
);

CREATE TABLE IF NOT EXISTS plays (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    track_id INTEGER NOT NULL REFERENCES tracks (id),
    service TEXT NOT NULL,
    played_at TEXT NOT NULL,
    ms_played INTEGER,
    source TEXT NOT NULL,
    context TEXT,
    UNIQUE (track_id, service, played_at)
);

CREATE INDEX IF NOT EXISTS idx_plays_played_at ON plays (played_at);
CREATE INDEX IF NOT EXISTS idx_plays_track_id ON plays (track_id);
