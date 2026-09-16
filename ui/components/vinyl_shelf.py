def render_vinyl_shelf(st, tracks):
    """Render a row of album-art 'records' for the given tracks (list of sqlite3.Row)."""
    if not tracks:
        st.info("No tracks to show yet — import some listening history first.")
        return

    columns = st.columns(min(len(tracks), 6))
    for col, track in zip(columns, tracks):
        with col:
            if track["art_url"]:
                st.image(track["art_url"], use_container_width=True)
            else:
                st.markdown("🎵")
            st.caption(f"**{track['title']}**\n\n{track['artist']}")
