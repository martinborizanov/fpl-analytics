// ===== AI Advice Tab =====

document.getElementById('btn-get-advice').addEventListener('click', getAdvice);

async function getAdvice() {
  const userId = window.Settings.userId;
  if (!userId) { window.showToast('Please set your FPL User ID first'); return; }

  const focus = document.getElementById('advice-focus').value;
  const gwId = parseInt(document.getElementById('advice-gw').value || '0', 10);
  const output = document.getElementById('advice-output');
  const status = document.getElementById('advice-status');
  const btn = document.getElementById('btn-get-advice');

  output.textContent = '';
  output.classList.add('typing-cursor');
  status.textContent = 'Generating AI advice via Ollama...';
  btn.disabled = true;

  try {
    // Trigger generation
    const res = await window.apiFetch(`/advice/${userId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ gameweekId: gwId, focus })
    });

    const requestId = res.requestId;
    if (!requestId) throw new Error('No requestId returned');

    status.textContent = `Streaming response (requestId: ${requestId})...`;

    // Connect to SSE stream
    const evtSource = new EventSource(`/api/v1/advice/${userId}/stream/${requestId}`);

    evtSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.token) {
          output.textContent += data.token;
          // Auto-scroll to bottom
          output.scrollTop = output.scrollHeight;
        }
      } catch (e) { /* ignore parse errors */ }
    };

    evtSource.addEventListener('complete', () => {
      evtSource.close();
      output.classList.remove('typing-cursor');
      status.textContent = 'Generation complete ✓';
      btn.disabled = false;
    });

    evtSource.addEventListener('error', (e) => {
      evtSource.close();
      output.classList.remove('typing-cursor');
      status.textContent = 'Stream error or timeout.';
      btn.disabled = false;
    });

    evtSource.onerror = () => {
      evtSource.close();
      output.classList.remove('typing-cursor');
      status.textContent = 'Connection closed.';
      btn.disabled = false;
    };

  } catch (e) {
    output.classList.remove('typing-cursor');
    status.textContent = 'Error: ' + e.message;
    btn.disabled = false;
    window.showToast('Failed to get advice: ' + e.message);
  }
}
