// ===== League Tab =====

let rankChart = null;

document.getElementById('btn-load-league').addEventListener('click', loadLeague);

async function loadLeague() {
  const leagueId = window.Settings.leagueId;
  if (!leagueId) { window.showToast('Please set your League ID first'); return; }

  try {
    const data = await window.apiFetch(`/league/${leagueId}/standings`);
    if (data.status === 'fetching') {
      window.showToast('Fetching league data, try again shortly');
      return;
    }
    renderStandings(data);
  } catch (e) {
    window.showToast('Error loading league: ' + e.message);
  }
}

function renderStandings(data) {
  const tbody = document.getElementById('standings-tbody');
  tbody.innerHTML = '';

  let standings = [];
  // Handle both raw MongoDB doc and parsed standings
  if (data.raw) {
    try {
      const parsed = JSON.parse(data.raw);
      standings = parsed.standings?.results || [];
    } catch (e) { standings = []; }
  } else {
    standings = data.standings || [];
  }

  standings.forEach(entry => {
    const rankChange = (entry.last_rank || entry.lastRank || 0) - (entry.rank || 0);
    const changeEl = rankChange > 0
      ? `<span class="rank-change-up">▲${rankChange}</span>`
      : rankChange < 0
        ? `<span class="rank-change-down">▼${Math.abs(rankChange)}</span>`
        : `<span class="rank-change-same">—</span>`;

    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td><strong>${entry.rank || '—'}</strong></td>
      <td>${esc(entry.player_name || entry.playerName || '—')}</td>
      <td>${esc(entry.entry_name || entry.entryName || '—')}</td>
      <td>${entry.event_total ?? entry.eventTotal ?? '—'}</td>
      <td><strong>${entry.total ?? entry.totalPoints ?? '—'}</strong></td>
      <td>${changeEl}</td>
    `;
    tbody.appendChild(tr);
  });

  if (!standings.length) {
    tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--text-secondary)">No standings data available.</td></tr>';
  }

  // Draw rank-over-time chart (placeholder data until history endpoint is implemented)
  renderRankChart(standings.slice(0, 10));
}

function renderRankChart(topEntries) {
  const ctx = document.getElementById('rankChart').getContext('2d');
  if (rankChart) rankChart.destroy();

  const labels = Array.from({ length: 8 }, (_, i) => 'GW ' + (i + 21));
  const colors = ['#00d4aa', '#8b5cf6', '#3b82f6', '#f85149', '#d29922', '#3fb950', '#e879f9', '#22d3ee', '#fb923c', '#a3e635'];

  const datasets = topEntries.slice(0, 5).map((entry, i) => ({
    label: entry.entry_name || entry.entryName || 'Team ' + (i + 1),
    data: labels.map(() => Math.floor(Math.random() * 5) + 1), // Placeholder
    borderColor: colors[i],
    backgroundColor: 'transparent',
    tension: 0.3,
    pointRadius: 4,
  }));

  rankChart = new Chart(ctx, {
    type: 'line',
    data: { labels, datasets },
    options: {
      responsive: true,
      plugins: {
        legend: { labels: { color: '#8b949e', font: { family: 'Segoe UI' } } },
        title: { display: true, text: 'Rank Over Recent Gameweeks', color: '#e6edf3' }
      },
      scales: {
        y: {
          reverse: true,
          ticks: { color: '#8b949e', stepSize: 1 },
          grid: { color: '#30363d' }
        },
        x: {
          ticks: { color: '#8b949e' },
          grid: { color: '#30363d' }
        }
      }
    }
  });
}

function esc(str) {
  if (str == null) return '';
  return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}
