def render(st, db):
    st.header("Historical Import")
    st.caption("Upload exports from each service. Parsing is not wired up yet — this is the intake UI.")

    with st.expander("Spotify — Extended Streaming History", expanded=True):
        st.file_uploader(
            "Upload the .json files from your Spotify data export",
            type=["json"],
            accept_multiple_files=True,
            key="spotify_upload",
        )

    with st.expander("Apple Music — Play Activity"):
        st.file_uploader(
            "Upload the Play Activity .csv from your Apple data export",
            type=["csv"],
            accept_multiple_files=True,
            key="apple_upload",
        )

    with st.expander("YouTube Music — Takeout history"):
        st.file_uploader(
            "Upload watch-history.json from Google Takeout",
            type=["json"],
            accept_multiple_files=True,
            key="ytmusic_upload",
        )

    st.button("Import uploaded files", disabled=True, help="Ingestor logic not implemented yet")
