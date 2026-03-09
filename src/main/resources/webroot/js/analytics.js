// ===== Analytics Tab =====

document.getElementById('btn-load-analytics').addEventListener('click', loadAnalytics);

async function loadAnalytics() {
  const userId = window.Settings.userId;
  if (!userId) { window.showToast('Please set your FPL User ID first'); return; }

  const gwId = parseInt(document.querySelector('input[placeholder="Gameweek"]')?.value || '0');
  try {
    const report = await window.apiFetch(`/analytics/${userId}/gameweek/${gwId || 0}`);
    if (report.status === 'computing') {
      document.getElementById('analytics-gw').textContent = '...';
      window.showToast('Computing analytics, try again in a moment');
      return;
    }
    renderAnalytics(report);
  } catch (e) {
    window.showToast('Error loading analytics: ' + e.message);
  }
}

function renderAnalytics(report) {
  document.getElementById('analytics-gw').textContent = report.gameweekId || '?';
  renderFormTable(report.formScores || []);
  renderTransfers(report.transferSuggestions || []);
  renderChips(report.chipTiming);
  renderFdr(report.fixtureDifficulty || []);
}

// ===== Form Table =====
function renderFormTable(formScores) {
  const tbody = document.getElementById('form-tbody');
  tbody.innerHTML = '';
  formScores.forEach(fs => {
    const tr = document.createElement('tr');
    const trendClass = { UP: 'trend-up', DOWN: 'trend-down', STABLE: 'trend-stable' }[fs.trend] || 'trend-stable';
    const trendEmoji = { UP: '↑', DOWN: '↓', STABLE: '→' }[fs.trend] || '→';
    tr.innerHTML = `
      <td><strong>${esc(fs.webName)}</strong></td>
      <td>${esc(fs.teamShortName || '')}</td>
      <td><strong style="color:var(--accent-green)">${fs.formScore?.toFixed(1) ?? '—'}</strong></td>
      <td class="${trendClass}">${trendEmoji} ${esc(fs.trend)}</td>
      <td>${(fs.last5Points || []).join(', ') || '—'}</td>
    `;
    tbody.appendChild(tr);
  });
  if (!formScores.length) {
    tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text-secondary)">No form data yet. Ensure player history is refreshed.</td></tr>';
  }
}

// ===== Transfer Cards =====
function renderTransfers(suggestions) {
  const container = document.getElementById('transfers-container');
  container.innerHTML = '';
  if (!suggestions.length) {
    container.innerHTML = '<p style="color:var(--text-secondary)">No transfer suggestions available. Analytics may still be computing.</p>';
    return;
  }
  suggestions.forEach(ts => {
    const div = document.createElement('div');
    div.className = 'transfer-card';
    div.innerHTML = `
      <div class="transfer-player">
        <div class="transfer-player-name">${esc(ts.transferOut?.webName || '—')}</div>
        <div class="transfer-player-meta">Form ${ts.transferOut?.formScore?.toFixed(1) ?? '—'} · FDR ${ts.transferOut?.fdrScore?.toFixed(1) ?? '—'}</div>
      </div>
      <div class="transfer-arrow">→</div>
      <div class="transfer-player">
        <div class="transfer-player-name">${esc(ts.transferIn?.webName || '—')}</div>
        <div class="transfer-player-meta">Form ${ts.transferIn?.formScore?.toFixed(1) ?? '—'} · FDR ${ts.transferIn?.fdrScore?.toFixed(1) ?? '—'}</div>
      </div>
      <div>
        <div class="transfer-ev">+${ts.evGain?.toFixed(1) ?? '0'}</div>
        <div class="transfer-ev-label">EV gain</div>
      </div>
      <div class="transfer-reasoning">${esc(ts.reasoning || '')}</div>
    `;
    container.appendChild(div);
  });
}

// ===== Chip Cards =====
function renderChips(chipTiming) {
  const container = document.getElementById('chips-container');
  container.innerHTML = '';
  if (!chipTiming) {
    container.innerHTML = '<p style="color:var(--text-secondary)">No chip data available.</p>';
    return;
  }
  const chips = [chipTiming.wildcard, chipTiming.freeHit, chipTiming.tripleCaptain, chipTiming.benchBoost];
  chips.filter(Boolean).forEach(chip => {
    const scoreClass = chip.score >= 7 ? 'score-high' : chip.score >= 4 ? 'score-mid' : 'score-low';
    const unavailableClass = !chip.available ? 'chip-unavailable' : '';
    const div = document.createElement('div');
    div.className = `chip-card ${unavailableClass}`;
    div.innerHTML = `
      <div class="chip-card-header">
        <span class="chip-name">${esc(chip.chipName)}</span>
        <span class="chip-score ${scoreClass}">${chip.score}/10</span>
      </div>
      <div class="chip-recommendation">${esc(chip.recommendation)}</div>
      <div class="chip-rationale">${esc(chip.rationale)}</div>
    `;
    container.appendChild(div);
  });
}

// ===== FDR Heat Map Table =====
function renderFdr(fdrList) {
  const container = document.getElementById('fdr-container');
  if (!fdrList.length) {
    container.innerHTML = '<p style="color:var(--text-secondary)">No fixture data yet.</p>';
    return;
  }
  let html = '<table class="fdr-table"><thead><tr><th>Team</th>';
  const maxFixtures = Math.max(...fdrList.map(f => (f.next5Fixtures || []).length));
  for (let i = 1; i <= maxFixtures; i++) html += `<th>GW+${i}</th>`;
  html += '<th>Avg FDR</th></tr></thead><tbody>';
  fdrList.forEach(team => {
    html += `<tr><td><strong>${esc(team.teamShortName || team.teamName || '')}</strong></td>`;
    (team.next5Fixtures || []).forEach(f => {
      const d = f.difficulty || 3;
      html += `<td><span class="fdr-cell fdr-${d}">${esc(f.opponent || '')} ${f.isHome ? '(H)' : '(A)'}</span></td>`;
    });
    // Pad empty cells
    for (let i = (team.next5Fixtures || []).length; i < maxFixtures; i++) html += '<td>—</td>';
    html += `<td><strong>${team.fdrScore?.toFixed(1) ?? '—'}</strong></td></tr>`;
  });
  html += '</tbody></table>';
  container.innerHTML = html;
}

function esc(str) {
  if (str == null) return '';
  return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}
