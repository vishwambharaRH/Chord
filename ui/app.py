import sys
from pathlib import Path

import streamlit as st

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.database.db_manager import DBManager
from ui.components.color_theme import inject_theme
from ui.views import historical_import, overview, top_charts

st.set_page_config(page_title="Chord", page_icon="🎵", layout="wide")
inject_theme(st)

st.title("🎵 Chord")

db = DBManager()

PAGES = {
    "Overview": overview,
    "Top Charts": top_charts,
    "Historical Import": historical_import,
}

page = st.sidebar.radio("Navigate", list(PAGES.keys()))
PAGES[page].render(st, db)
