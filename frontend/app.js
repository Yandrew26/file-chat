// FileChat demo UI. Talks to the gateway endpoints:
//   POST /upload/pdf?conversationId=        upload + index a PDF
//   GET  /chat/graph/rag/stream?message&conversationId   SSE answer stream
//   GET  /chat/history/pages | /chat/history/getByConversationId
const $ = (id) => document.getElementById(id);
const el = { app: $('app'), convList: $('convList'), docTitle: $('docTitle'), connPill: $('connPill'),
  empty: $('empty'), chat: $('chat'), messages: $('messages'), input: $('input'), composer: $('composer'),
  send: $('send'), stop: $('stop'), suggestions: $('suggestions'), drop: $('drop'), fileInput: $('fileInput'),
  status: $('uploadStatus'), toast: $('toast'), settings: $('settings') };

// ---- settings (per-browser convenience; safe if storage is blocked) -----------------
const DEFAULTS = { baseUrl: '', authId: 'demo-user', token: 'demo-token', userId: '12345678' };
const store = {
  get(k, fallback) { try { return JSON.parse(localStorage.getItem(k)) ?? fallback; } catch { return fallback; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* ignore */ } },
};
let settings = { ...DEFAULTS, ...store.get('filechat.settings', {}) };
let docNames = store.get('filechat.docNames', {}); // conversationId -> uploaded file name

const state = { conv: null, sending: false, abort: null, serverConvs: [] };

// ---- API -------------------------------------------------------------------------
class ApiError extends Error { constructor(status) { super(`HTTP ${status}`); this.status = status; } }
const friendly = (e) => e instanceof ApiError
  ? ({ 401: 'Missing credentials — check authID / authorization in Connection.',
       403: 'The gateway rejected this token (403). Check Connection settings.' }[e.status] ?? `The server returned ${e.status}.`)
  : e.name === 'AbortError' ? 'Stopped.' : 'Could not reach the server. Is it running?';

function api(path, init = {}) {
  const headers = { authorization: `Bearer ${settings.token}`, authID: settings.authId, ...init.headers };
  return fetch(settings.baseUrl.replace(/\/$/, '') + path, { ...init, headers }).then((r) => {
    if (!r.ok) throw new ApiError(r.status);
    return r;
  });
}
const q = (o) => new URLSearchParams(o).toString();

// ---- helpers ---------------------------------------------------------------------
const esc = (s) => s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const inline = (s) => esc(s)
  .replace(/\[Doc:\s*([^\]]+)\]/g, '<span class="cite" title="Source document">$1</span>')
  .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  .replace(/\*(.+?)\*/g, '<em>$1</em>');
function renderMarkdown(text) {
  const out = []; let list = false;
  for (const line of text.split('\n')) {
    const m = line.match(/^\s*[-*]\s+(.*)/);
    if (m) { if (!list) { out.push('<ul>'); list = true; } out.push(`<li>${inline(m[1])}</li>`); continue; }
    if (list) { out.push('</ul>'); list = false; }
    if (line.trim()) out.push(`<p>${inline(line)}</p>`);
  }
  if (list) out.push('</ul>');
  return out.join('');
}
const when = (s) => { // "yyyy-MM-dd HH:mm:ss" (server sends GMT+8; shown as given)
  const d = new Date(s.replace(' ', 'T')); if (isNaN(d)) return '';
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
};
let toastTimer;
function toast(msg) { el.toast.textContent = msg; el.toast.hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => (el.toast.hidden = true), 4200); }
const newConversationId = () => `${settings.userId}_${crypto.randomUUID().replaceAll('-', '')}`;

// ---- sidebar ---------------------------------------------------------------------
async function loadConversations() {
  try {
    const page = await (await api(`/chat/history/pages?${q({ pageNum: 1, pageSize: 30, userId: settings.userId })}`)).json();
    state.serverConvs = page.data;
  } catch (e) { state.serverConvs = []; if (e instanceof ApiError) toast(friendly(e)); }
  renderSidebar();
}
function renderSidebar() {
  const items = new Map();
  for (const c of state.serverConvs) items.set(c.conversationId, { id: c.conversationId, title: docNames[c.conversationId] ?? c.content, meta: when(c.createdDate), doc: !!docNames[c.conversationId] });
  if (state.conv && !items.has(state.conv.id)) items.set(state.conv.id, { id: state.conv.id, title: state.conv.name, meta: 'Just now', doc: true });
  const list = [...items.values()];
  if (state.conv && !state.serverConvs.some((c) => c.conversationId === state.conv.id)) list.unshift(list.splice(list.findIndex((i) => i.id === state.conv.id), 1)[0]);
  el.convList.replaceChildren(...(list.length ? list.map((i) => {
    const b = document.createElement('button');
    b.className = 'conv'; b.type = 'button';
    if (state.conv?.id === i.id) b.setAttribute('aria-current', 'true');
    b.innerHTML = `<span class="conv-title">${esc(i.title)}</span><span class="conv-meta">${esc(i.meta)}</span>`;
    b.addEventListener('click', () => openConversation(i.id, i.title, i.doc));
    return b;
  }) : [Object.assign(document.createElement('div'), { className: 'none', textContent: 'No conversations yet.' })]));
}

// ---- views -----------------------------------------------------------------------
function setTitle(name, isDoc) {
  el.docTitle.innerHTML = isDoc
    ? `<span class="doc-chip">PDF</span><span class="doc-title">${esc(name)}</span>`
    : `<span class="doc-title">${esc(name)}</span>`;
}
function showEmpty() {
  state.conv = null; el.empty.hidden = false; el.chat.hidden = true; setTitle('New chat', false);
  el.status.textContent = ''; el.status.className = 'status'; renderSidebar(); closeMenu();
}
function showChat(id, name, isDoc) {
  state.conv = { id, name }; el.empty.hidden = true; el.chat.hidden = false; setTitle(name, isDoc);
  el.messages.replaceChildren(); renderSidebar(); closeMenu(); el.input.focus({ preventScroll: true });
}
async function openConversation(id, title, isDoc) {
  if (state.sending) return;
  showChat(id, title, isDoc);
  try {
    const rows = await (await api(`/chat/history/getByConversationId?${q({ conversationId: id })}`)).json();
    rows.sort((a, b) => a.id - b.id).forEach((r) => addMessage(r.type === 'USER' ? 'user' : 'bot', r.content));
    setSuggestions(rows.length === 0);
  } catch (e) { toast(friendly(e)); }
}

function addMessage(role, text = '') {
  const wrap = document.createElement('div'); wrap.className = `msg ${role === 'user' ? 'user' : 'bot'}`;
  const bubble = document.createElement('div'); bubble.className = 'bubble';
  if (role === 'user') bubble.textContent = text;
  else { wrap.innerHTML = '<div class="avatar" aria-hidden="true">F</div>'; bubble.innerHTML = renderMarkdown(text); }
  wrap.append(bubble); el.messages.append(wrap); scrollDown();
  return bubble;
}
const scrollDown = () => { el.messages.scrollTop = el.messages.scrollHeight; };

const SUGGESTIONS = ['Give me a summary', 'Who wrote this?', 'What are the main risks?', 'How did churn change?'];
function setSuggestions(show) {
  el.suggestions.replaceChildren(...(show ? SUGGESTIONS.map((s) => {
    const b = document.createElement('button'); b.type = 'button'; b.className = 'chip'; b.textContent = s;
    b.addEventListener('click', () => send(s)); return b;
  }) : []));
}

// ---- upload ----------------------------------------------------------------------
async function upload(file, label = file.name) {
  if (!/\.pdf$/i.test(file.name) && file.type !== 'application/pdf') { el.status.className = 'status err'; el.status.textContent = 'Please choose a PDF file.'; return; }
  const id = newConversationId();
  el.status.className = 'status'; el.status.innerHTML = `<span class="spinner"></span>Indexing ${esc(label)}…`;
  try {
    await api(`/upload/pdf?${q({ conversationId: id })}`, { method: 'POST', headers: { 'content-type': 'application/octet-stream' }, body: await file.arrayBuffer() });
  } catch (e) { el.status.className = 'status err'; el.status.textContent = friendly(e); return; }
  docNames[id] = label; store.set('filechat.docNames', docNames);
  showChat(id, label, true); setSuggestions(true);
}

// ---- chat / streaming ------------------------------------------------------------
function setSending(on) {
  state.sending = on; el.send.hidden = on; el.stop.hidden = !on; el.input.disabled = on;
  if (!on) el.input.focus({ preventScroll: true });
}
async function send(text) {
  text = text.trim(); if (!text || state.sending || !state.conv) return;
  el.input.value = ''; autosize(); setSuggestions(false); addMessage('user', text);
  const bubble = addMessage('bot'); bubble.classList.add('thinking');
  bubble.innerHTML = 'Searching the document<span class="dots"><span>.</span><span>.</span><span>.</span></span>';
  setSending(true); state.abort = new AbortController();
  let answer = '';
  try {
    const res = await api(`/chat/graph/rag/stream?${q({ message: text, conversationId: state.conv.id })}`,
      { signal: state.abort.signal, headers: { accept: 'text/event-stream' } });
    const reader = res.body.getReader(); const dec = new TextDecoder(); let buf = '';
    for (;;) {
      const { done, value } = await reader.read(); if (done) break;
      buf += dec.decode(value, { stream: true });
      let i; // SSE events are separated by a blank line
      while ((i = buf.indexOf('\n\n')) >= 0) {
        const evt = buf.slice(0, i); buf = buf.slice(i + 2);
        const data = evt.split('\n').filter((l) => l.startsWith('data:')).map((l) => l.slice(5)).join('\n');
        if (!data) continue;
        // Server frames look like {"<graph node>": "<text chunk>"}
        try { answer += Object.values(JSON.parse(data)).join(''); } catch { answer += data; }
        bubble.classList.remove('thinking'); bubble.classList.add('caret'); bubble.innerHTML = renderMarkdown(answer); scrollDown();
      }
    }
  } catch (e) {
    if (e.name === 'AbortError') { answer = answer ? answer + '\n\n*(stopped)*' : '*(stopped)*'; }
    else { bubble.closest('.msg').classList.add('err'); answer = friendly(e); }
  } finally {
    bubble.classList.remove('thinking', 'caret'); bubble.innerHTML = renderMarkdown(answer || '*(no response)*');
    setSending(false); state.abort = null; loadConversations();
  }
}

// ---- wiring ----------------------------------------------------------------------
function autosize() { el.input.style.height = 'auto'; el.input.style.height = Math.min(el.input.scrollHeight, 160) + 'px'; }
el.input.addEventListener('input', autosize);
el.input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); el.composer.requestSubmit(); } });
el.composer.addEventListener('submit', (e) => { e.preventDefault(); send(el.input.value); });
el.stop.addEventListener('click', () => state.abort?.abort());
$('newChat').addEventListener('click', () => { if (!state.sending) showEmpty(); });

el.fileInput.addEventListener('change', () => { const f = el.fileInput.files[0]; if (f) upload(f); el.fileInput.value = ''; });
el.drop.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); el.fileInput.click(); } });
['dragenter', 'dragover'].forEach((t) => el.drop.addEventListener(t, (e) => { e.preventDefault(); el.drop.classList.add('over'); }));
['dragleave', 'drop'].forEach((t) => el.drop.addEventListener(t, (e) => { e.preventDefault(); el.drop.classList.remove('over'); }));
el.drop.addEventListener('drop', (e) => { const f = e.dataTransfer.files[0]; if (f) upload(f); });
$('trySample').addEventListener('click', async () => {
  try { const r = await fetch('sample/q3-research-report.pdf'); upload(new File([await r.blob()], 'q3-research-report.pdf', { type: 'application/pdf' })); }
  catch { toast('Sample file not found.'); }
});

// theme
const applyTheme = (t) => { if (t) document.documentElement.dataset.theme = t; else delete document.documentElement.dataset.theme; };
applyTheme(store.get('filechat.theme', null));
$('toggleTheme').addEventListener('click', () => {
  const dark = (document.documentElement.dataset.theme ?? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')) === 'dark';
  const next = dark ? 'light' : 'dark'; applyTheme(next); store.set('filechat.theme', next);
});

// mobile drawer
const closeMenu = () => el.app.classList.remove('menu-open');
$('openMenu').addEventListener('click', () => el.app.classList.add('menu-open'));
$('scrim').addEventListener('click', closeMenu);

// settings dialog
const fields = { baseUrl: $('setBase'), authId: $('setAuthId'), token: $('setToken'), userId: $('setUser') };
const pill = () => { el.connPill.textContent = settings.baseUrl ? settings.baseUrl.replace(/^https?:\/\//, '') : 'Mock API'; };
$('openSettings').addEventListener('click', () => { for (const k in fields) fields[k].value = settings[k]; el.settings.showModal(); closeMenu(); });
$('settingsForm').addEventListener('submit', (e) => {
  if (e.submitter?.value !== 'save') return;
  for (const k in fields) settings[k] = fields[k].value.trim();
  store.set('filechat.settings', settings); pill(); loadConversations();
});

pill(); showEmpty(); loadConversations();
