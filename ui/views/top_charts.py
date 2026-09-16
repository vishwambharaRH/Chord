import pandas as pd


def render(st, db):
    st.header("Top Charts")

    limit = st.slider("Number of tracks", min_value=5, max_value=50, value=20)
    rows = db.fetch_top_tracks(limit=limit)

    if not rows:
        st.info("No plays recorded yet. Head to 'Historical Import' to load your listening history.")
        return

    df = pd.DataFrame([dict(r) for r in rows])
    df["hours_played"] = (df["total_ms_played"].fillna(0) / 1000 / 60 / 60).round(2)

    st.bar_chart(df.set_index("title")["play_count"])
    st.dataframe(
        df[["title", "artist", "album", "play_count", "hours_played"]],
        use_container_width=True,
        hide_index=True,
    )
