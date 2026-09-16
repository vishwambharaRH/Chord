from ui.components.vinyl_shelf import render_vinyl_shelf


def render(st, db):
    st.header("Overview")

    stats = db.total_stats()
    total_plays = stats["total_plays"] or 0
    unique_tracks = stats["unique_tracks"] or 0
    total_hours = (stats["total_ms_played"] or 0) / 1000 / 60 / 60

    col1, col2, col3 = st.columns(3)
    col1.metric("Total plays", f"{total_plays:,}")
    col2.metric("Unique tracks", f"{unique_tracks:,}")
    col3.metric("Hours listened", f"{total_hours:,.1f}")

    st.subheader("Recently played")
    recent = db.fetch_recent_plays(limit=10)
    if not recent:
        st.info("No plays recorded yet. Head to 'Historical Import' to load your listening history.")
        return

    for play in recent:
        st.write(f"**{play['title']}** — {play['artist']} · {play['service']} · {play['played_at']}")

    st.subheader("Top tracks")
    render_vinyl_shelf(st, db.fetch_top_tracks(limit=6))
