PALETTE = {
    "background": "#121212",
    "surface": "#1e1e1e",
    "primary": "#1db954",
    "text": "#f5f5f5",
    "muted": "#a0a0a0",
}


def inject_theme(st):
    st.markdown(
        f"""
        <style>
        .stApp {{
            background-color: {PALETTE['background']};
            color: {PALETTE['text']};
        }}
        [data-testid="stSidebar"] {{
            background-color: {PALETTE['surface']};
        }}
        </style>
        """,
        unsafe_allow_html=True,
    )
