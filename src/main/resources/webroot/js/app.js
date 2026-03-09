// ===== FPL Analytics — Main App JS =====

const API_BASE = '/api/v1';

// User settings persisted in localStorage
const Settings = {
  get userId() { return parseInt(localStorage.getItem('fpl.userId') || '0', 10); },
  set userId(v) { localStorage.setItem('fpl.userId', v); },
  get leagueId() { return parseInt(localStorage.getItem('fpl.leagueId') || '0', 10); },
  set leagueId(v) { localStorage.setItem('fpl.leagueId', v); },
};

// ===== Tab Navigation =====
document.querySelectorAll('.tab-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById('tab-' + btn.dataset.tab).classList.add('active');
  });
});

// ===== User ID Save =====
document.getElementById('btnSaveIds').addEventListener('click', () => {
  const uid = parseInt(document.getElementById('inputUserId').value, 10);
  const lid = parseInt(document.getElementById('inputLeagueId').value, 10);
  if (uid > 0) Settings.userId = uid;
  if (lid > 0) Settings.leagueId = lid;
  showToast('Settings saved');
  loadDashboard();
});

// Restore saved values on page load
window.addEventListener('DOMContentLoaded', () => {
  if (Settings.userId) document.getElementById('inputUserId').value = Settings.userId;
  if (Settings.leagueId) document.getElementById('inputLeagueId').value = Settings.leagueId;
  loadDashboard();
});

// ===== API Helper =====
async function apiFetch(path, options = {}) {
  const res = await fetch(API_BASE + path, options);
  if (!res.ok) throw new Error(`HTTP ${res.status} on ${path}`);
  return res.json();
}

// ===== Dashboard =====
async function loadDashboard() {
  // Health check
  try {
    const health = await apiFetch('/health');
    const el = document.getElementById('health-status');
    el.textContent = health.status;
    el.style.color = health.status === 'UP' ? 'var(--success)' : 'var(--danger)';
  } catch (e) {
    document.getElementById('health-status').textContent = 'DOWN';
  }
}

document.getElementById('btn-refresh-bootstrap').addEventListener('click', async () => {
  const status = document.getElementById('refresh-status');
  status.textContent = 'Refreshing...';
  try {
    await apiFetch('/refresh/bootstrap', { method: 'POST' });
    status.textContent = 'Refresh triggered ✓';
    setTimeout(() => status.textContent = '', 3000);
  } catch (e) {
    status.textContent = 'Error: ' + e.message;
  }
});

// ===== Toast Notification =====
function showToast(msg) {
  const toast = document.createElement('div');
  toast.textContent = msg;
  Object.assign(toast.style, {
    position: 'fixed', bottom: '24px', right: '24px',
    background: 'var(--accent-green)', color: '#000',
    padding: '10px 20px', borderRadius: '6px',
    fontWeight: '600', zIndex: '9999',
    animation: 'none'
  });
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 2500);
}

window.Settings = Settings;
window.apiFetch = apiFetch;
window.showToast = showToast;
