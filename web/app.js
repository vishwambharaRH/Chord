const WEEKDAY_LABELS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const CHART_COLORS = {
  primary: "#1db954",
  grid: "#2a2a2a",
  text: "#a0a0a0",
};
const SERVICE_LABELS = {
  spotify: "Spotify",
  youtube: "YouTube",
  ytmusic: "YouTube Music",
  applemusic: "Apple Music",
};

const charts = {};
let fullData = null;
let activeFilter = "all";

function formatHours(ms) {
  return (ms / 1000 / 60 / 60).toLocaleString(undefined, { maximumFractionDigits: 1 });
}

function formatDate(iso) {
  const d = new Date(iso);
  return d.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function clear(id) {
  const node = document.getElementById(id);
  node.innerHTML = "";
  return node;
}

function renderStatGrid(totals) {
  const grid = clear("stat-grid");
  const cards = [
    { label: "Total plays", value: totals.total_plays.toLocaleString() },
    { label: "Unique tracks", value: totals.unique_tracks.toLocaleString() },
    {
      label: "Hours listened",
      value: totals.total_ms_played == null ? "—" : formatHours(totals.total_ms_played),
    },
  ];
  cards.forEach(({ label, value }) => {
    const card = el("div", "stat-card");
    if (value === "—") card.title = "This service's export doesn't include listening duration.";
    card.append(el("div", "value", value), el("div", "label", label));
    grid.append(card);
  });
}

function renderRecentPlays(plays) {
  const list = clear("recent-plays");
  if (!plays.length) {
    list.append(el("li", null, "No plays for this filter yet."));
    return;
  }
  plays.forEach((play) => {
    const item = el("li");
    const info = el("span", null, `${play.title} — ${play.artist}`);
    const meta = el("span", "meta", `${play.service} · ${formatDate(play.played_at)}`);
    item.append(info, meta);
    list.append(item);
  });
}

function renderRankList(containerId, items, { titleKey, subtitleKey, countKey }) {
  const list = clear(containerId);
  if (!items.length) {
    list.append(el("li", null, "No data for this filter yet."));
    return;
  }
  items.forEach((item, index) => {
    const li = el("li");
    li.append(
      el("span", "rank", String(index + 1)),
      el("span", "title", item[titleKey]),
      el("span", "subtitle", subtitleKey ? item[subtitleKey] || "" : ""),
      el("span", "count", `${item[countKey].toLocaleString()} plays`)
    );
    list.append(li);
  });
}

function baseChartOptions(extra = {}) {
  return {
    responsive: true,
    plugins: { legend: { display: false } },
    scales: {
      x: { ticks: { color: CHART_COLORS.text }, grid: { color: CHART_COLORS.grid } },
      y: { ticks: { color: CHART_COLORS.text }, grid: { color: CHART_COLORS.grid }, beginAtZero: true },
    },
    ...extra,
  };
}

function upsertChart(id, config) {
  if (charts[id]) {
    charts[id].destroy();
  }
  charts[id] = new Chart(document.getElementById(id), config);
}

function renderCharts(filterData, serviceBreakdown) {
  upsertChart("chart-month", {
    type: "line",
    data: {
      labels: filterData.by_month.map((r) => r.month),
      datasets: [
        {
          label: "Plays",
          data: filterData.by_month.map((r) => r.play_count),
          borderColor: CHART_COLORS.primary,
          backgroundColor: "rgba(29, 185, 84, 0.15)",
          fill: true,
          tension: 0.25,
        },
      ],
    },
    options: baseChartOptions(),
  });

  const weekdayByIndex = new Array(7).fill(0);
  filterData.by_weekday.forEach((r) => (weekdayByIndex[r.weekday] = r.play_count));
  upsertChart("chart-weekday", {
    type: "bar",
    data: {
      labels: WEEKDAY_LABELS,
      datasets: [{ data: weekdayByIndex, backgroundColor: CHART_COLORS.primary }],
    },
    options: baseChartOptions(),
  });

  const hourByIndex = new Array(24).fill(0);
  filterData.by_hour.forEach((r) => (hourByIndex[r.hour] = r.play_count));
  upsertChart("chart-hour", {
    type: "bar",
    data: {
      labels: hourByIndex.map((_, h) => `${h}:00`),
      datasets: [{ data: hourByIndex, backgroundColor: CHART_COLORS.primary }],
    },
    options: baseChartOptions(),
  });

  upsertChart("chart-service", {
    type: "bar",
    data: {
      labels: serviceBreakdown.map((r) => SERVICE_LABELS[r.service] || r.service),
      datasets: [{ data: serviceBreakdown.map((r) => r.play_count), backgroundColor: CHART_COLORS.primary }],
    },
    options: { ...baseChartOptions(), indexAxis: "y" },
  });
}

function renderFilterBar() {
  const bar = clear("service-filter");
  bar.append(el("span", "filter-label", "Service:"));

  const options = [{ key: "all", label: "All" }, ...fullData.services.map((s) => ({
    key: s,
    label: SERVICE_LABELS[s] || s,
  }))];

  options.forEach(({ key, label }) => {
    const btn = el("button", "filter-btn", label);
    if (key === activeFilter) btn.classList.add("active");
    btn.addEventListener("click", () => {
      activeFilter = key;
      renderFilterBar();
      renderFiltered();
    });
    bar.append(btn);
  });
}

function renderFiltered() {
  const filterData = fullData.filters[activeFilter];

  renderStatGrid(filterData.totals);
  renderRecentPlays(filterData.recent_plays);
  renderRankList("top-tracks", filterData.top_tracks, {
    titleKey: "title",
    subtitleKey: "artist",
    countKey: "play_count",
  });
  renderRankList("top-artists", filterData.top_artists, {
    titleKey: "artist",
    countKey: "play_count",
  });
  const serviceBreakdown =
    activeFilter === "all"
      ? fullData.by_service
      : fullData.by_service.filter((r) => r.service === activeFilter);
  renderCharts(filterData, serviceBreakdown);
}

function renderVinylShelf(albums) {
  const shelf = clear("vinyl-shelf");

  if (!albums.length) {
    shelf.append(el("p", "vinyl-empty", "No albums yet — import some listening history first."));
    return;
  }

  const ROW_SIZE = 6;
  const shown = albums;

  for (let i = 0; i < shown.length; i += ROW_SIZE) {
    const row = el("div", "vinyl-shelf-row");
    shown.slice(i, i + ROW_SIZE).forEach((album) => {
      const item = el("div", "vinyl-item");
      const disc = el("div", "vinyl-disc");
      const sleeve = el("div", "vinyl-sleeve");
      if (album.art_url) {
        sleeve.style.backgroundImage = `url("${album.art_url}")`;
      } else {
        sleeve.classList.add("vinyl-sleeve-placeholder");
        sleeve.textContent = "🎵";
      }
      item.append(disc, sleeve, renderVinylStats(album));

      const label = el("div", "vinyl-label");
      label.append(document.createTextNode(album.album), el("span", "artist", album.artist));

      const wrapper = el("div");
      wrapper.append(item, label);
      row.append(wrapper);
    });
    shelf.append(row);
  }
}

function formatDuration(ms) {
  if (!ms) return "unknown time";
  const totalMinutes = Math.round(ms / 1000 / 60);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours === 0) return `${minutes}m`;
  return `${hours}h ${minutes}m`;
}

function renderVinylStats(album) {
  const stats = el("div", "vinyl-stats");
  stats.append(
    el("div", "vinyl-stats-line", `${album.play_count.toLocaleString()} plays`),
    el(
      "div",
      "vinyl-stats-line",
      album.service_count === 1 ? "1 service" : `${album.service_count} services`
    ),
    el("div", "vinyl-stats-line", formatDuration(album.total_ms_played))
  );
  return stats;
}

function setupTabs() {
  const buttons = document.querySelectorAll(".tab-btn");
  buttons.forEach((btn) => {
    btn.addEventListener("click", () => {
      buttons.forEach((b) => b.classList.remove("active"));
      document.querySelectorAll(".tab-panel").forEach((p) => p.classList.remove("active"));
      btn.classList.add("active");
      document.getElementById(`tab-${btn.dataset.tab}`).classList.add("active");
    });
  });
}

async function main() {
  setupTabs();
  try {
    const res = await fetch(new URLSearchParams(location.search).get("data") || "data.json", { cache: "no-store" });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    fullData = await res.json();
  } catch (err) {
    document.getElementById("app").hidden = true;
    document.getElementById("empty-state").hidden = false;
    return;
  }

  document.getElementById("generated-at").textContent =
    "Updated " + new Date(fullData.generated_at).toLocaleString();

  renderFilterBar();
  renderFiltered();
  renderVinylShelf((fullData.filters.spotify || fullData.filters.all).top_albums);
}

main();
